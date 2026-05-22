/**
 * Scene Editor — three sub-screens: setup → composer → preview.
 * Holds all scene state locally; saves to backend on explicit Save.
 * WhatsApp visual spec from CLAUDE.md is preserved exactly.
 */
import { useEffect, useRef, useState, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { scenesApi, CharacterDto, MessageDto } from '@/services/api'
import { genId, nowTime, colorForIndex, initials } from '@/lib/utils'
import { playSendSound, playReceiveSound } from '@/lib/audio'

// ── types ──────────────────────────────────────────────────────────────────
interface Character {
  id: string
  name: string
  color: string
  avatar: string | null
}

interface Message {
  id: string
  charId: string
  charName: string
  charColor: string
  charAvatar: string | null
  text: string
  time: string
  isOutgoing: boolean
}

type Screen = 'setup' | 'composer' | 'preview'
type PlaySpeed = 0.5 | 1 | 1.5 | 2

// ── helpers ────────────────────────────────────────────────────────────────
function Avatar({ char, size = 36 }: { char: { name: string; color: string; avatar: string | null }; size?: number }) {
  if (char.avatar) {
    return (
      <img
        src={char.avatar}
        alt={char.name}
        style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }}
      />
    )
  }
  return (
    <div
      style={{
        width: size, height: size, borderRadius: '50%', background: char.color,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: '#fff', fontWeight: 700, fontSize: size * 0.38, flexShrink: 0,
      }}
    >
      {initials(char.name)}
    </div>
  )
}

function TypingIndicator({ char }: { char: Character }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 6, padding: '0 12px 8px', maxWidth: 280 }}>
      <Avatar char={char} size={28} />
      <div style={{
        background: '#fff', borderRadius: '8px 8px 8px 0', padding: '10px 14px',
        boxShadow: '0 1px 2px rgba(0,0,0,.13)', display: 'flex', gap: 4, alignItems: 'center',
      }}>
        {[0, 1, 2].map(i => (
          <span key={i} className="typing-dot" style={{
            display: 'inline-block', width: 8, height: 8, borderRadius: '50%',
            background: '#90A4AE', animationDelay: `${i * 0.2}s`
          }} />
        ))}
      </div>
    </div>
  )
}

function Bubble({ msg, showSender, animate }: {
  msg: Message
  showSender: boolean
  animate?: boolean
}) {
  const out = msg.isOutgoing
  return (
    <div style={{
      display: 'flex', alignItems: 'flex-end', gap: 6,
      flexDirection: out ? 'row-reverse' : 'row',
      padding: '2px 12px',
    }}>
      {!out && (
        <div style={{ width: 28, flexShrink: 0 }}>
          {showSender && (
            <Avatar char={{ name: msg.charName, color: msg.charColor, avatar: msg.charAvatar }} size={28} />
          )}
        </div>
      )}
      <div className={animate ? 'bubble-pop' : undefined} style={{ maxWidth: 280 }}>
        {showSender && !out && (
          <div style={{ fontSize: 12, fontWeight: 600, color: msg.charColor, marginBottom: 2, paddingLeft: 2 }}>
            {msg.charName}
          </div>
        )}
        <div style={{
          background: out ? '#DCF8C6' : '#FFFFFF',
          borderRadius: out ? '8px 8px 0 8px' : '8px 8px 8px 0',
          padding: '6px 8px 6px 9px',
          boxShadow: '0 1px 2px rgba(0,0,0,.13)',
          fontSize: 14.5,
          lineHeight: 1.4,
          color: '#111',
          wordBreak: 'break-word',
        }}>
          <span style={{ whiteSpace: 'pre-wrap' }}>{msg.text}</span>
          <div style={{
            display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
            gap: 3, marginTop: 2
          }}>
            <span style={{ fontSize: 11, color: '#667781' }}>{msg.time}</span>
            {out && <span style={{ fontSize: 13, color: '#53BDEB' }}>✓✓</span>}
          </div>
        </div>
      </div>
    </div>
  )
}

// ── main component ─────────────────────────────────────────────────────────
export function SceneEditorPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isNew = id === 'new'

  const [screen, setScreen] = useState<Screen>('setup')
  const [sceneName, setSceneName] = useState('My Scene')
  const [characters, setCharacters] = useState<Character[]>([])
  const [messages, setMessages] = useState<Message[]>([])
  const [selectedCharId, setSelectedCharId] = useState<string | null>(null)
  const [messageInput, setMessageInput] = useState('')
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(!isNew)
  const [sceneId, setSceneId] = useState<string | null>(isNew ? null : (id ?? null))

  // Preview state
  const [playbackIndex, setPlaybackIndex] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)
  const [playSpeed, setPlaySpeed] = useState<PlaySpeed>(1)
  const [visibleMessages, setVisibleMessages] = useState<Message[]>([])
  const [typingChar, setTypingChar] = useState<Character | null>(null)
  const playbackRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const previewScrollRef = useRef<HTMLDivElement>(null)
  const composerScrollRef = useRef<HTMLDivElement>(null)

  // Load existing scene
  useEffect(() => {
    if (!isNew && id) {
      scenesApi.get(id)
        .then(detail => {
          setSceneName(detail.name)
          setSceneId(detail.id)
          setCharacters(detail.characters.map(c => ({
            id: c.id, name: c.name, color: c.color, avatar: c.avatar,
          })))
          setMessages(detail.messages.map(m => ({
            id: m.id, charId: m.charId, charName: m.charName,
            charColor: m.charColor, charAvatar: m.charAvatar,
            text: m.text, time: m.time, isOutgoing: m.outgoing,
          })))
          if (detail.characters.length > 0) setSelectedCharId(detail.characters[0].id)
        })
        .catch(() => navigate('/'))
        .finally(() => setLoading(false))
    }
  }, [id, isNew, navigate])

  // Auto-scroll composer
  useEffect(() => {
    if (screen === 'composer' && composerScrollRef.current) {
      composerScrollRef.current.scrollTop = composerScrollRef.current.scrollHeight
    }
  }, [messages, screen])

  // Auto-scroll preview
  useEffect(() => {
    if (screen === 'preview' && previewScrollRef.current) {
      previewScrollRef.current.scrollTop = previewScrollRef.current.scrollHeight
    }
  }, [visibleMessages, typingChar, screen])

  // Cleanup playback on unmount
  useEffect(() => () => { if (playbackRef.current) clearTimeout(playbackRef.current) }, [])

  // ── setup screen ────────────────────────────────────────────────────────

  function addCharacter() {
    const name = prompt('Character name:')?.trim()
    if (!name) return
    const newChar: Character = {
      id: genId(),
      name,
      color: colorForIndex(characters.length),
      avatar: null,
    }
    setCharacters(prev => {
      const next = [...prev, newChar]
      if (next.length === 1) setSelectedCharId(newChar.id)
      return next
    })
  }

  function removeCharacter(charId: string) {
    setCharacters(prev => prev.filter(c => c.id !== charId))
    setMessages(prev => prev.filter(m => m.charId !== charId))
    if (selectedCharId === charId) setSelectedCharId(null)
  }

  function pickAvatar(charId: string) {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.onchange = () => {
      const file = input.files?.[0]
      if (!file) return
      const reader = new FileReader()
      reader.onload = (e) => {
        const dataUrl = e.target?.result as string
        setCharacters(prev => prev.map(c => c.id === charId ? { ...c, avatar: dataUrl } : c))
      }
      reader.readAsDataURL(file)
    }
    input.click()
  }

  // ── composer screen ─────────────────────────────────────────────────────

  function sendMessage() {
    if (!messageInput.trim() || !selectedCharId) return
    const char = characters.find(c => c.id === selectedCharId)
    if (!char) return
    const isOutgoing = characters[0]?.id === selectedCharId
    const msg: Message = {
      id: genId(),
      charId: char.id,
      charName: char.name,
      charColor: char.color,
      charAvatar: char.avatar,
      text: messageInput.trim(),
      time: nowTime(),
      isOutgoing,
    }
    setMessages(prev => [...prev, msg])
    setMessageInput('')
  }

  function deleteMessage(msgId: string) {
    setMessages(prev => prev.filter(m => m.id !== msgId))
  }

  // ── preview playback ────────────────────────────────────────────────────

  function resetPreview() {
    if (playbackRef.current) clearTimeout(playbackRef.current)
    setIsPlaying(false)
    setPlaybackIndex(0)
    setVisibleMessages([])
    setTypingChar(null)
  }

  const playNextMessage = useCallback((index: number, msgs: Message[], speed: PlaySpeed) => {
    if (index >= msgs.length) {
      setIsPlaying(false)
      return
    }
    const msg = msgs[index]
    const char = characters.find(c => c.id === msg.charId) ?? null

    if (msg.isOutgoing) {
      setVisibleMessages(prev => [...prev, msg])
      playSendSound()
      setPlaybackIndex(index + 1)
      playbackRef.current = setTimeout(() => playNextMessage(index + 1, msgs, speed), 600 / speed)
    } else {
      // Show typing indicator
      setTypingChar(char)
      const typingDuration = (1000 + msg.text.length * 30) / speed
      playbackRef.current = setTimeout(() => {
        setTypingChar(null)
        setVisibleMessages(prev => [...prev, msg])
        playReceiveSound()
        setPlaybackIndex(index + 1)
        playbackRef.current = setTimeout(() => playNextMessage(index + 1, msgs, speed), 400 / speed)
      }, typingDuration)
    }
  }, [characters])

  function startPlayback() {
    if (messages.length === 0) return
    if (playbackIndex >= messages.length) {
      // restart
      setVisibleMessages([])
      setTypingChar(null)
      setPlaybackIndex(0)
      setIsPlaying(true)
      playbackRef.current = setTimeout(() => playNextMessage(0, messages, playSpeed), 300)
    } else {
      setIsPlaying(true)
      playbackRef.current = setTimeout(() => playNextMessage(playbackIndex, messages, playSpeed), 300)
    }
  }

  function stopPlayback() {
    if (playbackRef.current) clearTimeout(playbackRef.current)
    setIsPlaying(false)
  }

  function handleProgressClick(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    const targetIndex = Math.round(ratio * messages.length)
    stopPlayback()
    setPlaybackIndex(targetIndex)
    setVisibleMessages(messages.slice(0, targetIndex))
    setTypingChar(null)
  }

  // ── save ────────────────────────────────────────────────────────────────

  async function saveScene() {
    setSaving(true)
    try {
      const payload = {
        name: sceneName,
        characters: characters.map((c, i) => ({
          id: c.id, name: c.name, color: c.color, avatar: c.avatar, orderIndex: i,
        })),
        messages: messages.map((m, i) => ({
          id: m.id, charId: m.charId, charName: m.charName, charColor: m.charColor,
          charAvatar: m.charAvatar, text: m.text, time: m.time,
          outgoing: m.isOutgoing, orderIndex: i,
        })),
      }
      if (sceneId) {
        await scenesApi.update(sceneId, payload)
      } else {
        const created = await scenesApi.create(payload)
        setSceneId(created.id)
        navigate(`/scene/${created.id}`, { replace: true })
      }
      alert('Scene saved!')
    } catch {
      alert('Failed to save scene.')
    } finally {
      setSaving(false)
    }
  }

  // ── render ──────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-wa-bg">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-wa-green border-t-transparent" />
      </div>
    )
  }

  // Sender suppression: show name only when previous msg was from different char
  function showSenderLabel(msgs: Message[], idx: number) {
    if (msgs[idx].isOutgoing) return false
    if (idx === 0) return true
    return msgs[idx - 1].charId !== msgs[idx].charId
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#ECE5DD' }}>

      {/* ── SETUP SCREEN ─────────────────────────────────────────── */}
      {screen === 'setup' && (
        <>
          <div style={{
            background: '#075E54', color: '#fff', display: 'flex',
            alignItems: 'center', justifyContent: 'space-between',
            padding: '0 12px', height: 56, flexShrink: 0,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <button onClick={() => navigate('/')} style={{ background: 'none', border: 'none', color: '#fff', fontSize: 20, cursor: 'pointer', padding: 4 }}>‹</button>
              <input
                value={sceneName}
                onChange={e => setSceneName(e.target.value)}
                style={{
                  background: 'rgba(255,255,255,0.15)', border: 'none', borderRadius: 8,
                  color: '#fff', fontSize: 15, fontWeight: 600, padding: '4px 8px',
                  outline: 'none', width: 180,
                }}
                placeholder="Scene name"
              />
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={saveScene}
                disabled={saving || characters.length === 0}
                style={{
                  background: saving ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.2)',
                  border: 'none', borderRadius: 8, color: '#fff',
                  padding: '6px 12px', cursor: saving ? 'default' : 'pointer', fontSize: 13,
                }}
              >
                {saving ? '…' : '💾 Save'}
              </button>
              <button
                onClick={() => { setScreen('composer'); if (characters.length > 0 && !selectedCharId) setSelectedCharId(characters[0].id) }}
                disabled={characters.length === 0}
                style={{
                  background: characters.length === 0 ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.2)',
                  border: 'none', borderRadius: 8, color: '#fff',
                  padding: '6px 12px', cursor: characters.length === 0 ? 'default' : 'pointer', fontSize: 13,
                }}
              >
                Next →
              </button>
            </div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
            {characters.length === 0 && (
              <p style={{ color: '#888', textAlign: 'center', marginTop: 32, fontSize: 14 }}>
                Add at least one character. The first one is "You" (outgoing, green bubbles).
              </p>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 480, margin: '0 auto' }}>
              {characters.map((char, i) => (
                <div key={char.id} style={{
                  background: '#fff', borderRadius: 12, padding: '10px 12px',
                  display: 'flex', alignItems: 'center', gap: 10,
                  boxShadow: '0 1px 3px rgba(0,0,0,.08)',
                }}>
                  <button onClick={() => pickAvatar(char.id)} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                    <Avatar char={char} size={44} />
                  </button>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 15 }}>{char.name}</div>
                    <div style={{ fontSize: 12, color: '#888' }}>
                      {i === 0 ? '👤 You (outgoing)' : '👥 Incoming'}
                      {' · '}
                      <span style={{ color: char.color }}>●</span>
                      {' '}tap avatar to change photo
                    </div>
                  </div>
                  <button
                    onClick={() => removeCharacter(char.id)}
                    style={{ background: 'none', border: 'none', color: '#ccc', cursor: 'pointer', fontSize: 18, padding: 4 }}
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>

          <div style={{ padding: '12px 16px', borderTop: '1px solid #ddd', background: '#f9f9f9' }}>
            <button
              onClick={addCharacter}
              style={{
                width: '100%', maxWidth: 480, display: 'block', margin: '0 auto',
                background: '#075E54', color: '#fff', border: 'none', borderRadius: 12,
                padding: '12px', fontSize: 15, fontWeight: 600, cursor: 'pointer',
              }}
            >
              ＋ Add Character
            </button>
          </div>
        </>
      )}

      {/* ── COMPOSER SCREEN ──────────────────────────────────────── */}
      {screen === 'composer' && (
        <>
          <div style={{
            background: '#075E54', color: '#fff', display: 'flex',
            alignItems: 'center', justifyContent: 'space-between',
            padding: '0 12px', height: 56, flexShrink: 0,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <button onClick={() => setScreen('setup')} style={{ background: 'none', border: 'none', color: '#fff', fontSize: 20, cursor: 'pointer', padding: 4 }}>‹</button>
              <div>
                <div style={{ fontWeight: 600, fontSize: 15 }}>{sceneName}</div>
                <div style={{ fontSize: 11, opacity: 0.8 }}>{characters.length} characters</div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={saveScene}
                disabled={saving}
                style={{
                  background: 'rgba(255,255,255,0.2)', border: 'none', borderRadius: 8,
                  color: '#fff', padding: '6px 12px', cursor: 'pointer', fontSize: 13,
                }}
              >
                {saving ? '…' : '💾 Save'}
              </button>
              <button
                onClick={() => { setScreen('preview'); resetPreview() }}
                disabled={messages.length === 0}
                style={{
                  background: messages.length === 0 ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.2)',
                  border: 'none', borderRadius: 8, color: '#fff',
                  padding: '6px 12px', cursor: messages.length === 0 ? 'default' : 'pointer', fontSize: 13,
                }}
              >
                Preview ▶
              </button>
            </div>
          </div>

          {/* Chat bubble list */}
          <div ref={composerScrollRef} className="wa-bg" style={{ flex: 1, overflowY: 'auto', paddingTop: 8, paddingBottom: 8 }}>
            {messages.map((msg, i) => (
              <div key={msg.id} style={{ position: 'relative' }} className="group">
                <Bubble msg={msg} showSender={showSenderLabel(messages, i)} />
                <button
                  onClick={() => deleteMessage(msg.id)}
                  style={{
                    position: 'absolute', top: 4, right: msg.isOutgoing ? 12 : 'auto',
                    left: msg.isOutgoing ? 'auto' : 12,
                    background: 'rgba(0,0,0,0.5)', color: '#fff',
                    border: 'none', borderRadius: '50%', width: 20, height: 20,
                    cursor: 'pointer', fontSize: 10, display: 'none', alignItems: 'center', justifyContent: 'center',
                  }}
                  className="delete-btn"
                  title="Delete message"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>

          {/* Character chips + input */}
          <div style={{ background: '#f0f0f0', borderTop: '1px solid #ddd', flexShrink: 0 }}>
            <div style={{ display: 'flex', gap: 6, padding: '8px 12px 4px', overflowX: 'auto' }}>
              {characters.map(char => (
                <button
                  key={char.id}
                  onClick={() => setSelectedCharId(char.id)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 5,
                    background: selectedCharId === char.id ? char.color : '#e0e0e0',
                    color: selectedCharId === char.id ? '#fff' : '#444',
                    border: 'none', borderRadius: 20, padding: '5px 10px',
                    cursor: 'pointer', fontSize: 13, fontWeight: 500, flexShrink: 0,
                    transition: 'background 0.15s',
                  }}
                >
                  <Avatar char={char} size={18} />
                  {char.name}
                </button>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 8, padding: '4px 12px 10px' }}>
              <input
                value={messageInput}
                onChange={e => setMessageInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }}
                placeholder={selectedCharId ? 'Type a message…' : 'Select a character first'}
                disabled={!selectedCharId}
                style={{
                  flex: 1, borderRadius: 24, border: 'none', padding: '10px 16px',
                  fontSize: 14.5, outline: 'none', background: '#fff',
                  boxShadow: '0 1px 2px rgba(0,0,0,.1)',
                }}
              />
              <button
                onClick={sendMessage}
                disabled={!messageInput.trim() || !selectedCharId}
                style={{
                  width: 44, height: 44, borderRadius: '50%',
                  background: messageInput.trim() && selectedCharId ? '#075E54' : '#ccc',
                  border: 'none', color: '#fff', cursor: messageInput.trim() && selectedCharId ? 'pointer' : 'default',
                  fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center',
                  transition: 'background 0.15s',
                }}
              >
                ➤
              </button>
            </div>
          </div>
        </>
      )}

      {/* ── PREVIEW SCREEN ───────────────────────────────────────── */}
      {screen === 'preview' && (
        <>
          <div style={{
            background: '#075E54', color: '#fff', display: 'flex',
            alignItems: 'center', justifyContent: 'space-between',
            padding: '0 12px', height: 56, flexShrink: 0,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <button
                onClick={() => { stopPlayback(); setScreen('composer') }}
                style={{ background: 'none', border: 'none', color: '#fff', fontSize: 20, cursor: 'pointer', padding: 4 }}
              >
                ‹
              </button>
              <div>
                <div style={{ fontWeight: 600, fontSize: 15 }}>{sceneName}</div>
                <div style={{ fontSize: 11, opacity: 0.8 }}>{messages.length} messages</div>
              </div>
            </div>
            <button
              onClick={saveScene}
              disabled={saving}
              style={{
                background: 'rgba(255,255,255,0.2)', border: 'none', borderRadius: 8,
                color: '#fff', padding: '6px 12px', cursor: 'pointer', fontSize: 13,
              }}
            >
              {saving ? '…' : '💾 Save'}
            </button>
          </div>

          {/* Preview messages */}
          <div ref={previewScrollRef} className="wa-bg" style={{ flex: 1, overflowY: 'auto', paddingTop: 8, paddingBottom: 8 }}>
            {visibleMessages.map((msg, i) => (
              <Bubble key={msg.id} msg={msg} showSender={showSenderLabel(visibleMessages, i)} animate />
            ))}
            {typingChar && <TypingIndicator char={typingChar} />}
          </div>

          {/* Player controls */}
          <div style={{ background: '#fff', borderTop: '1px solid #ddd', padding: '10px 12px', flexShrink: 0 }}>
            {/* Progress bar */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
              <div
                onClick={handleProgressClick}
                style={{
                  flex: 1, height: 4, background: '#e0e0e0', borderRadius: 2, cursor: 'pointer', position: 'relative',
                }}
              >
                <div style={{
                  position: 'absolute', left: 0, top: 0, height: '100%',
                  width: `${messages.length > 0 ? (playbackIndex / messages.length) * 100 : 0}%`,
                  background: '#075E54', borderRadius: 2, transition: 'width 0.1s',
                }} />
              </div>
              <span style={{ fontSize: 12, color: '#888', flexShrink: 0 }}>
                {playbackIndex} / {messages.length}
              </span>
            </div>

            {/* Speed + play button */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', gap: 4 }}>
                {([0.5, 1, 1.5, 2] as PlaySpeed[]).map(s => (
                  <button
                    key={s}
                    onClick={() => setPlaySpeed(s)}
                    style={{
                      padding: '4px 8px', borderRadius: 8, border: 'none', fontSize: 12,
                      background: playSpeed === s ? '#075E54' : '#f0f0f0',
                      color: playSpeed === s ? '#fff' : '#555',
                      cursor: 'pointer',
                    }}
                  >
                    {s}×
                  </button>
                ))}
              </div>
              <button
                onClick={isPlaying ? stopPlayback : startPlayback}
                style={{
                  width: 44, height: 44, borderRadius: '50%', border: 'none',
                  background: '#075E54', color: '#fff', fontSize: 20, cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}
              >
                {isPlaying ? '⏸' : '▶'}
              </button>
            </div>
            <p style={{ fontSize: 11, color: '#aaa', textAlign: 'center', marginTop: 6 }}>
              📱 MP4 export available in the Android app
            </p>
          </div>
        </>
      )}

      {/* hover-reveal delete button for composer */}
      <style>{`
        .group:hover .delete-btn { display: flex !important; }
      `}</style>
    </div>
  )
}
