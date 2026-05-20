import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { useUserStore } from '@/store/user'
import type { ApiResponse } from '@/types'

const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor - add JWT token
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor - handle errors
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    // 检测后端返回的新 JWT（角色变更时）
    const newToken = response.headers['x-new-token']
    const newRefreshToken = response.headers['x-new-refresh-token']
    if (newToken && newRefreshToken) {
      const userStore = useUserStore()
      userStore.setToken(newToken, newRefreshToken)
      userStore.fetchUserInfo()
    }
    const { data } = response
    if (data.code === 200 || data.code === 201) {
      return response
    }
    return Promise.reject(new Error(data.message || '请求失败'))
  },
  async (error) => {
    const { response } = error
    if (response?.status === 401 || response?.status === 403) {
      const userStore = useUserStore()
      try {
        await userStore.refreshToken()
        // Retry original request with new token
        error.config.headers.Authorization = `Bearer ${userStore.token}`
        return request(error.config)
      } catch {
        userStore.logout()
        window.location.href = '/login'
      }
    }
    const message = response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default request
