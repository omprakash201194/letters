import axios from 'axios'
import { auth } from '@/lib/firebase'

const api = axios.create({ baseURL: '/api' })

// Attach Firebase ID token to every request
api.interceptors.request.use(async (config) => {
  const user = auth.currentUser
  if (user) {
    const token = await user.getIdToken()
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// --- types ---

export interface CharacterDto {
  id: string
  name: string
  color: string
  avatar: string | null
  orderIndex: number
}

export interface MessageDto {
  id: string
  charId: string
  charName: string
  charColor: string
  charAvatar: string | null
  text: string
  time: string
  outgoing: boolean
  orderIndex: number
}

export interface SceneSummary {
  id: string
  name: string
  characterCount: number
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface SceneDetail {
  id: string
  name: string
  characters: CharacterDto[]
  messages: MessageDto[]
  createdAt: string
  updatedAt: string
}

export interface SaveScenePayload {
  name: string
  characters: CharacterDto[]
  messages: MessageDto[]
}

export interface LetterSummary {
  id: string
  recipient: string
  subject: string | null
  mood: string | null
  letterDate: string | null
  sealedUntil: string | null
  createdAt: string
  updatedAt: string
}

export interface LetterDetail {
  id: string
  recipient: string
  subject: string | null
  content: string | null
  mood: string | null
  letterDate: string | null
  sealedUntil: string | null
  createdAt: string
  updatedAt: string
}

export interface SaveLetterPayload {
  recipient: string
  subject: string | null
  content: string | null
  mood: string | null
  letterDate: string | null
  sealedUntil: string | null
}

// --- API calls ---

export const scenesApi = {
  list: (): Promise<SceneSummary[]> =>
    api.get('/scenes').then(r => r.data),

  get: (id: string): Promise<SceneDetail> =>
    api.get(`/scenes/${id}`).then(r => r.data),

  create: (payload: SaveScenePayload): Promise<SceneDetail> =>
    api.post('/scenes', payload).then(r => r.data),

  update: (id: string, payload: SaveScenePayload): Promise<SceneDetail> =>
    api.put(`/scenes/${id}`, payload).then(r => r.data),

  delete: (id: string): Promise<void> =>
    api.delete(`/scenes/${id}`).then(() => undefined),
}

export const lettersApi = {
  list: (): Promise<LetterSummary[]> =>
    api.get('/letters').then(r => r.data),

  get: (id: string): Promise<LetterDetail> =>
    api.get(`/letters/${id}`).then(r => r.data),

  create: (payload: SaveLetterPayload): Promise<LetterDetail> =>
    api.post('/letters', payload).then(r => r.data),

  update: (id: string, payload: SaveLetterPayload): Promise<LetterDetail> =>
    api.put(`/letters/${id}`, payload).then(r => r.data),

  delete: (id: string): Promise<void> =>
    api.delete(`/letters/${id}`).then(() => undefined),
}
