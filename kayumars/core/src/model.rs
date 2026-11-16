//! Domain models for Kayumars launcher.
//! All types are designed for zero-copy or minimal-copy serialisation across FFI.

use serde::{Deserialize, Serialize};

/// Type of search result item.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum SearchResultKind {
    App,
    Contact,
    Setting,
}

/// Represents an installed Android application or launcher activity.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Record)]
pub struct AppEntry {
    /// Package name identifier (e.g., "org.telegram.messenger")
    pub package: String,
    /// User-visible display label (e.g., "Telegram")
    pub label: String,
    /// Multi-user profile identifier (0 for main profile)
    pub user_serial: u32,
    /// Flags (e.g. system app, work profile, hidden)
    pub flags: u32,
}

/// Scored item returned from search queries or top-app suggestions.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct SearchResult {
    /// Unique package name identifier
    pub package: String,
    /// Display label
    pub label: String,
    /// Combined fuzzy + frequency + recency score (higher is better)
    pub score: f32,
    /// Category of the result item
    pub kind: SearchResultKind,
}

/// Single append-only event recorded on app launch.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct HistoryEvent {
    /// Target application package
    pub package: String,
    /// Epoch millisecond when launch occurred
    pub timestamp_ms: u64,
}
