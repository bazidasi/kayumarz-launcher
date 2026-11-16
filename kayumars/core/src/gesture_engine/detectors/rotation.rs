//! Optional two-finger rotation detector — disabled by default to preserve battery.

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};
use crate::gesture_engine::core::gesture_state::TouchPoint;

#[inline]
pub fn detect_rotation_deg(
    start_angle_rad: f32,
    p1: &TouchPoint,
    p2: &TouchPoint,
    config: &GestureConfig,
) -> Option<f32> {
    let rule = config.rule(GestureKind::Rotation)?;
    if !rule.enabled {
        return None;
    }
    let cur_angle = (p2.y_dp - p1.y_dp).atan2(p2.x_dp - p1.x_dp);
    let delta_deg = (cur_angle - start_angle_rad).to_degrees().abs();
    if delta_deg >= rule.effective_distance_dp() {
        Some(delta_deg)
    } else {
        None
    }
}
