use serde::{Deserialize, Serialize};

/// Android OS system actions executed by the Kotlin/Java shell via decoupled safe bridges.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum SystemAction {
    LockScreen,
    ExpandNotifications,
    ExpandQuickSettings,
    OpenVolumePanel,
    ToggleTorch,
}
