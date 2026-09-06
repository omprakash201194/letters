# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## What This Project Is

**Letters** — a native Android app with two unrelated modules sharing one shell:

| Module | What it does |
|---|---|
| **Chat Scenes** | WhatsApp-style fake-conversation builder. Create characters, compose a group chat message by message, replay it with typing indicators, bubble animations and synthesized tones, and export it as an MP4. |
| **Unsent Letters** | Ruled-paper letter editor. Recipient, subject, body, mood, date, and an optional "time capsule" seal that hides the letter until a future date. |

Kotlin + Jetpack Compose, `minSdk` 26, local-first: **Room is the source of truth. There is no
backend, no account, and nothing leaves the device.**

It began as a Spring Boot + React webapp deployed to k3s. That was removed once the Android port
landed; `git log` has it if you ever need to look. Two things outlive it:

- The k3s objects (`letters-backend`, `letters-frontend`, their services and the
  `letters.homelab.local` ingress) still exist in the `homelab` namespace at 0 replicas. Harmless,
  but orphaned — nothing in this repo deploys or removes them any more.
- The visual and behavioural specs below *are* the web app's, preserved deliberately. The point of
  the port was that it looks and moves identically.

---

## Repo Layout

```
letters/
└── android/
    └── app/src/
        ├── main/java/com/ogautam/letters/
        │   ├── data/          Room entities, DAOs, repositories, DataStore prefs, avatar files
        │   ├── audio/         ToneSynth (PCM) + TonePlayer
        │   ├── export/        MP4 encoder — SceneExporter, YuvConverter, SceneAudioTrack
        │   └── ui/
        │       ├── common/    Shared chrome, date field, formatting, the daily prompt
        │       ├── home/      The shell: prompt, search, module tiles, pen name
        │       ├── letters/   Unsent Letters — list and paper editor
        │       ├── scenes/    Scene list, player, and chat/ — the Canvas renderer
        │       └── theme/     Palette, typography, Lora
        ├── test/              JVM + Robolectric
        └── androidTest/       The export, which needs a real codec
```

---

## Build & Test

```bash
cd android
./gradlew assembleDebug              # APK
./gradlew testDebugUnitTest          # JVM + Robolectric
./gradlew connectedDebugAndroidTest  # the MP4 export — needs a device or emulator
```

### Running it on an emulator

Worth the setup: clicking through has caught defects the build and the tests could not — a mood
chip clipped off the screen edge, a renderer painting over the header, a bubble that stayed
invisible after a scrub.

```bash
sudo usermod -aG kvm $USER           # once, then log out and back in
avdmanager create avd -n letters-test -k "system-images;android-34;default;x86_64" -d pixel_5
emulator -avd letters-test -no-window -gpu swiftshader_indirect -memory 2048
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb exec-out screencap -p > shot.png
```

Without the `kvm` group it falls back to software emulation and crawls. When driving the UI from
`adb`, disable the IME first (`adb shell ime disable com.android.inputmethod.latin/.LatinIME`) —
otherwise the keyboard covers the controls and blind taps land on its keys.

---

## Chat Scenes — behavior that must not drift

The editor is one route with three steps: **setup → composer → preview**. A scene is not saved
between them, which is why they are one screen.

### Key invariant
The first character added is **"You"** — outgoing, green bubbles, right-aligned, double ticks, no avatar. All others are incoming. Enforced by index: `characters[0]` is always the outgoing participant. Changing this breaks the entire scene model.

### Sender label and avatar suppression
One rule drives both. For an incoming message, show the sender name label **and** the avatar only when it is the first message or the previous message came from a different character. Consecutive messages from the same person show neither. Outgoing messages never show either. The avatar slot stays reserved at 28dp so bubbles in a run stay aligned.

### Other rules
- Deleting a character also deletes all of their messages.
- Messages can be deleted but not edited or reordered.
- Progress bar scrubbing jumps to a message boundary, stops playback, and shows that message settled — not at the start of its pop-in, which with playback paused would never finish.

### The denormalised sender fields
A message carries a snapshot of its sender's name, colour and avatar so playback stays correct
after a character is edited or deleted. The cost is that renaming a character has to rewrite the
name on their messages, and removing one has to recolour everyone after them *and* their messages,
since colour is assigned by list position. That is the right price. Do not "fix" it by joining
back to `scene_characters`.

---

## WhatsApp Visual Spec (must be preserved exactly)

Lives in `ChatTheme.kt` as dp/sp constants. The renderer is handed a density rather than
reading one, so the same numbers produce the same picture on screen and in an exported frame.

| Element | Value |
|---|---|
| Outgoing bubble color | `#DCF8C6` |
| Incoming bubble color | `#FFFFFF` |
| Chat background | `#ECE5DD` + 80dp cross/diamond tile, drawn in `ChatRenderer` |
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


---

## Sound Design

Two synthesized tones, generated as PCM by `ToneSynth` — no audio files:

- **Send** (outgoing): 880 Hz → 1100 Hz, 120 ms, single tone
- **Receive** (incoming): 1100 Hz → 880 Hz (90 ms), then 880 Hz → 750 Hz (90 ms), offset by 95 ms

Sine wave, gain 0.18 with an exponential ramp to 0.001. The phase is integrated rather than
computed from an instantaneous frequency; the naive form chirps audibly on a ramp.

They are synthesized rather than bundled because the MP4 encoder needs the samples themselves,
to write onto an audio track at exact offsets. `TonePlayer` only plays them.

---


---

## Playback Logic

`PlaybackTimeline` expresses playback as a function of time. Speed is one of
`0.5 | 1 | 1.5 | 2` and divides every duration below.

| Step | Duration |
|---|---|
| Initial kick | 300 ms (not divided by speed) |
| Outgoing message | instant — bubble + send tone |
| …then pause | `600 / speed` |
| Incoming typing indicator | `(1000 + text.length * 30) / speed` |
| Incoming message | instant — bubble pop-in + receive tone |
| …then pause | `400 / speed` |

Playback ends when the clock reaches the total; pressing play again **restarts from 0**.

The web app chained `setTimeout`s, which can only run forwards, in real time, once. The same
durations as absolute timestamps are what let the progress bar scrub exactly and let the MP4
encoder sample the same playback at a fixed frame rate.

---


---

## Unsent Letters — paper spec

| Element | Value |
|---|---|
| Paper | `#fffef9`, border `#e2d5c0`, radius 4px |
| Header | `#5c4a3a`, height 56px |
| Screen ground | `#f5f0e8` |
| Margin rule | 1px vertical at x=72, `rgba(220,80,60,.25)` |
| Ruled lines | every 28px, `#e8ddc8` |
| Body text | Lora 15.5sp, line height **exactly 28dp** |
| "Dear" line | Lora 17sp, `#3d2b1f` |
| Subject | Lora italic 14sp, `#6b5040` |
| Sign-off | Lora italic 14sp, `#9a8060` |

The body line height must equal the ruled-line pitch or the text drifts off the lines — one
`LINE_PITCH` constant drives both. The text box also centres each line in its box
(`LineHeightStyle.Alignment.Center`, no font padding); Compose's default leading distribution
sits the text high off the rules.

Lora stands in for the web app's Georgia, which Android does not ship.

Moods (single-select, tap again to clear): 🙏 grateful · 🌱 hopeful · ❤️ love · 🌙 nostalgic · ⭐ proud · 💧 sad · 🔥 angry · 🕊️ lonely

**Sealing is a UI convention, not encryption.** The row always holds the full body; only the UI
withholds it. Do not describe it to users as protection.

---


---

## Home shell

- **Daily prompt** — fixed list of 20, indexed by `floor(epochMillis / 86_400_000) % 20`. Same for everyone, rotates at UTC midnight, repeats every 20 days.
- **Search** — filters the already-observed lists. Matches scene names, letter recipients and subjects. Does **not** search letter bodies.
- **Pen name** — stored in DataStore, defaults to "You". Used for the letter sign-off.

---

## The Android app — what to know before extending it

- **No backend, no Firebase, no login.** Room is the source of truth; there is no `user_id`.
- **Avatars are file paths** under `filesDir/avatars`, downscaled on import. The web app inlined a
  base64 data URL onto the character *and* onto every message they sent.
- **Message `time` is a `LocalTime`**, not a pre-rendered string. The web persisted the output of
  `toLocaleTimeString()`, so a scene composed on a 24-hour device rendered `14:32` forever.
- **One Canvas renderer serves the preview, the composer and the MP4 encoder**, so what plays in
  the app is what lands in the file. This is why the chat surface is Canvas rather than Compose
  layout, and it is the constraint most of the design below follows from.

Phase 2 shipped the home shell (daily prompt, search across both modules, pen name), the letters
list and the full paper editor — moods, ruled body, time capsule, dirty-state guard. Three things
about it worth knowing before extending:

- **PNG export is deliberately not in it.** Capturing the paper needs a renderer that draws the
  whole letter independent of the scroll viewport — the same shape of problem as the Canvas chat
  surface in phase 3, and it should be solved once, there, rather than twice.
- **The body's line height and the ruled-line pitch are the same 28dp constant**, and the text box
  centres each line in its box (`LineHeightStyle.Alignment.Center`, no font padding) because
  Compose's default leading distribution sits the text high off the rules. Change one, change both.
- **The app declares `enableEdgeToEdge()`** and headers draw their background under the status bar
  while insetting their content. Don't add a screen without `navigationBarsPadding()` on its
  content — `targetSdk` 36 means the system will not letterbox this for us.

Verified on an emulator (API 34, x86_64, headless + `adb` screenshots), which found three real
defects that the build and the tests could not: the eighth mood was clipped off the right edge,
the blank-recipient message offered a pointless "Cancel", and nothing handled window insets. To
repeat it: `emulator -avd letters-test -no-window -gpu swiftshader_indirect`, then
`adb install -r` and `adb exec-out screencap -p > shot.png`. The host user must be in the `kvm`
group or it falls back to software emulation.

Phase 3 built the chat renderer and its playback.

- **`ChatRenderer` draws onto a plain `android.graphics.Canvas`**, given a `PlaybackState` and an
  elapsed time. Nothing about it is Compose-aware, because phase 5 draws it into a Bitmap with no
  composition running. `ChatLayout` measures separately from drawing, so the rules worth testing —
  sender-label suppression, the reserved avatar column, the 280dp cap — are testable without one.
- **Playback is a timeline, not a chain of timeouts.** The web app's `setTimeout` chain can only
  run forwards, in real time, once. `PlaybackTimeline.stateAt(t)` gives the same durations as a
  function of time, which is what lets the progress bar scrub and what phase 5 will sample at a
  fixed frame rate. The durations themselves are unchanged and pinned by tests.
- **`ToneSynth` produces PCM**, and `TonePlayer` merely plays it. Same reason: the muxer needs the
  samples. The view model depends on the `SceneTones` interface, not on `AudioTrack`.
- **A Compose draw scope is not clipped to its node.** The renderer fills its surface, so the
  Canvas needs `clipToBounds()` and an explicit height — without both it paints over the header.
  This cost an hour; do not remove either.
- **A shadow layer ignores the paint's alpha.** Anything drawn part-faded has to scale the shadow
  colour itself, or a bubble mid-pop casts a full shadow under nothing.

Phase 4 built the scene list and the editor — one route, three steps (setup → composer →
preview), as the web app had it, because a scene is not saved between them.

- **The composer draws with the same `ChatRenderer` the player does**, through a shared
  `ChatCanvas`. It differs only in what drives it: the message list rather than a playback clock.
  Tapping a bubble hit-tests the measured layout to find which message to delete.
- **Character colour is assigned by list position**, so removing someone recolours everyone after
  them — *and* the messages they already sent, since colour and name are snapshotted onto each
  message. Renaming propagates the same way. This is the price of the denormalisation, and it is
  the right price; do not "fix" it by joining back to `scene_characters`.
- **Avatars are files under `filesDir/avatars`**, downscaled on import, referenced by path. The web
  inlined a base64 data URL onto the character and onto every message they sent.
- The keyboard's action key sends, as Enter did on the web. Without an explicit `ImeAction.Send`
  the field just takes a newline into the middle of a message.

Phase 5 exports the scene to an MP4, from the ⬇ MP4 button on the preview screen. 720×1280, 30fps,
H.264 + AAC. The user picks the name and location through the document picker, and the muxer is
handed that document's descriptor — a provider Uri need not be a path this process can open.

- **Audio sync was the risk, and the timeline is what removed it.** Picture and sound are both
  derived from the same `PlaybackTimeline`: frames are sampled at fixed intervals, and the tones
  are mixed into one PCM buffer at sample-accurate offsets by `SceneAudioTrack`. Neither is
  recorded alongside the other, so there is nothing to drift.
- **The encoder is fed ByteBuffers, not a Surface.** A Surface input would mean rendering through
  OpenGL, which would be a second renderer — the thing this whole design exists to avoid. The cost
  is an ARGB→YUV420 conversion per frame (`YuvConverter`), paid in a background export.
- **The muxer needs every track added before it starts**, so the audio is encoded first and its
  packets held in memory (seconds of AAC), then written once the video's format arrives.
- **The encoder's input buffer is not tightly packed, and the emulator will not tell you that.**
  Rows are padded to the codec's `KEY_STRIDE` and chroma begins after `KEY_SLICE_HEIGHT` rows —
  both read from `codec.inputFormat` after `start()`. Assuming `stride == width` shears every row
  a little further than the last and puts chroma in the wrong plane: a smeared, green picture. The
  emulator's `c2.android.avc.encoder` reports `stride == width`, so it cannot catch this; a
  hardware encoder generally pads. Every export logs its codec, colour format and stride under the
  `SceneExporter` tag — start there if a file ever looks wrong.
- **Ask the codec that will actually encode** which colour formats it takes. `createEncoderByType`
  need not return the first AVC encoder in `MediaCodecList`, so a format taken from that list can
  belong to a different codec.
- **`COLOR_FormatYUV420Flexible` has to be accepted.** Plenty of hardware encoders offer nothing
  else, and refusing it means refusing to export at all on those devices — the failure looks like a
  0-byte file, because the throw happens before the muxer starts. Flexible says only that the
  buffer is 4:2:0, never which layout, so a frame is written through the codec's own
  `getInputImage()`: its planes carry the `rowStride` and the `pixelStride` that say where the
  bytes actually go. Prefer that path whenever the codec offers an Image at all; the declared
  format is the fallback, not the source of truth.
- Export is verified by an **instrumented test** (`app/src/androidTest`) that exports a scene on a
  device and decodes the frames back, asserting the background before the first message and the
  outgoing bubble's green after the last. Run it with `./gradlew connectedDebugAndroidTest`; the
  JVM tests cannot cover this because it needs a codec. Note what it still cannot cover: the
  stride handling above, unless the device it runs on happens to pad.


---

## Conventions

`// reason:` comments for non-obvious decisions only — not narration. Constructor injection; the
service locator on `LettersApplication` is deliberate and stays until the graph outgrows it.
Anything with a rule in it (measurement, timing, colour conversion) is kept separate from anything
that draws, so it can be tested without a device.
