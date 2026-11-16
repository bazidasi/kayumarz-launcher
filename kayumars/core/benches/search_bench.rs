use criterion::{black_box, criterion_group, criterion_main, Criterion};
use kayumars_core::model::AppEntry;
use kayumars_core::Kayumars;

fn generate_500_apps() -> Vec<AppEntry> {
    let mut apps = Vec::with_capacity(500);
    let sample_names = [
        ("Telegram", "org.telegram.messenger"),
        ("Chrome", "com.android.chrome"),
        ("WhatsApp", "com.whatsapp"),
        ("Spotify", "com.spotify.music"),
        ("YouTube", "com.google.android.youtube"),
        ("Settings", "com.android.settings"),
        ("Camera", "com.android.camera"),
        ("Gmail", "com.google.android.gm"),
        ("Maps", "com.google.android.apps.maps"),
        ("Instagram", "com.instagram.android"),
        ("Slack", "com.Slack"),
        ("Discord", "com.discord"),
        ("Firefox", "org.mozilla.firefox"),
        ("Signal", "org.thoughtcrime.securesms"),
        ("Termux", "com.termux"),
    ];

    for i in 0..500 {
        let (base_label, base_pkg) = sample_names[i % sample_names.len()];
        apps.push(AppEntry {
            package: format!("{}.app{}", base_pkg, i),
            label: format!("{} {}", base_label, i),
            user_serial: 0,
            flags: 0,
        });
    }
    apps
}

fn bench_search_500_apps(c: &mut Criterion) {
    let tmp = tempfile::tempdir().unwrap();
    let engine = Kayumars::new(tmp.path().to_str().unwrap().to_string());
    let apps = generate_500_apps();
    engine.replace_index(apps);

    // Warm-up launch history
    for i in 0..50 {
        engine.log_launch(format!("com.android.chrome.app{}", i * 2), 1_700_000_000 + i);
    }

    let mut group = c.benchmark_group("kayumars_search");
    group.measurement_time(std::time::Duration::from_secs(3));

    group.bench_function("search_prefix_te_500_apps", |b| {
        b.iter(|| {
            let res = engine.search(black_box("te".to_string()), black_box(12));
            black_box(res);
        })
    });

    group.bench_function("search_fuzzy_spot_500_apps", |b| {
        b.iter(|| {
            let res = engine.search(black_box("spot".to_string()), black_box(12));
            black_box(res);
        })
    });

    group.bench_function("replace_index_500_apps", |b| {
        let new_apps = generate_500_apps();
        b.iter(|| {
            engine.replace_index(black_box(new_apps.clone()));
        })
    });

    group.finish();
}

criterion_group!(benches, bench_search_500_apps);
criterion_main!(benches);
