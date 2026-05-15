import request from './request'
import type { ApiResponse, PageData, Comment } from '@/types'

export const commentApi = {
  getList(resourceId: number, page = 1, size = 20) {
    return request.get<ApiResponse<PageData<Comment>>>(`/resources/${resourceId}/comments`, {
      params: { page, size },
    })
  },
  create(resourceId: number, content: string, parentId?: number) {
    return request.post<ApiResponse<Comment>>(`/resources/${resourceId}/comments`, {
      content,
      parentId: parentId || null,
    })
  },
  delete(id: number) {
    return request.delete(`/comments/${id}`)
  },
  like(id: number) {
    return request.post<ApiResponse<{ liked: boolean; likeCount: number }>>(`/comments/${id}/like`)
  },
}
