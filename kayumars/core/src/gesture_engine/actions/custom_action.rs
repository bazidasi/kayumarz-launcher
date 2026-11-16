use serde::{Deserialize, Serialize};

/// User-defined custom or shell action identifier routed to Kotlin handler.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Record)]
pub struct CustomAction {
    pub action_id: String,
    pub payload: String,
}
