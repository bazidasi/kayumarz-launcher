//! Strictly decoupled Dispatcher: converts a recognized gesture + surface context
//! into a semantic `DispatchedCommand` for the Android shell to execute.

use serde::{Deserialize, Serialize};

use crate::gesture_engine::actions::GestureAction;
use crate::gesture_engine::core::gesture_config::GestureKind;
use crate::gesture_engine::core::gesture_state::{GestureFsmState, SurfaceZone};
use crate::gesture_engine::settings::profiles::GestureProfile;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct DispatchedCommand {
    pub recognized: bool,
    pub gesture: Option<GestureKind>,
    pub action: GestureAction,
    pub zone: SurfaceZone,
    pub fsm_state: GestureFsmState,
    pub target_package: Option<String>,
    pub drag_dx_dp: f32,
    pub drag_dy_dp: f32,
}

impl DispatchedCommand {
    pub fn none(zone: SurfaceZone, fsm_state: GestureFsmState) -> Self {
        Self {
            recognized: false,
            gesture: None,
            action: GestureAction::None,
            zone,
            fsm_state,
            target_package: None,
            drag_dx_dp: 0.0,
            drag_dy_dp: 0.0,
        }
    }
}

pub struct GestureDispatcher;

impl GestureDispatcher {
    pub fn dispatch(
        profile: &GestureProfile,
        kind: GestureKind,
        zone: SurfaceZone,
        fsm_state: GestureFsmState,
        target_package: Option<String>,
        drag_dx_dp: f32,
        drag_dy_dp: f32,
    ) -> DispatchedCommand {
        let action = profile.resolve_action(kind, target_package.as_deref());
        DispatchedCommand {
            recognized: true,
            gesture: Some(kind),
            action,
            zone,
            fsm_state,
            target_package,
            drag_dx_dp,
            drag_dy_dp,
        }
    }
}
