import request from './request'
import type { ApiResponse, Resource, User, FollowStats, FollowedUser, PublicUserProfile, FollowPageItem, PageData } from '@/types'

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
  getMyResources(status?: string) {
    return request.get<ApiResponse<Resource[]>>('/users/resources', { params: { status } })
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
  submitPublisherApplication(reason: string) {
    return request.post<ApiResponse<string>>('/users/publisher-applications', { reason })
  },
  getPublisherApplication() {
    return request.get<ApiResponse<{ id: number; userId: number; reason: string; status: string; rejectReason: string; createdAt: string } | null>>('/users/publisher-applications')
  },
  /** 关注/取关切换，返回 { following, followers } */
  toggleFollow(userId: number) {
    return request.post<ApiResponse<{ following: boolean; followers: number }>>(`/users/${userId}/follow`)
  },
  /** 某用户的关注数据 */
  getFollowStats(userId: number) {
    return request.get<ApiResponse<FollowStats>>(`/users/${userId}/follow`)
  },
  /** 我关注的用户列表（评论 @ 选择器数据源） */
  getFollowingList() {
    return request.get<ApiResponse<FollowedUser[]>>('/users/following')
  },
  /** 公开用户主页信息（匿名可访问） */
  getPublicProfile(userId: number) {
    return request.get<ApiResponse<PublicUserProfile>>(`/users/${userId}`)
  },
  /** 某用户已发布的资源分页（匿名可访问） */
  getUserPublishedResources(userId: number, page = 1, size = 10) {
    return request.get<ApiResponse<PageData<Resource>>>(`/users/${userId}/resources`, { params: { page, size } })
  },
  /** 某用户的粉丝分页列表（匿名可访问） */
  getFollowers(userId: number, page = 1, size = 20) {
    return request.get<ApiResponse<PageData<FollowPageItem>>>(`/users/${userId}/followers`, { params: { page, size } })
  },
  /** 某用户关注的人的分页列表（匿名可访问） */
  getFollowingPage(userId: number, page = 1, size = 20) {
    return request.get<ApiResponse<PageData<FollowPageItem>>>(`/users/${userId}/following`, { params: { page, size } })
  },
}
