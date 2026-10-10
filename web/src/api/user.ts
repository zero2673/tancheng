import { http } from '@/utils/request'
import type { UserDTO } from '@/types'

export const userApi = {
  sendCode: (phone: string) => http.post(`/user/code?phone=${encodeURIComponent(phone)}`),
  login: (data: { phone: string; code?: string; password?: string }) => http.post<string>('/user/login', data),
  logout: () => http.post('/user/logout'),
  me: () => http.get<UserDTO>('/user/me'),
  info: (id: string) => http.get('/user/info/' + id),
  queryById: (id: string) => http.get<UserDTO>('/user/' + id),
  sign: () => http.post('/user/sign'),
  signCount: () => http.get<string>('/user/sign/count'),
}