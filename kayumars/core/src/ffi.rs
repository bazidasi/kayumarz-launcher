//! UniFFI FFI boundary exporting the high-performance Rust core to Kotlin.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use parking_lot::Mutex;

use crate::gesture_engine::{
    core::dispatcher::DispatchedCommand,
    core::gesture_config::{GestureKind, GestureRule},
    core::gesture_state::TouchEvent,
    core::recognizer::GestureRecognizer,
    settings::persistence::GesturePersistence,
    settings::profiles::GestureProfilePreset,
    GestureAction,
};
use crate::history::{HistoryData, HistoryTracker};
use crate::index::AppIndex;
use crate::model::{AppEntry, SearchResult};
use crate::rank::rank_top_apps;
use crate::search::SearchEngine;
use crate::store::{PersistentState, StateStore};

#[derive(uniffi::Object)]
pub struct Kayumars {
    index: Arc<AppIndex>,
    history: Arc<HistoryTracker>,
    search_engine: Arc<SearchEngine>,
    store: Arc<StateStore>,
    gesture_store: Arc<GesturePersistence>,
    gesture_recognizer: Mutex<GestureRecognizer>,
    last_save: Mutex<Instant>,
    dirty: AtomicBool,
}

#[uniffi::export]
impl Kayumars {
    /// Initialises Kayumars with persistent cache directory.
    /// Loads cached apps, launch logs, and gesture profiles.
    #[uniffi::constructor]
    pub fn new(config_dir: String) -> Arc<Kayumars> {
        let store = Arc::new(StateStore::new(&config_dir));
        let loaded = store.load();
        let gesture_store = Arc::new(GesturePersistence::new(&config_dir));
        let loaded_gestures = gesture_store.load();

        let index = Arc::new(AppIndex::new());
        if !loaded.entries.is_empty() {
            index.replace_index(loaded.entries);
        }

        let history = Arc::new(HistoryTracker::from_data(loaded.history));
        let search_engine = Arc::new(SearchEngine::new());

        Arc::new(Kayumars {
            index,
            history,
            search_engine,
            store,
            gesture_store,
            gesture_recognizer: Mutex::new(GestureRecognizer::new(loaded_gestures)),
            last_save: Mutex::new(Instant::now()),
            dirty: AtomicBool::new(false),
        })
    }

    /// Processes a single Android MotionEvent snapshot in Rust and returns a decoupled command.
    pub fn process_touch_event(&self, event: TouchEvent) -> DispatchedCommand {
        self.gesture_recognizer.lock().process_event(event)
    }

    /// Event-driven timeout check for LongPress or DoubleTap expiry.
    pub fn check_gesture_timeout(&self, now_ms: u64) -> DispatchedCommand {
        self.gesture_recognizer.lock().check_timeout(now_ms)
    }

    /// Switches active gesture profile preset and persists to disk.
    pub fn set_gesture_profile(&self, preset: GestureProfilePreset) {
        let mut rec = self.gesture_recognizer.lock();
        rec.set_preset(preset);
        let _ = self.gesture_store.save(rec.profile());
    }

    pub fn get_gesture_profile_preset(&self) -> GestureProfilePreset {
        self.gesture_recognizer.lock().profile().preset
    }

    /// Updates a specific gesture rule (sensitivity, distance, timeout, enabled, action).
    pub fn set_gesture_rule(&self, rule: GestureRule) {
        let mut rec = self.gesture_recognizer.lock();
        rec.profile_mut().config.rules.insert(rule.kind, rule);
        let _ = self.gesture_store.save(rec.profile());
    }

    /// Configures a dedicated gesture on a specific app icon (e.g., Telegram shortcuts).
    pub fn set_icon_gesture(&self, package: String, kind: GestureKind, action: GestureAction) {
        let mut rec = self.gesture_recognizer.lock();
        rec.set_icon_gesture(package, kind, action);
        let _ = self.gesture_store.save(rec.profile());
    }

    /// Atomically replaces the application index from PackageManager scan.
    pub fn replace_index(&self, apps: Vec<AppEntry>) {
        self.index.replace_index(apps);
        self.dirty.store(true, Ordering::Release);
        self.debounced_save();
    }

    /// Executes sub-5ms fuzzy search with frequency and recency re-ranking.
    pub fn search(&self, query: String, max_results: u8) -> Vec<SearchResult> {
        let snapshot = self.index.load();
        let (counts, total, recency) = self.history.get_all_stats();

        self.search_engine.search(
            &snapshot,
            &query,
            max_results as usize,
            &counts,
            total,
            &recency,
        )
    }

    /// Records an app launch event to update frequency and recency weighting.
    pub fn log_launch(&self, package: String, timestamp_ms: u64) {
        self.history.record_launch(package, timestamp_ms);
        self.dirty.store(true, Ordering::Release);
        self.debounced_save();
    }

    /// Clears all launch history and immediately persists the empty state.
    pub fn reset_history(&self) {
        self.history.reset();
        self.save_now();
    }

    /// Returns top suggested apps when the search bar is empty.
    pub fn top_apps(&self, max_results: u8) -> Vec<SearchResult> {
        let snapshot = self.index.load();
        let entries: Vec<AppEntry> = snapshot.apps.iter().map(|a| a.entry.clone()).collect();
        let (counts, total, recency) = self.history.get_all_stats();

        rank_top_apps(
            &entries,
            &counts,
            total,
            &recency,
            max_results as usize,
        )
    }

    /// Immediately flushes all index and history states to disk.
    pub fn save_now(&self) {
        let snapshot = self.index.load();
        let entries: Vec<AppEntry> = snapshot.apps.iter().map(|a| a.entry.clone()).collect();
        let history_data: HistoryData = self.history.snapshot();

        let state = PersistentState {
            version: 1,
            entries,
            history: history_data,
        };

        let _ = self.store.save(&state);
        let _ = self.gesture_store.save(self.gesture_recognizer.lock().profile());
        self.dirty.store(false, Ordering::Release);
        *self.last_save.lock() = Instant::now();
    }
}

impl Kayumars {
    /// Debounces file disk writes by 2 seconds to avoid flash wear.
    fn debounced_save(&self) {
        let now = Instant::now();
        let mut last = self.last_save.lock();
        if now.duration_since(*last) >= Duration::from_secs(2) {
            *last = now;
            drop(last);
            self.save_now();
        }
    }
}
