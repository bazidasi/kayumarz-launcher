//! Optional edge-swipe detector — disabled by default to preserve battery.

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};

#[inline]
pub fn detect_edge_swipe(
    start_x_dp: f32,
    screen_width_dp: f32,
    dx_dp: f32,
    config: &GestureConfig,
) -> Option<GestureKind> {
    let rule = config.rule(GestureKind::EdgeSwipe)?;
    if !rule.enabled {
        return None;
    }
    let edge_band = 24.0;
    let from_left = start_x_dp <= edge_band && dx_dp >= rule.effective_distance_dp();
    let from_right =
        start_x_dp >= (screen_width_dp - edge_band) && -dx_dp >= rule.effective_distance_dp();
    if from_left || from_right {
        Some(GestureKind::EdgeSwipe)
    } else {
        None
    }
}
