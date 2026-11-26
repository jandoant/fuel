# Fuel app icon

A flame (energy) whose core is a leaf (food): food as fuel. Gradient from accent green `#22A06B` at the base to fat orange `#EA6A2A` at the tip, on the warm off-white background `#F6F3EE` (style D).

| File | Use |
|---|---|
| `ic_fuel_foreground.svg` | Adaptive icon foreground (108×108 dp canvas, artwork inside the 66 dp safe zone) |
| `ic_fuel_background.svg` | Adaptive icon background (solid `#F6F3EE`) |
| `ic_fuel_monochrome.svg` | Themed icon layer (Android 13+ Material You) |
| `ic_fuel_512.png` | 512 px full-bleed render (e.g. for About screen) |
| `preview.png` | Circle / squircle / themed / home-screen-size preview |

In Android Studio: **File → New → Image Asset**, *Launcher Icons (Adaptive and Legacy)*, choose the foreground SVG as the foreground layer, colour `#F6F3EE` as background, and the monochrome SVG for the monochrome layer. Or convert the SVGs to VectorDrawables (`res/drawable/ic_launcher_foreground.xml` etc.) and reference them from `mipmap-anydpi/ic_launcher.xml`.
