//! In-memory app index with pre-normalized Unicode representations.
//! Supports lock-free atomic replacement via `ArcSwap`.

use std::collections::HashMap;
use std::sync::Arc;
use arc_swap::ArcSwap;
use unicode_normalization::UnicodeNormalization;

use crate::model::AppEntry;

/// Normalized entry holding pre-processed search strings to eliminate hot-path allocations.
#[derive(Debug, Clone)]
pub struct IndexedApp {
    pub entry: AppEntry,
    /// Lowercase, Unicode NFC-normalized label for zero-GC matching
    pub normalized_label: String,
    /// Fast lowercase ASCII-prefix or short substring
    pub normalized_package: String,
}

/// Snapshot of all indexed applications and fast lookup structures.
#[derive(Debug, Default)]
pub struct IndexSnapshot {
    pub apps: Vec<IndexedApp>,
    /// Fast mapping from package name to index position in `apps`
    pub package_to_idx: HashMap<String, usize>,
    /// Prefix bucket index for O(1) direct prefix checks
    pub prefix_map: HashMap<String, Vec<usize>>,
}

impl IndexSnapshot {
    pub fn new(entries: Vec<AppEntry>) -> Self {
        let count = entries.len();
        let mut apps = Vec::with_capacity(count);
        let mut package_to_idx = HashMap::with_capacity(count);
        let mut prefix_map: HashMap<String, Vec<usize>> = HashMap::with_capacity(count * 2);

        for (idx, entry) in entries.into_iter().enumerate() {
            let norm_label: String = entry.label.trim().nfc().collect::<String>().to_lowercase();
            let norm_pkg = entry.package.to_lowercase();

            // Store prefixes for instant 1-character and 2-character shortcuts
            let char_count = norm_label.chars().count();
            if char_count >= 1 {
                let first_char: String = norm_label.chars().take(1).collect();
                prefix_map.entry(first_char).or_default().push(idx);
            }
            if char_count >= 2 {
                let two_chars: String = norm_label.chars().take(2).collect();
                prefix_map.entry(two_chars).or_default().push(idx);
            }

            package_to_idx.insert(entry.package.clone(), idx);
            apps.push(IndexedApp {
                entry,
                normalized_label: norm_label,
                normalized_package: norm_pkg,
            });
        }

        Self {
            apps,
            package_to_idx,
            prefix_map,
        }
    }
}

/// Thread-safe in-memory index wrapper.
pub struct AppIndex {
    snapshot: ArcSwap<IndexSnapshot>,
}

impl AppIndex {
    pub fn new() -> Self {
        Self {
            snapshot: ArcSwap::from_pointee(IndexSnapshot::default()),
        }
    }

    /// Atomically swaps the entire application index.
    /// Rebuilding a 500-app index takes < 1 ms.
    pub fn replace_index(&self, entries: Vec<AppEntry>) {
        let new_snapshot = Arc::new(IndexSnapshot::new(entries));
        self.snapshot.store(new_snapshot);
    }

    /// Loads the current atomic snapshot without locking.
    #[inline(always)]
    pub fn load(&self) -> Arc<IndexSnapshot> {
        self.snapshot.load_full()
    }

    /// Returns the number of currently indexed applications.
    #[inline]
    pub fn len(&self) -> usize {
        self.snapshot.load().apps.len()
    }

    #[inline]
    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }
}

impl Default for AppIndex {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_index_replacement_and_normalization() {
        let index = AppIndex::new();
        let apps = vec![
            AppEntry {
                package: "org.telegram.messenger".into(),
                label: "Telegram".into(),
                user_serial: 0,
                flags: 0,
            },
            AppEntry {
                package: "com.android.chrome".into(),
                label: "Chrome".into(),
                user_serial: 0,
                flags: 0,
            },
        ];

        index.replace_index(apps);
        let snap = index.load();
        assert_eq!(snap.apps.len(), 2);
        assert_eq!(snap.apps[0].normalized_label, "telegram");
        assert_eq!(snap.apps[1].normalized_label, "chrome");
    }
}
