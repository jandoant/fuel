# Targets maths: defaults and why

The pure-Kotlin module `:core-model` (`dev.jandoant.fuel.core.model`) implements PRD §6.1–6.6.
All constants live in `ModelDefaults.kt`. Where the PRD left a choice open, the code made a choice, marked `CHOICE` in the source.
Jan confirmed all of them on 2026-11-26 (decisions #26–28); G14 and G17 were changed to the alternative.

| # | Topic | Choice in the code | Rejected alternative |
|---|---|---|---|
| G1 | Trend smoothing | α = 0.10 per day | 0.15 (PRD hints readings are standardised, so less smoothing is fine) |
| G2 | Several weigh-ins per day | First reading of the day | Lowest reading |
| G3 | Reference weight for g/kg | Average of the daily trend over the 7/14/28-day window (matches the Targets prototype numbers) | Simply the latest trend weight |
| G4 | "Likely range" for balance from weight | ±1 standard error (~68 %) | ±1.96 SE (95 %) — about twice as wide |
| G5 | Balance label | "Maintenance" whenever the likely range includes 0 | Fixed band, e.g. ±100 kcal |
| G6 | Maintenance update cadence | Once per 7 days of the phase (Kalman update), daily zone recomputed | Daily update |
| G7 | Kalman tuning | Start ±300 kcal, drift ±50 kcal/week, logging-noise floor ±100 kcal. In steady state about 30 % of a gap is applied per week; uncertainty settles near ±75 kcal | Faster (the check-in mockup says "60 % of the gap applied") |
| G8 | Bootstrap (< 21 days) | Same update, but evidence weighted down by 21 / days-in-phase | Linear blend from starting maintenance |
| G9 | Weight gap 2–6 days | Evidence weighted down ×2 | — |
| G10 | ±50 kcal/day cap | Whole zone shifts with the capped target (width kept) | Cap only the target |
| G11 | Calorie floor | Target and both zone ends lifted to ≥ floor | — |
| G12 | No weigh-in yet in the phase | No calorie zone shown | Use starting maintenance with a guessed weight |
| G13 | Carb zone below 0 g | Clamped to 0 | Show negative / warn |
| G14 | "N left" headline | target − eaten, clamped at 0; status says "X above target" | Negative "left" (as in the prototype) |
| G15 | Rate status for Maintain | ±0.1 %/week = on track, else "losing"/"gaining" | — |
| G16 | Rate status for Gain | Direction-aware ("faster" = gaining more than goal). The Energy mockup treated gain like loss | — |
| G17 | Actual rate | 28-day weigh-in regression slope ÷ trend weight (same as Balance from weight) | Trend change over the last 7 days |

## Build
Verified locally 2026-11-27: AGP 9.5.3, Kotlin 2.4.21, Compose BOM 2026.11.01, compileSdk 37.1 (Compose 1.13 requires it), Gradle 9.9.2, JDK 21. Manrope is bundled as a variable font (`app/src/main/res/font/manrope.ttf`).
