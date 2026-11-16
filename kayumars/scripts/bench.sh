#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "  KAYUMARS (کیومرث) - Performance Benchmark Suite         "
echo "=========================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "[1/2] Running Rust Criterion microbenchmarks (500 apps)..."
(
    cd "$ROOT_DIR/core"
    cargo bench --bench search_bench
)

echo "[2/2] Running connected Android Macrobenchmark tests..."
(
    cd "$ROOT_DIR"
    ./gradlew :benchmark:connectedCheck || {
        echo "[!] No connected Android device found; skipped connectedCheck."
    }
)

echo "=========================================================="
echo "  Benchmark runs completed. View reports in core/target/criterion"
echo "=========================================================="
