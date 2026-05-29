#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

EXPECTED_SIGNER_FILE="${EXPECTED_SIGNER_FILE:-$ROOT_DIR/config/ci/pinned-debug-signer-sha256.txt}"

fail() {
    echo "apk-signer: $*" >&2
    exit 1
}

latest_build_tools_dir() {
    [[ -n "${ANDROID_HOME:-}" ]] || fail "ANDROID_HOME must be set"
    find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1
}

single_apk() {
    local dir="$1"
    local pattern="$2"
    [[ -d "$dir" ]] || return 0
    find "$dir" -type f -name "$pattern" | sort | head -n 1
}

normalize_sha256() {
    tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]'
}

signer_sha256_for() {
    "$APKSIGNER" verify --print-certs "$1" |
        sed -n 's/.*SHA-256 digest: //p' |
        head -n 1 |
        normalize_sha256
}

run_gradle() {
    ./gradlew --no-daemon --console=plain "$@"
}

[[ -f "$EXPECTED_SIGNER_FILE" ]] || fail "expected signer file missing: $EXPECTED_SIGNER_FILE"
EXPECTED_SIGNER="$(normalize_sha256 < "$EXPECTED_SIGNER_FILE")"
[[ "$EXPECTED_SIGNER" =~ ^[0-9a-f]{64}$ ]] || fail "expected signer is not a SHA-256 fingerprint"

BUILD_TOOLS_DIR="$(latest_build_tools_dir)"
[[ -n "$BUILD_TOOLS_DIR" ]] || fail "no Android build-tools directory found"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
[[ -x "$APKSIGNER" ]] || fail "apksigner not found at $APKSIGNER"

CURRENT_APK="${CURRENT_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/debug" "*-debug.apk")}"
if [[ -z "$CURRENT_APK" ]]; then
    run_gradle assembleDebug
    CURRENT_APK="${CURRENT_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/debug" "*-debug.apk")}"
fi
[[ -f "$CURRENT_APK" ]] || fail "current debug APK was not built"

CURRENT_SIGNER="$(signer_sha256_for "$CURRENT_APK")"
[[ -n "$CURRENT_SIGNER" ]] || fail "could not read APK signer certificate"
if [[ "$CURRENT_SIGNER" != "$EXPECTED_SIGNER" ]]; then
    fail "debug APK signer changed; expected=$EXPECTED_SIGNER actual=$CURRENT_SIGNER. Android will reject in-place updates from the pinned signer."
fi

echo "apk-signer: signer=$CURRENT_SIGNER"
