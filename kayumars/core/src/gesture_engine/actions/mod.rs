pub mod app_action;
pub mod custom_action;
pub mod navigation_action;
pub mod system_action;

use serde::{Deserialize, Serialize};

use app_action::AppAction;
use custom_action::CustomAction;
use navigation_action::NavigationAction;
use system_action::SystemAction;

/// Semantic action emitted by the Rust Gesture Engine.
/// Android shell receives this and performs the actual Intent / System / UI invocation.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum GestureAction {
    None,
    App(AppAction),
    System(SystemAction),
    Navigation(NavigationAction),
    Custom(CustomAction),
}

impl Default for GestureAction {
    fn default() -> Self {
        GestureAction::None
    }
}
