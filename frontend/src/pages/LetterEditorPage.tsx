import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { lettersApi } from '@/services/api'
import { useAuth } from '@/hooks/useAuth'

const MOODS = [
  { key: 'grateful',  emoji: '🙏', label: 'Grateful' },
  { key: 'hopeful',   emoji: '🌱', label: 'Hopeful' },
  { key: 'love',      emoji: '❤️', label: 'Love' },
  { key: 'nostalgic', emoji: '🌙', label: 'Nostalgic' },
  { key: 'proud',     emoji: '⭐', label: 'Proud' },
  { key: 'sad',       emoji: '💧', label: 'Sad' },
  { key: 'angry',     emoji: '🔥', label: 'Angry' },
  { key: 'lonely',    emoji: '🕊️', label: 'Lonely' },
]

function todayIso(): string {
  return new Date().toISOString().split('T')[0]
}

function formatDateDisplay(iso: string): string {
  const [y, m, d] = iso.split('-')
  const months = ['January','February','March','April','May','June','July','August','September','October','November','December']
  return `${months[parseInt(m) - 1]} ${parseInt(d)}, ${y}`
}

export function LetterEditorPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const isNew = id === 'new'

  const [letterId, setLetterId] = useState<string | null>(isNew ? null : (id ?? null))
  const [recipient, setRecipient] = useState('')
  const [subject, setSubject] = useState('')
  const [content, setContent] = useState('')
  const [mood, setMood] = useState<string | null>(null)
  const [letterDate, setLetterDate] = useState(todayIso())
  const [loading, setLoading] = useState(!isNew)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const contentRef = useRef<HTMLTextAreaElement>(null)

  // Load existing letter
  useEffect(() => {
    if (!isNew && id) {
      lettersApi.get(id)
        .then(detail => {
          setRecipient(detail.recipient)
          setSubject(detail.subject ?? '')
          setContent(detail.content ?? '')
          setMood(detail.mood)
          setLetterDate(detail.letterDate ?? todayIso())
        })
        .catch(() => navigate('/letters'))
        .finally(() => setLoading(false))
    }
  }, [id, isNew, navigate])

  async function saveLetter() {
    if (!recipient.trim()) {
      contentRef.current?.focus()
      alert('Who is this letter to?')
      return
    }
    setSaving(true)
    try {
      const payload = {
        recipient: recipient.trim(),
        subject: subject.trim() || null,
        content: content || null,
        mood,
        letterDate,
      }
      if (letterId) {
        await lettersApi.update(letterId, payload)
      } else {
        const created = await lettersApi.create(payload)
        setLetterId(created.id)
        navigate(`/letter/${created.id}`, { replace: true })
      }
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch {
      alert('Failed to save letter.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', background: '#f5f0e8' }}>
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-amber-800 border-t-transparent" />
      </div>
    )
  }

  const lineHeight = 28 // px — matches the ruled-line CSS

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#f5f0e8' }}>
      {/* Header */}
      <div style={{
        background: '#5c4a3a', color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 16px', height: 56, flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            onClick={() => navigate('/letters')}
            style={{ background: 'none', border: 'none', color: '#fff', fontSize: 20, cursor: 'pointer', padding: 4 }}
          >
            ‹
          </button>
          <span style={{ fontFamily: 'Georgia, serif', fontWeight: 600, fontSize: 16 }}>
            {isNew ? 'New Letter' : 'Edit Letter'}
          </span>
        </div>
        <button
          onClick={saveLetter}
          disabled={saving}
          style={{
            background: saved ? 'rgba(100,200,100,0.3)' : 'rgba(255,255,255,0.2)',
            border: 'none', borderRadius: 8,
            color: '#fff', padding: '6px 14px', cursor: saving ? 'default' : 'pointer',
            fontSize: 13, fontWeight: 500, transition: 'background 0.3s',
          }}
        >
          {saving ? '…' : saved ? '✓ Saved' : '💾 Save'}
        </button>
      </div>

      {/* Letter paper */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 16px 32px' }}>
        <div style={{
          maxWidth: 560, margin: '0 auto',
          background: '#fffef9',
          border: '1px solid #e2d5c0',
          borderRadius: 4,
          boxShadow: '0 4px 20px rgba(0,0,0,0.10), 0 1px 4px rgba(0,0,0,0.06)',
          padding: '32px 36px 40px',
          minHeight: 500,
          position: 'relative',
        }}>
          {/* Red margin line (left) */}
          <div style={{
            position: 'absolute', left: 72, top: 0, bottom: 0, width: 1,
            background: 'rgba(220,80,60,0.25)',
          }} />

          {/* Date + mood row */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
            {/* Date input — styled as handwritten */}
            <input
              type="date"
              value={letterDate}
              onChange={e => setLetterDate(e.target.value)}
              style={{
                fontFamily: 'Georgia, serif', fontSize: 13, color: '#9a8060',
                border: 'none', background: 'transparent', outline: 'none',
                cursor: 'pointer',
              }}
            />
            {/* Mood pills */}
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
              {MOODS.map(m => (
                <button
                  key={m.key}
                  onClick={() => setMood(mood === m.key ? null : m.key)}
                  title={m.label}
                  style={{
                    fontSize: 18, background: 'none', border: 'none', cursor: 'pointer',
                    opacity: mood === null ? 0.5 : mood === m.key ? 1 : 0.25,
                    transform: mood === m.key ? 'scale(1.25)' : 'scale(1)',
                    transition: 'opacity 0.15s, transform 0.15s',
                    padding: 2,
                  }}
                >
                  {m.emoji}
                </button>
              ))}
            </div>
          </div>

          {/* "Dear ..." salutation */}
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 8 }}>
            <span style={{
              fontFamily: 'Georgia, serif', fontSize: 17, color: '#3d2b1f', whiteSpace: 'nowrap',
            }}>
              Dear
            </span>
            <input
              value={recipient}
              onChange={e => setRecipient(e.target.value)}
              placeholder="…"
              style={{
                fontFamily: 'Georgia, serif', fontSize: 17, color: '#3d2b1f',
                border: 'none', borderBottom: '1px dashed #c8b090',
                background: 'transparent', outline: 'none',
                flex: 1, minWidth: 80,
              }}
            />
            <span style={{ fontFamily: 'Georgia, serif', fontSize: 17, color: '#3d2b1f' }}>,</span>
          </div>

          {/* Subject */}
          <input
            value={subject}
            onChange={e => setSubject(e.target.value)}
            placeholder="Subject (optional)"
            style={{
              width: '100%', boxSizing: 'border-box',
              fontFamily: 'Georgia, serif', fontStyle: 'italic',
              fontSize: 14, color: '#6b5040',
              border: 'none', borderBottom: '1px dashed #e0cdb0',
              background: 'transparent', outline: 'none',
              marginBottom: 20, paddingBottom: 4,
            }}
          />

          {/* Lined body */}
          <div style={{ position: 'relative' }}>
            {/* Ruled lines behind the textarea */}
            <div style={{
              position: 'absolute', inset: 0, pointerEvents: 'none',
              backgroundImage: `repeating-linear-gradient(
                transparent,
                transparent ${lineHeight - 1}px,
                #e8ddc8 ${lineHeight - 1}px,
                #e8ddc8 ${lineHeight}px
              )`,
              backgroundSize: `100% ${lineHeight}px`,
            }} />
            <textarea
              ref={contentRef}
              value={content}
              onChange={e => setContent(e.target.value)}
              placeholder="Write what you never said…"
              rows={14}
              style={{
                width: '100%', boxSizing: 'border-box',
                fontFamily: 'Georgia, serif', fontSize: 15.5,
                lineHeight: `${lineHeight}px`,
                color: '#2d1f0f',
                border: 'none', background: 'transparent', outline: 'none',
                resize: 'none', overflow: 'hidden',
                position: 'relative', zIndex: 1,
                padding: 0,
              }}
              onInput={e => {
                // Auto-grow textarea
                const el = e.currentTarget
                el.style.height = 'auto'
                el.style.height = el.scrollHeight + 'px'
              }}
            />
          </div>

          {/* Sign-off */}
          <div style={{
            marginTop: 32, textAlign: 'right',
            fontFamily: 'Georgia, serif', fontStyle: 'italic',
            fontSize: 14, color: '#9a8060',
          }}>
            — {user?.displayName ?? 'You'}
          </div>

          {/* Date display at bottom */}
          {letterDate && (
            <div style={{
              marginTop: 4, textAlign: 'right',
              fontFamily: 'Georgia, serif', fontSize: 12, color: '#b8a080',
            }}>
              {formatDateDisplay(letterDate)}
            </div>
          )}
        </div>
      </div>

      {/* Stamp decoration */}
      <style>{`
        @keyframes fadeInSaved { from { opacity: 0; } to { opacity: 1; } }
      `}</style>
    </div>
  )
}
