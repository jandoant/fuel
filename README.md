# Fuel

Personal Android app (Kotlin + Jetpack Compose) for body measurements, meal planning and adaptive calorie/macro targets. Single user, sideloaded.

## Contents
| Path | What |
|---|---|
| `CLAUDE.md` | Instructions for Claude Code (read automatically) |
| `docs/PRD.md` | Product requirements: goals, data sources, algorithms, every screen |
| `docs/design-system.md` | Colours, typography, shapes, chart rules (style D) |
| `docs/decisions.md` | Decision log — start here when something changes |
| `docs/maths-assumptions.md` | Targets maths defaults and the alternatives that were rejected |
| `app/` | Android app: Compose UI (all v1 screens), ViewModels, WorkManager jobs, manual DI (`AppContainer`) |
| `core-data/` | Room schema, repositories, Health Connect gateway, Open Food Facts client, JSON backup |
| `core-model/` | Pure Kotlin maths for adaptive targets (PRD §6) with unit tests |
| `prototype/screens/` | Interactive mockups of all screens (Claude Design canvas source) |
| `prototype/design-directions/` | The four explored visual directions (D = chosen) |
| `assets/icon/` | App icon: adaptive foreground/background/monochrome SVGs, 512 px PNG, preview |

The prototype can be viewed and clicked through on the canvas:
https://claude.ai/artifact/BUi1si8RHKvbz72DkVsCDA (Page 1 = app, "v2 – design directions" = styles).

## Getting started with Claude Code
```bash
cd fuel
git init && git add . && git commit -m "Docs and prototype"
claude
```
First prompt suggestion:
> Read CLAUDE.md and docs/PRD.md. Scaffold the Android project (Kotlin, Compose, Room, Hilt, WorkManager, Health Connect) and implement Milestone 0: the Health Connect spike. Tell me which data origins and record types you find.

## Building and installing
JVM tests: `./gradlew test` (JDK 21). On-device tests (Room, backup, Compose components): `./gradlew connectedDebugAndroidTest` — note this uninstalls the app afterwards, so do not run it on a phone with real data unless you have a backup.

Open the project in Android Studio (or use `./gradlew installDebug`) with the Pixel connected via USB/wireless debugging. Keep your signing key outside the repository.
