# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## What This Project Is

A **WhatsApp-style chat scene builder** — create characters, compose a fake group chat, preview it with animations and sounds, and (eventually) export as MP4. The current deliverable is a **single-file HTML/CSS/JS web prototype** that validates UX and visual fidelity before a native Android implementation.

No build tools, no dependencies, no frameworks. Open `index.html` directly in a browser — no server needed.

---

## Files

| File | Purpose |
|---|---|
| `index.html` | Entire web prototype — HTML, CSS, and JS in one file |
| `CLAUDE.md` | This file |

---

## Architecture

Three screens managed in `index.html` via `showScreen(name)` toggling CSS `display`:

| Screen | Purpose |
|---|---|
| `setup` | Add characters (name + avatar photo) |
| `composer` | Build conversation message by message |
| `preview` | Replay scene with animations and sounds |

### State shape
```js
state = {
  characters:    [{ id, name, color, avatar }],
  messages:      [{ id, charId, charName, charColor, charAvatar, text, time, isOutgoing }],
  selectedCharId: string | null,
  playbackIndex:  number,
  isPlaying:      boolean,
  playSpeed:      0.5 | 1 | 1.5 | 2,
}
```

### Key invariant
First character added = **"You"** (outgoing, green bubbles, right-aligned). All others are incoming. This is enforced by index — `characters[0]` is always the outgoing participant. Changing this breaks the entire scene model.

### Sender name display
In the composer and preview, a sender name label is shown above an incoming bubble only when the previous message was from a different character (i.e. consecutive messages from the same person suppress the label).

### Delete in composer
Each bubble in the composer has a hover-revealed ✕ button that removes it from `state.messages` and re-renders. There is no delete in the preview.

### Progress bar scrubbing
Clicking the progress bar in the preview jumps to a position: stops playback, sets `playbackIndex`, and re-renders all messages up to that index from scratch.

---

## WhatsApp Visual Spec (must be preserved exactly)

| Element | Value |
|---|---|
| Outgoing bubble color | `#DCF8C6` |
| Incoming bubble color | `#FFFFFF` |
| Chat background | `#ECE5DD` + SVG cross/diamond tile |
| Header | `#075E54` |
| Timestamp text | `#667781`, 11px, right-aligned inside bubble |
| Double ticks (read) | `✓✓` in `#53BDEB` |
| Bubble font | 14.5px, line-height 1.4 |
| Outgoing border-radius | `8px 8px 0 8px` |
| Incoming border-radius | `8px 8px 8px 0` |
| Typing dots color | `#90A4AE`, 8px diameter |
| Typing dot animation | `translateY(-5px)` bounce, 0.2s stagger per dot |

---

## Sound Design

Two synthesized tones via Web Audio API oscillator — no external audio files:

- **Send** (outgoing): 880 Hz → 1100 Hz, 120 ms, single tone
- **Receive** (incoming): two tones — 1100 Hz → 880 Hz (90 ms), then 880 Hz → 750 Hz (90 ms), offset by 95 ms

Gain is set to 0.18 with an exponential ramp to silence. `AudioContext` is created once at page load and reused.

---

## Playback Logic

`playNextMessage()` drives the preview loop using `setTimeout` chains (no `setInterval`):

1. Outgoing messages appear instantly with send sound, then pause 600 ms / playSpeed before the next.
2. Incoming messages: show typing indicator → wait `(1000 + text.length * 30) / playSpeed` ms → remove indicator → pop in bubble with receive sound → pause 400 ms / playSpeed before the next.
3. Bubble pop-in: `bubblePop` keyframe — `scale(0.7) opacity:0` → `scale(1) opacity:1`, 250 ms, `cubic-bezier(0.34, 1.56, 0.64, 1)` spring.
4. Playback ends naturally when `playbackIndex >= messages.length`; hitting play again restarts from 0.

---

## Known Risks for Native Android Port

1. **Audio sync in MP4 export** — `SoundPool` can't be captured as PCM. Must pre-load raw PCM and write manually to `MediaMuxer` audio track at exact frame timestamps. Hardest part of the project.
2. **Typing indicator in export** — Real-time CSS animation doesn't translate to offscreen Canvas. Recommended v1 approach: substitute a static pause of equivalent duration instead of rendering animated frames.
3. **Avatar bitmap consistency** — The `Bitmap` used in `RecyclerView` preview must match the offscreen `Canvas` exactly (size, circular clip, anti-aliasing). Any mismatch causes preview/export divergence.
4. **MediaCodec pipeline** — Requires `MediaCodec` (H.264) + Surface-backed EGL input + `MediaMuxer` to mux video and PCM audio into MP4.

## Android Build Order (when native work begins)

1. `Character.java` + `ChatMessage.java` + `ChatScene.java` — data models
2. Room DB (`AppDatabase`, DAOs)
3. Character setup screen
4. `ChatBubbleView` — custom view matching WhatsApp bubble spec above
5. Composer screen — `RecyclerView` + character chip selector
6. Preview player — `Handler`/`Runnable` chain + SoundPool
7. Export engine — `MediaCodec` + offscreen Canvas + `MediaMuxer` audio mux

---

## What the Web Prototype Does Not Cover

- MP4 export
- Scene persistence (Room DB planned for Android)
- Camera capture (FileReader handles gallery only)
- Exact WhatsApp notification sounds (synthesized approximations)
- Scrollback during long-scene playback (auto-scroll only)
