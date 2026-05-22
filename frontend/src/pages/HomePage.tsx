import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { signOut } from 'firebase/auth'
import { auth } from '@/lib/firebase'
import { scenesApi, lettersApi, SceneSummary, LetterSummary } from '@/services/api'
import { useAuth } from '@/hooks/useAuth'

const PROMPTS = [
  "Write to someone you haven't spoken to in over a year.",
  "Write to your past self at age 16.",
  "Write to someone who changed you without knowing it.",
  "Write to a place you'll never visit again.",
  "Write what you wish you'd said at exactly the right moment.",
  "Write to someone who believed in you before you did.",
  "Write to the version of yourself you're afraid to become.",
  "Write to someone you've forgiven but never told.",
  "Write to yourself, ten years from now.",
  "Write to the person who taught you what love really means.",
  "Write about a memory no one else remembers but you.",
  "Write to someone you lost too soon.",
  "Write to the child you once were.",
  "Write about the apology you still owe.",
  "Write to the stranger whose kindness you never forgot.",
  "Write to someone whose face you can't quite remember anymore.",
  "Write to a future child or grandchild who doesn't exist yet.",
  "Write to the friend you drifted from without a reason.",
  "Write to yourself on the hardest day of last year.",
  "Write to someone who never got to see who you became.",
]

function todayPrompt(): string {
  const dayIndex = Math.floor(Date.now() / 86_400_000)
  return PROMPTS[dayIndex % PROMPTS.length]
}

function isSealed(sealedUntil: string | null): boolean {
  if (!sealedUntil) return false
  return new Date(sealedUntil) > new Date()
}

const MOOD_EMOJI: Record<string, string> = {
  grateful: '🙏', hopeful: '🌱', love: '❤️', nostalgic: '🌙',
  proud: '⭐', sad: '💧', angry: '🔥', lonely: '🕊️',
}

export function HomePage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [scenes, setScenes] = useState<SceneSummary[]>([])
  const [letters, setLetters] = useState<LetterSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    Promise.all([scenesApi.list(), lettersApi.list()])
      .then(([s, l]) => { setScenes(s); setLetters(l) })
      .finally(() => setLoading(false))
  }, [])

  const q = query.trim().toLowerCase()
  const filteredScenes = q
    ? scenes.filter(s => s.name.toLowerCase().includes(q))
    : []
  const filteredLetters = q
    ? letters.filter(l =>
        l.recipient.toLowerCase().includes(q) ||
        (l.subject ?? '').toLowerCase().includes(q))
    : []
  const hasResults = filteredScenes.length > 0 || filteredLetters.length > 0
  const isSearching = q.length > 0

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#f7f5f2' }}>
      {/* Header */}
      <div style={{
        background: '#fff', borderBottom: '1px solid #e8e0d8',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 16px', height: 56, flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: 22 }}>✉️</span>
          <span style={{ fontFamily: 'Georgia, serif', fontWeight: 600, fontSize: 18, color: '#2d2010' }}>
            Letters
          </span>
        </div>
        <div style={{ position: 'relative' }}>
          <button
            onClick={() => setMenuOpen(o => !o)}
            style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}
          >
            {user?.photoURL
              ? <img src={user.photoURL} alt="avatar" style={{ width: 32, height: 32, borderRadius: '50%' }} />
              : <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#075E54', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: 13 }}>{user?.displayName?.[0] ?? '?'}</div>
            }
            <span style={{ fontSize: 13, color: '#555' }}>≡</span>
          </button>
          {menuOpen && (
            <div style={{
              position: 'absolute', right: 0, top: 40, background: '#fff',
              border: '1px solid #e0d8cc', borderRadius: 10,
              boxShadow: '0 4px 16px rgba(0,0,0,0.10)',
              zIndex: 100, minWidth: 160, overflow: 'hidden',
            }}>
              <div style={{ padding: '10px 14px', fontSize: 13, color: '#888', borderBottom: '1px solid #f0e8d8' }}>
                {user?.displayName ?? user?.email}
              </div>
              <button
                onClick={() => {
                  const current = localStorage.getItem('letters_pen_name') || user?.displayName || ''
                  const next = prompt('Your pen name:', current)
                  if (next !== null) {
                    const val = next.trim() || user?.displayName || 'Anonymous'
                    localStorage.setItem('letters_pen_name', val)
                  }
                  setMenuOpen(false)
                }}
                style={{
                  width: '100%', textAlign: 'left', padding: '10px 14px',
                  background: 'none', border: 'none', cursor: 'pointer',
                  fontSize: 14, color: '#3d2b1f', borderBottom: '1px solid #f0e8d8',
                }}
              >
                ✏️ Pen name
              </button>
              <button
                onClick={() => signOut(auth)}
                style={{
                  width: '100%', textAlign: 'left', padding: '10px 14px',
                  background: 'none', border: 'none', cursor: 'pointer',
                  fontSize: 14, color: '#c0392b',
                }}
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 16px 32px' }}>
        <div style={{ maxWidth: 520, margin: '0 auto' }}>

          {/* Search */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            background: '#fff', border: '1px solid #e0d8cc', borderRadius: 12,
            padding: '8px 14px', boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
            marginBottom: 20,
          }}>
            <span style={{ fontSize: 16, color: '#b0a090' }}>🔍</span>
            <input
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Search scenes & letters…"
              style={{
                flex: 1, border: 'none', outline: 'none',
                fontSize: 14, color: '#333', background: 'transparent',
              }}
            />
            {query && (
              <button onClick={() => setQuery('')} style={{ background: 'none', border: 'none', color: '#bbb', cursor: 'pointer', fontSize: 16 }}>✕</button>
            )}
          </div>

          {/* Search results */}
          {isSearching && (
            <div style={{ marginBottom: 24 }}>
              {!hasResults && (
                <div style={{ textAlign: 'center', color: '#b0a090', fontSize: 14, padding: '20px 0' }}>
                  No results for "{query}"
                </div>
              )}
              {filteredScenes.length > 0 && (
                <>
                  <div style={{ fontSize: 11, fontWeight: 600, color: '#a09080', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>Scenes</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
                    {filteredScenes.map(s => (
                      <div key={s.id} onClick={() => navigate(`/scene/${s.id}`)} style={tileStyle}>
                        <span style={{ fontSize: 20 }}>💬</span>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontWeight: 600, fontSize: 14, color: '#2d2010' }}>{s.name}</div>
                          <div style={{ fontSize: 12, color: '#a09080' }}>{s.characterCount} characters · {s.messageCount} messages</div>
                        </div>
                        <span style={{ color: '#ccc', fontSize: 18 }}>›</span>
                      </div>
                    ))}
                  </div>
                </>
              )}
              {filteredLetters.length > 0 && (
                <>
                  <div style={{ fontSize: 11, fontWeight: 600, color: '#a09080', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>Letters</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {filteredLetters.map(l => (
                      <div key={l.id} onClick={() => navigate(`/letter/${l.id}`)} style={tileStyle}>
                        <span style={{ fontSize: 20 }}>{l.mood && MOOD_EMOJI[l.mood] ? MOOD_EMOJI[l.mood] : (isSealed(l.sealedUntil) ? '🔒' : '✉️')}</span>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontWeight: 600, fontSize: 14, color: '#2d2010' }}>Dear {l.recipient}</div>
                          <div style={{ fontSize: 12, color: '#a09080' }}>{l.subject ?? '(no subject)'}</div>
                        </div>
                        <span style={{ color: '#ccc', fontSize: 18 }}>›</span>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          )}

          {/* Default home — hidden while searching */}
          {!isSearching && (
            <>
              {/* Daily prompt */}
              <div style={{
                background: 'linear-gradient(135deg, #5c4a3a 0%, #7a6250 100%)',
                borderRadius: 14, padding: '16px 18px', marginBottom: 20,
                boxShadow: '0 2px 10px rgba(92,74,58,0.20)',
              }}>
                <div style={{ fontSize: 11, fontWeight: 600, color: 'rgba(255,255,255,0.6)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
                  ✍️ Today's prompt
                </div>
                <div style={{ fontFamily: 'Georgia, serif', fontStyle: 'italic', fontSize: 15, color: '#fff', lineHeight: 1.5, marginBottom: 12 }}>
                  "{todayPrompt()}"
                </div>
                <button
                  onClick={() => navigate('/letter/new')}
                  style={{
                    background: 'rgba(255,255,255,0.18)', border: '1px solid rgba(255,255,255,0.3)',
                    borderRadius: 8, color: '#fff', padding: '6px 14px',
                    fontSize: 13, cursor: 'pointer', fontWeight: 500,
                  }}
                >
                  Write a letter →
                </button>
              </div>

              {/* Feature tiles */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {/* Chat Scenes tile */}
                <div
                  onClick={() => navigate('/scenes')}
                  style={{
                    background: 'linear-gradient(135deg, #f0faf0 0%, #e8f5e8 100%)',
                    border: '1px solid #c8e6c8', borderRadius: 14,
                    padding: '18px 20px', cursor: 'pointer',
                    boxShadow: '0 2px 8px rgba(7,94,84,0.08)',
                    display: 'flex', alignItems: 'center', gap: 14,
                  }}
                >
                  <div style={{
                    width: 48, height: 48, borderRadius: 12, background: '#075E54',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, flexShrink: 0,
                    boxShadow: '0 2px 6px rgba(7,94,84,0.25)',
                  }}>💬</div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 15, color: '#1a3d2b', marginBottom: 2 }}>Chat Scenes</div>
                    <div style={{ fontSize: 13, color: '#5a8a70' }}>
                      {loading ? '…' : `${scenes.length} scene${scenes.length !== 1 ? 's' : ''}`}
                    </div>
                  </div>
                  <span style={{ fontSize: 20, color: '#a0c8b0' }}>›</span>
                </div>

                {/* Unsent Letters tile */}
                <div
                  onClick={() => navigate('/letters')}
                  style={{
                    background: 'linear-gradient(135deg, #fdf8f0 0%, #f8f0e0 100%)',
                    border: '1px solid #e2d0b0', borderRadius: 14,
                    padding: '18px 20px', cursor: 'pointer',
                    boxShadow: '0 2px 8px rgba(92,74,58,0.08)',
                    display: 'flex', alignItems: 'center', gap: 14,
                  }}
                >
                  <div style={{
                    width: 48, height: 48, borderRadius: 12, background: '#5c4a3a',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, flexShrink: 0,
                    boxShadow: '0 2px 6px rgba(92,74,58,0.25)',
                  }}>✉️</div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 15, color: '#3d2b1f', marginBottom: 2 }}>Unsent Letters</div>
                    <div style={{ fontSize: 13, color: '#9a7a5a' }}>
                      {loading ? '…' : `${letters.length} letter${letters.length !== 1 ? 's' : ''}${letters.filter(l => isSealed(l.sealedUntil)).length > 0 ? ` · ${letters.filter(l => isSealed(l.sealedUntil)).length} sealed 🔒` : ''}`}
                    </div>
                  </div>
                  <span style={{ fontSize: 20, color: '#c8a880' }}>›</span>
                </div>
              </div>

              {/* Tagline */}
              <div style={{
                marginTop: 32, textAlign: 'center',
                fontFamily: 'Georgia, serif', fontStyle: 'italic',
                fontSize: 13, color: '#c0b090',
              }}>
                Your words, your way
              </div>
            </>
          )}
        </div>
      </div>

      {/* Dismiss menu on outside click */}
      {menuOpen && (
        <div
          style={{ position: 'fixed', inset: 0, zIndex: 99 }}
          onClick={() => setMenuOpen(false)}
        />
      )}
    </div>
  )
}

const tileStyle: React.CSSProperties = {
  background: '#fff', border: '1px solid #e8e0d0', borderRadius: 12,
  padding: '12px 14px', cursor: 'pointer',
  display: 'flex', alignItems: 'center', gap: 12,
  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
}
