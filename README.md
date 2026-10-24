# EcoLift

[![Android CI](https://github.com/AymanCode/AI-LIfting-App/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AymanCode/AI-LIfting-App/actions/workflows/android-ci.yml)

EcoLift is a workout tracker. I built it because logging had turned into bookkeeping. The
numbers went in and mostly disappeared. I wanted a set to take seconds to record, old
workouts to be easy to find, and months of training to add up to a history I could read.

This repository is the Android app. It has the same screens as the web app,
[AymanCode/ecolift-web](https://github.com/AymanCode/ecolift-web), which I use at the gym.
The Android app keeps everything on the phone in Room, with no account and no sync.

## Screenshots

These are from the Android app with demo data.

| Workout log | Set card |
| --- | --- |
| <img src="docs/assets/android/log.png" width="390" alt="Friday's log suggesting Lower Strength, with Back Squat 270x5 and Romanian Deadlift 230x6 to beat"> | <img src="docs/assets/android/set-card.png" width="390" alt="Set 1 at 240 lb by 6 for a quads and glutes lift, with an Est. 1RM of 288 against 306"> |

| Progress | Split |
| --- | --- |
| <img src="docs/assets/android/progress.png" width="390" alt="Progress by split, with Upper Strength charts for Barbell Bench Press, Incline Dumbbell Press and Pull Up"> | <img src="docs/assets/android/splits.png" width="390" alt="October calendar with 6 sessions and the Wed, Oct 21 Upper Strength note"> |

| IronMind | Cardio |
| --- | --- |
| <img src="docs/assets/android/ironmind.png" width="390" alt="IronMind desk noticing Barbell Bench Press up from 210 to 220 lb and a flat Hanging Knee Raise"> | <img src="docs/assets/android/cardio.png" width="390" alt="Cardio week arc with 1 h 18 min over the last 7 days and a 30:00 treadmill session"> |

## What it does

- Logs exercises, sets, reps, weight, completion and rest time.
- Finds an exercise even when you don't type its saved name exactly.
- Keeps ordered training splits and suggests the next session. With **Smart suggest** on,
  Split shows splits learned from your recent sessions. With it off, Split shows only the
  splits you made.
- Shows each exercise's history, estimated 1RM, trend and previous sets.
- Saves a finished training cycle as a snapshot, so editing today's split never rewrites
  an old workout.
- Gives each cardio machine its own fields, such as incline, cadence, pace, stroke rate and
  floors.
- Backs up to and restores from a local file.
- Reads old notes written as `135x8`, `135 lb x 8`, `135 for 8 8 6` and similar.
- Has an assistant, IronMind, for corrections and imports that are slow to do by hand.

## IronMind

You type the change in plain words:

- `yesterday calf raises 90 for 12 10 8`
- `for last saturday my deadlift top set should say three fifteen for four not 275`
- `delete the extra squat set from May 10`

Kotlin code parses each request, so sending a message doesn't call a model. If the parser
can't read a line, it keeps your text as an editable draft instead of guessing. **Try AI** on
that draft calls Groq in a debug build with `GROQ_API_KEY` set, and Gemini Nano on API 31 and
newer otherwise. The model never writes to the database.

Deletes and renames wait in the chat with **Apply** and **Cancel**. Other changes save right
away, and the latest one offers **Undo** until you restart the app or restore a backup. The
prompt cases that check this behavior are in `src/test/resources/agent_eval`.

## Design choices

Logging shouldn't interrupt a workout. The current exercise gets the screen, the buttons
stay in reach, and last time's weights are easy to reuse. Much of the commit history is
small spacing, wording and tap fixes that I noticed between sets.

An old workout should never change because I edited the split I use now. The app stores a
finished cycle as a snapshot, and the migrations, backups and database constraints all treat
that history as product data.

Everything except IronMind works without a model. The assistant is there for edits that
take longer to type than to ask for.

## How it started

The first version tested whether small local models could build an app I would use. I ran
Qwen models from 3B to 9B through Aider and Continue and had an MVP on my phone in three
days. Even one function at a time, the small models wrote code that a larger model had to
repair too often, so I dropped the local-only rule. AI agents have written most of the code
since. I decide what to build, what stays, and what needs another pass when the app gets in
my way at the gym.

The web app is a Kotlin/Wasm PWA built with Compose Multiplatform, so it also runs on iOS
without App Store review. It uses Supabase for sign-in and storage.

## Run it

You need the Android SDK or Android Studio, JDK 17, and a device or emulator on API 26 or
newer.

```bash
git clone https://github.com/AymanCode/AI-LIfting-App.git
cd AI-LIfting-App
./gradlew installDebug
```

On Windows, use `gradlew.bat`. Gradle writes the APK to `build/outputs/apk/debug/`.

Only debug builds and opt-in assistant evals use a Groq key. `build.gradle.kts` reads
`GROQ_API_KEY` from a Gradle property or the environment, never from `local.properties`, and
release builds leave it blank. Don't commit `local.properties`, `.env`, model files or
release signing files.

`.android/debug.keystore` is checked in so debug and CI builds share a certificate. Upgrade
tests need that to install one build over another. It is not a release key.

## Test it

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest # needs a device or emulator
```

CI runs ktlint 1.5.0 and the unit tests, and builds the app and the instrumented-test APK.
It doesn't run the instrumented tests. It also checks the debug APK's signer and that a new
build can install over an older one. [docs/TESTING.md](docs/TESTING.md) has more commands.

## Code layout

```text
src/main/java/com/ayman/ecolift
  agent/             IronMind routing, tools, patches, audit and undo
  ai/                exercise classification and the Groq client
  android/           RoomRepos, AndroidAppServices and platform shims
  android/platform/  AndroidHost, backup UI, device actions and backup actions
  cardio/            cardio fields, parsers and OCR result types
  data/              workout models and the calculations the screens use
  data/room/         Room entities, mapped to the web models
  ui/                web screens, navigation, theme and view models
  web/               AppRepos, repository interfaces and demo data

analytics/  DuckDB loader for backup exports
schemas/    exported Room schemas
docs/       architecture and testing notes
```

Start with [AgentOrchestrator.kt](src/main/java/com/ayman/ecolift/agent/AgentOrchestrator.kt)
for an IronMind request, [Migrations.kt](src/main/java/com/ayman/ecolift/data/Migrations.kt)
for schema changes, and
[LogViewModel.kt](src/main/java/com/ayman/ecolift/ui/viewmodel/LogViewModel.kt) for the Log
screen. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) covers the rest.

## License

[MIT](LICENSE)
