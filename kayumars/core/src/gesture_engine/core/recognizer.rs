//! Event-driven Hybrid Gesture Recognizer.
//! - Zero background threads or sensor polling.
//! - Skips disabled detectors immediately.
//! - Gates multi-touch detectors strictly to `SurfaceZone::HomeScreen` for battery life.

use crate::gesture_engine::actions::GestureAction;
use crate::gesture_engine::core::dispatcher::{DispatchedCommand, GestureDispatcher};
use crate::gesture_engine::core::gesture_config::GestureKind;
use crate::gesture_engine::core::gesture_state::{
    GestureFsmState, GestureSession, SurfaceZone, TouchAction, TouchEvent, TouchPoint,
};
use crate::gesture_engine::detectors::{
    double_tap::{is_pending_tap_expired, matches_double_tap},
    drag::detect_long_press_drag,
    long_press::check_long_press_reached,
    pinch::{detect_pinch, pointer_centroid_dp, pointer_span_dp},
    swipe::{detect_single_swipe, detect_two_finger_swipe},
    tap::evaluate_single_tap_candidate,
};
use crate::gesture_engine::settings::profiles::{GestureProfile, GestureProfilePreset};

pub struct GestureRecognizer {
    session: GestureSession,
    profile: GestureProfile,
    multi_gesture_emitted: bool,
}

impl Default for GestureRecognizer {
    fn default() -> Self {
        Self::new(GestureProfile::default())
    }
}

impl GestureRecognizer {
    pub fn new(profile: GestureProfile) -> Self {
        Self {
            session: GestureSession::default(),
            profile,
            multi_gesture_emitted: false,
        }
    }

    pub fn profile(&self) -> &GestureProfile {
        &self.profile
    }

    pub fn profile_mut(&mut self) -> &mut GestureProfile {
        &mut self.profile
    }

    pub fn set_preset(&mut self, preset: GestureProfilePreset) {
        let custom_icons = self.profile.icon_gestures.clone();
        let mut next = GestureProfile::from_preset(preset);
        for (pkg, map) in custom_icons {
            next.icon_gestures.entry(pkg).or_insert(map);
        }
        self.profile = next;
    }

    pub fn set_icon_gesture(
        &mut self,
        package: String,
        kind: GestureKind,
        action: GestureAction,
    ) {
        let entry = self.profile.icon_gestures.entry(package).or_default();
        entry.insert(kind, action);
    }

    pub fn fsm_state(&self) -> GestureFsmState {
        self.session.fsm
    }

    /// Evaluates a single TouchEvent from Android MotionEvent without background polling.
    pub fn process_event(&mut self, event: TouchEvent) -> DispatchedCommand {
        let zone = event.zone;
        let cfg = &self.profile.config;

        match event.action {
            TouchAction::Down => {
                if let Some(p0) = event.points.first() {
                    self.multi_gesture_emitted = false;
                    self.session.zone = zone;
                    self.session.target_package = event.target_package;
                    self.session.down_time_ms = event.timestamp_ms;
                    self.session.last_time_ms = event.timestamp_ms;
                    self.session.primary_id = p0.id;
                    self.session.start_x_dp = p0.x_dp;
                    self.session.start_y_dp = p0.y_dp;
                    self.session.last_x_dp = p0.x_dp;
                    self.session.last_y_dp = p0.y_dp;
                    self.session.max_pointer_count = event.points.len().max(1);
                    self.session.long_press_triggered = false;
                    self.session.drag_started = false;
                    self.session.fsm = GestureFsmState::PossibleTap;
                }
                DispatchedCommand::none(zone, self.session.fsm)
            }

            TouchAction::PointerDown => {
                // Multi-touch is gated to HomeScreen when `home_only_multitouch` is enabled
                self.session.max_pointer_count =
                    self.session.max_pointer_count.max(event.points.len());
                if event.points.len() >= 2
                    && (!cfg.home_only_multitouch || self.session.zone == SurfaceZone::HomeScreen)
                {
                    let (p1, p2) = (&event.points[0], &event.points[1]);
                    self.session.multi_start_span_dp = pointer_span_dp(p1, p2);
                    let (cx, cy) = pointer_centroid_dp(p1, p2);
                    self.session.multi_start_cx_dp = cx;
                    self.session.multi_start_cy_dp = cy;
                    self.session.clear_pending_tap();
                    self.session.fsm = GestureFsmState::MultiTouchTracking;
                }
                DispatchedCommand::none(self.session.zone, self.session.fsm)
            }

            TouchAction::Move => {
                self.session.last_time_ms = event.timestamp_ms;
                if event.points.is_empty() {
                    return DispatchedCommand::none(self.session.zone, self.session.fsm);
                }

                // Multi-touch branch (HomeScreen gated)
                if event.points.len() >= 2
                    && matches!(self.session.fsm, GestureFsmState::MultiTouchTracking)
                    && !self.multi_gesture_emitted
                {
                    let (p1, p2) = (&event.points[0], &event.points[1]);
                    let duration_ms = event.timestamp_ms.saturating_sub(self.session.down_time_ms);

                    if cfg.is_enabled(GestureKind::PinchIn) || cfg.is_enabled(GestureKind::PinchOut)
                    {
                        let cur_span = pointer_span_dp(p1, p2);
                        if let Some(pinch_kind) =
                            detect_pinch(self.session.multi_start_span_dp, cur_span, duration_ms, cfg)
                        {
                            self.multi_gesture_emitted = true;
                            return GestureDispatcher::dispatch(
                                &self.profile,
                                pinch_kind,
                                self.session.zone,
                                self.session.fsm,
                                None,
                                0.0,
                                0.0,
                            );
                        }
                    }

                    if cfg.is_enabled(GestureKind::TwoFingerSwipeUp)
                        || cfg.is_enabled(GestureKind::TwoFingerSwipeDown)
                    {
                        let (cx, cy) = pointer_centroid_dp(p1, p2);
                        let dx = cx - self.session.multi_start_cx_dp;
                        let dy = cy - self.session.multi_start_cy_dp;
                        if let Some(tf_kind) = detect_two_finger_swipe(dx, dy, duration_ms, cfg) {
                            self.multi_gesture_emitted = true;
                            return GestureDispatcher::dispatch(
                                &self.profile,
                                tf_kind,
                                self.session.zone,
                                self.session.fsm,
                                None,
                                dx,
                                dy,
                            );
                        }
                    }
                    return DispatchedCommand::none(self.session.zone, self.session.fsm);
                }

                // Single-pointer branch
                let p0 = find_primary(&event.points, self.session.primary_id);
                self.session.last_x_dp = p0.x_dp;
                self.session.last_y_dp = p0.y_dp;

                if check_long_press_reached(&self.session, event.timestamp_ms, p0.x_dp, p0.y_dp, cfg)
                {
                    self.session.long_press_triggered = true;
                    self.session.fsm = GestureFsmState::LongPressActive;
                    self.session.clear_pending_tap();
                    return GestureDispatcher::dispatch(
                        &self.profile,
                        GestureKind::LongPress,
                        self.session.zone,
                        self.session.fsm,
                        self.session.target_package.clone(),
                        0.0,
                        0.0,
                    );
                }

                if detect_long_press_drag(&self.session, p0.x_dp, p0.y_dp, cfg) {
                    self.session.drag_started = true;
                    self.session.fsm = GestureFsmState::Dragging;
                    return GestureDispatcher::dispatch(
                        &self.profile,
                        GestureKind::LongPressDrag,
                        self.session.zone,
                        self.session.fsm,
                        self.session.target_package.clone(),
                        p0.x_dp - self.session.start_x_dp,
                        p0.y_dp - self.session.start_y_dp,
                    );
                }

                // Early Swipe state hint if touch slop exceeded
                let dx = p0.x_dp - self.session.start_x_dp;
                let dy = p0.y_dp - self.session.start_y_dp;
                if !self.session.long_press_triggered
                    && (dx * dx + dy * dy) > (cfg.touch_slop_dp * cfg.touch_slop_dp)
                {
                    self.session.fsm = GestureFsmState::Swiping;
                }

                DispatchedCommand::none(self.session.zone, self.session.fsm)
            }

            TouchAction::PointerUp => {
                DispatchedCommand::none(self.session.zone, self.session.fsm)
            }

            TouchAction::Up => {
                let target_pkg = self.session.target_package.clone();
                let active_zone = self.session.zone;

                if self.session.max_pointer_count >= 2 || self.multi_gesture_emitted {
                    self.session.reset_active_pointer();
                    return DispatchedCommand::none(active_zone, self.session.fsm);
                }

                let p0 = event
                    .points
                    .first()
                    .copied()
                    .unwrap_or(TouchPoint {
                        id: self.session.primary_id,
                        x_dp: self.session.last_x_dp,
                        y_dp: self.session.last_y_dp,
                    });

                if self.session.drag_started || self.session.long_press_triggered {
                    self.session.reset_active_pointer();
                    return DispatchedCommand::none(active_zone, self.session.fsm);
                }

                let tap_timeout = cfg
                    .rule(GestureKind::SingleTap)
                    .map(|r| r.timeout_ms)
                    .unwrap_or(220);

                if evaluate_single_tap_candidate(
                    &self.session,
                    p0.x_dp,
                    p0.y_dp,
                    event.timestamp_ms,
                    cfg,
                    tap_timeout,
                ) {
                    // Check DoubleTap match
                    if matches_double_tap(&self.session, p0.x_dp, p0.y_dp, event.timestamp_ms, cfg)
                    {
                        self.session.clear_pending_tap();
                        self.session.reset_active_pointer();
                        return GestureDispatcher::dispatch(
                            &self.profile,
                            GestureKind::DoubleTap,
                            active_zone,
                            GestureFsmState::Idle,
                            target_pkg,
                            0.0,
                            0.0,
                        );
                    }

                    // If DoubleTap is enabled on this zone/app, buffer tap briefly or resolve immediately
                    let double_tap_possible = cfg.is_enabled(GestureKind::DoubleTap);
                    if double_tap_possible {
                        self.session.pending_tap_time_ms = Some(event.timestamp_ms);
                        self.session.pending_tap_x_dp = p0.x_dp;
                        self.session.pending_tap_y_dp = p0.y_dp;
                        self.session.pending_tap_zone = active_zone;
                        self.session.pending_tap_package = target_pkg;
                        self.session.reset_active_pointer();
                        return DispatchedCommand::none(active_zone, self.session.fsm);
                    }

                    self.session.reset_active_pointer();
                    return GestureDispatcher::dispatch(
                        &self.profile,
                        GestureKind::SingleTap,
                        active_zone,
                        GestureFsmState::Idle,
                        target_pkg,
                        0.0,
                        0.0,
                    );
                }

                // Evaluate single-finger directional swipe
                self.session.clear_pending_tap();
                let dx = p0.x_dp - self.session.start_x_dp;
                let dy = p0.y_dp - self.session.start_y_dp;
                let duration_ms = event.timestamp_ms.saturating_sub(self.session.down_time_ms);

                self.session.reset_active_pointer();
                if let Some(swipe_kind) = detect_single_swipe(dx, dy, duration_ms, cfg) {
                    return GestureDispatcher::dispatch(
                        &self.profile,
                        swipe_kind,
                        active_zone,
                        GestureFsmState::Idle,
                        target_pkg,
                        dx,
                        dy,
                    );
                }

                DispatchedCommand::none(active_zone, self.session.fsm)
            }

            TouchAction::Cancel => {
                self.session.clear_pending_tap();
                self.session.reset_active_pointer();
                DispatchedCommand::none(zone, GestureFsmState::Idle)
            }
        }
    }

    /// Event-driven timer callback invoked by Android Handler only while waiting for
    /// LongPress or DoubleTap window expiry (zero background polling).
    pub fn check_timeout(&mut self, now_ms: u64) -> DispatchedCommand {
        let cfg = &self.profile.config;

        // 1. Check active pointer reaching LongPress threshold while finger held stationary
        if matches!(self.session.fsm, GestureFsmState::PossibleTap)
            && check_long_press_reached(
                &self.session,
                now_ms,
                self.session.last_x_dp,
                self.session.last_y_dp,
                cfg,
            )
        {
            self.session.long_press_triggered = true;
            self.session.fsm = GestureFsmState::LongPressActive;
            self.session.clear_pending_tap();
            return GestureDispatcher::dispatch(
                &self.profile,
                GestureKind::LongPress,
                self.session.zone,
                self.session.fsm,
                self.session.target_package.clone(),
                0.0,
                0.0,
            );
        }

        // 2. Check pending single tap whose double-tap window just expired
        if matches!(self.session.fsm, GestureFsmState::AwaitDoubleTap)
            && is_pending_tap_expired(&self.session, now_ms, cfg)
        {
            let zone = self.session.pending_tap_zone;
            let pkg = self.session.pending_tap_package.clone();
            self.session.clear_pending_tap();
            if cfg.is_enabled(GestureKind::SingleTap) {
                return GestureDispatcher::dispatch(
                    &self.profile,
                    GestureKind::SingleTap,
                    zone,
                    GestureFsmState::Idle,
                    pkg,
                    0.0,
                    0.0,
                );
            }
        }

        DispatchedCommand::none(self.session.zone, self.session.fsm)
    }
}

#[inline]
fn find_primary(points: &[TouchPoint], primary_id: i32) -> TouchPoint {
    for p in points {
        if p.id == primary_id {
            return *p;
        }
    }
    points[0]
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::gesture_engine::actions::{
        app_action::AppAction, navigation_action::NavigationAction, system_action::SystemAction,
    };

    fn pt(id: i32, x: f32, y: f32) -> TouchPoint {
        TouchPoint {
            id,
            x_dp: x,
            y_dp: y,
        }
    }

    #[test]
    fn test_all_12_v1_gestures() {
        let mut rec = GestureRecognizer::default();

        // 1. SingleTap (after double-tap window expiry)
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 100,
            zone: SurfaceZone::AppIcon,
            target_package: Some("org.telegram.messenger".into()),
            points: vec![pt(0, 50.0, 50.0)],
        });
        let up1 = rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 150,
            zone: SurfaceZone::AppIcon,
            target_package: Some("org.telegram.messenger".into()),
            points: vec![pt(0, 51.0, 51.0)],
        });
        assert!(!up1.recognized);
        let cmd1 = rec.check_timeout(500);
        assert_eq!(cmd1.gesture, Some(GestureKind::SingleTap));
        assert_eq!(cmd1.action, GestureAction::App(AppAction::LaunchTarget));

        // 2. DoubleTap on HomeScreen -> LockScreen
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 1000,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 120.0, 300.0)],
        });
        rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 1040,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 120.0, 300.0)],
        });
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 1120,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 122.0, 302.0)],
        });
        let dt = rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 1160,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 122.0, 302.0)],
        });
        assert_eq!(dt.gesture, Some(GestureKind::DoubleTap));
        assert_eq!(dt.action, GestureAction::System(SystemAction::LockScreen));

        // 3 & 4. LongPress -> LongPressDrag
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 2000,
            zone: SurfaceZone::AppIcon,
            target_package: Some("org.telegram.messenger".into()),
            points: vec![pt(0, 80.0, 80.0)],
        });
        let lp = rec.check_timeout(2450);
        assert_eq!(lp.gesture, Some(GestureKind::LongPress));
        let drag = rec.process_event(TouchEvent {
            action: TouchAction::Move,
            timestamp_ms: 2500,
            zone: SurfaceZone::AppIcon,
            target_package: Some("org.telegram.messenger".into()),
            points: vec![pt(0, 120.0, 80.0)],
        });
        assert_eq!(drag.gesture, Some(GestureKind::LongPressDrag));
        assert_eq!(drag.action, GestureAction::App(AppAction::MoveIcon));
        rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 2550,
            zone: SurfaceZone::AppIcon,
            target_package: Some("org.telegram.messenger".into()),
            points: vec![pt(0, 120.0, 80.0)],
        });

        // 5-8. Swipe Up/Down/Left/Right
        let swipes = [
            ((100.0, 300.0), (100.0, 200.0), GestureKind::SwipeUp),
            ((100.0, 200.0), (100.0, 300.0), GestureKind::SwipeDown),
            ((200.0, 200.0), (100.0, 200.0), GestureKind::SwipeLeft),
            ((100.0, 200.0), (200.0, 200.0), GestureKind::SwipeRight),
        ];
        for (i, ((sx, sy), (ex, ey), expected)) in swipes.iter().enumerate() {
            let t0 = 3000 + (i as u64) * 500;
            rec.process_event(TouchEvent {
                action: TouchAction::Down,
                timestamp_ms: t0,
                zone: SurfaceZone::HomeScreen,
                target_package: None,
                points: vec![pt(0, *sx, *sy)],
            });
            let cmd = rec.process_event(TouchEvent {
                action: TouchAction::Up,
                timestamp_ms: t0 + 140,
                zone: SurfaceZone::HomeScreen,
                target_package: None,
                points: vec![pt(0, *ex, *ey)],
            });
            assert_eq!(cmd.gesture, Some(*expected));
        }

        // 9. PinchIn
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 6000,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 200.0)],
        });
        rec.process_event(TouchEvent {
            action: TouchAction::PointerDown,
            timestamp_ms: 6010,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 200.0), pt(1, 220.0, 200.0)],
        });
        let pinch_in = rec.process_event(TouchEvent {
            action: TouchAction::Move,
            timestamp_ms: 6150,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 130.0, 200.0), pt(1, 190.0, 200.0)],
        });
        assert_eq!(pinch_in.gesture, Some(GestureKind::PinchIn));
        assert_eq!(
            pinch_in.action,
            GestureAction::Navigation(NavigationAction::OpenOverview)
        );
        rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 6200,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![],
        });

        // 10. PinchOut
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 7000,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 140.0, 200.0)],
        });
        rec.process_event(TouchEvent {
            action: TouchAction::PointerDown,
            timestamp_ms: 7010,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 140.0, 200.0), pt(1, 180.0, 200.0)],
        });
        let pinch_out = rec.process_event(TouchEvent {
            action: TouchAction::Move,
            timestamp_ms: 7150,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 200.0), pt(1, 220.0, 200.0)],
        });
        assert_eq!(pinch_out.gesture, Some(GestureKind::PinchOut));
        assert_eq!(
            pinch_out.action,
            GestureAction::Navigation(NavigationAction::ReturnHome)
        );
        rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 7200,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![],
        });

        // 11. TwoFingerSwipeUp -> QuickSettings
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 8000,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 400.0)],
        });
        rec.process_event(TouchEvent {
            action: TouchAction::PointerDown,
            timestamp_ms: 8010,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 400.0), pt(1, 180.0, 400.0)],
        });
        let tf_up = rec.process_event(TouchEvent {
            action: TouchAction::Move,
            timestamp_ms: 8160,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 310.0), pt(1, 180.0, 310.0)],
        });
        assert_eq!(tf_up.gesture, Some(GestureKind::TwoFingerSwipeUp));
        assert_eq!(
            tf_up.action,
            GestureAction::System(SystemAction::ExpandQuickSettings)
        );
        rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 8200,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![],
        });

        // 12. TwoFingerSwipeDown -> OpenLauncherSettings
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 9000,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 200.0)],
        });
        rec.process_event(TouchEvent {
            action: TouchAction::PointerDown,
            timestamp_ms: 9010,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 200.0), pt(1, 180.0, 200.0)],
        });
        let tf_down = rec.process_event(TouchEvent {
            action: TouchAction::Move,
            timestamp_ms: 9160,
            zone: SurfaceZone::HomeScreen,
            target_package: None,
            points: vec![pt(0, 100.0, 290.0), pt(1, 180.0, 290.0)],
        });
        assert_eq!(tf_down.gesture, Some(GestureKind::TwoFingerSwipeDown));
        assert_eq!(
            tf_down.action,
            GestureAction::Navigation(NavigationAction::OpenLauncherSettings)
        );
    }

    #[test]
    fn test_per_icon_telegram_gestures() {
        let mut rec = GestureRecognizer::default();
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 100,
            zone: SurfaceZone::AppIcon,
            target_package: Some("org.telegram.messenger".into()),
            points: vec![pt(0, 60.0, 200.0)],
        });
        let swipe_up = rec.process_event(TouchEvent {
            action: TouchAction::Up,
            timestamp_ms: 220,
            zone: SurfaceZone::AppIcon,
            target_package: Some("org.telegram.messenger".into()),
            points: vec![pt(0, 60.0, 110.0)],
        });
        assert_eq!(swipe_up.gesture, Some(GestureKind::SwipeUp));
        assert_eq!(
            swipe_up.action,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "org.telegram.messenger".into(),
                shortcut_id: "tg_new_message".into(),
                label: "New Message".into(),
            })
        );
    }

    #[test]
    fn test_multitouch_gated_to_home_screen() {
        let mut rec = GestureRecognizer::default();
        rec.process_event(TouchEvent {
            action: TouchAction::Down,
            timestamp_ms: 100,
            zone: SurfaceZone::AppDrawer,
            target_package: None,
            points: vec![pt(0, 100.0, 200.0)],
        });
        rec.process_event(TouchEvent {
            action: TouchAction::PointerDown,
            timestamp_ms: 110,
            zone: SurfaceZone::AppDrawer,
            target_package: None,
            points: vec![pt(0, 100.0, 200.0), pt(1, 200.0, 200.0)],
        });
        assert_ne!(rec.fsm_state(), GestureFsmState::MultiTouchTracking);
    }
}
