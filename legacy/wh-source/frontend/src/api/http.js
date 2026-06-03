import axios from 'axios'
import NProgress from '../utils/nprogress'
import { ElMessage } from 'element-plus'

const baseURL = import.meta.env.VITE_API_BASE_URL
const TOKEN_KEY = 'repu_token'
const ROLE_KEY = 'repu_role'

export const http = axios.create({
  baseURL,
  timeout: 15000,
})

http.interceptors.request.use(
  (config) => {
    NProgress.start()
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    NProgress.done()
    return Promise.reject(error)
  }
)

http.interceptors.response.use(
  (resp) => {
    NProgress.done()
    const payload = resp?.data
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code !== 0) {
        if (payload.code === 401) {
          localStorage.removeItem(TOKEN_KEY)
          localStorage.removeItem(ROLE_KEY)
          if (window?.location?.pathname !== '/login') {
            window.location.href = '/login'
          }
        }
        ElMessage.error(payload.msg || '请求失败')
        return Promise.reject(new Error(payload.msg || '请求失败'))
      }
      return payload.data
    }
    return payload
  },
  (err) => {
    NProgress.done()
    let msg = '网络请求错误'
    if (err.response) {
      if (err.response.status === 401) {
        msg = '未登录或登录已过期'
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(ROLE_KEY)
        if (window?.location?.pathname !== '/login') {
          window.location.href = '/login'
        }
      } else if (err.response.data && err.response.data.msg) {
        msg = err.response.data.msg
      } else {
        msg = `请求错误 ${err.response.status}`
      }
    } else if (err.message) {
      msg = err.message
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  },
)

export function cleanParams(params) {
  const out = {}
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v === null || v === undefined) return
    if (typeof v === 'string' && v.trim() === '') return
    out[k] = v
  })
  return out
}
