import request from './request'
import type { ApiResponse, Resource, User } from '@/types'

export const userApi = {
  getProfile() {
    return request.get<ApiResponse<User>>('/users/profile')
  },
  updateProfile(data: { nickname?: string; bio?: string }) {
    return request.put<ApiResponse<User>>('/users/profile', data)
  },
  getFavorites() {
    return request.get<ApiResponse<Resource[]>>('/users/favorites')
  },
  getMyResources() {
    return request.get<ApiResponse<Resource[]>>('/users/resources')
  },
  getStatistics() {
    return request.get<ApiResponse<{
      publishedCount: number
      totalViews: number
      totalLikes: number
      avgRating: number
      totalFavorites: number
    }>>('/users/statistics')
  },
  uploadAvatar(formData: FormData) {
    return request.post<ApiResponse<{ avatarUrl: string }>>('/users/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
