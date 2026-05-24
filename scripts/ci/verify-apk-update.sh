#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PACKAGE_NAME="${PACKAGE_NAME:-com.ayman.ecolift}"
TEST_PACKAGE="${TEST_PACKAGE:-com.ayman.ecolift.test}"
TEST_RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
ADB="${ADB:-${ANDROID_HOME:-}/platform-tools/adb}"
OLD_WORKTREE=""
TMP_DIR="$(mktemp -d)"

cleanup() {
    set +e
    if [[ -n "${ADB:-}" && -x "$ADB" ]]; then
        "$ADB" uninstall "$TEST_PACKAGE" >/dev/null 2>&1
        "$ADB" uninstall "$PACKAGE_NAME" >/dev/null 2>&1
    fi
    if [[ -n "$OLD_WORKTREE" && -d "$OLD_WORKTREE" ]]; then
        git worktree remove --force "$OLD_WORKTREE" >/dev/null 2>&1
    fi
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
    echo "update-check: $*" >&2
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

find_update_base_ref() {
    if [[ -n "${UPDATE_BASE_REF:-}" ]]; then
        echo "$UPDATE_BASE_REF"
        return
    fi

    while read -r ref; do
        if git cat-file -e "$ref:.android/debug.keystore" 2>/dev/null; then
            echo "$ref"
            return
        fi
    done < <(git rev-list --first-parent HEAD^ 2>/dev/null || git rev-list --first-parent HEAD)

    fail "could not find an ancestor containing .android/debug.keystore; set UPDATE_BASE_REF explicitly"
}

run_gradle() {
    local dir="$1"
    shift
    (cd "$dir" && ./gradlew --no-daemon --console=plain "$@")
}

wait_for_device() {
    "$ADB" wait-for-device
    until [[ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; do
        sleep 2
    done
}

run_instrumentation_class() {
    local class_name="$1"
    local output
    if ! output="$("$ADB" shell am instrument -w -r -e class "$class_name" "$TEST_RUNNER" 2>&1)"; then
        echo "$output"
        fail "instrumentation command failed for $class_name"
    fi
    echo "$output"
    if [[ "$output" == *"FAILURES!!!"* || "$output" == *"INSTRUMENTATION_STATUS_CODE: -2"* ]]; then
        fail "instrumentation test failed for $class_name"
    fi
    if [[ "$output" != *"OK ("* ]]; then
        fail "instrumentation test did not report success for $class_name"
    fi
}

[[ -x "$ADB" ]] || fail "adb not found at $ADB"
BUILD_TOOLS_DIR="$(latest_build_tools_dir)"
[[ -n "$BUILD_TOOLS_DIR" ]] || fail "no Android build-tools directory found"
AAPT="$BUILD_TOOLS_DIR/aapt"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
[[ -x "$AAPT" ]] || fail "aapt not found at $AAPT"
[[ -x "$APKSIGNER" ]] || fail "apksigner not found at $APKSIGNER"

CURRENT_APK="${CURRENT_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/debug" "*-debug.apk")}"
TEST_APK="${TEST_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/androidTest/debug" "*-debug-androidTest.apk")}"
if [[ -z "$CURRENT_APK" || -z "$TEST_APK" ]]; then
    run_gradle "$ROOT_DIR" assembleDebug assembleDebugAndroidTest
    CURRENT_APK="${CURRENT_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/debug" "*-debug.apk")}"
    TEST_APK="${TEST_APK:-$(single_apk "$ROOT_DIR/build/outputs/apk/androidTest/debug" "*-debug-androidTest.apk")}"
fi
[[ -f "$CURRENT_APK" ]] || fail "current debug APK was not built"
[[ -f "$TEST_APK" ]] || fail "debug androidTest APK was not built"

OLD_REF="$(find_update_base_ref)"
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
CURRENT_VERSION_CODE="$(version_code_for "$CURRENT_APK")"
[[ "$OLD_VERSION_CODE" =~ ^[0-9]+$ ]] || fail "old APK versionCode is not numeric: $OLD_VERSION_CODE"
[[ "$CURRENT_VERSION_CODE" =~ ^[0-9]+$ ]] || fail "current APK versionCode is not numeric: $CURRENT_VERSION_CODE"
if (( CURRENT_VERSION_CODE <= OLD_VERSION_CODE )); then
    fail "versionCode must increase for user updates: old=$OLD_VERSION_CODE current=$CURRENT_VERSION_CODE"
fi

OLD_SIGNER="$(signer_sha256_for "$OLD_APK")"
CURRENT_SIGNER="$(signer_sha256_for "$CURRENT_APK")"
[[ -n "$OLD_SIGNER" && -n "$CURRENT_SIGNER" ]] || fail "could not read APK signer certificates"
[[ "$OLD_SIGNER" == "$CURRENT_SIGNER" ]] || fail "APK signer changed; Android will reject in-place updates"

wait_for_device
"$ADB" uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
"$ADB" uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true

echo "update-check: installing $OLD_REF versionCode=$OLD_VERSION_CODE"
"$ADB" install -r "$OLD_APK"
"$ADB" install -r "$TEST_APK"
run_instrumentation_class "com.ayman.ecolift.update.UpgradeSeedInstrumentedTest"

echo "update-check: updating to current versionCode=$CURRENT_VERSION_CODE"
"$ADB" install -r "$CURRENT_APK"
"$ADB" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5
if [[ -z "$("$ADB" shell pidof "$PACKAGE_NAME" | tr -d '\r')" ]]; then
    fail "updated app did not stay running after launch"
fi

run_instrumentation_class "com.ayman.ecolift.update.UpgradeVerifyInstrumentedTest"
run_instrumentation_class "com.ayman.ecolift.data.DatabaseHardeningInstrumentedTest"

echo "update-check: APK updates, launches, and migrates seeded workout data"
