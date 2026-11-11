# CodeMotion

**Code ko motion graphics video me badalne wali Android app** — khaas tor par Code Urdu
jaisi faceless, search-led videos ke liye, InsiderForce wale motion-graphics standard par
(har 2–3 second harkat, koi scene static nahi).

**Version 0.2.0 — FULL app (ek hi app, saare phases mukammal).**

## Kaam ka flow (3 steps)

1. **Script banao** — App ke andar apna code ya script likho, Effects Gallery se live
   preview dekh kar target effects select karo, aur app ek `script.md` file bana degi
   jis me repo ka naam (`brpportal/codemotion`) aur target effect IDs likhe hote hain.
2. **Claude Code ko do** — `script.md` Claude Code ko do (isi repo par kaam karte hue).
   Wo `catalog/effects.json` aur `docs/FORMAT_SPEC.md` ke rules par ek `project.json`
   file return karega — scenes aur layers ki mukammal tafseel ke sath.
3. **App me import, edit aur export karo** — `project.json` import karo: animated
   preview chalegi, har layer edit kar sakte ho, project save kar sakte ho, aur
   akhir me **MP4 video export** ho jayegi.

## Features (sab kuch is ek app me)

- **Home**: New Script / Effects Gallery / Import Project / Load Demo Project / My Projects
- **Effects Gallery**: har effect ki card par live looping preview, naam + Roman Urdu
  description, select checkbox (script ke liye)
- **New Script**: title, Code/Script toggle, content, 9:16 / 16:9 format, selected
  effects ke sath `script.md` generate — Share aur Save ke sath
- **Animated Preview (Player)**: asal renderer se chalta hua preview — Play/Pause,
  seek slider, scene chips, Loop toggle, scenes auto-advance
- **Pro Editor (Alight Motion / InShot style)** — Project Studio se kisi layer par
  tap karo:
  - **Timeline**: seconds ruler (drag = playhead scrub), har layer ki ek track —
    bar ko drag karo to startMs move, bar ke **edges drag karo to trim**;
    − / + se timeline zoom (px/second)
  - **On-canvas editing**: preview par layer tap = select (outline dikhta hai),
    drag = x/y live move
  - **Property tabs**: Content (text/code, **Image Upload / Replace** — image app ke
    andar copy ho jati hai) • Transform (X/Y/W/H, scale %, **opacity, rotation**) •
    Style (font size, color/bg presets + hex, transparent bhi) • Animation
    (saare **26 catalog effects**, **GSAP easing dropdown**, **spring profile**,
    **stagger** controls, start/duration steppers + sliders) • Layer (Duplicate,
    Delete, Move up/down z-order, **Lock / Hide** toggles, scene duration)
  - Add layer playhead par banta hai (+Text / +Code / +Card / +Image / +Highlight),
    Add scene, Delete scene (Project Studio se)
- **Save / Load**:
  - **Save JSON** — project.json file jahan chaho save karo (schema bilkul FORMAT_SPEC wala)
  - **Save internal** — app ke andar naam de kar save; **My Projects** se dobara kholo ya delete karo
  - **Import** — bahari project.json kholo; **Demo** project built-in hai
- **MP4 Export**: 1080x1920 ya 720x1280, 30 fps, progress bar ke sath; file app ke
  Movies folder me banti hai, **Share** button se kahin bhi bhej sakte ho
  (export ki had: 60 second tak ki video)

## Animation engine (professional standard)

- Ek hi **shared Canvas renderer** preview aur MP4 export dono chalata hai —
  jo preview me dikhta hai, bilkul wahi export hota hai.
- **GSAP-style easings** (`Easings.kt`): linear, sine, power1–4, expo, circ, back
  (overshoot 1.70158), elastic, bounce — har family ki .in / .out / .inOut shaklein.
  **Har effect named easing par chalta hai** — gallery preview, animated player aur
  renderer teeno me wiring mukammal hai; layer ka apna `easing` field default
  curve ko override kar sakta hai.
- **Spring engine** (`Springs.kt`, Remotion model): asli damped oscillator —
  analytic, playhead se deterministic. Profiles: **smooth** (damping 200, koi
  bounce nahi), **snappy**, **bouncy**. `pop-in` spring par settle hota hai;
  `keyword-pulse` **ek** spring pulse hai (looping sine nahi).
- **Stagger engine** (`Stagger.kt`, GSAP model): each/amount + from
  start/center/end/edges — word-reveal (har lafz rise + fade), code-typewriter
  (per-line + caret), char-stagger-in (per-char, from center).
- Entrances: **power3.out / expo.out** decays; exits power2.in; camera
  **push-in / pan**: sine.inOut. **impact-shake**: deterministic decaying wiggle
  (CustomWiggle model) — random jitter khatam.
- **26 effects**: 17 original + 9 naye — `char-stagger-in`, `highlight-sweep`,
  `underline-draw`, `counter-up`, `box-marker`, `path-arrow`, `bounce-drop`,
  `elastic-snap`, `glow-pulse`. Markers hand-drawn style (rough-notation):
  circle/box do overlapping strokes ke sath, perfect geometry nahi.
- **Image layers**: photo picker se image app ke andar copy hoti hai; renderer
  use decode + cache karke center-crop draw karta hai — preview, player aur
  MP4 export teeno me.

## Is repo me kya hai

| Cheez | Jagah |
|---|---|
| Android app (Kotlin + Jetpack Compose) | `app/` |
| Effects catalog (17 effects, JSON) | `catalog/effects.json` |
| project.json format + script template | `docs/FORMAT_SPEC.md` |
| Demo project (app ke andar bhi hai) | `app/src/main/assets/sample_project.json` |
| Skills references (GSAP / Remotion / Three.js) | `references/` |

## Phase status

- ✅ **Phase 1** — app skeleton, script generator, effects gallery, import + static viewer
- ✅ **Phase 2** — shared renderer, animated preview, full layer editing, save/load
- ✅ **Phase 3** — MP4 export (MediaCodec), share

## Build

GitHub Actions har push par **minified release APK** banata hai
(`.github/workflows/build.yml`): JDK 17 → `./gradlew assembleRelease` → artifact
**`codemotion-release-apk`** (chhota size, ~1 MB — WhatsApp delivery ke liye).

Local: Android Studio me project kholo aur Run dabao. Repo me sirf
`gradle/wrapper/gradle-wrapper.properties` hai (NOVA-Workspace wala pattern) — wrapper
jar CI me generate hota hai.
