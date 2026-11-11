# CodeMotion

**Code ko motion graphics video me badalne wali Android app** — khaas tor par Code Urdu
jaisi faceless, search-led videos ke liye, InsiderForce wale motion-graphics standard par
(har 2–3 second harkat, koi scene static nahi).

## Kaam ka flow (3 steps)

1. **Script banao** — App ke andar apna code ya script likho, Effects Gallery se live
   preview dekh kar target effects select karo, aur app ek `script.md` file bana degi
   jis me repo ka naam (`brpportal/codemotion`) aur target effect IDs likhe hote hain.
2. **Claude Code ko do** — `script.md` Claude Code ko do (isi repo par kaam karte hue).
   Wo `catalog/effects.json` aur `docs/FORMAT_SPEC.md` ke rules par ek `project.json`
   file return karega — scenes aur layers ki mukammal tafseel ke sath.
3. **App me import karo** — `project.json` app me import karo: har scene ki alag layers
   khul jayengi, jinhe edit karke video banaya jayega.

## Is repo me kya hai

| Cheez | Jagah |
|---|---|
| Android app (Kotlin + Jetpack Compose) | `app/` |
| Effects catalog (17 effects, JSON) | `catalog/effects.json` |
| project.json format + script template | `docs/FORMAT_SPEC.md` |
| Demo project (app ke andar bhi hai) | `app/src/main/assets/sample_project.json` |

## Phase 1 scope (ye version)

- Home screen: New Script / Effects Gallery / Import Project / Load Demo Project
- **Effects Gallery**: har effect ki card par **live looping preview** (usi engine ki
  jhalak jo aage video render karega), naam + Roman Urdu description, select checkbox
- **New Script**: title, Code/Script toggle, content, 9:16 / 16:9 format, selected
  effects ke sath `script.md` generate — Share aur Save ke sath
- **Project Viewer**: project.json import karke scenes/layers ki list aur har scene ka
  static (first-frame) preview

Phase 2: animated scene preview + layer editing (text, timing, color, position).
Phase 3: MP4 export (MediaCodec), project save/load.

## Build

GitHub Actions har push par debug APK banata hai (`.github/workflows/build.yml`):
JDK 17 → `./gradlew assembleDebug` → artifact **`codemotion-debug-apk`**.

Local: Android Studio me project kholo aur Run dabao. Repo me sirf
`gradle/wrapper/gradle-wrapper.properties` hai (NOVA-Workspace wala pattern) — wrapper
jar CI me generate hota hai; local pehli dafa `./gradlew` chalane se pehle Android
Studio khud wrapper bana deta hai.
