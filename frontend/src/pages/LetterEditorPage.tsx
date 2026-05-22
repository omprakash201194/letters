import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toPng } from 'html-to-image'
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
  const months = ['January','February','March','April','May','June','July',
                  'August','September','October','November','December']
  return `${months[parseInt(m) - 1]} ${parseInt(d)}, ${y}`
}

function isSealed(sealedUntil: string | null): boolean {
  if (!sealedUntil) return false
  return new Date(sealedUntil) > new Date()
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
  const [sealedUntil, setSealedUntil] = useState<string | null>(null)
  const [sealToggle, setSealToggle] = useState(false)
  const [loading, setLoading] = useState(!isNew)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [exporting, setExporting] = useState(false)

  const paperRef = useRef<HTMLDivElement>(null)
  const contentRef = useRef<HTMLTextAreaElement>(null)

  const sealed = isSealed(sealedUntil)

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
          setSealedUntil(detail.sealedUntil)
          if (detail.sealedUntil) setSealToggle(true)
        })
        .catch(() => navigate('/letters'))
        .finally(() => setLoading(false))
    }
  }, [id, isNew, navigate])

  async function saveLetter() {
    if (!recipient.trim()) {
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
        sealedUntil: sealToggle ? sealedUntil : null,
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

  async function exportAsImage() {
    if (!paperRef.current) return
    setExporting(true)
    try {
      const dataUrl = await toPng(paperRef.current, { cacheBust: true, pixelRatio: 2 })
      const link = document.createElement('a')
      link.href = dataUrl
      link.download = `letter-to-${recipient.trim().replace(/\s+/g, '-') || 'unknown'}.png`
      link.click()
    } catch {
      alert('Export failed. Try again.')
    } finally {
      setExporting(false)
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', background: '#f5f0e8' }}>
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-amber-800 border-t-transparent" />
      </div>
    )
  }

  const lineHeight = 28

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#f5f0e8' }}>
      {/* Header */}
      <div style={{
        background: '#5c4a3a', color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 12px', height: 56, flexShrink: 0, gap: 8,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button onClick={() => navigate('/letters')}
            style={{ background: 'none', border: 'none', color: '#fff', fontSize: 22, cursor: 'pointer', padding: '0 2px', lineHeight: 1 }}>
            ‹
          </button>
          <span style={{ fontFamily: 'Georgia, serif', fontWeight: 600, fontSize: 15 }}>
            {isNew ? 'New Letter' : 'Edit Letter'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          {/* Export button — only on saved, non-sealed letters */}
          {!isNew && !sealed && (
            <button
              onClick={exportAsImage}
              disabled={exporting}
              title="Export as image"
              style={{
                background: 'rgba(255,255,255,0.15)', border: 'none', borderRadius: 8,
                color: '#fff', padding: '6px 10px', cursor: 'pointer', fontSize: 13,
              }}
            >
              {exporting ? '…' : '📤'}
            </button>
          )}
          <button
            onClick={saveLetter}
            disabled={saving}
            style={{
              background: saved ? 'rgba(100,200,100,0.3)' : 'rgba(255,255,255,0.2)',
              border: 'none', borderRadius: 8,
              color: '#fff', padding: '6px 12px', cursor: saving ? 'default' : 'pointer',
              fontSize: 13, fontWeight: 500, transition: 'background 0.3s',
            }}
          >
            {saving ? '…' : saved ? '✓ Saved' : '💾 Save'}
          </button>
        </div>
      </div>

      {/* Letter paper */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 16px 32px' }}>
        <div style={{ maxWidth: 560, margin: '0 auto' }}>

          {/* Sealed view — content hidden */}
          {sealed ? (
            <div
              ref={paperRef}
              style={{
                background: '#fffef9', border: '1px solid #e2d5c0',
                borderRadius: 4, boxShadow: '0 4px 20px rgba(0,0,0,0.10)',
                padding: '40px 36px', minHeight: 400,
                display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16,
                textAlign: 'center',
              }}
            >
              <div style={{ fontSize: 56 }}>🔒</div>
              <div style={{ fontFamily: 'Georgia, serif', fontWeight: 600, fontSize: 20, color: '#3d2b1f' }}>
                Time Capsule
              </div>
              <div style={{ fontFamily: 'Georgia, serif', fontSize: 14, color: '#9a8060', fontStyle: 'italic' }}>
                Dear {recipient},
              </div>
              <div style={{ fontSize: 13, color: '#b8a080' }}>
                This letter is sealed until
              </div>
              <div style={{ fontFamily: 'Georgia, serif', fontWeight: 600, fontSize: 16, color: '#5c4a3a' }}>
                {sealedUntil ? formatDateDisplay(sealedUntil) : ''}
              </div>
              <div style={{ fontSize: 12, color: '#c8b898', marginTop: 8 }}>
                Come back then to read it.
              </div>
            </div>
          ) : (
            /* Paper editor */
            <div
              ref={paperRef}
              style={{
                background: '#fffef9',
                border: '1px solid #e2d5c0',
                borderRadius: 4,
                boxShadow: '0 4px 20px rgba(0,0,0,0.10), 0 1px 4px rgba(0,0,0,0.06)',
                padding: '32px 36px 40px',
                minHeight: 500,
                position: 'relative',
              }}
            >
              {/* Red margin line */}
              <div style={{ position: 'absolute', left: 72, top: 0, bottom: 0, width: 1, background: 'rgba(220,80,60,0.25)' }} />

              {/* Date + mood */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
                <input
                  type="date"
                  value={letterDate}
                  onChange={e => setLetterDate(e.target.value)}
                  style={{
                    fontFamily: 'Georgia, serif', fontSize: 13, color: '#9a8060',
                    border: 'none', background: 'transparent', outline: 'none', cursor: 'pointer',
                  }}
                />
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  {MOODS.map(m => (
                    <button key={m.key} onClick={() => setMood(mood === m.key ? null : m.key)} title={m.label}
                      style={{
                        fontSize: 18, background: 'none', border: 'none', cursor: 'pointer',
                        opacity: mood === null ? 0.5 : mood === m.key ? 1 : 0.25,
                        transform: mood === m.key ? 'scale(1.25)' : 'scale(1)',
                        transition: 'opacity 0.15s, transform 0.15s', padding: 2,
                      }}>
                      {m.emoji}
                    </button>
                  ))}
                </div>
              </div>

              {/* Dear ... */}
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginBottom: 8 }}>
                <span style={{ fontFamily: 'Georgia, serif', fontSize: 17, color: '#3d2b1f', whiteSpace: 'nowrap' }}>Dear</span>
                <input
                  value={recipient}
                  onChange={e => setRecipient(e.target.value)}
                  placeholder="…"
                  style={{
                    fontFamily: 'Georgia, serif', fontSize: 17, color: '#3d2b1f',
                    border: 'none', borderBottom: '1px dashed #c8b090',
                    background: 'transparent', outline: 'none', flex: 1, minWidth: 80,
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
                <div style={{
                  position: 'absolute', inset: 0, pointerEvents: 'none',
                  backgroundImage: `repeating-linear-gradient(transparent, transparent ${lineHeight - 1}px, #e8ddc8 ${lineHeight - 1}px, #e8ddc8 ${lineHeight}px)`,
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
                    color: '#2d1f0f', border: 'none', background: 'transparent', outline: 'none',
                    resize: 'none', overflow: 'hidden', position: 'relative', zIndex: 1, padding: 0,
                  }}
                  onInput={e => {
                    const el = e.currentTarget
                    el.style.height = 'auto'
                    el.style.height = el.scrollHeight + 'px'
                  }}
                />
              </div>

              {/* Sign-off */}
              <div style={{ marginTop: 32, textAlign: 'right', fontFamily: 'Georgia, serif', fontStyle: 'italic', fontSize: 14, color: '#9a8060' }}>
                — {user?.displayName ?? 'You'}
              </div>
              {letterDate && (
                <div style={{ marginTop: 4, textAlign: 'right', fontFamily: 'Georgia, serif', fontSize: 12, color: '#b8a080' }}>
                  {formatDateDisplay(letterDate)}
                </div>
              )}
            </div>
          )}

          {/* Time capsule toggle */}
          <div style={{
            marginTop: 16, background: '#fff', border: '1px solid #e8e0d0',
            borderRadius: 12, padding: '14px 16px',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <div style={{ fontWeight: 600, fontSize: 14, color: '#3d2b1f', display: 'flex', alignItems: 'center', gap: 6 }}>
                  🔒 Time capsule
                </div>
                <div style={{ fontSize: 12, color: '#b0a080', marginTop: 2 }}>
                  Seal this letter until a future date
                </div>
              </div>
              {/* Toggle switch */}
              <button
                onClick={() => {
                  setSealToggle(t => !t)
                  if (!sealedUntil) {
                    // Default: 1 year from today
                    const d = new Date()
                    d.setFullYear(d.getFullYear() + 1)
                    setSealedUntil(d.toISOString().split('T')[0])
                  }
                }}
                style={{
                  width: 44, height: 24, borderRadius: 12,
                  background: sealToggle ? '#5c4a3a' : '#d0c8bc',
                  border: 'none', cursor: 'pointer', position: 'relative',
                  transition: 'background 0.2s', flexShrink: 0,
                }}
              >
                <div style={{
                  position: 'absolute', top: 3, left: sealToggle ? 23 : 3,
                  width: 18, height: 18, borderRadius: '50%', background: '#fff',
                  transition: 'left 0.2s', boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
                }} />
              </button>
            </div>

            {sealToggle && (
              <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 13, color: '#7a6250' }}>Opens on</span>
                <input
                  type="date"
                  value={sealedUntil ?? ''}
                  min={todayIso()}
                  onChange={e => setSealedUntil(e.target.value || null)}
                  style={{
                    fontFamily: 'Georgia, serif', fontSize: 13, color: '#3d2b1f',
                    border: '1px solid #d0c0a0', borderRadius: 6,
                    padding: '4px 8px', background: '#fdf8f0', outline: 'none', cursor: 'pointer',
                  }}
                />
                {sealed && (
                  <span style={{ fontSize: 12, color: '#9a8060', fontStyle: 'italic' }}>· currently sealed</span>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
