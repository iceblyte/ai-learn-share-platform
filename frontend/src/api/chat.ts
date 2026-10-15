import request from './request'
import type { ApiResponse, PageData } from '@/types'

export interface ChatSessionItem {
  id: number
  userId: number
  title: string
  contextType: string
  contextId: number | null
  messageCount: number
  lastMessageAt: string | null
  createdAt: string
}

export interface ChatMessageItem {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  contextJson?: string | null
  tokensUsed: number
  latencyMs: number
  status: 'OK' | 'FAILED'
  createdAt: string
}

export interface ChatSearchItem {
  messageId: number
  sessionId: number
  sessionTitle: string
  role: string
  content: string
  createdAt: string
}

/** 对话持久化 API（扩展二）。流式继续提问走 fetch，不经过 axios。 */
export const chatApi = {
  createSession(contextType?: string, contextId?: number) {
    return request.post<ApiResponse<ChatSessionItem>>('/chat/sessions', {
      contextType,
      contextId,
    })
  },
  listSessions(page = 1, size = 20) {
    return request.get<ApiResponse<PageData<ChatSessionItem>>>('/chat/sessions', {
      params: { page, size },
    })
  },
  listMessages(sessionId: number, page = 1, size = 50) {
    return request.get<ApiResponse<PageData<ChatMessageItem>>>(
      `/chat/sessions/${sessionId}/messages`,
      { params: { page, size } }
    )
  },
  deleteSession(sessionId: number) {
    return request.delete<ApiResponse<void>>(`/chat/sessions/${sessionId}`)
  },
  search(keyword: string, limit = 20) {
    return request.get<ApiResponse<ChatSearchItem[]>>('/chat/messages/search', {
      params: { keyword, limit },
    })
  },
  exportMarkdown(sessionId: number) {
    return request.post<ApiResponse<{ markdown: string; filename: string }>>(
      `/chat/sessions/${sessionId}/export`
    )
  },
}
