//! Per-gesture sensitivity, distance, timeout, and battery optimization configuration.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

use crate::gesture_engine::actions::GestureAction;

/// All gestures supported by the Kayumars Gesture Engine.
/// Includes the 12 mandatory v1.0 gestures plus 2 optional (disabled by default) detectors.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, uniffi::Enum)]
pub enum GestureKind {
    // --- v1.0 Mandatory Basic & Drag Gestures ---
    SingleTap,
    DoubleTap,
    LongPress,
    LongPressDrag,
    SwipeUp,
    SwipeDown,
    SwipeLeft,
    SwipeRight,
    // --- v1.0 Mandatory Multi-Touch Gestures ---
    PinchIn,
    PinchOut,
    TwoFingerSwipeUp,
    TwoFingerSwipeDown,
    // --- Optional Future Detectors (Disabled by default for battery) ---
    Rotation,
    EdgeSwipe,
}

impl GestureKind {
    pub fn is_multitouch(self) -> bool {
        matches!(
            self,
            GestureKind::PinchIn
                | GestureKind::PinchOut
                | GestureKind::TwoFingerSwipeUp
                | GestureKind::TwoFingerSwipeDown
                | GestureKind::Rotation
        )
    }

    pub fn all_v1() -> &'static [GestureKind] {
        &[
            GestureKind::SingleTap,
            GestureKind::DoubleTap,
            GestureKind::LongPress,
            GestureKind::LongPressDrag,
            GestureKind::SwipeUp,
            GestureKind::SwipeDown,
            GestureKind::SwipeLeft,
            GestureKind::SwipeRight,
            GestureKind::PinchIn,
            GestureKind::PinchOut,
            GestureKind::TwoFingerSwipeUp,
            GestureKind::TwoFingerSwipeDown,
        ]
    }
}

/// Configurable rule for an individual gesture.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct GestureRule {
    pub kind: GestureKind,
    pub enabled: bool,
    /// Multiplier (0.5 = less sensitive / needs larger movement, 1.0 = default, 2.0 = ultra sensitive)
    pub sensitivity: f32,
    /// Minimum travel distance in dp (scaled inversely by sensitivity)
    pub min_distance_dp: f32,
    /// Detection speed / timeout window in milliseconds
    pub timeout_ms: u64,
    /// Semantic action dispatched when triggered on Home screen
    pub action: GestureAction,
}

impl GestureRule {
    pub fn effective_distance_dp(&self) -> f32 {
        let s = self.sensitivity.clamp(0.4, 2.5);
        (self.min_distance_dp / s).max(4.0)
    }
}

/// Engine-wide thresholds and enabled gesture rules.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct GestureConfig {
    /// Touch slop before a tap becomes a movement (dp)
    pub touch_slop_dp: f32,
    /// Maximum distance between first and second tap of DoubleTap (dp)
    pub double_tap_slop_dp: f32,
    /// Restrict multi-touch detectors strictly to HomeScreen for battery savings
    pub home_only_multitouch: bool,
    /// Disable any unused optional detectors (Rotation, EdgeSwipe)
    pub skip_disabled_detectors: bool,
    /// Per-gesture rules
    pub rules: HashMap<GestureKind, GestureRule>,
}

impl Default for GestureConfig {
    fn default() -> Self {
        use crate::gesture_engine::actions::{
            app_action::AppAction, navigation_action::NavigationAction, system_action::SystemAction,
        };

        let mut rules = HashMap::new();
        let insert = |map: &mut HashMap<GestureKind, GestureRule>,
                      kind: GestureKind,
                      enabled: bool,
                      sensitivity: f32,
                      min_distance_dp: f32,
                      timeout_ms: u64,
                      action: GestureAction| {
            map.insert(
                kind,
                GestureRule {
                    kind,
                    enabled,
                    sensitivity,
                    min_distance_dp,
                    timeout_ms,
                    action,
                },
            );
        };

        insert(&mut rules, GestureKind::SingleTap, true, 1.0, 0.0, 220, GestureAction::App(AppAction::LaunchTarget));
        insert(&mut rules, GestureKind::DoubleTap, true, 1.0, 0.0, 260, GestureAction::System(SystemAction::LockScreen));
        insert(&mut rules, GestureKind::LongPress, true, 1.0, 0.0, 420, GestureAction::Navigation(NavigationAction::OpenEditMenu));
        insert(&mut rules, GestureKind::LongPressDrag, true, 1.0, 14.0, 420, GestureAction::App(AppAction::MoveIcon));
        insert(&mut rules, GestureKind::SwipeUp, true, 1.0, 56.0, 500, GestureAction::Navigation(NavigationAction::OpenAppDrawer));
        insert(&mut rules, GestureKind::SwipeDown, true, 1.0, 56.0, 500, GestureAction::System(SystemAction::ExpandNotifications));
        insert(&mut rules, GestureKind::SwipeLeft, true, 1.0, 64.0, 450, GestureAction::Navigation(NavigationAction::NextPage));
        insert(&mut rules, GestureKind::SwipeRight, true, 1.0, 64.0, 450, GestureAction::Navigation(NavigationAction::PreviousPage));
        insert(&mut rules, GestureKind::PinchIn, true, 1.0, 44.0, 650, GestureAction::Navigation(NavigationAction::OpenOverview));
        insert(&mut rules, GestureKind::PinchOut, true, 1.0, 44.0, 650, GestureAction::Navigation(NavigationAction::ReturnHome));
        insert(&mut rules, GestureKind::TwoFingerSwipeUp, true, 1.0, 64.0, 550, GestureAction::System(SystemAction::ExpandQuickSettings));
        insert(&mut rules, GestureKind::TwoFingerSwipeDown, true, 1.0, 64.0, 550, GestureAction::Navigation(NavigationAction::OpenLauncherSettings));

        // Optional detectors disabled by default for optimal battery
        insert(&mut rules, GestureKind::Rotation, false, 1.0, 35.0, 700, GestureAction::None);
        insert(&mut rules, GestureKind::EdgeSwipe, false, 1.0, 48.0, 400, GestureAction::Navigation(NavigationAction::PreviousPage));

        Self {
            touch_slop_dp: 12.0,
            double_tap_slop_dp: 36.0,
            home_only_multitouch: true,
            skip_disabled_detectors: true,
            rules,
        }
    }
}

impl GestureConfig {
    pub fn is_enabled(&self, kind: GestureKind) -> bool {
        self.rules.get(&kind).map(|r| r.enabled).unwrap_or(false)
    }

    pub fn rule(&self, kind: GestureKind) -> Option<&GestureRule> {
        self.rules.get(&kind)
    }
}
