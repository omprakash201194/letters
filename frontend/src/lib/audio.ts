// Web Audio API synthesized tones — no external files needed.
// AudioContext is created lazily on first interaction (browser policy).

let ctx: AudioContext | null = null

function getCtx(): AudioContext {
  if (!ctx) ctx = new AudioContext()
  return ctx
}

function playTone(freqStart: number, freqEnd: number, duration: number, startAt = 0): void {
  const c = getCtx()
  const osc = c.createOscillator()
  const gain = c.createGain()

  osc.connect(gain)
  gain.connect(c.destination)

  osc.type = 'sine'
  osc.frequency.setValueAtTime(freqStart, c.currentTime + startAt)
  osc.frequency.linearRampToValueAtTime(freqEnd, c.currentTime + startAt + duration / 1000)

  gain.gain.setValueAtTime(0.18, c.currentTime + startAt)
  gain.gain.exponentialRampToValueAtTime(0.001, c.currentTime + startAt + duration / 1000)

  osc.start(c.currentTime + startAt)
  osc.stop(c.currentTime + startAt + duration / 1000 + 0.05)
}

export function playSendSound(): void {
  playTone(880, 1100, 120)
}

export function playReceiveSound(): void {
  playTone(1100, 880, 90)
  playTone(880, 750, 90, 0.095)
}
