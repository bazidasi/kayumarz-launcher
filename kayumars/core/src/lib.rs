//! Kayumars Core Engine — Fast by doing less, fast by Rust.
//!
//! Sub-millisecond fuzzy search, zero-allocation matching paths,
//! KISS frequency-recency ranking, and bincode atomic persistence.

pub mod classify;
pub mod ffi;
pub mod gesture_engine;
pub mod history;
pub mod index;
pub mod jni_bridge;
pub mod model;
pub mod rank;
pub mod search;
pub mod store;

pub use ffi::Kayumars;
pub use gesture_engine::{
    DispatchedCommand, GestureAction, GestureKind, GestureProfilePreset, GestureRule,
    SurfaceZone, TouchAction, TouchEvent, TouchPoint,
};
pub use model::{AppEntry, SearchResult, SearchResultKind};

uniffi::setup_scaffolding!();
