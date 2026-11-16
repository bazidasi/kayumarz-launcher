//! Ranking engine implementing the KISS Launcher "frequency × recency" formula.
//! Combined with Nucleo's fuzzy match score to deliver instant, adaptive search results.

use std::collections::HashMap;
use crate::model::{AppEntry, SearchResult, SearchResultKind};

/// Computes the KISS-style frequency and recency weighting multiplier.
///
/// Formula:
/// `combined_multiplier = frequency_weight * recency_weight`
/// where:
/// - `frequency_weight = (launches_for_app + 1) / (total_launches + total_installed_apps)`
/// - `recency_weight = 1.0 / recent_position` (where position 1 is most recent)
#[inline]
pub fn compute_ranking_multiplier(
    app_launches: u32,
    total_launches: u64,
    total_installed: usize,
    recent_position: Option<usize>,
) -> f32 {
    let smoothing_apps = (total_installed.max(1)) as f64;
    // Laplace-smoothed frequency ratio so unlaunched apps still retain reasonable score
    let freq_numerator = (app_launches as f64) + 1.0;
    let freq_denominator = (total_launches as f64) + smoothing_apps;
    let freq_weight = (freq_numerator / freq_denominator) as f32;

    // Recency weight: 1.0 / position. Unlaunched apps are treated as position (total_installed + 5)
    let pos = match recent_position {
        Some(p) => (p.max(1)) as f32,
        None => (total_installed + 5) as f32,
    };
    let recency_weight = 1.0 / pos;

    freq_weight * recency_weight
}

/// Computes the final search score for a fuzzy match.
#[inline]
pub fn score_fuzzy_result(
    fuzzy_score: u32,
    app_launches: u32,
    total_launches: u64,
    total_installed: usize,
    recent_position: Option<usize>,
) -> f32 {
    let multiplier = compute_ranking_multiplier(
        app_launches,
        total_launches,
        total_installed,
        recent_position,
    );
    // Base fuzzy score scaled with frequency × recency multiplier
    (fuzzy_score as f32) * (1.0 + multiplier * 100.0)
}

/// Produces top-N suggested applications for an empty query state.
/// Sorted by frequency × recency descending.
pub fn rank_top_apps(
    entries: &[AppEntry],
    counts: &HashMap<String, u32>,
    total_launches: u64,
    recency_order: &HashMap<String, usize>,
    max_results: usize,
) -> Vec<SearchResult> {
    let total_installed = entries.len();
    let mut scored: Vec<SearchResult> = entries
        .iter()
        .map(|app| {
            let app_launches = *counts.get(&app.package).unwrap_or(&0);
            let recent_pos = recency_order.get(&app.package).copied();
            let mult = compute_ranking_multiplier(
                app_launches,
                total_launches,
                total_installed,
                recent_pos,
            );
            SearchResult {
                package: app.package.clone(),
                label: app.label.clone(),
                score: mult * 1000.0,
                kind: SearchResultKind::App,
            }
        })
        .collect();

    // Sort descending by score. If equal, sort alphabetically by label.
    scored.sort_by(|a, b| {
        b.score
            .partial_cmp(&a.score)
            .unwrap_or(std::cmp::Ordering::Equal)
            .then_with(|| a.label.cmp(&b.label))
    });

    scored.truncate(max_results);
    scored
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ranking_fixtures() {
        // App A launched 10 times, most recent (pos 1)
        // App B launched 10 times, older (pos 5)
        // App C launched 1 time, never launched recently (None)
        let total = 50;
        let installed = 100;

        let score_a = score_fuzzy_result(100, 10, total, installed, Some(1));
        let score_b = score_fuzzy_result(100, 10, total, installed, Some(5));
        let score_c = score_fuzzy_result(100, 1, total, installed, None);

        assert!(score_a > score_b, "Most recent should beat older app with same launch count");
        assert!(score_b > score_c, "Higher launch count should beat lower launch count");
    }

    #[test]
    fn test_empty_query_top_apps() {
        let entries = vec![
            AppEntry {
                package: "app.rare".into(),
                label: "Rare App".into(),
                user_serial: 0,
                flags: 0,
            },
            AppEntry {
                package: "app.frequent".into(),
                label: "Frequent App".into(),
                user_serial: 0,
                flags: 0,
            },
        ];

        let mut counts = HashMap::new();
        counts.insert("app.frequent".into(), 40);
        counts.insert("app.rare".into(), 1);

        let mut recency = HashMap::new();
        recency.insert("app.frequent".into(), 1);
        recency.insert("app.rare".into(), 2);

        let top = rank_top_apps(&entries, &counts, 41, &recency, 5);
        assert_eq!(top.len(), 2);
        assert_eq!(top[0].package, "app.frequent");
    }
}
