//! Kayumars Hybrid Gesture Engine
//!
//! Battery-conscious, event-driven geometric gesture recognition in pure Rust.
//! Strictly decoupled from Android OS execution: recognizes raw touch geometry
//! and dispatches semantic `GestureCommand` values for the Kotlin/Java shell.

pub mod actions;
pub mod core;
pub mod detectors;
pub mod settings;

pub use actions::app_action::AppAction;
pub use actions::custom_action::CustomAction;
pub use actions::navigation_action::NavigationAction;
pub use actions::system_action::SystemAction;
pub use actions::GestureAction;
pub use core::dispatcher::{DispatchedCommand, GestureDispatcher};
pub use core::gesture_config::{GestureConfig, GestureKind, GestureRule};
pub use core::gesture_state::{
    GestureFsmState, SurfaceZone, TouchAction, TouchEvent, TouchPoint,
};
pub use core::recognizer::GestureRecognizer;
pub use settings::profiles::{GestureProfile, GestureProfilePreset, IconGestureMap};
