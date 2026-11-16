//! Event-driven Long-Press detector without background polling.

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};
use crate::gesture_engine::core::gesture_state::GestureSession;
use crate::gesture_engine::detectors::tap::is_within_tap_slop;

#[inline]
pub fn check_long_press_reached(
    session: &GestureSession,
    now_ms: u64,
    cur_x_dp: f32,
    cur_y_dp: f32,
    config: &GestureConfig,
) -> bool {
    let rule = match config.rule(GestureKind::LongPress) {
        Some(r) if r.enabled => r,
        _ => return false,
    };
    if session.long_press_triggered || session.max_pointer_count != 1 || session.drag_started {
        return false;
    }

    let elapsed = now_ms.saturating_sub(session.down_time_ms);
    if elapsed < rule.timeout_ms {
        return false;
    }

    is_within_tap_slop(
        session.start_x_dp,
        session.start_y_dp,
        cur_x_dp,
        cur_y_dp,
        config.touch_slop_dp,
    )
}
