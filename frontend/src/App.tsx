import { Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { LoginPage } from '@/pages/LoginPage'
import { ScenesPage } from '@/pages/ScenesPage'
import { SceneEditorPage } from '@/pages/SceneEditorPage'
import { LettersPage } from '@/pages/LettersPage'
import { LetterEditorPage } from '@/pages/LetterEditorPage'

export default function App() {
  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<ProtectedRoute><ScenesPage /></ProtectedRoute>} />
        <Route path="/scene/:id" element={<ProtectedRoute><SceneEditorPage /></ProtectedRoute>} />
        <Route path="/letters" element={<ProtectedRoute><LettersPage /></ProtectedRoute>} />
        <Route path="/letter/:id" element={<ProtectedRoute><LetterEditorPage /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}
