use kayumars_core::model::AppEntry;
use kayumars_core::Kayumars;

#[test]
fn test_search_and_ranking_flow() {
    let tmp = tempfile::tempdir().unwrap();
    let kayumars = Kayumars::new(tmp.path().to_str().unwrap().to_string());

    let apps = vec![
        AppEntry {
            package: "org.telegram.messenger".into(),
            label: "Telegram".into(),
            user_serial: 0,
            flags: 0,
        },
        AppEntry {
            package: "com.android.chrome".into(),
            label: "Google Chrome".into(),
            user_serial: 0,
            flags: 0,
        },
        AppEntry {
            package: "com.termux".into(),
            label: "Termux".into(),
            user_serial: 0,
            flags: 0,
        },
    ];

    kayumars.replace_index(apps);

    // Initial search: "te" should match Telegram and Termux
    let res = kayumars.search("te".into(), 5);
    assert!(!res.is_empty());
    assert!(res.iter().any(|r| r.label == "Telegram"));
    assert!(res.iter().any(|r| r.label == "Termux"));

    // Launch Termux multiple times to boost its KISS ranking
    for t in 1..=10 {
        kayumars.log_launch("com.termux".into(), 1000 + t);
    }

    // Now searching "te" should put Termux first due to frequency and recency
    let ranked = kayumars.search("te".into(), 5);
    assert_eq!(ranked[0].package, "com.termux");

    // Empty query top apps should also feature Termux as #1
    let top = kayumars.top_apps(5);
    assert_eq!(top[0].package, "com.termux");

    // Save and reload test
    kayumars.save_now();
    let reloaded = Kayumars::new(tmp.path().to_str().unwrap().to_string());
    let reloaded_top = reloaded.top_apps(5);
    assert_eq!(reloaded_top[0].package, "com.termux");
}
