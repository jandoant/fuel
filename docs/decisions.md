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
