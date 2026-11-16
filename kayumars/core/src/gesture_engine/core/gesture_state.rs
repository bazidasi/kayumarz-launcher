//! Event-driven touch states and finite-state-machine transitions.
//! Uses lightweight integer timestamps and dp coordinates to avoid allocations.

use serde::{Deserialize, Serialize};

/// Surface where the touch sequence originated.
/// Multi-touch detectors are gated strictly to `HomeScreen` to save battery.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, uniffi::Enum)]
pub enum SurfaceZone {
    HomeScreen,
    AppIcon,
    AppDrawer,
    SearchOverlay,
    SettingsSheet,
}

/// Low-level pointer phase emitted by Android MotionEvent.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum TouchAction {
    Down,
    PointerDown,
    Move,
    PointerUp,
    Up,
    Cancel,
}

/// Compact pointer sample in density-independent pixels (dp).
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TouchPoint {
    pub id: i32,
    pub x_dp: f32,
    pub y_dp: f32,
}

/// Touch event passed from Android View/Activity into the Rust engine.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct TouchEvent {
    pub action: TouchAction,
    pub timestamp_ms: u64,
    pub zone: SurfaceZone,
    /// Present when touch started over a launcher icon (e.g., "org.telegram.messenger").
    pub target_package: Option<String>,
    pub points: Vec<TouchPoint>,
}

/// Recognition finite-state machine phases.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum GestureFsmState {
    Idle,
    PossibleTap,
    AwaitDoubleTap,
    LongPressActive,
    Dragging,
    Swiping,
    MultiTouchTracking,
}

/// Snapshot held between Down -> Move -> Up for zero-allocation tracking.
#[derive(Debug, Clone)]
pub struct GestureSession {
    pub fsm: GestureFsmState,
    pub zone: SurfaceZone,
    pub target_package: Option<String>,
    pub down_time_ms: u64,
    pub last_time_ms: u64,
    pub primary_id: i32,
    pub start_x_dp: f32,
    pub start_y_dp: f32,
    pub last_x_dp: f32,
    pub last_y_dp: f32,
    pub max_pointer_count: usize,
    pub multi_start_span_dp: f32,
    pub multi_start_cx_dp: f32,
    pub multi_start_cy_dp: f32,
    pub long_press_triggered: bool,
    pub drag_started: bool,
    pub pending_tap_time_ms: Option<u64>,
    pub pending_tap_x_dp: f32,
    pub pending_tap_y_dp: f32,
    pub pending_tap_zone: SurfaceZone,
    pub pending_tap_package: Option<String>,
}

impl Default for GestureSession {
    fn default() -> Self {
        Self {
            fsm: GestureFsmState::Idle,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            down_time_ms: 0,
            last_time_ms: 0,
            primary_id: -1,
            start_x_dp: 0.0,
            start_y_dp: 0.0,
            last_x_dp: 0.0,
            last_y_dp: 0.0,
            max_pointer_count: 0,
            multi_start_span_dp: 0.0,
            multi_start_cx_dp: 0.0,
            multi_start_cy_dp: 0.0,
            long_press_triggered: false,
            drag_started: false,
            pending_tap_time_ms: None,
            pending_tap_x_dp: 0.0,
            pending_tap_y_dp: 0.0,
            pending_tap_zone: SurfaceZone::HomeScreen,
            pending_tap_package: None,
        }
    }
}

impl GestureSession {
    pub fn reset_active_pointer(&mut self) {
        self.fsm = if self.pending_tap_time_ms.is_some() {
            GestureFsmState::AwaitDoubleTap
        } else {
            GestureFsmState::Idle
        };
        self.primary_id = -1;
        self.max_pointer_count = 0;
        self.multi_start_span_dp = 0.0;
        self.long_press_triggered = false;
        self.drag_started = false;
        self.target_package = None;
    }

    pub fn clear_pending_tap(&mut self) {
        self.pending_tap_time_ms = None;
        self.pending_tap_package = None;
        if matches!(self.fsm, GestureFsmState::AwaitDoubleTap) {
            self.fsm = GestureFsmState::Idle;
        }
    }
}
