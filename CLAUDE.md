# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## What This Project Is

**Letters** — a personal webapp with two unrelated modules sharing one shell:

| Module | What it does |
|---|---|
| **Chat Scenes** | WhatsApp-style fake-conversation builder. Create characters, compose a group chat message by message, replay it with typing indicators, bubble animations and synthesized tones. |
| **Unsent Letters** | Ruled-paper letter editor. Recipient, subject, body, mood, date, and an optional "time capsule" seal that hides the letter until a future date. Exports as PNG. |

Spring Boot 3.4.4 backend (Java 21) + React 18 / Vite / Tailwind frontend, deployed to k3s in the `homelab` namespace at `letters.homelab.local`.

## Current Status — read this first

- **Not running.** `letters-backend` and `letters-frontend` are both scaled to **0 replicas** in `homelab`. Services and ingress still exist. The manifests say `replicas: 1`, so this is manual drift, not config.
- **No data.** `letters`, `scenes`, `scene_characters` and `scene_messages` are all empty in the shared `homelab` Postgres.
- **A native Android port is the plan of record.** Decisions locked: **local-first Room, no backend**; **Kotlin + Compose with a Canvas-drawn chat surface**; **MP4 export in scope** as the final phase. The full porting spec lives at
  <https://claude.ai/code/artifact/db7d3c98-197b-438a-842d-0a6a787bc289>.

To bring the webapp back up: `kubectl scale deploy/letters-backend deploy/letters-frontend -n homelab --replicas=1`. Images `1.0.0` are already in the local registry.

---

## Repo Layout

```
letters/
├── backend/            Spring Boot 3.4.4, Java 21, JPA + Firebase Admin
│   ├── Dockerfile      Multi-stage — Maven build → eclipse-temurin:21-jre-alpine
│   ├── pom.xml
│   └── src/main/java/com/ogautam/letters/
│       ├── config/     FirebaseConfig, FirebaseProperties, WebConfig
│       ├── controller/ LetterController, SceneController
│       ├── dto/        Request/response records
│       ├── model/      Letter, Scene, SceneCharacter, SceneMessage
│       ├── repository/ LetterRepository, SceneRepository
│       ├── security/   FirebaseAuthFilter, FirebaseUserPrincipal, SecurityConfig
│       └── service/    LetterService, SceneService
├── frontend/           React 18 + Vite + Tailwind, served by nginx
│   ├── Dockerfile
│   ├── nginx.conf      SPA fallback + /api/ proxy to letters-backend:8080
│   └── src/
│       ├── pages/      Home, Login, Scenes, SceneEditor, Letters, LetterEditor
│       ├── services/   api.ts — axios client, attaches Firebase ID token
│       ├── lib/        audio.ts (Web Audio tones), firebase.ts, utils.ts
│       └── hooks/      useAuth.ts
├── k8s/                Deployments, Services, Ingress, SealedSecrets, SECRETS.md
├── build-and-push.sh   Builds and pushes both images to localhost:30500
├── DEPLOY.md           Step-by-step deployment checklist
├── index.html          LEGACY — the original single-file prototype. Superseded by
│                       frontend/. Kept only as a reference for the visual spec.
└── CLAUDE.md           This file
```

---

## Architecture

### Auth
Firebase Google sign-in on the frontend. Every request carries `Authorization: Bearer <Firebase ID token>` (attached by an axios interceptor). `FirebaseAuthFilter` verifies the token and sets a `FirebaseUserPrincipal` carrying the UID. Every row is scoped by `user_id`; the security chain is stateless and permits only `/actuator/**` anonymously.

### The `/api` prefix gotcha
Controllers are mapped at `/letters` and `/scenes` — **no `/api` prefix**. The frontend calls `/api/letters`, and `nginx.conf` proxies `location /api/` to `http://letters-backend:8080/` with a **trailing slash**, which strips `/api` before forwarding. Adding `/api` to a controller mapping will 404.

### Persistence
JPA with `ddl-auto: update` against the shared `homelab` Postgres. No Flyway. Four tables:

| Entity | Notes |
|---|---|
| `Letter` | Flat. `sealedUntil` null = not sealed. |
| `Scene` | Owns characters and messages, both `cascade = ALL, orphanRemoval = true`, ordered by `orderIndex`. |
| `SceneCharacter` | Client-generated string id. `avatar` is a base64 data URL in a TEXT column. |
| `SceneMessage` | Carries a **snapshot** of the sender's name, color and avatar. |

Two model decisions that are load-bearing, not accidents:
- **Sender fields are denormalised onto each message** so playback stays correct after a character is edited or deleted. Do not "fix" this by joining to `scene_characters`.
- **Scene updates clear and re-insert** both child collections rather than diffing. Scenes are small (< 100 messages) and this is deliberately simpler.

### API surface

```
GET    /letters          list summaries (contentPreview = first 120 chars)
POST   /letters          create
GET    /letters/{id}     detail
PUT    /letters/{id}     update
DELETE /letters/{id}

GET    /scenes           list summaries (character + message counts)
POST   /scenes           create
GET    /scenes/{id}      detail (characters + messages)
PUT    /scenes/{id}      full replace
PATCH  /scenes/{id}/name rename only
DELETE /scenes/{id}
```

All scoped to the caller's UID; a miss returns 404 rather than 403.

---

## Chat Scenes — behavior that must not drift

`SceneEditorPage.tsx` is one route with three modes: **setup → composer → preview**.

### Key invariant
The first character added is **"You"** — outgoing, green bubbles, right-aligned, double ticks, no avatar. All others are incoming. Enforced by index: `characters[0]` is always the outgoing participant. Changing this breaks the entire scene model.

### Sender label and avatar suppression
One rule drives both. For an incoming message, show the sender name label **and** the avatar only when it is the first message or the previous message came from a different character. Consecutive messages from the same person show neither. Outgoing messages never show either. The avatar slot stays reserved at 28px so bubbles in a run stay aligned.

### Other rules
- Deleting a character also deletes all of their messages.
- Messages can be deleted but not edited or reordered.
- Progress bar scrubbing jumps to `round(ratio × messageCount)`, stops playback, and rebuilds the visible list from scratch — no animation, no sound.

---

## WhatsApp Visual Spec (must be preserved exactly)

| Element | Value |
|---|---|
| Outgoing bubble color | `#DCF8C6` |
| Incoming bubble color | `#FFFFFF` |
| Chat background | `#ECE5DD` + 80px SVG cross/diamond tile (`.wa-bg` in `index.css`) |
| Header | `#075E54`, height 56px |
| Timestamp text | `#667781`, 11px, right-aligned inside bubble |
| Double ticks (read) | `✓✓` in `#53BDEB`, 13px, outgoing only |
| Bubble font | 14.5px, line-height 1.4, color `#111` |
| Outgoing border-radius | `8px 8px 0 8px` |
| Incoming border-radius | `8px 8px 8px 0` |
| Bubble shadow | `0 1px 2px rgba(0,0,0,.13)` |
| Max bubble width | 280px |
| Sender label | 12px, weight 600, character color |
| Avatar sizes | 28 chat · 36 default · 44 setup list · 18 chips |
| Typing dots | `#90A4AE`, 8px diameter |
| Typing dot animation | `translateY(-5px)` bounce, 1s loop, 0.2s stagger per dot |
| Bubble pop-in | `scale(0.7)` → `scale(1)`, 250ms, `cubic-bezier(0.34, 1.56, 0.64, 1)` |

Character colors are assigned by position, cycling `index % 8`:
`#E91E63 #9C27B0 #3F51B5 #2196F3 #009688 #FF5722 #795548 #607D8B`

---

## Sound Design

Two synthesized tones via Web Audio API oscillator (`lib/audio.ts`) — no audio files:

- **Send** (outgoing): 880 Hz → 1100 Hz, 120 ms, single tone
- **Receive** (incoming): 1100 Hz → 880 Hz (90 ms), then 880 Hz → 750 Hz (90 ms), offset by 95 ms

Sine wave, gain 0.18 with an exponential ramp to 0.001. `AudioContext` is created lazily on first use (browser autoplay policy).

---

## Playback Logic

`playNextMessage()` drives the preview with chained `setTimeout` calls (never `setInterval`). Speed is one of `0.5 | 1 | 1.5 | 2` and divides every duration below.

| Step | Duration |
|---|---|
| Initial kick | 300 ms (not divided by speed) |
| Outgoing message | instant — bubble + send tone |
| …then pause | `600 / speed` |
| Incoming typing indicator | `(1000 + text.length * 30) / speed` |
| Incoming message | instant — bubble pop-in + receive tone |
| …then pause | `400 / speed` |

Playback ends when the index reaches the message count; pressing play again **restarts from 0**. The timeout handle is cleared on unmount.

---

## Unsent Letters — paper spec

| Element | Value |
|---|---|
| Paper | `#fffef9`, border `#e2d5c0`, radius 4px |
| Header | `#5c4a3a`, height 56px |
| Screen ground | `#f5f0e8` |
| Margin rule | 1px vertical at x=72, `rgba(220,80,60,.25)` |
| Ruled lines | every 28px, `#e8ddc8` |
| Body text | Georgia 15.5px, line-height **exactly 28px** |
| "Dear" line | Georgia 17px, `#3d2b1f` |
| Subject | Georgia italic 14px, `#6b5040` |
| Sign-off | Georgia italic 14px, `#9a8060` |

The 28px body line-height must equal the ruled-line pitch or the text drifts off the lines.

Moods (single-select, tap again to clear): 🙏 grateful · 🌱 hopeful · ❤️ love · 🌙 nostalgic · ⭐ proud · 💧 sad · 🔥 angry · 🕊️ lonely

**Sealing is a UI convention, not encryption.** The backend returns a sealed letter's full content; only the frontend withholds it. Do not describe it to users as protection.

---

## Home shell

- **Daily prompt** — fixed list of 20, indexed by `floor(Date.now() / 86_400_000) % 20`. Same for everyone, rotates at UTC midnight, repeats every 20 days.
- **Search** — client-side over the already-loaded lists. Matches scene names, letter recipients and subjects. Does **not** search letter bodies.
- **Pen name** — stored in `localStorage` under `letters_pen_name`, defaults to the Firebase display name. Used for the letter sign-off.

---

## Build & Deploy

```bash
export VITE_FIREBASE_API_KEY=... VITE_FIREBASE_AUTH_DOMAIN=... VITE_FIREBASE_PROJECT_ID=...
./build-and-push.sh 1.0.1        # builds + pushes both images to localhost:30500
# bump image tags in k8s/, then:
kubectl apply -f k8s/
```

Never use `latest`. See `DEPLOY.md` for the full checklist and `k8s/SECRETS.md` for the SealedSecret workflow (DB credentials and the Firebase service account).

---

## Android Port

Decisions are locked; the full requirements spec is linked at the top of this file. Summary of what changes:

- **No backend, no Firebase, no login.** Room is the source of truth. `user_id` is dropped from the schema.
- **Avatars become file paths**, not base64 data URLs — the current model duplicates a full-size data URL onto every message from that character.
- **Message `time` becomes a `LocalTime`**, not a pre-rendered locale string. The web persists the output of `toLocaleTimeString()`, so a scene composed on a 24-hour device renders `14:32` forever.
- **One Canvas renderer serves both the live preview and the MP4 encoder**, so what plays in the app is what lands in the file. This is why the chat surface is Canvas rather than Compose layout.

Build order: **1** Room data layer ✅ → **2** shell + Letters ✅ → **3** Canvas chat renderer standalone → **4** scene editor over it → **5** MP4 export.

Phase 2 shipped the home shell (daily prompt, search across both modules, pen name), the letters
list and the full paper editor — moods, ruled body, time capsule, dirty-state guard. Three things
about it worth knowing before extending:

- **PNG export is deliberately not in it.** Capturing the paper needs a renderer that draws the
  whole letter independent of the scroll viewport — the same shape of problem as the Canvas chat
  surface in phase 3, and it should be solved once, there, rather than twice.
- **The body's line height and the ruled-line pitch are the same 28dp constant**, and the text box
  centres each line in its box (`LineHeightStyle.Alignment.Center`, no font padding) because
  Compose's default leading distribution sits the text high off the rules. Change one, change both.
- `ScenesScreen` is an honest placeholder; the scenes data layer underneath it is already real, so
  home's scene count and scene search results work today.
- **The app declares `enableEdgeToEdge()`** and headers draw their background under the status bar
  while insetting their content. Don't add a screen without `navigationBarsPadding()` on its
  content — `targetSdk` 36 means the system will not letterbox this for us.

Verified on an emulator (API 34, x86_64, headless + `adb` screenshots), which found three real
defects that the build and the tests could not: the eighth mood was clipped off the right edge,
the blank-recipient message offered a pointless "Cancel", and nothing handled window insets. To
repeat it: `emulator -avd letters-test -no-window -gpu swiftshader_indirect`, then
`adb install -r` and `adb exec-out screencap -p > shot.png`. The host user must be in the `kvm`
group or it falls back to software emulation.

MP4 export risk: audio sync is the hardest part — tones must be synthesized to PCM and written to the `MediaMuxer` audio track at exact frame timestamps, which is why the tones stay synthesized rather than becoming bundled WAVs.

---

## Conventions

Inherited from `HomeLab/CLAUDE.md` — constructor injection only, `@Slf4j` for logging, thin controllers, `@ConfigurationProperties` for config, `Optional<T>` handled properly, `// reason:` comments for non-obvious decisions only.
