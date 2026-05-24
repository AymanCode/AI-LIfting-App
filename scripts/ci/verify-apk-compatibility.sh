#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PACKAGE_NAME="${PACKAGE_NAME:-com.ayman.ecolift}"
OLD_WORKTREE=""
TMP_DIR="$(mktemp -d)"

cleanup() {
    set +e
    if [[ -n "$OLD_WORKTREE" && -d "$OLD_WORKTREE" ]]; then
        git worktree remove --force "$OLD_WORKTREE" >/dev/null 2>&1
    fi
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
    echo "apk-compatibility: $*" >&2
    exit 1
}

latest_build_tools_dir() {
    [[ -n "${ANDROID_HOME:-}" ]] || fail "ANDROID_HOME must be set"
    find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1
}

single_apk() {
    local dir="$1"
    local pattern="$2"
    find "$dir" -type f -name "$pattern" | sort | head -n 1
}

package_name_for() {
    "$AAPT" dump badging "$1" | sed -n "s/package: name='\([^']*\)'.*/\1/p" | head -n 1
}

version_code_for() {
    "$AAPT" dump badging "$1" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -n 1
}

signer_sha256_for() {
    "$APKSIGNER" verify --print-certs "$1" |
        sed -n 's/.*SHA-256 digest: //p' |
        head -n 1 |
        tr -d ':' |
        tr '[:upper:]' '[:lower:]'
}

version_code_for_ref() {
    git show "$1:build.gradle.kts" 2>/dev/null |
        sed -n 's/.*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' |
        head -n 1
}

find_update_base_ref() {
    local current_version_code="$1"
    local ref_version_code

    if [[ -n "${UPDATE_BASE_REF:-}" ]]; then
        echo "$UPDATE_BASE_REF"
        return
    fi

    while read -r ref; do
        if ! git cat-file -e "$ref:.android/debug.keystore" 2>/dev/null; then
            continue
        fi
        ref_version_code="$(version_code_for_ref "$ref")"
        if [[ "$ref_version_code" =~ ^[0-9]+$ ]] && (( ref_version_code < current_version_code )); then
            echo "$ref"
            return
        fi
    done < <(git rev-list --first-parent HEAD^ 2>/dev/null || git rev-list --first-parent HEAD)

    fail "could not find an ancestor with a lower versionCode and .android/debug.keystore; set UPDATE_BASE_REF explicitly"
}

run_gradle() {
    local dir="$1"
    shift
    (cd "$dir" && ./gradlew --no-daemon --console=plain "$@")
}

BUILD_TOOLS_DIR="$(latest_build_tools_dir)"
[[ -n "$BUILD_TOOLS_DIR" ]] || fail "no Android build-tools directory found"
AAPT="$BUILD_TOOLS_DIR/aapt"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
[[ -x "$AAPT" ]] || fail "aapt not found at $AAPT"
[[ -x "$APKSIGNER" ]] || fail "apksigner not found at $APKSIGNER"

CURRENT_APK="${CURRENT_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/debug" "*-debug.apk")}"
if [[ -z "$CURRENT_APK" ]]; then
    run_gradle "$ROOT_DIR" assembleDebug
    CURRENT_APK="${CURRENT_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/debug" "*-debug.apk")}"
fi
[[ -f "$CURRENT_APK" ]] || fail "current debug APK was not built"

CURRENT_VERSION_CODE="$(version_code_for "$CURRENT_APK")"
[[ "$CURRENT_VERSION_CODE" =~ ^[0-9]+$ ]] || fail "current APK versionCode is not numeric: $CURRENT_VERSION_CODE"

OLD_REF="$(find_update_base_ref "$CURRENT_VERSION_CODE")"
OLD_WORKTREE="$TMP_DIR/old-app"
git worktree add --detach "$OLD_WORKTREE" "$OLD_REF" >/dev/null
chmod +x "$OLD_WORKTREE/gradlew"
run_gradle "$OLD_WORKTREE" assembleDebug
OLD_APK="$(single_apk "$OLD_WORKTREE/build/outputs/apk/debug" "*-debug.apk")"
[[ -f "$OLD_APK" ]] || fail "old debug APK was not built from $OLD_REF"

OLD_PACKAGE="$(package_name_for "$OLD_APK")"
CURRENT_PACKAGE="$(package_name_for "$CURRENT_APK")"
[[ "$OLD_PACKAGE" == "$CURRENT_PACKAGE" ]] || fail "package changed from $OLD_PACKAGE to $CURRENT_PACKAGE"
[[ "$CURRENT_PACKAGE" == "$PACKAGE_NAME" ]] || fail "expected package $PACKAGE_NAME, got $CURRENT_PACKAGE"

OLD_VERSION_CODE="$(version_code_for "$OLD_APK")"
[[ "$OLD_VERSION_CODE" =~ ^[0-9]+$ ]] || fail "old APK versionCode is not numeric: $OLD_VERSION_CODE"
if (( CURRENT_VERSION_CODE <= OLD_VERSION_CODE )); then
    fail "versionCode must increase for user updates: old=$OLD_VERSION_CODE current=$CURRENT_VERSION_CODE"
fi

OLD_SIGNER="$(signer_sha256_for "$OLD_APK")"
CURRENT_SIGNER="$(signer_sha256_for "$CURRENT_APK")"
[[ -n "$OLD_SIGNER" && -n "$CURRENT_SIGNER" ]] || fail "could not read APK signer certificates"
[[ "$OLD_SIGNER" == "$CURRENT_SIGNER" ]] || fail "APK signer changed; Android will reject in-place updates"

echo "apk-compatibility: package=$CURRENT_PACKAGE oldVersionCode=$OLD_VERSION_CODE currentVersionCode=$CURRENT_VERSION_CODE signer=$CURRENT_SIGNER"
