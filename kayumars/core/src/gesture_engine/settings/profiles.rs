//! Gesture Profiles & Per-Icon Gesture Registry (e.g., Telegram icon shortcuts).

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

use crate::gesture_engine::actions::{
    app_action::AppAction, navigation_action::NavigationAction, system_action::SystemAction,
    GestureAction,
};
use crate::gesture_engine::core::gesture_config::{GestureConfig, GestureKind};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, uniffi::Enum)]
pub enum GestureProfilePreset {
    BalancedDefault,
    BatterySaver,
    OneHanded,
    PowerUser,
}

/// Per-icon gesture mapping: package_name -> (GestureKind -> GestureAction).
pub type IconGestureMap = HashMap<String, HashMap<GestureKind, GestureAction>>;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct GestureProfile {
    pub preset: GestureProfilePreset,
    pub config: GestureConfig,
    pub icon_gestures: IconGestureMap,
}

impl Default for GestureProfile {
    fn default() -> Self {
        Self::from_preset(GestureProfilePreset::BalancedDefault)
    }
}

impl GestureProfile {
    pub fn from_preset(preset: GestureProfilePreset) -> Self {
        let mut config = GestureConfig::default();

        match preset {
            GestureProfilePreset::BalancedDefault => {}
            GestureProfilePreset::BatterySaver => {
                // Disable multi-touch & drag polling overhead when user wants ultra-low wakeups
                for kind in [
                    GestureKind::PinchIn,
                    GestureKind::PinchOut,
                    GestureKind::TwoFingerSwipeUp,
                    GestureKind::TwoFingerSwipeDown,
                    GestureKind::Rotation,
                    GestureKind::EdgeSwipe,
                ] {
                    if let Some(rule) = config.rules.get_mut(&kind) {
                        rule.enabled = false;
                    }
                }
            }
            GestureProfilePreset::OneHanded => {
                // SwipeDown triggers QuickSettings or Search easily with lower min distance
                if let Some(r) = config.rules.get_mut(&GestureKind::SwipeDown) {
                    r.action = GestureAction::Navigation(NavigationAction::OpenSearch);
                    r.min_distance_dp = 44.0;
                    r.sensitivity = 1.25;
                }
                if let Some(r) = config.rules.get_mut(&GestureKind::SwipeUp) {
                    r.min_distance_dp = 44.0;
                    r.sensitivity = 1.25;
                }
                if let Some(r) = config.rules.get_mut(&GestureKind::TwoFingerSwipeDown) {
                    r.action = GestureAction::System(SystemAction::ExpandNotifications);
                }
            }
            GestureProfilePreset::PowerUser => {
                // Enable EdgeSwipe and tighter latencies for fast power navigation
                if let Some(r) = config.rules.get_mut(&GestureKind::EdgeSwipe) {
                    r.enabled = true;
                }
                if let Some(r) = config.rules.get_mut(&GestureKind::SwipeDown) {
                    r.action = GestureAction::Navigation(NavigationAction::OpenSearch);
                }
                if let Some(r) = config.rules.get_mut(&GestureKind::DoubleTap) {
                    r.timeout_ms = 220;
                }
            }
        }

        Self {
            preset,
            config,
            icon_gestures: Self::default_icon_gestures(),
        }
    }

    /// Pre-configured high-utility per-icon gestures (including Telegram example).
    pub fn default_icon_gestures() -> IconGestureMap {
        let mut map = HashMap::new();

        // Telegram: Tap -> Open, DoubleTap -> Saved Messages, SwipeUp -> New Message,
        // SwipeDown -> Search, SwipeLeft -> Contacts, SwipeRight -> Settings
        let mut telegram = HashMap::new();
        telegram.insert(
            GestureKind::SingleTap,
            GestureAction::App(AppAction::LaunchTarget),
        );
        telegram.insert(
            GestureKind::DoubleTap,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "org.telegram.messenger".into(),
                shortcut_id: "tg_saved_messages".into(),
                label: "Saved Messages".into(),
            }),
        );
        telegram.insert(
            GestureKind::SwipeUp,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "org.telegram.messenger".into(),
                shortcut_id: "tg_new_message".into(),
                label: "New Message".into(),
            }),
        );
        telegram.insert(
            GestureKind::SwipeDown,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "org.telegram.messenger".into(),
                shortcut_id: "tg_search".into(),
                label: "Search".into(),
            }),
        );
        telegram.insert(
            GestureKind::SwipeLeft,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "org.telegram.messenger".into(),
                shortcut_id: "tg_contacts".into(),
                label: "Contacts".into(),
            }),
        );
        telegram.insert(
            GestureKind::SwipeRight,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "org.telegram.messenger".into(),
                shortcut_id: "tg_settings".into(),
                label: "Telegram Settings".into(),
            }),
        );
        map.insert("org.telegram.messenger".into(), telegram);

        // Camera: DoubleTap -> Selfie / Video Mode, SwipeUp -> QR Scanner
        let mut camera = HashMap::new();
        camera.insert(
            GestureKind::SwipeUp,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "com.android.camera".into(),
                shortcut_id: "cam_video".into(),
                label: "Record Video".into(),
            }),
        );
        camera.insert(
            GestureKind::DoubleTap,
            GestureAction::App(AppAction::LaunchShortcut {
                package: "com.android.camera".into(),
                shortcut_id: "cam_selfie".into(),
                label: "Front Camera".into(),
            }),
        );
        map.insert("com.android.camera".into(), camera);

        map
    }

    pub fn resolve_action(
        &self,
        kind: GestureKind,
        target_package: Option<&str>,
    ) -> GestureAction {
        if let Some(pkg) = target_package {
            if let Some(per_app) = self.icon_gestures.get(pkg) {
                if let Some(act) = per_app.get(&kind) {
                    return act.clone();
                }
            }
            // Fallback sensible icon defaults if package has no explicit override
            match kind {
                GestureKind::SingleTap => return GestureAction::App(AppAction::LaunchTarget),
                GestureKind::LongPress => {
                    return GestureAction::Navigation(NavigationAction::OpenEditMenu)
                }
                GestureKind::LongPressDrag => return GestureAction::App(AppAction::MoveIcon),
                _ => {}
            }
        }

        self.config
            .rule(kind)
            .map(|r| r.action.clone())
            .unwrap_or(GestureAction::None)
    }
}
