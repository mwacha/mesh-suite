import { reactive } from 'vue'

export type ToastType = 'success' | 'error'

export interface ToastItem {
  id: number
  message: string
  type: ToastType
}

const toasts = reactive<ToastItem[]>([])
let nextId = 0

function removeToast(id: number) {
  const index = toasts.findIndex((t) => t.id === id)
  if (index !== -1) {
    toasts.splice(index, 1)
  }
}

function showToast(message: string, type: ToastType = 'success', duration = 3000) {
  const id = nextId++
  toasts.push({ id, message, type })
  setTimeout(() => removeToast(id), duration)
}

export function useToast() {
  return { toasts, showToast, removeToast }
}
