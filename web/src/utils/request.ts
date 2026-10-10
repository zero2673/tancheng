import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types'

const TOKEN_KEY = 'tancheng_token'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const setToken = (token: string) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截：带上 token（后端 RefreshTokenInterceptor 会拿它换用户信息并续期）
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.authorization = token
  }
  return config
})

// 响应拦截：统一处理后端 Result 结构与 401
instance.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    if (res.success) {
      return res as unknown as never
    }
    ElMessage.error(res.errorMsg || '请求失败')
    return Promise.reject(new Error(res.errorMsg || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.warning('请先登录')
      clearToken()
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else {
      ElMessage.error(error.message || '网络异常，请稍后再试')
    }
    return Promise.reject(error)
  },
)

export function request<T = unknown>(config: AxiosRequestConfig): Promise<Result<T>> {
  return instance.request(config) as unknown as Promise<Result<T>>
}

export const http = {
  get: <T = unknown>(url: string, params?: object) => request<T>({ url, method: 'get', params }),
  post: <T = unknown>(url: string, data?: object) => request<T>({ url, method: 'post', data }),
  put: <T = unknown>(url: string, data?: object) => request<T>({ url, method: 'put', data }),
  delete: <T = unknown>(url: string) => request<T>({ url, method: 'delete' }),
  upload: <T = unknown>(url: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<T>({ url, method: 'post', data: form, headers: { 'Content-Type': 'multipart/form-data' } })
  },
}