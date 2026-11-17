//! App Library classification.
//!
//! The keyword tables used to live in Kotlin (`AppMetadataHelper.classifyApp`).
//! They live here so a single ordered rule set decides every package, and the
//! Android `ApplicationInfo.category` hint can be folded in without a second
//! JNI round-trip per app.

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

/// Library buckets, keyed exactly like the Kotlin `LibraryCategory` enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AppCategory {
    Productivity,
    Social,
    Creativity,
    Utilities,
    Games,
    Other,
}

impl AppCategory {
    pub fn key(self) -> &'static str {
        match self {
            AppCategory::Productivity => "PRODUCTIVITY",
            AppCategory::Social => "SOCIAL",
            AppCategory::Creativity => "CREATIVITY",
            AppCategory::Utilities => "UTILITIES",
            AppCategory::Games => "GAMES",
            AppCategory::Other => "OTHER",
        }
    }
}

/// `android.content.pm.ApplicationInfo.category` values (API 26+).
const CAT_GAME: i32 = 0;
const CAT_AUDIO: i32 = 1;
const CAT_VIDEO: i32 = 2;
const CAT_IMAGE: i32 = 3;
const CAT_SOCIAL: i32 = 4;
const CAT_NEWS: i32 = 5;
const CAT_MAPS: i32 = 6;
const CAT_PRODUCTIVITY: i32 = 7;
const CAT_ACCESSIBILITY: i32 = 8;

/// One app to classify. `cat` is `ApplicationInfo.category` (-1 = undefined).
#[derive(Debug, Clone, Deserialize)]
pub struct ClassifyInput {
    pub pkg: String,
    #[serde(default)]
    pub label: String,
    #[serde(default = "minus_one")]
    pub cat: i32,
}

fn minus_one() -> i32 {
    -1
}

fn any(hay: &str, needles: &[&str]) -> bool {
    needles.iter().any(|n| hay.contains(n))
}

/// Ordered rules: games → social → productivity → creativity → utilities.
/// Earlier rules win, so put the narrow signal before the broad one.
pub fn classify(package: &str, label: &str, system_category: i32) -> AppCategory {
    let low = format!("{} {}", package, label).to_lowercase();

    // Explicit play-store hint beats keywords (a "Cards" game is still a game).
    if system_category == CAT_GAME {
        return AppCategory::Games;
    }

    if any(
        &low,
        &[
            "game", "sudoku", "puzzle", "arcade", "chess", "clash", "minecraft", "roblox",
            "pubg", "amongus", ".game",
        ],
    ) {
        return AppCategory::Games;
    }

    if any(
        &low,
        &[
            "telegram", "whatsapp", "instagram", "discord", "twitter", "x.com", "messag",
            "dialer", "phone", "eitaa", "bale", "rubika", "signal", "whatsapp", "contact",
            "slack", "meet", "snapchat", "facebook", "tiktok", "linkedin", "reddit", "mastodon",
            "threads", "wechat", "line", "viber", "skype", "zoom", "teams",
        ],
    ) || matches!(system_category, CAT_SOCIAL | CAT_NEWS)
    {
        return AppCategory::Social;
    }

    if any(
        &low,
        &[
            "mail", "gmail", "docs", "sheets", "slides", "drive", "calendar", "notion",
            "obsidian", "keep", "notes", "note", "bank", "wallet", "stripe", "pdf", "office",
            "word", "excel", "powerpoint", "github", "gitlab", "termux", "todo", "tasks",
            "invoice", "account", "budget", "finance", "paypal", "binance", "trading",
            "scanner", "scan", "translate", "dictionary", "chatgpt", "openai", "claude",
            "copilot", "perplexity", "gemini", "androidide", "acode", "terminal", "shell", "ssh",
        ],
    ) || system_category == CAT_PRODUCTIVITY
    {
        return AppCategory::Productivity;
    }

    if any(
        &low,
        &[
            "camera", "photo", "gallery", "music", "spotify", "podcast", "youtube", "vlc",
            "video", "movie", "player", "studio", "paint", "draw", "sketch", "edit", "design",
            "canva", "figma", "netflix", "telewebion", "filimo", "shazam", "recorder",
            "instrument", "piano", "guitar", "image", "collage", "wallpaper",
        ],
    ) || matches!(system_category, CAT_AUDIO | CAT_VIDEO | CAT_IMAGE)
    {
        return AppCategory::Creativity;
    }

    if any(
        &low,
        &[
            "setting", "files", "file manager", "vpn", "chrome", "firefox", "edge", "browser",
            "internet", "maps", "navigation", "waze", "weather", "compass", "qr", "bitwarden",
            "calc", "clock", "alarm", "timer", "flashlight", "wifi", "bluetooth", "network",
            "speedtest", "phone manager", "cleaner", "store", "play store", "market",
            "keyboard", "launcher", "accessibility", "sim", "battery", "downloader",
            "archive", "compass", "level", "ruler", "magnifier", "radio", "fm",
        ],
    ) || matches!(system_category, CAT_MAPS | CAT_ACCESSIBILITY)
    {
        return AppCategory::Utilities;
    }

    AppCategory::Other
}

/// Batch classification: pkg -> category key. Used by the JNI entry.
pub fn classify_batch(input: &[ClassifyInput]) -> BTreeMap<String, &'static str> {
    input
        .iter()
        .map(|a| (a.pkg.clone(), classify(&a.pkg, &a.label, a.cat).key()))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn browser_and_settings_are_utilities() {
        assert_eq!(classify("com.android.chrome", "Chrome", -1), AppCategory::Utilities);
        assert_eq!(classify("com.android.settings", "Settings", -1), AppCategory::Utilities);
        assert_eq!(classify("com.google.android.apps.maps", "Maps", -1), AppCategory::Utilities);
    }

    #[test]
    fn messaging_is_social_even_when_named_oddly() {
        assert_eq!(classify("org.telegram.messenger", "Telegram", -1), AppCategory::Social);
        assert_eq!(classify("com.whatsapp", "WhatsApp", -1), AppCategory::Social);
        assert_eq!(classify("ir.eitaa.messenger", "Eitaa", -1), AppCategory::Social);
    }

    #[test]
    fn office_and_finance_are_productivity() {
        assert_eq!(classify("notion.id", "Notion", -1), AppCategory::Productivity);
        assert_eq!(classify("com.google.android.gm", "Gmail", -1), AppCategory::Productivity);
        assert_eq!(classify("com.example.bank", "Bank", -1), AppCategory::Productivity);
        assert_eq!(classify("com.termux", "Termux", -1), AppCategory::Productivity);
    }

    #[test]
    fn media_is_creativity() {
        assert_eq!(classify("com.google.android.apps.photos", "Photos", -1), AppCategory::Creativity);
        assert_eq!(classify("com.spotify.music", "Spotify", -1), AppCategory::Creativity);
        assert_eq!(classify("org.videolan.vlc", "VLC", -1), AppCategory::Creativity);
    }

    #[test]
    fn system_category_hint_wins_for_games() {
        assert_eq!(classify("com.example.cards", "Cards", CAT_GAME), AppCategory::Games);
        assert_eq!(classify("com.example.notes", "Notes", CAT_PRODUCTIVITY), AppCategory::Productivity);
        // Undefined hint falls back to keywords.
        assert_eq!(classify("com.example.notes", "Notes", -1), AppCategory::Productivity);
    }

    #[test]
    fn unknown_package_is_other() {
        assert_eq!(classify("com.acme.zzz", "Zzz", -1), AppCategory::Other);
    }

    #[test]
    fn batch_covers_every_input_and_is_stable() {
        let apps = vec![
            ClassifyInput { pkg: "com.android.chrome".into(), label: "Chrome".into(), cat: -1 },
            ClassifyInput { pkg: "org.telegram.messenger".into(), label: "Telegram".into(), cat: -1 },
            ClassifyInput { pkg: "com.acme.zzz".into(), label: "Zzz".into(), cat: -1 },
        ];
        let out = classify_batch(&apps);
        assert_eq!(out.len(), 3);
        assert_eq!(out["com.android.chrome"], "UTILITIES");
        assert_eq!(out["org.telegram.messenger"], "SOCIAL");
        assert_eq!(out["com.acme.zzz"], "OTHER");
    }

    #[test]
    fn every_category_has_a_key() {
        for c in [
            AppCategory::Productivity,
            AppCategory::Social,
            AppCategory::Creativity,
            AppCategory::Utilities,
            AppCategory::Games,
            AppCategory::Other,
        ] {
            assert!(!c.key().is_empty());
        }
    }
}
