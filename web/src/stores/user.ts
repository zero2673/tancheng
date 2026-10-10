import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserDTO } from '@/types'
import { userApi } from '@/api/user'
import { clearToken, getToken, setToken } from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref<UserDTO | null>(null)
  const isLogin = computed(() => !!token.value)

  function saveToken(value: string) {
    token.value = value
    setToken(value)
  }

  async function loadProfile(force = false) {
    if (!token.value) return null
    if (profile.value && !force) return profile.value
    const res = await userApi.me()
    profile.value = (res.data as UserDTO) ?? null
    return profile.value
  }

  async function logout() {
    try {
      await userApi.logout()
    } catch {
      // 登出失败不阻塞前端状态清理
    }
    token.value = ''
    profile.value = null
    clearToken()
  }

  return { token, profile, isLogin, saveToken, loadProfile, logout }
})