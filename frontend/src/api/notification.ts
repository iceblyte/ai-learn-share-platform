import request from './request'
import type { ApiResponse, PageData } from '@/types'

export interface NotificationItem {
  id: number
  type: 'FOLLOW' | 'LIKE_RESOURCE' | 'LIKE_COMMENT' | 'FAVORITE' | 'COMMENT' | 'REPLY' | 'MENTION' | 'AI_SUMMARY' | string
  resourceId: number | null
  commentId: number | null
  content: string | null
  isRead: number
  createdAt: string
  actorId?: number
  actorNickname?: string
  actorAvatarUrl?: string
  resourceTitle?: string
}

export const notificationApi = {
  list(page = 1, size = 15) {
    return request.get<ApiResponse<PageData<NotificationItem>>>('/notifications', { params: { page, size } })
  },
  unreadCount() {
    return request.get<ApiResponse<number>>('/notifications/unread-count')
  },
  markRead(id: number) {
    return request.put<ApiResponse<void>>(`/notifications/${id}/read`)
  },
  markAllRead() {
    return request.put<ApiResponse<void>>('/notifications/read-all')
  },
}
