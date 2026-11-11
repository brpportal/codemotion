# CodeMotion Format Spec — v1

Repo: **brpportal/codemotion** — ye repo hi single source of truth hai: app ka source,
effects catalog (`catalog/effects.json`) aur ye format spec sab yahi rehte hain.

Flow: **app script.md banati hai → Claude Code usay parh kar `project.json` return karta
hai → app project.json import karke layered, editable video banati hai.**

---

## 1. project.json schema

```json
{
  "canvas": { "w": 1080, "h": 1920, "fps": 30 },
  "scenes": [
    {
      "id": "scene1",
      "durationMs": 3000,
      "layers": [
        {
          "id": "title",
          "type": "text",
          "effect": "pop-in",
          "startMs": 0,
          "durationMs": 600,
          "x": 60, "y": 620, "w": 960, "h": 260,
          "text": "Code Urdu",
          "code": "",
          "color": "#FFFFFF",
          "bg": "#00000000",
          "fontSize": 120
        }
      ]
    }
  ]
}
```

### Fields

| Field | Matlab |
|---|---|
| `canvas.w / canvas.h` | Video size px me — Shorts: 1080x1920, Long: 1920x1080 |
| `canvas.fps` | Frames per second (aam tor par 30) |
| `scenes[].id` | Scene ka unique naam |
| `scenes[].durationMs` | Scene ki kul muddat (ms) |
| `layers[].id` | Layer ka unique naam (scene ke andar) |
| `layers[].type` | `text` / `code` / `card` / `image` / `highlight` |
| `layers[].effect` | **Sirf catalog ka effect ID** (neeche list) |
| `layers[].startMs` | Scene ke start se layer kab shuru ho (ms) |
| `layers[].durationMs` | Layer ki animation + display muddat (ms) |
| `layers[].x / y / w / h` | Canvas par position aur size (px, top-left origin) |
| `layers[].text` | text/card/highlight layer ka text |
| `layers[].code` | code layer ka code (`\n` se lines) |
| `layers[].color` | Text ka rang `#RRGGBB` |
| `layers[].bg` | Background rang `#AARRGGBB` (`#00000000` = transparent) |
| `layers[].fontSize` | Font size (canvas px me) |

Layers array ki **order = z-order** hai: pehle wala layer sab se neeche, akhri wala sab se upar.

### Optional fields (v2 — na ho to default lagta hai, purane projects chalte rehte hain)

| Field | Default | Matlab |
|---|---|---|
| `layers[].easing` | effect ki apni curve | GSAP easing naam jo effect ki default curve ko override kare: `linear`, `sine.in/out/inOut`, `power1..4.in/out/inOut`, `expo.*`, `circ.*`, `back.*`, `elastic.*`, `bounce.*` (masalan `"back.out"`) |
| `layers[].spring` | effect ke mutabiq | Spring profile (`pop-in`, `keyword-pulse`, `glow-pulse` par): `"smooth"` (damping 200, koi bounce nahi), `"snappy"`, `"bouncy"` |
| `layers[].stagger` | effect ke mutabiq | Reveal effects (`word-reveal`, `code-typewriter`, `char-stagger-in`) me har unit ke darmiyan delay: ya to number (ms, masalan `110`) ya object `{"eachMs": 110, "from": "start"}` — `from` = `start` / `center` / `end` / `edges` |
| `layers[].opacity` | `1.0` | Layer ki opacity 0..1 (editor me 0–100% slider) |
| `layers[].rotation` | `0` | Static rotation, degrees (clockwise) |
| `layers[].imagePath` | — | `image` type ke liye: app ke internal images folder me copy hui image ka path. **Note:** exported project.json kisi doosri device par le jao to image file bhi sath chahiye hogi — path wahan nahi milega to layer placeholder dikhayega (v1 ki hadd). |
| `layers[].locked` | `false` | Editor me select/drag band (render hota rehta hai) |
| `layers[].hidden` | `false` | Layer render hi nahi hota (na preview, na export) |

**Motion ke usool (renderer):** har value playhead time se deterministic banti hai —
koi random, koi wall-clock loop nahi. Entrances `power3.out`/`expo.out`, pop family
asli spring par settle hoti hai, exits `power2.in`, camera `sine.inOut`. Sirf
transform + opacity animate hota hai.

## 2. Effects catalog (sirf yehi IDs allowed hain)

Source: `catalog/effects.json`

| Category | Effect IDs |
|---|---|
| text | `pop-in`, `slide-up`, `typewriter`, `word-reveal`, `keyword-pulse`, `char-stagger-in`, `highlight-sweep`, `underline-draw`, `counter-up` |
| code | `code-typewriter`, `line-highlight`, `zoom-to-line` |
| card | `slide-card`, `flip-in`, `draw-arrow`, `circle-marker`, `box-marker`, `path-arrow`, `bounce-drop`, `elastic-snap` |
| camera | `push-in`, `pan`, `impact-shake`, `whoosh`, `fade` |
| highlight | `glow-pulse` |

Naye effects ki technique: `char-stagger-in` = har harf stagger pop (from: center default) •
`highlight-sweep` = lafz ke peeche marker band sweep • `underline-draw` = hand-drawn underline •
`box-marker` = hand-drawn double-stroke box • `path-arrow` = curved arrow, head auto-rotate •
`bounce-drop` = bounce.out se gir kar rukna • `elastic-snap` = elastic.out scale-in •
`glow-pulse` = shadowBlur glow ka ek spring pulse • `counter-up` = text ke pehle number ko
0 se target tak count (power3.out, aakhir me snap).

Camera effects poore scene par lagte hain — un ke liye ek layer banao jis ka
`type` = `card` ya `text` ho aur `w/h` poora canvas ho, ya scene ke kisi main layer par
camera effect do.

## 3. Generation rules (Claude Code ke liye lazmi)

1. **Sirf catalog ke effect IDs** istemal karo — koi naya ID mat banao, warna app use
   render nahi kar payegi.
2. **Koi scene 3 second (3000ms) se zyada static nahi** — har 2–3s me koi na koi layer
   animate ho ya naya layer aye (InsiderForce rule: har 2–3 sec harkat).
3. Pehli line **hook** ho: shocking fact, bold claim, sawal ya curiosity gap.
   "Hello dosto" / "Aaj hum baat karenge" kabhi nahi.
4. Har scene ka pehla layer `startMs: 0` ke qareeb shuru ho; layers ki timing scene ke
   `durationMs` ke andar rakho.
5. Layer timing hamesha **milliseconds (ms)** me hai — seconds me nahi.
6. Text chhota aur bold ho: ek layer me 2–6 alfaz se zyada nahi (captions style alag hai,
   ye motion-graphics layers hain).
7. Colors high contrast rakho: dark bg par white/yellow text.
8. **Sirf project.json file return karo** — koi extra code ya explanation file nahi.

## 4. script.md template (app ye generate karti hai)

```markdown
# CodeMotion Script — <TITLE>

## Repo
brpportal/codemotion — is repo ki catalog/effects.json aur docs/FORMAT_SPEC.md hi
qanoon hai. Sirf wahi effect IDs istemal karo jo catalog me hain.

## Canvas
<1080x1920 (9:16 Shorts) ya 1920x1080 (16:9 Long)>, 30 fps

## Target Effects (sirf yehi istemal karo)
- <effect-id> (<Effect Name>)
- ...

## Content Type
<Code / Script>

## Content
<user ka code ya script text>

## Tumhara Kaam
Upar diye gaye content se ek motion-graphics video ki project.json file banao.
Rules:
- Sirf catalog ke effect IDs.
- Koi scene 3s se zyada static nahi; har layer par animation.
- Pehla scene hook ho.
- Timing ms me, layers scene duration ke andar.
- Sirf project.json return karo (schema: canvas{w,h,fps}, scenes[]{id,durationMs,
  layers[]{id,type,effect,startMs,durationMs,x,y,w,h,text,code,color,bg,fontSize}
  + optional: easing, spring, stagger, opacity, rotation, imagePath, locked, hidden}).
```
