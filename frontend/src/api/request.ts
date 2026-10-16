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
    const { response, config } = error
    // 403 = 已登录但无权限，刷新令牌无济于事，直接放行错误
    if (response?.status === 401) {
      const userStore = useUserStore()
      const url: string = config?.url || ''
      const isAuthRequest = url.includes('/auth/refresh') || url.includes('/auth/login')
      // 护栏：认证接口自身失败、本地没有 refreshToken、或该请求已重试过，
      // 都不再尝试刷新——否则 401 -> refresh -> 401 -> refresh 自我递归，
      // 未登录时会形成对 /auth/refresh 的请求风暴，页面直接卡死
      if (!isAuthRequest && !config?._retry && userStore.refreshTokenValue) {
        config._retry = true
        try {
          await userStore.refreshToken()
          // Retry original request with new token
          config.headers.Authorization = `Bearer ${userStore.token}`
          return request(config)
        } catch {
          userStore.clearAuth()
          if (window.location.pathname !== '/login') {
            window.location.href = '/login'
          }
          return Promise.reject(new Error('登录已过期，请重新登录'))
        }
      }
      // 未登录访客或刷新接口自身 401：仅清理本地状态并放行错误，
      // 由页面自身的 catch 兜底（如推荐位留空），不再打扰访客
      userStore.clearAuth()
    }
    const message = response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default request
