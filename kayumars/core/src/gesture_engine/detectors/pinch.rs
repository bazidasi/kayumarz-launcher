//! Two-finger Pinch In / Pinch Out detector (Home screen gated for battery efficiency).

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};
use crate::gesture_engine::core::gesture_state::TouchPoint;

#[inline]
pub fn pointer_span_dp(p1: &TouchPoint, p2: &TouchPoint) -> f32 {
    let dx = p2.x_dp - p1.x_dp;
    let dy = p2.y_dp - p1.y_dp;
    (dx * dx + dy * dy).sqrt()
}

#[inline]
pub fn pointer_centroid_dp(p1: &TouchPoint, p2: &TouchPoint) -> (f32, f32) {
    ((p1.x_dp + p2.x_dp) * 0.5, (p1.y_dp + p2.y_dp) * 0.5)
}

#[inline]
pub fn detect_pinch(
    start_span_dp: f32,
    cur_span_dp: f32,
    duration_ms: u64,
    config: &GestureConfig,
) -> Option<GestureKind> {
    if start_span_dp <= 1.0 {
        return None;
    }
    let delta = cur_span_dp - start_span_dp;
    let kind = if delta < 0.0 {
        GestureKind::PinchIn
    } else {
        GestureKind::PinchOut
    };
    let rule = config.rule(kind)?;
    if !rule.enabled || duration_ms > rule.timeout_ms {
        return None;
    }
    if delta.abs() >= rule.effective_distance_dp() {
        Some(kind)
    } else {
        None
    }
}
