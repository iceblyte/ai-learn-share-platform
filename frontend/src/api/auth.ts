import request from './request'
import type { ApiResponse, LoginRequest, RegisterRequest, LoginResponse, User } from '@/types'

export const authApi = {
  login(data: LoginRequest) {
    return request.post<ApiResponse<LoginResponse>>('/auth/login', data)
  },
  register(data: RegisterRequest) {
    return request.post<ApiResponse<LoginResponse>>('/auth/register', data)
  },
  refresh(refreshToken: string) {
    return request.post<ApiResponse<LoginResponse>>('/auth/refresh', { refreshToken })
  },
  logout() {
    return request.post('/auth/logout')
  },
  me() {
    return request.get<ApiResponse<User>>('/auth/me')
  },
}
