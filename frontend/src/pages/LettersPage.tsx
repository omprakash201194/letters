import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { lettersApi, LetterSummary } from '@/services/api'
import { useAuth } from '@/hooks/useAuth'

const MOODS: Record<string, string> = {
  grateful: '🙏',
  hopeful: '🌱',
  love: '❤️',
  nostalgic: '🌙',
  proud: '⭐',
  sad: '💧',
  angry: '🔥',
  lonely: '🕊️',
}

function isSealed(sealedUntil: string | null): boolean {
  if (!sealedUntil) return false
  return new Date(sealedUntil) > new Date()
}

function formatDate(iso: string | null): string {
  if (!iso) return ''
  // iso is LocalDate: "2026-05-22"
  const [y, m, d] = iso.split('-')
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
  return `${months[parseInt(m) - 1]} ${parseInt(d)}, ${y}`
}

export function LettersPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [letters, setLetters] = useState<LetterSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [deleting, setDeleting] = useState<string | null>(null)

  useEffect(() => {
    lettersApi.list()
      .then(setLetters)
      .finally(() => setLoading(false))
  }, [])

  async function handleDelete(id: string, e: React.MouseEvent) {
    e.stopPropagation()
    if (!confirm('Delete this letter?')) return
    setDeleting(id)
    await lettersApi.delete(id)
    setLetters(prev => prev.filter(l => l.id !== id))
    setDeleting(null)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#f5f0e8' }}>
      {/* Header */}
      <div style={{
        background: '#5c4a3a', color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 16px', height: 56, flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            onClick={() => navigate('/', { replace: false })}
            style={{ background: 'none', border: 'none', color: '#fff', fontSize: 20, cursor: 'pointer', padding: 4 }}
          >
            ‹
          </button>
          <span style={{ fontFamily: 'Georgia, serif', fontWeight: 600, fontSize: 17 }}>Unsent Letters</span>
        </div>
        <button
          onClick={() => navigate('/letter/new')}
          style={{
            background: 'rgba(255,255,255,0.2)', border: 'none', borderRadius: 8,
            color: '#fff', padding: '6px 14px', cursor: 'pointer', fontSize: 13, fontWeight: 500,
          }}
        >
          ✍️ Write
        </button>
      </div>

      {/* List */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px' }}>
        {loading && (
          <div style={{ textAlign: 'center', marginTop: 60, color: '#888' }}>
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-amber-800 border-t-transparent" style={{ margin: '0 auto 12px' }} />
          </div>
        )}

        {!loading && letters.length === 0 && (
          <div style={{ textAlign: 'center', marginTop: 80, color: '#9a8060' }}>
            <div style={{ fontSize: 48, marginBottom: 12 }}>✉️</div>
            <div style={{ fontFamily: 'Georgia, serif', fontSize: 18, marginBottom: 8 }}>No letters yet</div>
            <div style={{ fontSize: 14, color: '#b8a080' }}>
              Write the things you never said
            </div>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 520, margin: '0 auto' }}>
          {letters.map(letter => (
            <div
              key={letter.id}
              onClick={() => navigate(`/letter/${letter.id}`)}
              style={{
                background: '#fffef9',
                border: '1px solid #e2d5c0',
                borderRadius: 8,
                padding: '14px 16px',
                cursor: 'pointer',
                boxShadow: '0 2px 6px rgba(0,0,0,0.06)',
                position: 'relative',
                transition: 'box-shadow 0.15s',
              }}
            >
              {/* Mood + date row */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <span style={{ fontSize: 20 }}>
                  {letter.mood && MOODS[letter.mood] ? MOODS[letter.mood] : '✉️'}
                </span>
                <span style={{ fontSize: 12, color: '#a09070' }}>
                  {letter.letterDate ? formatDate(letter.letterDate) : formatDate(letter.updatedAt.split('T')[0])}
                </span>
              </div>

              {/* Dear ... */}
              <div style={{ fontFamily: 'Georgia, serif', fontSize: 13, color: '#9a8060', marginBottom: 4 }}>
                Dear {letter.recipient},
              </div>

              {/* Subject */}
              {letter.subject && (
                <div style={{ fontFamily: 'Georgia, serif', fontWeight: 600, fontSize: 15, color: '#3d2b1f', marginBottom: 4 }}>
                  {letter.subject}
                </div>
              )}
              {!letter.subject && (
                <div style={{ fontFamily: 'Georgia, serif', fontSize: 14, color: '#9a8060', fontStyle: 'italic', marginBottom: 4 }}>
                  (no subject)
                </div>
              )}

              {/* Content preview */}
              {!isSealed(letter.sealedUntil) && letter.contentPreview && (
                <div style={{ fontSize: 13, color: '#8a7060', lineHeight: 1.5, fontFamily: 'Georgia, serif' }}>
                  {letter.contentPreview}
                </div>
              )}
              {isSealed(letter.sealedUntil) && (
                <div style={{ fontSize: 12, color: '#b8a080', fontStyle: 'italic' }}>
                  🔒 Opens {letter.sealedUntil ? formatDate(letter.sealedUntil) : ''}
                </div>
              )}

              {/* Mood label */}
              {letter.mood && (
                <div style={{ marginTop: 8, fontSize: 11, color: '#b8a080', textTransform: 'capitalize' }}>
                  {letter.mood}
                </div>
              )}

              {/* Delete button */}
              <button
                onClick={(e) => handleDelete(letter.id, e)}
                disabled={deleting === letter.id}
                style={{
                  position: 'absolute', top: 10, right: 10,
                  background: 'none', border: 'none', color: '#ccc',
                  cursor: 'pointer', fontSize: 16, padding: 4,
                  lineHeight: 1,
                }}
                title="Delete letter"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Author hint */}
      {!loading && letters.length > 0 && (
        <div style={{ padding: '8px 0 12px', textAlign: 'center', fontSize: 11, color: '#b8a080', fontFamily: 'Georgia, serif', fontStyle: 'italic' }}>
          — {user?.displayName ?? 'You'}
        </div>
      )}
    </div>
  )
}
