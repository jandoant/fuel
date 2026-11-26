# Decision log

| # | Decision | Why |
|---|---|---|
| 1 | Native Android app (Kotlin + Compose), sideloaded, single user | Personal use only; direct Health Connect access |
| 2 | Target Android 17 on Jan's Pixel | No compatibility burden |
| 3 | Local-only storage (Room) + daily JSON backup to a user-picked folder | Privacy, simplicity; backup target confirmed by Jan 2026-11-26 |
| 4 | English UI | Like the prototype |
| 5 | Scale data via Fitdays → Google Health → Health Connect (no BLE, no cloud scraping) | Keeps Fitdays' body-composition values; zero reverse engineering |
| 6 | Calorie target = logged intake + weight trend + goal (% BW/week); never Garmin/BMR | Watch expenditure is too inaccurate; adaptive approach self-corrects logging bias |
| 7 | Goal is a range in % of body weight per week → calorie zone, target = midpoint | Daily variation is fine; model works on weekly averages |
| 8 | Protein/fat as g/kg trend-weight ranges (window 7/14/28 d); carbs fill the rest; fiber minimum 30 g | User preference; carbs derived from planned P/F |
| 9 | Body fat informational only | BIA noise and hydration bias, especially early in a deficit |
| 10 | Weekly check-in with diagnosis (plan vs execution) and adaptation | Closes the feedback loop transparently |
| 11 | Garmin calibration in v1, display only | Learn the watch's bias without using it for targets |
| 12 | Import full Fitdays history via Health Connect history permission | Many legacy weigh-ins exist |
| 13 | Tracking phases (start date, start fresh) instead of deleting data | Restart the model without losing history |
| 14 | Recipes are templates; planned recipes are independent copies | Edits must not leak either way |
| 15 | No portions on recipe templates | Recipes describe one usual serving; batch splitting via multiple entries |
| 16 | Serving units per food with step + range, editable on Food details | Fast, sensible input everywhere |
| 17 | Food details is read-only for adding; adding only via "+" sheet with effect preview | One clear way to add |
| 18 | Today shows only today; side-drawer navigation; no bottom bar/FAB | Focus; more room for plan |
| 19 | Four fixed meal slots (Breakfast, Lunch, Dinner, Snacks) plus one optional Extra slot (off by default, user sets name and position, e.g. "Pre-workout") | Confirmed by Jan 2026-11-26; covers training days without full slot configuration |
| 20 | Write only eaten entries to Health Connect as NutritionRecord (one per entry; un-ticking deletes it) | Confirmed by Jan 2026-11-26; Health Connect reflects what was actually eaten |
| 21 | No "special day" tagging | Explicitly removed by the user |
| 22 | Visual style D: warm off-white, Manrope, rings for macros, compact calorie bar, timeline plan | Chosen from four directions |
| 23 | App name is **Fuel** (formerly "Body & Nutrition") | Confirmed by Jan 2026-11-26 |
| 24 | Application id `dev.jandoant.fuel` | Confirmed by Jan 2026-11-26; must never change (a new id means reinstall and losing local data) |
| 25 | App icon: flame whose inner core is a leaf, gradient from accent green (base) to fat orange (tip) on warm off-white (`assets/icon/`) | Represents "Fuel" = food as energy; matches style D |
| 26 | Targets maths defaults (details and rejected alternatives in `docs/maths-assumptions.md`): trend α 0.10/day on the first weigh-in of the day; reference weight = average trend over the 7/14/28-day window; maintenance updated weekly by a Kalman step (~30 % of the gap per week in steady state) | Confirmed by Jan 2026-11-26; weight swings are physiological, and slower adaptation keeps targets calm |
| 27 | "N left" never goes below 0; between target and the zone's top the status says "X above target" | Confirmed by Jan 2026-11-26; a negative "left" was confusing |
| 28 | Actual rate (%/week) comes from the same 28-day weigh-in regression as "Balance from weight" | Confirmed by Jan 2026-11-26; Energy and Body show consistent numbers, less jumpy than a 7-day difference |
| 29 | Manual dependency injection (`AppContainer`), no Hilt | PRD §3 allows it; one small graph, fewer build plugins, tests build the same graph with an in-memory DB |
| 30 | Modules: `:core-model` (pure Kotlin maths + domain models), `:core-data` (Room, DataStore, Health Connect, Open Food Facts, backup), `:app` (Compose UI, ViewModels, WorkManager) | Maths stays Android-free and JVM-tested; data layer tested on device |
| 31 | Logged intake for the model = days **before today** with at least one ticked entry; today is excluded until it is over | A half-eaten day would bias maintenance down |
| 32 | NutritionRecords use nominal meal times (Breakfast 08:00, Lunch 12:30, Snacks 15:30, Extra 17:00, Dinner 19:00), clientRecordId `fuel-entry-<id>` | Fuel does not record when exactly food was eaten |
| 33 | Open Food Facts User-Agent is `Fuel/1.0 (personal Android app, non-commercial)` without a contact email | No personal data leaves the phone; add an email in `OpenFoodFactsClient.USER_AGENT` if OFF ever asks |
| 34 | Weekly check-ins are derived from stored data on the fly (aligned to phase weeks, so "what was adapted" matches the maintenance update); only the weekly **forecast** is stored, frozen when first written | No duplicated state; forecasts must not be rewritten after the fact |
| 35 | Micronutrient limits: free sugar is approximated by total sugar (row labelled "Sugar"); the 10 %-of-energy limits use the day's calorie target, or 2,000 kcal before a target exists | OFF has no free-sugar field; avoids false "over limit" on day one |
| 36 | Check-in diagnosis is explanatory; the weekly Kalman update (#26) still uses intake − balance even in "execution" weeks | intake − balance is a valid maintenance observation whether or not the plan was followed. **Open question for Jan** — PRD §6.7 says "keep maintenance" for execution weeks |
| 37 | Shopping list (v1 stub): grams per food over the week's not-yet-eaten entries, shown in a dialog | PRD allows a stub; real generation is a later phase |
| 38 | New installs start "Phase 1" today with 2,500 kcal starting maintenance (editable in Settings) | Needed to run the model; the user sets the real value |
