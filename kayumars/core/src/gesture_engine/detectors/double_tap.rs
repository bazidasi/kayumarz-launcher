//! Double-tap detector with configurable inter-tap timeout and slop.

use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind, GestureRule};
use crate::gesture_engine::core::gesture_state::GestureSession;

/// Sensitivity-scaled inter-tap window shared by matching and expiry so a
/// pending tap is never resolved before its double-tap window has closed.
#[inline]
fn double_tap_window_ms(rule: &GestureRule) -> u64 {
    let scaled = ((rule.timeout_ms as f32) * rule.sensitivity.clamp(0.5, 2.0)) as u64;
    scaled.max(120)
}

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
    if delta_ms > double_tap_window_ms(rule) {
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
        .map(double_tap_window_ms)
        .unwrap_or(260);
    now_ms.saturating_sub(first_time) >= window
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::gesture_engine::core::gesture_state::SurfaceZone;

    fn session_with_pending(t: u64) -> GestureSession {
        let mut s = GestureSession::default();
        s.zone = SurfaceZone::HomeScreen;
        s.pending_tap_time_ms = Some(t);
        s.pending_tap_zone = SurfaceZone::HomeScreen;
        s
    }

    #[test]
    fn expiry_window_matches_sensitivity_scaled_match_window() {
        let mut config = GestureConfig::default();
        let rule = config.rules.get_mut(&GestureKind::DoubleTap).unwrap();
        rule.sensitivity = 2.0; // window = 260 * 2 = 520 ms

        let session = session_with_pending(1000);
        // At 400 ms after the first tap the match window is still open,
        // so the pending tap must not have expired yet.
        assert!(matches_double_tap(&session, 0.0, 0.0, 1400, &config));
        assert!(!is_pending_tap_expired(&session, 1400, &config));
        assert!(is_pending_tap_expired(&session, 1520, &config));
    }
}
