import { reactive } from 'vue'

/**
 * 页面上下文（扩展一）：当前页面正在展示的可提问内容。
 *
 * 只在客户端登记"我在看哪个资源 + 我划选了什么"——标题、摘要、正文
 * 一律由后端按 resourceId 二次校验并富化，避免客户端注入伪造正文。
 * 无上下文的页面保持 NONE，聊天行为与 V1 完全一致。
 */
export interface PageContextState {
  type: 'NONE' | 'RESOURCE'
  resourceId: number | null
  /** 用户在正文中划选的片段（限 500 字，与服务端校验一致） */
  selectedText: string
}

export const SELECTED_TEXT_LIMIT = 500

export const pageContextStore = reactive<PageContextState>({
  type: 'NONE',
  resourceId: null,
  selectedText: '',
})

export function setResourcePageContext(resourceId: number) {
  pageContextStore.type = 'RESOURCE'
  pageContextStore.resourceId = resourceId
}

export function clearPageContext() {
  pageContextStore.type = 'NONE'
  pageContextStore.resourceId = null
  pageContextStore.selectedText = ''
}

export function setSelectedText(text: string) {
  const trimmed = text.trim()
  pageContextStore.selectedText = trimmed.slice(0, SELECTED_TEXT_LIMIT)
}
