#!/usr/bin/env bash
# Git Bash only. Bare bash on this machine is WSL and cannot see these paths.
set -euo pipefail

usage() {
  echo "usage: capture.sh <apk> <output-dir> [--fresh] [--seed-demo] [--shots <file>] [--serial <serial>] [--avd <name>] [--gpu <mode>]" >&2
}

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SHOTS="$SCRIPT_DIR/shots.txt"
AVD="${ECOLIFT_AVD:-ecolift_pixel}"
GPU="${ECOLIFT_GPU:-swiftshader_indirect}"
PORT="${ECOLIFT_PORT:-5554}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/c/Users/ayman/AppData/Local/Android/Sdk}}"
SERIAL_ARG=""
FRESH=0
SEED=0

if [[ $# -lt 2 ]]; then
  usage
  exit 2
fi
APK=$1
OUT=$2
shift 2
while [[ $# -gt 0 ]]; do
  case "$1" in
    --fresh) FRESH=1; shift ;;
    --seed-demo) SEED=1; shift ;;
    --shots) SHOTS=$2; shift 2 ;;
    --serial) SERIAL_ARG=$2; shift 2 ;;
    --avd) AVD=$2; shift 2 ;;
    --gpu) GPU=$2; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ ! -f "$APK" ]]; then
  echo "apk not found: $APK" >&2
  exit 1
fi
if [[ ! -f "$SHOTS" ]]; then
  echo "shots file not found: $SHOTS" >&2
  exit 1
fi
SDK=$(cygpath -u "$SDK")
ADB="$SDK/platform-tools/adb.exe"
EMULATOR="$SDK/emulator/emulator.exe"
if [[ ! -f "$ADB" || ! -f "$EMULATOR" ]]; then
  echo "adb or emulator is missing under $SDK" >&2
  exit 1
fi
if ! command -v python >/dev/null 2>&1; then
  echo "python is required to read uiautomator dumps" >&2
  exit 1
fi

find_aapt() {
  local latest
  latest=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1 || true)
  if [[ -z "$latest" ]]; then
    echo "no build-tools under $SDK" >&2
    exit 1
  fi
  if [[ -f "${latest}aapt.exe" ]]; then
    echo "${latest}aapt.exe"
  elif [[ -f "${latest}aapt" ]]; then
    echo "${latest}aapt"
  else
    echo "aapt is missing from $latest" >&2
    exit 1
  fi
}
AAPT=$(find_aapt)

# adb.exe is a Windows program. MSYS rewrites arguments that contain a slash
# (/sdcard, package/activity) unless this is set on the adb command itself.
run_adb() {
  # adb reads stdin. The shot loop's here-string is stdin, so leave it open and
  # a swipe or tap consumes the rest of that shot.
  if [[ -n "${SERIAL:-}" ]]; then
    MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' "$ADB" -s "$SERIAL" "$@" </dev/null
  else
    MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' "$ADB" "$@" </dev/null
  fi
}

read_launcher() {
  local badging
  badging=$("$AAPT" dump badging "$(cygpath -m "$APK")" || true)
  if [[ -z "$badging" ]]; then
    echo "aapt dump badging failed for $APK" >&2
    exit 1
  fi
  PACKAGE=$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)
  ACTIVITY=$(printf '%s\n' "$badging" | sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p" | head -n 1)
  if [[ "${PACKAGE:-}" != "com.ayman.ecolift" || -z "${ACTIVITY:-}" ]]; then
    echo "expected package com.ayman.ecolift with a launchable activity, got package '${PACKAGE:-}' activity '${ACTIVITY:-}'" >&2
    exit 1
  fi
}

validate_step() {
  local line="$1" verb rest
  verb=${line%% *}
  if [[ "$line" == *" "* ]]; then
    rest=${line#* }
  else
    rest=""
  fi
  case "$verb" in
    wait) [[ "$rest" =~ ^[0-9]+$ ]] ;;
    wait-text|tap-text|tap-desc) [[ -n "$rest" ]] ;;
    tap-xy) [[ "$rest" =~ ^[0-9]+\ [0-9]+$ ]] ;;
    swipe) [[ "$rest" =~ ^[0-9]+\ [0-9]+\ [0-9]+\ [0-9]+\ [0-9]+$ ]] ;;
    back) [[ -z "$rest" ]] ;;
    *) return 1 ;;
  esac
}

parse_shots() {
  local line current="" steps=""
  SHOT_NAMES=()
  SHOT_STEPS=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    line=${line%$'\r'}
    line=${line%%#*}
    line=${line#"${line%%[![:space:]]*}"}
    line=${line%"${line##*[![:space:]]}"}
    [[ -z "$line" ]] && continue
    if [[ "$line" == shot\ * ]]; then
      if [[ -n "$current" ]]; then
        SHOT_NAMES+=("$current")
        SHOT_STEPS+=("$steps")
      elif [[ -n "$steps" ]]; then
        echo "steps appear before the first shot in $SHOTS" >&2
        exit 1
      fi
      current=${line#shot }
      if [[ ! "$current" =~ ^[A-Za-z0-9_-]+$ ]]; then
        echo "bad shot name: $current" >&2
        exit 1
      fi
      steps=""
    else
      if ! validate_step "$line"; then
        echo "bad step: $line" >&2
        exit 1
      fi
      steps+="$line"$'\n'
    fi
  done < "$SHOTS"
  if [[ -n "$current" ]]; then
    SHOT_NAMES+=("$current")
    SHOT_STEPS+=("$steps")
  fi
  if [[ ${#SHOT_NAMES[@]} -eq 0 ]]; then
    echo "no shots in $SHOTS" >&2
    exit 1
  fi
}

start_emulator() {
  local log="/tmp/ecolift-${AVD}.log"
  echo "starting $AVD as emulator-${PORT} with -gpu $GPU" >&2
  # nohup plus disown keeps the emulator alive after this script exits.
  nohup "$EMULATOR" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim -no-metrics -gpu "$GPU" >"$log" 2>&1 &
  disown || true
}

ensure_emulator() {
  local found count
  # offline and device both count. A second start would fight the boot already in progress.
  found=$("$ADB" devices | tr -d '\r' | awk 'NR>1 && $1 ~ /^emulator-/ {print $1}')
  if [[ -n "$SERIAL_ARG" ]]; then
    SERIAL=$SERIAL_ARG
  elif [[ -z "$found" ]]; then
    start_emulator
    SERIAL="emulator-${PORT}"
  else
    count=$(printf '%s\n' "$found" | wc -l | tr -d ' ')
    if [[ "$count" != "1" ]]; then
      echo "more than one emulator is running. pass --serial" >&2
      printf '%s\n' "$found" >&2
      exit 1
    fi
    SERIAL=$(printf '%s\n' "$found" | head -n 1)
  fi
  wait_boot
}

wait_boot() {
  local i state booted
  for i in $(seq 1 150); do
    state=$("$ADB" devices | tr -d '\r' | awk -v s="$SERIAL" '$1==s {print $2}')
    if [[ "$state" == "device" ]]; then
      break
    fi
    if [[ "$i" == "150" ]]; then
      echo "timed out waiting for $SERIAL to appear" >&2
      exit 1
    fi
    sleep 2
  done
  for i in $(seq 1 180); do
    booted=$(run_adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    if [[ "$booted" == "1" ]]; then
      run_adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
      return
    fi
    if [[ "$i" == "180" ]]; then
      echo "timed out waiting for sys.boot_completed on $SERIAL" >&2
      exit 1
    fi
    sleep 2
  done
}

demo_status() {
  run_adb shell settings put global sysui_demo_allowed 1
  run_adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
  run_adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930 >/dev/null
  run_adb shell am broadcast -a com.android.systemui.demo -e command battery -e plugged false -e level 100 >/dev/null
  run_adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
  run_adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
}

dump_ui() {
  local dest="$1"
  run_adb shell uiautomator dump /sdcard/ecolift-uidump.xml >/dev/null
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' "$ADB" -s "$SERIAL" pull /sdcard/ecolift-uidump.xml "$(cygpath -m "$dest")" >/dev/null
}

# Attribute order in the dump is not stable, so the matcher is a real XML parse.
# The lowest match wins because a tab label is also painted in the page above it.
locate_node() {
  python - "$1" "$2" "$3" <<'PY'
import sys
import xml.etree.ElementTree as ET

mode, needle, path = sys.argv[1:]
try:
    tree = ET.parse(path)
except ET.ParseError:
    sys.exit(2)
best = None
for node in tree.iter("node"):
    values = []
    if mode in ("text", "either"):
        values.append(node.attrib.get("text", ""))
    if mode in ("desc", "either"):
        values.append(node.attrib.get("content-desc", ""))
    if needle not in values:
        continue
    raw = node.attrib.get("bounds", "")
    nums = raw.replace("][", ",").strip("[]").split(",")
    if len(nums) != 4:
        continue
    x1, y1, x2, y2 = (int(part) for part in nums)
    center = ((x1 + x2) // 2, (y1 + y2) // 2)
    if best is None or center[1] > best[1]:
        best = center
if best is None:
    sys.exit(1)
print(f"{best[0]} {best[1]}")
PY
}

wait_for_label() {
  local label="$1" i xml xy
  # The first composition on a cold seeded launch can take about 90s on this
  # emulator, and the system covers it with an "isn't responding" dialog while
  # the main thread is still inside setContent. Keep polling past that, and
  # tap Wait when that dialog is the thing on screen.
  for i in $(seq 1 120); do
    xml="$WORKDIR/ui.xml"
    if dump_ui "$xml"; then
      if locate_node either "$label" "$xml" >/dev/null; then
        return
      fi
      if locate_node text "EcoLift isn't responding" "$xml" >/dev/null; then
        if xy=$(locate_node text "Wait" "$xml"); then
          echo "dismissing the not-responding dialog" >&2
          run_adb shell input tap $xy
        fi
      fi
    fi
    sleep 1
  done
  echo "timed out waiting for '$label'" >&2
  exit 1
}

tap_node() {
  local mode="$1" label="$2" xml xy
  xml="$WORKDIR/ui.xml"
  dump_ui "$xml"
  if ! xy=$(locate_node "$mode" "$label" "$xml"); then
    echo "no $mode node '$label'" >&2
    exit 1
  fi
  echo "tap $mode '$label' at $xy" >&2
  run_adb shell input tap $xy
  sleep 0.6
}

run_step() {
  local line="$1" verb rest
  verb=${line%% *}
  if [[ "$line" == *" "* ]]; then
    rest=${line#* }
  else
    rest=""
  fi
  case "$verb" in
    wait) sleep "$(awk -v ms="$rest" 'BEGIN{printf "%.3f", ms/1000}')" ;;
    wait-text) wait_for_label "$rest" ;;
    tap-text) tap_node text "$rest" ;;
    tap-desc) tap_node desc "$rest" ;;
    tap-xy) echo "tap xy $rest" >&2; run_adb shell input tap $rest ;;
    swipe) run_adb shell input swipe $rest ;;
    back) run_adb shell input keyevent 4 ;;
    *) echo "unknown step: $line" >&2; exit 1 ;;
  esac
}

# The signature check rejects a text-mode redirect that rewrites newlines inside the PNG.
save_png() {
  local dest="$1" sig
  MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" exec-out screencap -p > "$dest"
  sig=$(od -An -t x1 -N 8 "$dest" | tr -d ' \r\n')
  if [[ "$sig" != "89504e470d0a1a0a" ]]; then
    echo "screencap did not write a png: $dest" >&2
    exit 1
  fi
}

run_shot() {
  local name="$1" steps="$2" line
  while IFS= read -r line <&3; do
    [[ -z "$line" ]] && continue
    run_step "$line"
  done 3<<<"$steps"
  sleep 0.5
  save_png "$OUT/$name.png"
  echo "captured $name"
}

launch_app() {
  if [[ "$FRESH" == "1" ]]; then
    run_adb shell pm clear "$PACKAGE"
  fi
  if [[ "$SEED" == "1" ]]; then
    run_adb shell am start -W -n "$PACKAGE/$ACTIVITY" --ez seedDemo true
  else
    run_adb shell am start -W -n "$PACKAGE/$ACTIVITY"
  fi
}

read_launcher
parse_shots
mkdir -p "$OUT"
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT
ensure_emulator
demo_status
run_adb install -r -t "$(cygpath -m "$APK")"
launch_app

for i in "${!SHOT_NAMES[@]}"; do
  run_shot "${SHOT_NAMES[$i]}" "${SHOT_STEPS[$i]}"
done
echo "serial $SERIAL"
