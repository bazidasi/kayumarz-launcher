//! In-memory bounded launch event history with automatic compaction.
//! Maintains an append-only ring buffer of recent launches and an aggregate frequency table.

use parking_lot::RwLock;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

use crate::model::HistoryEvent;

/// Maximum number of discrete launch events stored before auto-compaction.
pub const MAX_HISTORY_EVENTS: usize = 10_000;
/// Keep the most recent N events intact during compaction.
pub const COMPACT_RETAIN_EVENTS: usize = 2_000;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HistoryData {
    /// Append-only recent launch events
    pub events: Vec<HistoryEvent>,
    /// Compacted historical launch count per package
    pub historical_counts: HashMap<String, u32>,
    /// Total lifetime launches counted
    pub total_lifetime_launches: u64,
}

impl Default for HistoryData {
    fn default() -> Self {
        Self {
            events: Vec::with_capacity(1024),
            historical_counts: HashMap::new(),
            total_lifetime_launches: 0,
        }
    }
}

pub struct HistoryTracker {
    data: RwLock<HistoryData>,
}

impl HistoryTracker {
    pub fn new() -> Self {
        Self {
            data: RwLock::new(HistoryData::default()),
        }
    }

    pub fn from_data(data: HistoryData) -> Self {
        Self {
            data: RwLock::new(data),
        }
    }

    /// Logs an application launch event.
    /// Thread-safe and bounded: automatically compacts older events when threshold is exceeded.
    pub fn record_launch(&self, package: String, timestamp_ms: u64) {
        let mut guard = self.data.write();
        guard.total_lifetime_launches += 1;
        guard.events.push(HistoryEvent {
            package,
            timestamp_ms,
        });

        if guard.events.len() >= MAX_HISTORY_EVENTS {
            Self::compact_internal(&mut guard);
        }
    }

    fn compact_internal(data: &mut HistoryData) {
        let drain_count = data.events.len().saturating_sub(COMPACT_RETAIN_EVENTS);
        if drain_count == 0 {
            return;
        }

        // Fold drained events into historical counts
        for event in data.events.drain(0..drain_count) {
            *data.historical_counts.entry(event.package).or_insert(0) += 1;
        }
    }

    /// Returns:
    /// - `launch_count`: total launches for this app (recent + compacted)
    /// - `total_launches`: all launches across all apps
    /// - `recent_position`: 1-based index of how recently this package was launched (1 = most recent), or None if never
    pub fn get_stats_for_app(&self, package: &str) -> (u32, u64, Option<usize>) {
        let guard = self.data.read();
        let total = guard.total_lifetime_launches;
        let mut app_launches = *guard.historical_counts.get(package).unwrap_or(&0);

        let mut recent_position = None;
        let mut unique_seen = 0usize;
        let mut seen_packages = HashMap::new();

        // Traverse events from most recent to oldest
        for (i, event) in guard.events.iter().rev().enumerate() {
            if event.package == package {
                app_launches += 1;
                if recent_position.is_none() {
                    recent_position = Some(unique_seen + 1);
                }
            }
            if seen_packages.insert(&event.package, ()).is_none() {
                unique_seen += 1;
            }
            // Optimization: if we already found the app's most recent position, continue accumulating count
            let _ = i;
        }

        (app_launches, total, recent_position)
    }

    /// Returns map of package -> (launches, recent_order) for bulk ranking
    pub fn get_all_stats(&self) -> (HashMap<String, u32>, u64, HashMap<String, usize>) {
        let guard = self.data.read();
        let total = guard.total_lifetime_launches;
        let mut counts = guard.historical_counts.clone();
        let mut recency_order = HashMap::new();
        let mut order_counter = 1usize;

        for event in guard.events.iter().rev() {
            *counts.entry(event.package.clone()).or_insert(0) += 1;
            recency_order.entry(event.package.clone()).or_insert_with(|| {
                let pos = order_counter;
                order_counter += 1;
                pos
            });
        }

        (counts, total, recency_order)
    }

    /// Snapshot for bincode persistence.
    pub fn snapshot(&self) -> HistoryData {
        self.data.read().clone()
    }
}

impl Default for HistoryTracker {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_record_and_recency() {
        let tracker = HistoryTracker::new();
        tracker.record_launch("app.a".into(), 100);
        tracker.record_launch("app.b".into(), 200);
        tracker.record_launch("app.a".into(), 300);

        let (a_count, total, a_recency) = tracker.get_stats_for_app("app.a");
        assert_eq!(a_count, 2);
        assert_eq!(total, 3);
        assert_eq!(a_recency, Some(1)); // Most recent

        let (b_count, _, b_recency) = tracker.get_stats_for_app("app.b");
        assert_eq!(b_count, 1);
        assert_eq!(b_recency, Some(2)); // Second most recent
    }
}
