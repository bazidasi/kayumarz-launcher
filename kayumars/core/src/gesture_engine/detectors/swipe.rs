//! Directional swipe detector for single-finger and two-finger swipes.

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};

#[inline]
pub fn detect_single_swipe(
    dx_dp: f32,
    dy_dp: f32,
    duration_ms: u64,
    config: &GestureConfig,
) -> Option<GestureKind> {
    let abs_x = dx_dp.abs();
    let abs_y = dy_dp.abs();

    if abs_y >= abs_x {
        let kind = if dy_dp < 0.0 {
            GestureKind::SwipeUp
        } else {
            GestureKind::SwipeDown
        };
        let rule = config.rule(kind)?;
        if !rule.enabled || duration_ms > rule.timeout_ms {
            return None;
        }
        if abs_y >= rule.effective_distance_dp() {
            return Some(kind);
        }
    } else {
        let kind = if dx_dp < 0.0 {
            GestureKind::SwipeLeft
        } else {
            GestureKind::SwipeRight
        };
        let rule = config.rule(kind)?;
        if !rule.enabled || duration_ms > rule.timeout_ms {
            return None;
        }
        if abs_x >= rule.effective_distance_dp() {
            return Some(kind);
        }
    }
    None
}

#[inline]
pub fn detect_two_finger_swipe(
    dx_centroid_dp: f32,
    dy_centroid_dp: f32,
    duration_ms: u64,
    config: &GestureConfig,
) -> Option<GestureKind> {
    if dy_centroid_dp.abs() < dx_centroid_dp.abs() {
        return None;
    }
    let kind = if dy_centroid_dp < 0.0 {
        GestureKind::TwoFingerSwipeUp
    } else {
        GestureKind::TwoFingerSwipeDown
    };
    let rule = config.rule(kind)?;
    if !rule.enabled || duration_ms > rule.timeout_ms {
        return None;
    }
    if dy_centroid_dp.abs() >= rule.effective_distance_dp() {
        Some(kind)
    } else {
        None
    }
}
