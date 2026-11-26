# Fuel — Product Requirements Document

**Owner / only user:** Jan
**Status:** Ready for implementation (v1)
**Platform:** Native Android app (Kotlin + Jetpack Compose), sideloaded, never published to the Play Store
**Target device:** Jan's Google Pixel running Android 17
**Prototype:** `prototype/screens/*.dc.html` (interactive mockups, style "D") — live canvas: https://claude.ai/artifact/BUi1si8RHKvbz72DkVsCDA

---

## 1. Summary

A personal app that combines **body measurements** (smart scale) with **meal planning and food tracking**, and turns both into an **adaptive, self-correcting calorie and macro target**. The core idea:

1. Jan plans his meals in advance.
2. During the day he only ticks off what he actually ate (or adjusts amounts).
3. The app estimates his real energy expenditure purely from **logged intake + body-weight trend** — never from watch or formula estimates — and derives a daily calorie zone from a weight-change goal expressed in **% of body weight per week**.
4. Protein and fat targets are ranges in **g per kg of trend weight**; **carbs fill the remaining calories**; **fiber** is a first-class minimum target.
5. A **weekly check-in** closes the feedback loop: goal reached? plan or execution? were the estimates right? what was adapted?

## 2. Goals and non-goals

### Goals
- Make planning the default: plan → tick off → done.
- Give honest, data-driven targets that adapt to Jan's real metabolism.
- Show clearly, at every moment, what was eaten, what is still planned and whether the plan fits the goals.
- Use the scale history Jan already has (Fitdays → Google Health → Health Connect).
- Work fully offline, store everything locally, back up automatically.

### Non-goals
- No Play Store distribution, no accounts, no multi-user, no sharing.
- No calorie targets based on Garmin/watch expenditure or BMR formulas (Garmin data is used **only** for an optional calibration view).
- No "special day" tagging (explicitly removed).
- No body-fat-based calculations (body fat is informational only).
- No changing the day on the Today screen (Today is always today).
- No water tracking in v1 (later phase).

## 3. Platform and technical constraints

| Area | Decision |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, custom theme (no stock Material look — see `docs/design-system.md`) |
| Min / target SDK | Android 17 (use the corresponding API level; verify the exact number when scaffolding) |
| Architecture | Single-activity, MVVM / unidirectional data flow, Kotlin coroutines + Flow |
| Persistence | Room (SQLite) for all domain data, DataStore for settings |
| DI | Hilt (or manual DI if kept small — implementer's choice, document it) |
| Health data | Health Connect SDK (`androidx.health.connect:connect-client`) |
| Barcode | CameraX + ML Kit Barcode Scanning (on-device) |
| Food database | Open Food Facts API v2 via Ktor or Retrofit |
| Background work | WorkManager (Health Connect sync, daily recalculation, daily backup) |
| Distribution | Debug/release APK installed via ADB or Android Studio; signing key kept locally |
| Language | English UI only |
| Telemetry | None |

## 4. Data sources and integrations

### 4.1 Health Connect (read)
Data flow for the scale: **Fitdays app → Google Health → Health Connect**. Garmin: **Garmin Connect → Health Connect** (Garmin Connect supports Health Connect).

Read record types:
- `WeightRecord` — primary signal for everything.
- `BodyFatRecord`, `LeanBodyMassRecord` — informational composition trend only.
- `TotalCaloriesBurnedRecord`, `ActiveCaloriesBurnedRecord` — only for the Garmin calibration feature; **filter by data origin** (Garmin Connect package) so phone/other sources are not mixed in.

Requirements:
- Request `READ_HEALTH_DATA_HISTORY` to import **all historic weigh-ins** (Health Connect otherwise only returns ~30 days before the permission grant). This is a v1 requirement ("Import old Fitdays history").
- Incremental sync with change tokens; periodic sync via WorkManager and on app open.
- Deduplicate measurements (same timestamp/origin). Keep the data origin on every stored record.
- **Spike first (Milestone 0):** verify on the device which package appears as data origin for Fitdays weight/body fat (Fitdays directly or Google Health), which body metrics actually arrive, and how far back history goes.

### 4.2 Health Connect (write)
- Write a `NutritionRecord` for every **eaten** entry (energy, protein, fat, total carbs, fiber, plus available micronutrients). Use `clientRecordId` = entry id so edits update and un-ticking deletes the record.
- Never write planned (not eaten) entries.

### 4.3 Open Food Facts
- `GET /api/v2/product/{barcode}?fields=…` for barcode lookups; search via the search API.
- Send a **custom User-Agent** (`Fuel/1.0 (personal use; contact email)`).
- Respect rate limits (product reads ~100/min, search ~10/min); debounce search input.
- **Cache every product locally** (Room) — recent/favourites/my foods work offline.
- Default country/language: Germany / German product names where available.
- Products with missing values must be flagged (e.g. "Fibre missing — fill in") and editable locally (local override, never written back to OFF in v1).
- Attribution to Open Food Facts (ODbL) in Settings → About.

## 5. Core concepts and domain model

### 5.1 Entities (suggested Room schema, adapt as needed)
- **Food** — single item: name, brand, barcode?, source (OFF / custom), nutrients per 100 g (energy, protein, fat, carbs, sugar, saturated fat, fiber, salt, micronutrients), local overrides, favourite flag.
- **ServingUnit** — per food: name, grams per unit, step (for −/+), min, max, isDefault, source (built-in / Open Food Facts / user). Grams always exist (name and weight locked, step/range editable). Volume units (ml) treated as grams unless density is defined.
- **RecipeTemplate** — name, tags (Breakfast/Main/Snack…), favourite, ordered list of **TemplateIngredient** (foodId, unitId, amount). **No portion count.** Nutrition = sum of ingredients at default amounts.
- **PlanEntry** — date, mealSlot (Breakfast | Lunch | Dinner | Snacks, plus one optional Extra slot; see Settings), type (single | recipe), eaten flag, sort order.
  - Meal slots: Breakfast, Lunch, Dinner and Snacks are fixed. One **optional Extra slot** (default name "Pre-workout") is off by default; when enabled in Settings the user sets its name and position. Store the slot as data (id + display order), not a hard-coded enum. Disabling the slot hides it from Today and the plan but keeps existing entries (shown under it on days that have them).
  - Single: foodId, unitId, amount.
  - Recipe: templateId (for reference only) + **own copy** of ingredients (EntryIngredient: foodId, unitId, amount). Edits never touch the template; template edits never touch existing entries. `edited` = differs from the template snapshot taken at planning time.
- **Measurement** — timestamp, type (weight / bodyFat / leanMass), value, origin.
- **GarminEnergy** — date, total/active kcal, origin.
- **Phase** — id, name, startDate, endDate?, startingMaintenance. The model only uses data inside the current phase; older data remains visible.
- **ModelSnapshot** (daily) — date, trendWeight, maintenance, maintenanceUncertainty, calorieZone (low/high/target), macro targets, data-quality metrics.
- **CheckIn** (weekly) — week, goal result, plan-vs-execution stats, forecast, outcome, adaptation applied, explanation text.
- **Settings** — goal rate range, reference-weight window, protein/fat g/kg ranges, fiber minimum, max daily target change, calorie floor, Garmin calibration on/off, backup folder URI, optional extra meal slot (enabled, name, position).

### 5.2 Planned vs eaten
- Eaten food is always a subset of the plan (ticked entries). Unplanned food is added as a new entry (it raises the plan too).
- Every number on Today distinguishes **eaten**, **still planned** (= planned but not yet ticked) and **goal**.
- Optional later: compare against a frozen "original plan" snapshot (see §11).

## 6. Algorithms (must be unit-tested)

All constants below are defaults; keep them in one place.

### 6.1 Trend weight
- Time-aware exponential smoothing of daily weigh-ins (weight readings in the morning, standardised by the user). Alpha per day ≈ 0.1–0.15; for gaps, decay by elapsed days rather than inventing values.
- Multiple readings per day: use the first morning reading (or the minimum) — document the choice.

### 6.2 Balance from weight (no calorie data)
- Linear regression of weigh-ins over the last **28 days** → slope (kg/day).
- `balance_kcal_per_day = slope × 7700`.
- Uncertainty from residual scatter → "likely range". Shown on Body as "Balance from weight". Always uses 28 days regardless of chart range.

### 6.3 Maintenance (expenditure) estimate
- `maintenance ≈ average logged intake (logged days only) − balance_from_weight` over a rolling 28-day window.
- Smoothed with a Kalman-style update: new evidence weighted by data quality (days logged, weigh-ins, residual noise). Output: value + uncertainty band (shown on Energy chart).
- **Bootstrap:** until 3 weeks of data exist in the current phase, start from the user's starting maintenance (Settings) and blend towards the estimate.
- Missing food-log days are excluded, never counted as 0 kcal. If >2 days/week are missing, do not adapt maintenance that week.
- Weight gaps: 2–6 days → hold trend, widen uncertainty, adapt cautiously; ≥7 days → freeze maintenance, hold the target, mark the check-in "not enough data", recalibrate the trend gradually after return.

### 6.4 Goal → calorie zone
- Goal = range in **% body weight per week** (presets: Maintain, Lose 0.25–0.5 %, Lose 0.5–1.0 %, Gain 0.25–0.5 %).
- `kcal_delta = pct/100 × trendWeight × 7700 / 7` for both range ends → **calorie zone** `[low, high]`; daily **target = midpoint**.
- Daily target changes are capped (default **±50 kcal/day**); never below the **calorie floor** (default 1,800, configurable). Warn if the actual loss rate stays faster than 1 %/week.
- Future (planned) days use the current zone; when the model updates, planned days are re-evaluated and flagged if they fall outside — the plan itself is never changed automatically.

### 6.5 Macro and fiber targets
- Reference weight = **trend weight** over a configurable window (7 / 14 / 28 days, default 14). Recomputed once per day.
- Protein: range g/kg (default **1.8–2.0**). Fat: range g/kg (default **0.5–0.8**). Fiber: fixed minimum g/day (default **30 g**, DGE).
- **Carbs have no own target.** Per day: `carbs_low = (zone_low − plannedProtein×4 − plannedFat×9)/4`, `carbs_high = (zone_high − plannedProtein×4 − plannedFat×9)/4`, using the day's **planned** protein and fat. Hide the carb zone on days with no protein/fat planned.

### 6.6 Statuses
For each of calories, protein, fat, carbs, fiber:
- `fits` — planned total inside the zone (calories also show "X spare" or "X above target").
- `X short` / `X over` — planned total outside the zone (orange).
- `eaten X over` — eaten alone already exceeds the maximum (red + warning icon; big calorie number switches to "Over goal by").

### 6.7 Weekly check-in (feedback + adaptation)
1. **Goal reached?** Actual rate (trend, %/week) vs goal range; projection to a goal weight.
2. **Plan or execution?** Daily averages planned vs eaten for calories, protein, fat, carbs (vs dynamic zone) and fiber, with "days in range" counts. Diagnosis matrix:
   - Ate on target + rate on target → no change.
   - Ate on target + rate off → **estimate wrong → correct maintenance**.
   - Ate off target → **execution** → keep maintenance, explain.
3. **Forecast vs outcome:** each week store a forecast (expected trend change at planned intake); compare with the outcome; show hit history (±0.1 kg).
4. **What was adapted:** maintenance old → new, target old → new, one-sentence reason (e.g. "60 % of the ~65 kcal/day gap applied").
5. Composition note (low confidence) — fat vs lean trend over ≥4 weeks.
- Minimum data for a verdict: ≥4 weigh-ins and ≥5 logged days per week.

### 6.8 Garmin calibration (v1, optional, Settings toggle)
- `real_expenditure_week ≈ logged intake − balance_from_weight`.
- Regression over ≥8–12 weeks: `real_weekly_expenditure = a + b × Garmin_active_kcal` → factor `b` with confidence range; also overall ratio Garmin total / real.
- Display only (Energy screen card "Garmin calibration"); **never used for targets in v1**. Note that logging bias cannot be separated from Garmin error.

### 6.9 Body composition
- Body fat % and lean mass: smoothed trend + uncertainty band only; never used in calculations. Label "Estimated by the scale".

## 7. Screens and behaviour

Navigation: **side drawer** (no bottom tab bar). Main: Today (home), Meal plan, Calendar, Body, Energy, Trends, Weekly check-in. Secondary: Recipes, My foods, Macro & fiber targets, Settings. Sub-screens have a back arrow.
Prototype file names in brackets.

### 7.1 Today (home) [`Main.dc.html`]
- Header: menu button, "Today" + date (no day switching, no week strip), trend-weight chip → Body.
- **Calories** (compact, no card): heading, "N left" (target − eaten), horizontal bar (eaten solid green, still planned light green, goal zone with white hatching), two small green dots above the bar at the zone edges, line "X eaten · +Y planned" + status.
- **Macros** (no card): four **rings** (Protein, Fat, Carbs, Fiber): eaten arc solid, planned arc light, goal range as thin dark outer arc; centre = eaten, below = +planned; name + status badge. Legend eaten/planned/goal. Tapping → Macro & fiber targets.
- Micronutrients collapsed row ("so far today").
- **Today's plan** (own scroll area): timeline with meal sections (Breakfast/Lunch/Dinner/Snacks, plus the optional Extra slot when enabled) using time-of-day icons; icon turns green when all entries in that meal are eaten.
  - Each entry: round checkbox (toggles planned ↔ eaten — no separate "eaten" button), recipe badge / single item, name, amount, P/F/C/Fib (colour-coded, always visible; amount text truncates first), kcal, chevron.
  - Eaten = white card; planned = dashed outline, transparent.
  - Tap → expand inline:
    - Single item: amount input + serving-unit dropdown + grams equivalent.
    - Recipe: "Your copy of the recipe — changes apply only to this entry"; every ingredient with its own amount input, unit dropdown and nutrients; "+ Add ingredient"; "Save changes to template…" (explicit).
    - Recipe entries that differ from the template show an "edited" chip.
  - Every meal section ends with a dashed "+ Add to <meal>" row → Add food with that meal preselected.
- All numbers update live when amounts change or entries are ticked.

### 7.2 Add food [`AddFood.dc.html`]
- No meal dropdown (destination comes from the entry point).
- Barcode scanner (camera, flashlight), search field (OFF + own foods), tabs Recent / Favorites / My foods.
- List items: recipe or single icon, name, "RECIPE · n ingr." badge, amount + colour-coded P/F/C, kcal, "+" button. Tap item → Food details (read-only).
- "+" opens a **bottom sheet**:
  - Single: amount input + unit dropdown (with the food's serving units, steps, ranges).
  - Recipe: all ingredients adjustable for this entry ("template stays unchanged").
  - **Effect on today's plan** (or on the target day / recipe): for kcal, protein, fat, carbs (dynamic zone), fiber — bar with planned + this food + hatched goal zone, "before → after" and resulting status.
  - Primary button "Add 150 g" / "Add recipe · N kcal".
- "Not found? Create a product or recipe."

### 7.3 Food details [`Portion.dc.html`]
- **Read-only** for adding (no add button, no amount picker).
- Name, brand, barcode, source chips, favourite star, per-serving summary, nutrition table (100 g | serving), missing-value hints with "fill in".
- **Serving units editor**: list of units (name, = grams, step, range min–max, default toggle, source label). Grams locked except step/range. "+ Add serving unit". These units are used everywhere the food can be added or edited (step drives −/+, range clamps typing and buttons, default unit is preselected for new entries; switching units converts and clamps the amount).

### 7.4 Meal plan [`Plan.dc.html`]
- Week strip (Mon–Sun) with per-day fill indicator (fits = green, off = orange), week navigation.
- Day summary: planned kcal vs zone + status; Protein / Fat / Carbs / Fiber in a 2×2 grid with ranges (carb zone dynamic), colour-coded values outside range; micronutrients collapsed.
- Meal slots with entries (P/F/C/Fib), edit buttons.
- Actions: Copy day, Use template, Shopping list (shopping list may be a stub in v1).

### 7.5 Calendar [`Calendar.dc.html`]
- **Week view (default)** and month view toggle.
- Week: 7 columns with vertical calorie bars stacked by macro calories (bottom → top: **Protein, Fat, Carbs**), dashed outline for planned calories, dashed target line, number under each bar; summary (avg kcal, days on target) + legend.
- Month: day grid with status dots (on target / off / planned / none).
- Selected day details: totals (kcal, P/F/C/Fib colour-coded), micronutrients collapsed, meals with items (recipe/single icon, amount, P/F/C/Fib, kcal) and actions **"+ Today"** (item), **Copy meal**, **Copy day** → toast with Undo. Copied items keep their original meal slot by default.

### 7.6 Body [`Body.dc.html`]
- Range tabs: **4 weeks / 6 months / All time** (all time includes imported history).
- Trend-weight chart: daily dots + smoothed line, dynamic axis, start/"Today" labels, change for the period.
- "Balance from weight" card (always 28-day): ≈ kcal/day, label deficit/maintenance/surplus, scale −600…+600 with likely range, slope.
- Composition chart: body fat % (left axis 0–30) and lean mass kg (right axis 0–80) in one chart, both with trend + band; current values and period change; "not used in calculations".
- Recent measurements list.

### 7.7 Energy [`Energy.dc.html`]
- Maintenance estimate + uncertainty (± kcal), chart over ~8 weeks with narrowing band (add a small legend: line = estimate, band = likely range).
- Goal picker (% per week presets) → kg/week text.
- Today's target + zone; note that protein/fat come from g/kg ranges and carbs fill the rest.
- Actual rate vs goal range (on track / faster / slower).
- Link to the weekly check-in.
- Garmin calibration card (when enabled).

### 7.8 Trends [`Trends.dc.html`]
- Range tabs 4 weeks / 6 months / All time.
- **Calories & weight** chart: intake bars (daily / weekly avg / monthly avg depending on range; gaps for unlogged days), stepped calorie target line, weight trend + weigh-ins on a right axis.
- Stats: avg intake, avg target, weight change.
- **Fiber** chart vs 30 g line, average and days/weeks reaching the minimum.
- Micronutrients (daily average) collapsed.

### 7.9 Weekly check-in [`CheckIn.dc.html`]
- Sections 1–4 as in §6.7. Plan-vs-execution as a **chart**: per nutrient a row with target band (carbs included, dynamic), 7 daily dots in the nutrient colour (outside the band = off target, no extra colour), hollow marker = planned average, solid bar = actual average; label "Calories 2,510 kcal" with a target icon + planned average beneath; days-in-range count (orange below 5/7).

### 7.10 Recipes [`Recipes.dc.html`] and Recipe details [`RecipeDetail.dc.html`]
- List with search, filters (All, Favorites, Breakfast, Main, Snack), "New recipe"; cards show ingredients count, usage, kcal + P/F/C/Fib at default amounts.
- Details: template notice ("Planning creates an independent copy …, N planned entries keep their own amounts"), nutrition at default amounts with macro share bars, micronutrients, ingredient list with amount inputs / units / remove, "+ Add ingredient", buttons "Plan…" and "Add to today".

### 7.11 Macro & fiber targets [`Targets.dc.html`]
- Reference weight + window (7/14/28 days).
- Protein and fat Min/Max g/kg with inputs; resulting g/day.
- Fiber minimum g/day.
- Note: "Carbs have no target of their own — they fill the rest of the calorie zone."

### 7.12 Settings [`Settings.dc.html`]
- **Tracking phase**: current phase start date (date picker), "Start fresh from today" (confirmation; creates a new phase, nothing is deleted), previous phases list (incl. "Scale history only" for legacy weigh-ins).
- Goal & targets (links), Adaptive model (max target change, calorie floor, starting maintenance — move into phase section), Data sources (Health Connect status, Garmin calibration toggle, OFF country), Meal plan (optional extra meal slot: on/off, name, position between the fixed slots), General (units, backup folder, export/import, About/attribution).

### 7.13 Shared components
- **Navigation drawer** [`NavDrawer.dc.html`]: summary (trend weight, target), main + secondary items, active highlight, sync status.
- **Micronutrients section** [`Micros.dc.html`]: collapsed by default with summary ("1 low · 1 over limit"); expanded: Limits (salt ≤6 g, free sugar ≤10 % energy, saturated fat ≤10 % energy) and Vitamins & minerals (potassium, calcium, magnesium, iron, zinc, vitamins C, D, B12, folate) vs DGE references; data-coverage note; "today" variant does not flag "low" before the day is over.
- **Amount input**: compact stepper (−, typeable field, +) ~32 px high; accepts comma or point; keeps raw text while typing, normalises on blur; respects the unit's step and range; next to it a compact unit dropdown and grams equivalent.

## 8. Design

Use the visual language of prototype style **D** (see `docs/design-system.md`): warm off-white background, white cards with soft shadows and large radii, Manrope, green accent, macro colours Protein indigo / Fat orange / Carbs green / Fiber gold, recipe purple, consistent order **P · F · C · Fib** everywhere, eaten = solid, planned = light tint, goal = hatched zone / dark arc.

## 9. Non-functional requirements
- **Offline-first**: everything except OFF lookups and Health Connect sync works without network.
- **Privacy**: all data on device; no analytics; backups only to a user-chosen location.
- **Backup**: daily automatic JSON export (WorkManager) into a folder chosen via the Storage Access Framework (e.g. a Google Drive folder), keep the last N files; manual export/import in Settings. Import must restore the full database.
- **Performance**: Today renders < 300 ms from cold data; all recalculations off the main thread.
- **Correctness**: algorithm module with unit tests (trend, regression, maintenance updates, zones, carb zone, statuses, check-in diagnosis, unit conversion and clamping).
- **Accessibility**: content descriptions on icon buttons, colour never the only signal (status text accompanies colour), 4.5:1 contrast for text.

## 10. Milestones
0. **Spike** — Health Connect permissions incl. history; confirm data origins (Fitdays/Google Health, Garmin); read all weigh-ins; write a test NutritionRecord.
1. **Data layer** — Room schema, Health Connect sync worker, OFF client + cache, serving units.
2. **Today + Add food** — plan entries, tick off, expand/edit, add sheet with effect preview, Food details (+ serving units editor).
3. **Planning** — Meal plan, recipes (templates + copies), Calendar (week/month, copy to today).
4. **Model** — trend, balance, maintenance, goal → zone, macro/fiber targets, statuses; Energy screen; daily snapshots.
5. **Insights** — Body (incl. history import), Trends, weekly check-in, Garmin calibration.
6. **Settings & backup** — phases, backups, export/import, polish.

## 11. Later phases (not v1)
- Water tracking (design pending; was a planned "Add water" button).
- Fully configurable meal slots (beyond the four fixed slots plus one optional extra).
- Comparison against a frozen "original plan" ("+245 kcal vs original plan").
- Day-by-day targets based on calibrated Garmin activity.
- Shopping list generation from the plan.
- Home-screen widget (calories + macros).
- Optional cloud sync / desktop view.
- Contributing missing product data back to Open Food Facts.

## 12. Assumptions and open questions
- Confirmed 2026-11-26: four fixed meal slots plus one optional extra slot (#19); only eaten entries are written to Health Connect (#20); daily JSON backup to a user-picked folder (#3).
- To verify in Milestone 0: exact Health Connect data origins and which body metrics Fitdays provides via Google Health; availability of history beyond 30 days.
- Weight readings are standardised (morning, fasted, after toilet, dry feet) — the model may smooth less aggressively.
- The prototype uses sample data and generated values (e.g. per-meal macros on the Meal plan); real values come from the data layer.
