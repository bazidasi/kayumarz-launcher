//! LongPress + Drag detector for moving home screen icons with minimal math.

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};
use crate::gesture_engine::core::gesture_state::GestureSession;

#[inline]
pub fn detect_long_press_drag(
    session: &GestureSession,
    cur_x_dp: f32,
    cur_y_dp: f32,
    config: &GestureConfig,
) -> bool {
    if !session.long_press_triggered || session.max_pointer_count != 1 {
        return false;
    }
    let rule = match config.rule(GestureKind::LongPressDrag) {
        Some(r) if r.enabled => r,
        _ => return false,
    };

    let dx = cur_x_dp - session.start_x_dp;
    let dy = cur_y_dp - session.start_y_dp;
    let min_dist = rule.effective_distance_dp();
    (dx * dx + dy * dy) >= (min_dist * min_dist)
}
