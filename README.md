# EcoLift 🏋️‍♂️

A local-first, high-efficiency lifting app designed for the gym floor. Built with Kotlin, Jetpack Compose, and Room.

## Core Features

- **🚀 Instant Logging**: Add exercises with fuzzy search (Jaro-Winkler) and quick-add chips.
- **🔄 Dynamic Cycle Tracking**: Automatically loads your previous session's weight/reps based on your workout split (e.g., Push/Pull/Legs).
- **📈 Progress Visualization**: Track Personal Bests (PBs) and estimated 1RMs inline as you lift.
- **⏱️ Smart Rest Timer**: Auto-starting rest timer that stays visible while you log your next set.
- **🤖 IronMind AI (Beta)**: On-device LLM (Gemma 2B) for hands-free workout logging and equipment alternatives.
- **🛡️ Local-First**: No cloud, no account, no tracking. Your data stays on your device.

## What's New (v4.0)
- **Inline Editing**: Tap any exercise name to rename it instantly.
- **Database Migrations**: Safe migration from v3 to v4 with `restTimeSeconds` support.
- **UI Isolation**: AI engine is lazy-loaded to keep the main log snappy and crash-free.

## Architecture

- **UI**: Jetpack Compose + Material 3 (Material Icons Extended)
- **Database**: Room DB (v4) with safe migrations.
- **AI**: MediaPipe LLM Inference (Gemma 2B).
- **Pattern**: MVVM with StateFlow and Repository pattern.

## AI Setup (IronMind Agent)
IronMind uses an on-device LLM to process natural language. 
1. Go to the **IronMind** tab.
2. If the model is missing, use the **"Select Model File"** button to load a `gemma.bin` file (Gemma 2B / 4-bit quantized).
3. Once loaded, you can type or speak commands like *"Log 3 sets of bench at 225"* or *"What's a good alternative for the leg press?"*

## Development
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle 8.0.2

---
*Developed as a "Day 1" project using local LLMs (Qwen 2.5 Coder) and Claude Code.*
