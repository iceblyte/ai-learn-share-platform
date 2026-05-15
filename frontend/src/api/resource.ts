import request from './request'
import type { ApiResponse, PageData, Resource, ResourceCreateRequest, SearchParams } from '@/types'

export const resourceApi = {
  getList(params: SearchParams) {
    return request.get<ApiResponse<PageData<Resource>>>('/resources', { params })
  },
  getDetail(id: number) {
    return request.get<ApiResponse<Resource>>(`/resources/${id}`)
  },
  create(data: ResourceCreateRequest) {
    const formData = new FormData()
    formData.append('title', data.title)
    formData.append('categoryId', String(data.categoryId))
    formData.append('description', data.description)
    formData.append('resourceType', data.resourceType)
    data.tags.forEach(tag => formData.append('tags', tag))
    if (data.externalUrl) formData.append('externalUrl', data.externalUrl)
    if (data.files) data.files.forEach(file => formData.append('files', file))
    return request.post<ApiResponse<Resource>>('/resources', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  update(id: number, data: Partial<ResourceCreateRequest>) {
    return request.put<ApiResponse<Resource>>(`/resources/${id}`, data)
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
}
