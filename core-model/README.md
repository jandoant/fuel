# :core-model

Pure Kotlin (no Android) maths for Fuel's adaptive targets. Run the tests with `./gradlew :core-model:test`.

| File | PRD | What |
|---|---|---|
| `ModelDefaults.kt` | §6 | Every constant in one place |
| `TrendWeight.kt` | §6.1, §6.5 | Daily weigh-in choice, time-aware smoothing, reference weight |
| `WeightRegression.kt` | §6.2 | 28-day slope → balance from weight + likely range |
| `Maintenance.kt` | §6.3 | Weekly Kalman update, missing-log and weight-gap rules, bootstrap |
| `CalorieZone.kt` | §6.4 | Goal %/week → zone, ±50 kcal/day cap, floor, rate check |
| `MacroTargets.kt` | §6.5 | Protein/fat g/kg, fiber minimum, carb fill from planned P/F |
| `Status.kt` | §6.6 | fits / short / over / eaten over, calorie headline |
| `TargetsEngine.kt` | — | Replays the model day by day from the phase start → daily snapshots |

Defaults and the reasons for them are in `docs/maths-assumptions.md`.
