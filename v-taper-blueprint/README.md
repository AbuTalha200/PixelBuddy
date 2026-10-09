# V-TAPER BLUEPRINT — single-file product

`index.html` is the entire product: one self-contained file, inline CSS + inline JS,
no build step, no bundler, no npm install.

## Run it

```bash
# simplest — just open it
open index.html            # macOS
xdg-open index.html        # Linux

# or serve it (recommended: some browsers restrict file:// storage)
python3 -m http.server 8080
# → http://localhost:8080/
```

Everything works offline after the first load. Nothing is uploaded anywhere; all
user data lives in `localStorage` (settings, logs, check-ins) and IndexedDB
(progress photos).

## The only two things it loads from a CDN

| Dependency | Why | If it can't be reached |
|---|---|---|
| Tailwind CSS v4 browser build | utility classes | the file ships its own complete stylesheet, so the layout is unaffected |
| three.js r160 (unpkg, then jsDelivr) | the WebGL anatomy model | the interactive 2D SVG figure is mounted immediately and stays |

Nothing else is fetched — no fonts, no analytics, no `fetch()` after load.
The 2D figure is put on screen first and the 3D model swaps in when three.js
arrives, so a slow or offline connection never shows a blank anatomy tab.
If `navigator.onLine === false`, the CDN isn't even tried.

## Contents

- **Guide** — the 6 muscle groups, 17 ranked movements with tiers and V-values,
  skip lists, common mistakes, fun facts, posture / grooming / skin guides, MYTH/TRUTH FAQ
- **Anatomy** — tappable 3D model (17 named muscle meshes, 15,900 triangles,
  22 draw calls) with isolate, x-ray, camera presets and screenshot
- **Train** — 5 splits + a matcher quiz, a 12-week engine (3 phases + deload),
  double progression, volume guard rails, weekly planner, Today screen
- **Fuel** — 6 rule cards, scaling sample days, 14 recipes, grocery lists,
  diet break / refeed, eating out, Indian / Western / Mediterranean
- **Console** — calculators (calories & macros, muscular potential & timeline,
  golden-ratio 1.618, Navy body fat, Epley 1RM), workout logger, progress
  tracker, rest timer, daily checklist, streaks & badges, licence

57+ exercises (60), full English ⇄ Hindi dictionary toggle, metric/imperial,
3-state theme (auto / light / dark), ⌘/Ctrl-K search, WCAG 2.2 AA, print
stylesheet.

## Notes

- **Every number the app shows is computed from the §9 formula chain** in
  `calcEnergy / calcPotential / calcRatio / calcBF / e1RM`. Nothing is hard-coded.
  Worked example (70 kg, 175 cm, 22 y, male, ×1.375, bulk, target 78 kg):
  BMR 1,688.75 → TDEE 2,322.03 → target 2,622.03 kcal; protein 126 g,
  fat 72.8 g, carbs 365.6 g; ceiling 77 kg; ratio 1.4375.
  The spec's illustrative ≈2,586 kcal / ≈358 g differ only because its example
  used a slightly different TDEE base (2,286); no constant was fudged to match.
- **No payment code ships in the file.** The licence box validates
  `VT-XXXX-XXXX` keys locally (demo key: `VT-DEMO-2026`). §M6 documents the
  Gumroad / Lemon Squeezy / Razorpay / Stripe webhook flow that would issue them.
- **Photo mode is optional.** Set `ANATOMY_IMAGE` (top of the script) to a
  base64 WebP data URI of a 1024² front/back reference and the anatomy tab
  gains a photo mode with hotspots at x≈285 (front) and x≈740 (back).
  It is `null` by default, so the 3D and 2D modes are what you get out of the box.
- `window.VT` is a QA handle exposing the data and render functions for
  debugging and automated tests. It is the only global besides `window.Render3D`.
