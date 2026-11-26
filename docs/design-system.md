# Design system (style D)

Reference implementation: `prototype/screens/Main.dc.html` (Today) and `prototype/design-directions/V2Mix.dc.html`.

## Typography
- Font: **Manrope** (Google Fonts, bundle it in the APK via `res/font`), weights 500/600/700/800.
- Screen title 17/800 · Section heading 15/800 · Body 13–14/600–700 · Meta 11–12/600–700 · Big numbers 16–28/800.
- Use tabular figures for numbers where possible.

## Colours
| Token | Hex | Use |
|---|---|---|
| background | `#F6F3EE` | screen background |
| surface | `#FFFFFF` | cards, eaten entries |
| ink | `#1E1B18` | primary text |
| muted | `#776E65` | secondary text |
| subtle | `#857C73` | tertiary labels |
| line | `#EFE9E1` / `#F2EDE6` | dividers, tracks |
| track-strong | `#E9E2D8` | bar track on background |
| dashed | `#DDD5CB` | planned entries, add rows |
| input-border | `#DED6CB` | inputs |
| accent | `#22A06B` | calories, checkboxes, primary actions |
| accent-soft | `#BFE6D2` | planned calories |
| accent-text | `#177A50` | links, "fits" text |
| accent-bg | `#E6F5EC` | success chips |
| protein | `#4F46E5` / soft `#C7C9F7` / tint `#EEF0FF` |
| fat | `#EA6A2A` / soft `#F8CDB4` / tint `#FFF1E8` |
| carbs | `#1F9D63` / soft `#B9E5CD` / tint `#E8F6EE` |
| fiber | `#A27B2C` / soft `#E9D7AE` / tint `#FBF3E2` |
| recipe | `#7048C7` / bg `#F1EBFC` |
| warning | `#C2410C` text, `#FFEDE3` bg | plan short/over |
| danger | `#B42318` text, `#FDE8E6` bg | eaten over limit |

## Shape and elevation
- Radii: cards 22, entries 18, chips 999, inputs 8, icon tiles 12, round buttons 22.
- Shadow: `0 1px 2px rgba(40,30,20,0.05)` (+ `0 12px 32px rgba(40,30,20,0.06)` for emphasis).
- Top of Today has **no cards** around Calories and Macros (content sits on the background).

## Data visualisation rules
- Order everywhere: **Protein · Fat · Carbs · Fiber** (stacked bars bottom→top: P, F, C).
- **Eaten** = solid colour, **planned (not yet eaten)** = soft colour continuing after eaten.
- **Goal zone**: on bars white diagonal hatching over the bar (no dark tint) plus two small accent dots above the zone edges; on rings a thin dark (`ink`) outer arc.
- Values outside a band are visible by position — do not add extra "off" colours to data points.
- Status chips: green "fits", orange "X short/over", red "eaten X over" with warning icon.

## Components
- Round icon button 44 (white, soft shadow).
- Checkbox: 26 px circle, accent fill + white check when eaten, `#D6CEC4` outline when planned.
- Entry card: eaten = white solid; planned = transparent with 1.5 px dashed outline.
- Timeline node: 32 px rounded tile with time-of-day icon (sunrise / sun / moon / snack), accent when the meal is complete; 2 px connector line.
- Recipe badge: "RECIPE" 10/800 purple on light purple; single foods have no badge (or a neutral round icon in lists).
- Amount stepper: 32 px high, 30 px −/+ buttons, 48 px typeable field; compact unit dropdown (34 px).
- Bottom sheet: 20 px top radius, grab handle, scrim `rgba(23,25,27,0.42)`.
- Navigation drawer: 300 px, pill-shaped active item.
