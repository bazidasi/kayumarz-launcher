//! Double-tap detector with configurable inter-tap timeout and slop.

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};
use crate::gesture_engine::core::gesture_state::GestureSession;

#[inline]
pub fn matches_double_tap(
    session: &GestureSession,
    second_up_x_dp: f32,
    second_up_y_dp: f32,
    second_up_time_ms: u64,
    config: &GestureConfig,
) -> bool {
    let rule = match config.rule(GestureKind::DoubleTap) {
        Some(r) if r.enabled => r,
        _ => return false,
    };

    let first_time = match session.pending_tap_time_ms {
        Some(t) => t,
        None => return false,
    };

    if session.pending_tap_zone != session.zone
        || session.pending_tap_package != session.target_package
    {
        return false;
    }

    let delta_ms = second_up_time_ms.saturating_sub(first_time);
    let max_window = ((rule.timeout_ms as f32) * rule.sensitivity.clamp(0.5, 2.0)) as u64;
    if delta_ms > max_window.max(120) {
        return false;
    }

    let slop = config.double_tap_slop_dp;
    let dx = second_up_x_dp - session.pending_tap_x_dp;
    let dy = second_up_y_dp - session.pending_tap_y_dp;
    (dx * dx + dy * dy) <= (slop * slop)
}

#[inline]
pub fn is_pending_tap_expired(
    session: &GestureSession,
    now_ms: u64,
    config: &GestureConfig,
) -> bool {
    let first_time = match session.pending_tap_time_ms {
        Some(t) => t,
        None => return false,
    };
    let window = config
        .rule(GestureKind::DoubleTap)
        .map(|r| r.timeout_ms)
        .unwrap_or(260);
    now_ms.saturating_sub(first_time) >= window
}
