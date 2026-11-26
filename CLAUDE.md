# CLAUDE.md — Fuel (personal Android app)

## What this is
A native Android app for one person (Jan) that combines smart-scale data with meal planning and food tracking and derives adaptive calorie/macro targets. Sideloaded, never published.

**Read first:** `docs/PRD.md` (requirements, algorithms, screens) and `docs/design-system.md` (visual tokens).
**Reference prototype:** `prototype/screens/*.dc.html` — interactive HTML mockups. They are a *reference*, not code to port 1:1:
- The markup between `<x-dc>` tags shows layout and styling (inline CSS).
- The `<script type="text/x-dc">` class `renderVals()` contains the sample data and much of the UI logic (statuses, unit conversion and clamping, ring/bar maths, recipe copies) — useful as a behavioural spec.
- `{{…}}`, `<sc-for>`, `<sc-if>`, `<dc-import>` are template syntax of the design tool; they will not run outside it.
- `prototype/design-directions/` holds the explored styles; `V2Mix` (D) is the chosen one.

## Stack and conventions
- Kotlin, Jetpack Compose, coroutines/Flow, Room, DataStore, WorkManager, Health Connect SDK, CameraX + ML Kit barcode scanning, Ktor or Retrofit for Open Food Facts.
- minSdk/targetSdk: Android 17 (Pixel). No backwards compatibility work needed.
- Single activity, Compose Navigation, MVVM with immutable UI state.
- Keep **all nutrition/energy maths in a pure Kotlin module** (`domain/` or `:core-model`) with JUnit tests — no Android dependencies there.
- Custom Compose theme from `docs/design-system.md`; do not ship the stock Material look.
- English UI strings in `strings.xml`; `app_name` = "Fuel".
- Application id / namespace: `dev.jandoant.fuel` (never change it).
- Launcher icon: adaptive icon from `assets/icon/` (foreground, background, monochrome).

## Hard rules (from the product decisions)
- Calorie targets come **only** from logged intake + weight trend + goal (% body weight/week). Never from Garmin, watch or BMR formulas. Garmin data is display-only (calibration card).
- Body fat / lean mass are informational; never feed them into calculations.
- Carbs have no own target: carb zone = calorie zone − planned protein×4 − planned fat×9, ÷4.
- Order and colours: Protein · Fat · Carbs · Fiber, eaten solid / planned soft / goal hatched or dark arc.
- Recipes are templates; planning creates an independent copy. Never propagate changes between template and planned copies.
- Days without a food log are excluded from averages, never treated as 0 kcal.
- Today always shows today (no date switching there).
- No "special day" feature.

## Workflow
1. Start with Milestone 0 (Health Connect spike) from the PRD and report what data origins/records actually exist on the device before building the data layer.
2. Build milestone by milestone; keep the app runnable after each one.
3. Add unit tests for every algorithm in PRD §6 before wiring it to the UI.
4. When the PRD is ambiguous, ask Jan rather than guessing; record decisions in `docs/decisions.md`.
