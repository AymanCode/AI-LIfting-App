# Capture Android screenshots

`capture.sh` installs an EcoLift debug APK on the `ecolift_pixel` emulator and writes one PNG for each block in `shots.txt`.

## Prepare the machine

You need the Android SDK at `C:/Users/ayman/AppData/Local/Android/Sdk`, including platform-tools, emulator, build-tools, and the android-34 default x86_64 system image. The AVD name is `ecolift_pixel`, created from that image on a Pixel 7 profile at 1080 by 2400. Run the script with Git Bash from `C:/Program Files/Git/bin/bash.exe`. Python must be on `PATH`. The script uses it to read uiautomator XML.

## Capture a set

Pass the APK, then the output directory.

```
"C:/Program Files/Git/bin/bash.exe" scripts/screenshots/capture.sh path/to/Ecolift-debug.apk path/to/out
```

If no emulator is connected, the script starts `ecolift_pixel` headless. The flags are `-no-window`, `-no-audio`, `-no-boot-anim`, `-no-metrics`, and the selected `-gpu` mode. The script waits until `sys.boot_completed` is 1. It allows SystemUI demo mode, sets the clock to 0930, sets a full battery, shows full wifi, and hides notifications. It installs the APK with `adb install -r -t`. It reads the launcher activity with `aapt dump badging` and starts that activity. Each shot is written to `path/to/out/<name>.png`.

`aapt` must report the package `com.ayman.ecolift`. Any other APK stops the script before install.

## Choose a flag

Put flags after the output directory.

- `--fresh` clears application data for `com.ayman.ecolift` after install and before launch.
- `--seed-demo` launches with `--ez seedDemo true`. The baseline app ignores that extra. A later debug build can read it and load demo data.
- `--shots <file>` reads that step list instead of `scripts/screenshots/shots.txt` next to the script.
- `--serial <serial>` uses that adb device and does not start an AVD.
- `--avd <name>` is the AVD to start when none is running. The default is `ecolift_pixel`.
- `--gpu <mode>` is the emulator `-gpu` value when this script starts the AVD. The default is `swiftshader_indirect`.

`ECOLIFT_AVD`, `ECOLIFT_GPU`, and `ANDROID_SDK_ROOT` override those defaults. The script reads `ANDROID_HOME` when `ANDROID_SDK_ROOT` is unset.

Running the script again with the same arguments replaces the PNGs. The script reuses one connected emulator, including a row that is still `offline` during boot.

## Edit a shot

`shots.txt` holds one verb per line. A `#` starts a comment. `shot <name>` begins a PNG called `<name>.png`. The lines under it run in order, and then the script captures.

- `wait <milliseconds>` pauses.
- `wait-text <label>` polls until a node shows that exact text or content description.
- `tap-text <label>` taps the lowest node with that exact text.
- `tap-desc <label>` taps the lowest node with that exact content description.
- `tap-xy <x> <y>` taps those pixels.
- `swipe <x1> <y1> <x2> <y2> <milliseconds>` swipes.
- `back` sends the system back key.

The lowest match is the tab when the same word also appears in the page. The tab row sits under the page content.

## Proof output

The baseline proof writes into the detached worktree. Keep those PNGs out of the parity branch.

```
"C:/Program Files/Git/bin/bash.exe" scripts/screenshots/capture.sh "C:/Users/ayman/Documents/ecolift-android-baseline/build/outputs/apk/debug/Ecolift-debug.apk" "C:/Users/ayman/Documents/ecolift-android-baseline/artifacts/screenshots"
```

Leave those PNGs uncommitted. The script files under `scripts/screenshots` are the files the lead commits with the UI work.
