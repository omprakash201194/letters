import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { signOut } from 'firebase/auth'
import { auth } from '@/lib/firebase'
import { scenesApi, SceneSummary } from '@/services/api'
import { useAuth } from '@/hooks/useAuth'

export function ScenesPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [scenes, setScenes] = useState<SceneSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [deleting, setDeleting] = useState<string | null>(null)

  useEffect(() => {
    scenesApi.list()
      .then(setScenes)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  async function handleDelete(id: string, e: React.MouseEvent) {
    e.stopPropagation()
    if (!confirm('Delete this scene?')) return
    setDeleting(id)
    try {
      await scenesApi.delete(id)
      setScenes(s => s.filter(sc => sc.id !== id))
    } catch {
      alert('Failed to delete scene.')
    } finally {
      setDeleting(null)
    }
  }

  function formatDate(iso: string) {
    return new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })
  }

  return (
    <div className="flex h-full flex-col bg-gray-50">
      {/* Header */}
      <div className="flex items-center justify-between bg-wa-green px-4 py-3 shadow">
        <div className="flex items-center gap-3">
          <span className="text-2xl">💬</span>
          <h1 className="text-white font-semibold text-lg">Letters</h1>
        </div>
        <div className="flex items-center gap-3">
          {user?.photoURL && (
            <img src={user.photoURL} alt="avatar" className="h-8 w-8 rounded-full" />
          )}
          <button
            onClick={() => signOut(auth)}
            className="text-white/80 text-sm hover:text-white"
          >
            Sign out
          </button>
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto p-4">
        {loading ? (
          <div className="flex justify-center pt-16">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-wa-green border-t-transparent" />
          </div>
        ) : scenes.length === 0 ? (
          <div className="flex flex-col items-center justify-center pt-20 gap-4 text-center">
            <div className="text-6xl">🎬</div>
            <p className="text-gray-500 text-sm max-w-xs">
              No scenes yet. Tap the button below to create your first WhatsApp chat scene.
            </p>
          </div>
        ) : (
          <div className="flex flex-col gap-3 max-w-lg mx-auto">
            {scenes.map(scene => (
              <div
                key={scene.id}
                onClick={() => navigate(`/scene/${scene.id}`)}
                className="bg-white rounded-xl shadow-sm border border-gray-100 p-4 cursor-pointer hover:shadow-md transition-shadow flex items-center justify-between group"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-wa-green text-white text-lg flex-shrink-0">
                    💬
                  </div>
                  <div className="min-w-0">
                    <p className="font-medium text-gray-800 truncate">{scene.name}</p>
                    <p className="text-xs text-gray-400">
                      {scene.characterCount} characters · {scene.messageCount} messages · {formatDate(scene.updatedAt)}
                    </p>
                  </div>
                </div>
                <button
                  onClick={(e) => handleDelete(scene.id, e)}
                  disabled={deleting === scene.id}
                  className="ml-2 flex-shrink-0 p-2 rounded-lg text-gray-300 hover:text-red-400 hover:bg-red-50 transition-colors opacity-0 group-hover:opacity-100"
                  title="Delete scene"
                >
                  {deleting === scene.id ? (
                    <div className="h-4 w-4 animate-spin rounded-full border-2 border-red-300 border-t-transparent" />
                  ) : (
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  )}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* FAB */}
      <div className="p-4 flex justify-center">
        <button
          onClick={() => navigate('/scene/new')}
          className="flex items-center gap-2 bg-wa-green text-white px-6 py-3 rounded-full shadow-lg hover:bg-wa-green/90 active:scale-95 transition-all font-medium"
        >
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Scene
        </button>
      </div>
    </div>
  )
}
