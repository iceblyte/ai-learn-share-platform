import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/api/request'
import type { User, LoginRequest, RegisterRequest, LoginResponse, ApiResponse } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const refreshTokenValue = ref<string>(localStorage.getItem('refreshToken') || '')
  const userInfo = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const isPublisher = computed(() => userInfo.value?.role === 'PUBLISHER' || isAdmin.value)

  function setToken(newToken: string, newRefreshToken: string) {
    token.value = newToken
    refreshTokenValue.value = newRefreshToken
    localStorage.setItem('token', newToken)
    localStorage.setItem('refreshToken', newRefreshToken)
  }

  function clearAuth() {
    token.value = ''
    refreshTokenValue.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
  }

  async function login(data: LoginRequest) {
    const res = await request.post<ApiResponse<LoginResponse>>('/auth/login', data)
    const { token: t, refreshToken: rt, ...user } = res.data.data
    setToken(t, rt)
    userInfo.value = user as unknown as User
    return res.data
  }

  async function register(data: RegisterRequest) {
    const res = await request.post<ApiResponse<LoginResponse>>('/auth/register', data)
    const { token: t, refreshToken: rt, ...user } = res.data.data
    setToken(t, rt)
    userInfo.value = user as unknown as User
    return res.data
  }

  async function fetchUserInfo() {
    const res = await request.get<ApiResponse<User>>('/auth/me')
    userInfo.value = res.data.data
    return res.data
  }

  async function refreshToken() {
    const res = await request.post<ApiResponse<LoginResponse>>('/auth/refresh', {
      refreshToken: refreshTokenValue.value,
    })
    const { token: t, refreshToken: rt } = res.data.data
    setToken(t, rt)
  }

  function logout() {
    request.post('/auth/logout').catch(() => {})
    clearAuth()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    isPublisher,
    setToken,
    login,
    register,
    fetchUserInfo,
    refreshToken,
    logout,
    clearAuth,
  }
})
