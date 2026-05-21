import { signInWithPopup } from 'firebase/auth'
import { useNavigate } from 'react-router-dom'
import { auth, googleProvider } from '@/lib/firebase'

export function LoginPage() {
  const navigate = useNavigate()

  async function handleGoogleSignIn() {
    try {
      await signInWithPopup(auth, googleProvider)
      navigate('/')
    } catch (err) {
      console.error('Sign-in failed', err)
    }
  }

  return (
    <div className="flex h-full flex-col items-center justify-center bg-wa-bg gap-8 p-6">
      {/* Logo area */}
      <div className="flex flex-col items-center gap-3">
        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-wa-green text-white text-4xl shadow-lg">
          💬
        </div>
        <h1 className="text-3xl font-bold text-wa-green">Letters</h1>
        <p className="text-gray-500 text-center text-sm max-w-xs">
          Build WhatsApp-style chat scenes with characters, messages, and animated playback.
        </p>
      </div>

      {/* Sign in card */}
      <div className="bg-white rounded-2xl shadow-lg p-8 w-full max-w-sm flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-gray-800 text-center">Sign in to continue</h2>
        <button
          onClick={handleGoogleSignIn}
          className="flex items-center justify-center gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 text-gray-700 font-medium shadow-sm hover:bg-gray-50 transition-colors"
        >
          <svg width="20" height="20" viewBox="0 0 48 48">
            <path fill="#FFC107" d="M43.6 20H24v8h11.3C33.7 33.2 29.3 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3 0 5.7 1.1 7.8 2.9l5.7-5.7C34.1 6.5 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20c11 0 20-9 20-20 0-1.3-.1-2.7-.4-4z"/>
            <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.6 15.4 18.9 12 24 12c3 0 5.7 1.1 7.8 2.9l5.7-5.7C34.1 6.5 29.3 4 24 4 16.3 4 9.7 8.4 6.3 14.7z"/>
            <path fill="#4CAF50" d="M24 44c5.2 0 9.9-1.9 13.4-5l-6.2-5.2C29.4 35.5 26.8 36 24 36c-5.2 0-9.6-2.8-11.3-7L6 33.7C9.4 39.7 16.2 44 24 44z"/>
            <path fill="#1976D2" d="M43.6 20H24v8h11.3c-.9 2.4-2.5 4.4-4.6 5.8l6.2 5.2C40.7 35.8 44 30.3 44 24c0-1.3-.1-2.7-.4-4z"/>
          </svg>
          Continue with Google
        </button>
      </div>
    </div>
  )
}
