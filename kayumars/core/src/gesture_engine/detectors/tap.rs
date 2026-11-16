//! Single-tap detector using squared touch slop (zero sqrt).

use crate::gesture_engine::core::gesture_config::GestureConfig;
use crate::gesture_engine::core::gesture_state::GestureSession;

#[inline]
pub fn is_within_tap_slop(
    start_x_dp: f32,
    start_y_dp: f32,
    cur_x_dp: f32,
    cur_y_dp: f32,
    slop_dp: f32,
) -> bool {
    let dx = cur_x_dp - start_x_dp;
    let dy = cur_y_dp - start_y_dp;
    (dx * dx + dy * dy) <= (slop_dp * slop_dp)
}

#[inline]
pub fn evaluate_single_tap_candidate(
    session: &GestureSession,
    up_x_dp: f32,
    up_y_dp: f32,
    up_time_ms: u64,
    config: &GestureConfig,
    timeout_ms: u64,
) -> bool {
    if session.max_pointer_count > 1 || session.long_press_triggered || session.drag_started {
        return false;
    }
    let elapsed = up_time_ms.saturating_sub(session.down_time_ms);
    if elapsed > timeout_ms {
        return false;
    }
    is_within_tap_slop(
        session.start_x_dp,
        session.start_y_dp,
        up_x_dp,
        up_y_dp,
        config.touch_slop_dp,
    )
}
