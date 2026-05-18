import request from './request'
import type { ApiResponse, PageData, Resource, SearchParams } from '@/types'

export const resourceApi = {
  getList(params: SearchParams) {
    return request.get<ApiResponse<PageData<Resource>>>('/resources', { params })
  },
  getDetail(id: number) {
    return request.get<ApiResponse<Resource>>(`/resources/${id}`)
  },
  create(formData: FormData) {
    return request.post<ApiResponse<Resource>>('/resources', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  update(id: number, formData: FormData) {
    return request.put<ApiResponse<Resource>>(`/resources/${id}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  delete(id: number) {
    return request.delete(`/resources/${id}`)
  },
  getHot() {
    return request.get<ApiResponse<Resource[]>>('/resources/hot')
  },
  getLatest() {
    return request.get<ApiResponse<Resource[]>>('/resources/latest')
  },
  like(id: number) {
    return request.post<ApiResponse<{ liked: boolean; likeCount: number }>>(`/resources/${id}/like`)
  },
  favorite(id: number) {
    return request.post<ApiResponse<{ favorited: boolean; favoriteCount: number }>>(`/resources/${id}/favorite`)
  },
  rate(id: number, score: number) {
    return request.post<ApiResponse<{ myRating: number; avgRating: number; ratingCount: number }>>(
      `/resources/${id}/rating`,
      { score }
    )
  },
  getInteractions(id: number) {
    return request.get<ApiResponse<{ liked: boolean; favorited: boolean; myRating?: number }>>(`/resources/${id}/interactions`)
  },
}
