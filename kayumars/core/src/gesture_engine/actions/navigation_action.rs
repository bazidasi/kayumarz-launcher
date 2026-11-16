use serde::{Deserialize, Serialize};

/// Launcher internal navigation actions.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum NavigationAction {
    OpenAppDrawer,
    OpenSearch,
    NextPage,
    PreviousPage,
    OpenOverview,
    ReturnHome,
    OpenLauncherSettings,
    OpenEditMenu,
}
