import request from './request'
import type { ApiResponse, Category, Tag } from '@/types'

export const categoryApi = {
  getTree() {
    return request.get<ApiResponse<Category[]>>('/categories')
  },
  getDetail(id: number) {
    return request.get<ApiResponse<Category>>(`/categories/${id}`)
  },
  create(data: { name: string; parentId?: number }) {
    return request.post<ApiResponse<Category>>('/admin/categories', data)
  },
  update(id: number, data: { name: string }) {
    return request.put<ApiResponse<Category>>(`/admin/categories/${id}`, data)
  },
  delete(id: number) {
    return request.delete(`/admin/categories/${id}`)
  },
}

export const tagApi = {
  getList() {
    return request.get<ApiResponse<Tag[]>>('/tags')
  },
  getHot() {
    return request.get<ApiResponse<Tag[]>>('/tags/hot')
  },
  search(keyword: string) {
    return request.get<ApiResponse<Tag[]>>('/tags/search', { params: { keyword } })
  },
  create(data: { name: string }) {
    return request.post<ApiResponse<Tag>>('/admin/tags', data)
  },
  update(id: number, data: { name: string }) {
    return request.put<ApiResponse<Tag>>(`/admin/tags/${id}`, data)
  },
  delete(id: number) {
    return request.delete(`/admin/tags/${id}`)
  },
}
