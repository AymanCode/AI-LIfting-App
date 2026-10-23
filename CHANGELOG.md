# Changelog

## 2026-10-23

Android cold starts could show a light window and an empty Log while startup work blocked drawing.
This delayed access to saved workouts and briefly suggested that an existing session had no lifts.
Startup now uses an espresso window, runs startup data work on workers, reuses the exercise index, isolates transaction drafts, and initializes Log from loaded sets.
