import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function genId(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

export function nowTime(): string {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const COLORS = [
  '#E91E63', '#9C27B0', '#3F51B5', '#2196F3',
  '#009688', '#FF5722', '#795548', '#607D8B',
]
export function colorForIndex(i: number): string {
  return COLORS[i % COLORS.length]
}

export function initials(name: string): string {
  return name.trim().split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase()
}

export function escHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
