#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "  KAYUMARS (کیومرث) - Rust Core + Android APK Builder    "
echo "=========================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# 1. Ensure cargo-ndk is available
if ! command -v cargo-ndk &> /dev/null; then
    echo "[!] cargo-ndk not found in PATH. Installing cargo-ndk..."
    cargo install cargo-ndk || true
fi

# 2. Build Rust shared libraries for target architectures
echo "[+] Compiling Rust core via cargo-ndk..."
mkdir -p "$ROOT_DIR/app/src/main/jniLibs"

(
    cd "$ROOT_DIR/core"
    cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
        -o "$ROOT_DIR/app/src/main/jniLibs" build --release
)

echo "[✓] Native libraries built successfully:"
ls -lh "$ROOT_DIR/app/src/main/jniLibs"/*/*.so 2>/dev/null || true

# 3. Assemble Android application
echo "[+] Assembling Android application..."
cd "$ROOT_DIR"
./gradlew :app:assembleRelease --no-daemon

echo "=========================================================="
echo "  [SUCCESS] Kayumars APK ready in: app/build/outputs/apk/  "
echo "=========================================================="
