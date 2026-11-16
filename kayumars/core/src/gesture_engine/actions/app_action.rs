use serde::{Deserialize, Serialize};

/// Application-level semantic actions (open app, move icon, or launch per-icon deep action).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum AppAction {
    /// Launch the touched application's main launcher activity
    LaunchTarget,
    /// Launch a specific package explicitly (e.g., gesture mapped to another app)
    LaunchPackage { package: String },
    /// Launch an app-specific shortcut or deep-link (e.g. Telegram Saved Messages, New Message, Search)
    LaunchShortcut {
        package: String,
        shortcut_id: String,
        label: String,
    },
    /// Enter drag/reposition mode for the touched icon
    MoveIcon,
}
