import { computed, ref } from 'vue'

const TOKEN_KEY = 'repu_token'
const ROLE_KEY = 'repu_role'

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const role = ref(localStorage.getItem(ROLE_KEY) || '')

export function setAuth({ token: newToken, role: newRole }) {
  token.value = newToken || ''
  role.value = newRole || ''
  if (token.value) localStorage.setItem(TOKEN_KEY, token.value)
  else localStorage.removeItem(TOKEN_KEY)
  if (role.value) localStorage.setItem(ROLE_KEY, role.value)
  else localStorage.removeItem(ROLE_KEY)
}

export function clearAuth() {
  setAuth({ token: '', role: '' })
}

export function getStoredAuth() {
  return {
    token: localStorage.getItem(TOKEN_KEY) || '',
    role: localStorage.getItem(ROLE_KEY) || '',
  }
}

export function useAuth() {
  const isAuthed = computed(() => Boolean(token.value))
  return { token, role, isAuthed, setAuth, clearAuth }
}

