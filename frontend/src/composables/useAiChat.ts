/**
 * AI 聊天核心状态与流式逻辑（悬浮球 AiChatWidget 与全屏页 AiChat 共用）。
 *
 * <p>职责：会话列表（分页）、消息游标分页、SSE 流式发送（打字机缓冲 +
 * tool 事件收集 + done 收尾）、重命名/删除/搜索。视图层只负责渲染。</p>
 */
import { reactive, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { chatApi, type ChatSessionItem } from '@/api/chat'
import { setSelectedText } from '@/store/pageContext'
import { useRoute } from 'vue-router'

export type ChatRole = 'user' | 'assistant'

/** 回答配图（SSE images 事件 / 历史 images_json 快照） */
export interface AnswerImage {
  source: string
  url: string
  alt: string
}

/** 联网搜索来源（SSE sources 事件 / 历史 sources_json 快照） */
export interface WebSourceItem {
  title: string
  url: string
  snippet: string
}

export interface ChatMessage {
  id: number
  role: ChatRole
  content: string
  streaming?: boolean
  /** 本次回答中调用的工具名（SSE tool 事件，按顺序展示） */
  toolSteps?: string[]
  /** 回答配图（气泡下方图片条，不进正文） */
  images?: AnswerImage[]
  /** 联网搜索来源（回答下方来源条与来源面板） */
  sources?: WebSourceItem[]
  /** 回答因违规被撤回：正文清空，气泡渲染违规占位样式 */
  moderated?: boolean
}

/** 解析历史消息的 images_json 快照；非法数据返回 undefined（渲染不受影响） */
function parseImages(json: string | null | undefined): AnswerImage[] | undefined {
  if (!json) return undefined
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) && parsed.length ? (parsed as AnswerImage[]) : undefined
  } catch {
    return undefined
  }
}

/** 解析历史消息的 sources_json 快照；非法数据返回 undefined */
function parseSources(json: string | null | undefined): WebSourceItem[] | undefined {
  if (!json) return undefined
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) && parsed.length ? (parsed as WebSourceItem[]) : undefined
  } catch {
    return undefined
  }
}

export interface UseAiChatOptions {
  /** 返回随请求携带的页面上下文（悬浮球在资源页时提供，全屏页返回空） */
  getPayloadContext?: () => { pageContextType?: string; pageContextId?: number; selectedText?: string }
  /** 欢迎语 */
  welcome?: string
}

// 匀速上屏节奏：每 20ms 上屏 4 个字符（约 200 字/秒）
const REVEAL_INTERVAL_MS = 20
const REVEAL_BATCH = 4
const SESSION_PAGE_SIZE = 20

export function useAiChat(options: UseAiChatOptions = {}) {
  const route = useRoute()
  const userStore = useUserStore()

  // ---- 消息状态 ----
  const messages = ref<ChatMessage[]>([
    { id: 1, role: 'assistant', content: options.welcome || '我是学习助手，有什么可以帮你？' },
  ])
  let messageId = 2
  const sending = ref(false)
  const input = ref('')
  const currentSessionId = ref<number | null>(null)

  /** 智能搜索开关：开启后请求携带 webSearch=true，后端走 Tavily 联网检索并回传来源 */
  const webSearchEnabled = ref(false)

  // ---- 消息游标翻页 ----
  const loadingOlder = ref(false)
  const hasOlderMessages = ref(false)
  const oldestCursor = ref<number | null>(null)

  // ---- 会话列表（分页） ----
  const sessions = ref<ChatSessionItem[]>([])
  const sessionsTotal = ref(0)
  const historyLoading = ref(false)
  const searchKeyword = ref('')
  const searching = ref(false)
  const searchResults = ref<{ sessionId: number; sessionTitle: string; content: string }[]>([])
  const editingSessionId = ref<number | null>(null)
  const editingTitle = ref('')

  const hasMoreSessions = () => sessions.value.length < sessionsTotal.value

  async function loadSessions(append = false) {
    historyLoading.value = true
    try {
      const page = append ? Math.floor(sessions.value.length / SESSION_PAGE_SIZE) + 1 : 1
      const res = await chatApi.listSessions(page, SESSION_PAGE_SIZE)
      const data = res.data.data
      sessionsTotal.value = data.total
      sessions.value = append ? [...sessions.value, ...data.records] : data.records
    } catch {
      if (!append) sessions.value = []
    } finally {
      historyLoading.value = false
    }
  }

  async function loadMoreSessions() {
    if (historyLoading.value || !hasMoreSessions()) return
    await loadSessions(true)
  }

  async function loadSessionMessages(sessionId: number) {
    const res = await chatApi.listMessagesByCursor(sessionId, null, 20)
    const { records, nextCursor, hasMore } = res.data.data
    const ascending = [...records].reverse()
    const restored: ChatMessage[] = ascending.map((item) => ({
      id: item.id,
      role: item.role,
      content: item.content,
      images: parseImages(item.imagesJson),
      sources: parseSources(item.sourcesJson),
      moderated: item.status === 'MODERATED' || undefined,
    }))
    messages.value = restored.length
      ? restored
      : [{ id: 1, role: 'assistant', content: '这个会话还没有消息。' }]
    messageId = restored.length ? Math.max(...restored.map((item) => item.id)) + 1 : 2
    oldestCursor.value = nextCursor
    hasOlderMessages.value = hasMore
  }

  /**
   * 向上翻更早消息：前插消息并推进游标。
   * 视口锚定（避免跳底）由视图层在前插前后自行处理。
   */
  async function fetchOlderMessages(): Promise<boolean> {
    if (!currentSessionId.value || !hasOlderMessages.value || loadingOlder.value || !oldestCursor.value) return false
    loadingOlder.value = true
    try {
      const res = await chatApi.listMessagesByCursor(currentSessionId.value, oldestCursor.value, 20)
      const { records, nextCursor, hasMore } = res.data.data
      const older: ChatMessage[] = [...records].reverse().map((item) => ({
        id: item.id,
        role: item.role,
        content: item.content,
        images: parseImages(item.imagesJson),
        sources: parseSources(item.sourcesJson),
        moderated: item.status === 'MODERATED' || undefined,
      }))
      messages.value = [...older, ...messages.value]
      oldestCursor.value = nextCursor
      hasOlderMessages.value = hasMore
      return true
    } catch {
      return false
    } finally {
      loadingOlder.value = false
    }
  }

  async function openSession(sessionId: number) {
    await loadSessionMessages(sessionId)
    currentSessionId.value = sessionId
    input.value = ''
  }

  function startNewSession() {
    currentSessionId.value = null
    oldestCursor.value = null
    hasOlderMessages.value = false
    messages.value = [{ id: messageId++, role: 'assistant', content: options.welcome || '我是学习助手，有什么可以帮你？' }]
    input.value = ''
  }

  async function removeSession(sessionId: number) {
    await chatApi.deleteSession(sessionId)
    sessions.value = sessions.value.filter((item) => item.id !== sessionId)
    sessionsTotal.value = Math.max(0, sessionsTotal.value - 1)
    if (currentSessionId.value === sessionId) {
      startNewSession()
    }
  }

  function startRename(session: ChatSessionItem) {
    editingSessionId.value = session.id
    editingTitle.value = session.title || ''
  }

  async function saveRename(sessionId: number) {
    const title = editingTitle.value.trim()
    editingSessionId.value = null
    if (!title) return
    await chatApi.renameSession(sessionId, title)
    const target = sessions.value.find((item) => item.id === sessionId)
    if (target) target.title = title
  }

  async function runSearch() {
    const keyword = searchKeyword.value.trim()
    if (!keyword) {
      searchResults.value = []
      return
    }
    searching.value = true
    try {
      const res = await chatApi.search(keyword, 20)
      searchResults.value = res.data.data.map((item) => ({
        sessionId: item.sessionId,
        sessionTitle: item.sessionTitle,
        content: item.content,
      }))
    } catch {
      searchResults.value = []
    } finally {
      searching.value = false
    }
  }

  function formatTime(value: string | null) {
    if (!value) return ''
    return value.slice(0, 16).replace('T', ' ')
  }

  async function ensureSession(): Promise<number | null> {
    if (currentSessionId.value) return currentSessionId.value
    const context = options.getPayloadContext?.()
    const res = await chatApi.createSession(context?.pageContextType, context?.pageContextId)
    currentSessionId.value = res.data.data.id
    // 新会话即时进左侧列表（此时标题为空，首轮回答结束后再刷新一次）
    void loadSessions()
    return currentSessionId.value
  }

  async function sendMessage(questionOverride?: string) {
    const question = (questionOverride ?? input.value).trim()
    if (!question || sending.value) return

    messages.value.push({ id: messageId++, role: 'user', content: question })
    // 必须用 reactive 包裹：push 进数组后模板迭代拿到的是 proxy，
    // 打字机/tool 回调在闭包里持有的也是这个对象——直接改原始对象会
    // 绕过响应系统，子组件 AiMessageBubble 的 computed 永远拿到旧值，
    // 表现为"流式内容不显示、刷新后才出现"（数据实际已收到）。
    const assistantMessage = reactive<ChatMessage>({
      id: messageId++,
      role: 'assistant',
      content: '',
      streaming: true,
      toolSteps: [],
    })
    messages.value.push(assistantMessage)
    input.value = ''
    sending.value = true

    // ---- 打字机缓冲队列 ----
    // 网络分片到达疏密不均，先入缓冲再由定时器匀速上屏，保证同一速率逐步出现。
    let pendingText = ''
    let revealTimer: number | null = null
    let revealFinish: (() => void) | null = null
    let streamDone = false

    const stopReveal = () => {
      if (revealTimer != null) {
        clearInterval(revealTimer)
        revealTimer = null
      }
    }

    const startReveal = () => {
      if (revealTimer != null) return
      revealTimer = window.setInterval(() => {
        if (!pendingText) {
          stopReveal()
          if (streamDone) {
            const finish = revealFinish
            revealFinish = null
            finish?.()
          }
          return
        }
        assistantMessage.content += pendingText.slice(0, REVEAL_BATCH)
        pendingText = pendingText.slice(REVEAL_BATCH)
        messages.value = [...messages.value]
      }, REVEAL_INTERVAL_MS)
    }

    const waitForRevealDrain = () =>
      new Promise<void>((resolve) => {
        if (!pendingText) {
          resolve()
          return
        }
        revealFinish = resolve
      })

    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' }
      if (userStore.token) {
        headers.Authorization = `Bearer ${userStore.token}`
      }

      const context = options.getPayloadContext?.()
      const payload: Record<string, unknown> = {
        message: question,
        route: route.fullPath,
        pageTitle: document.title.replace(/\s*-\s*AI学习平台$/, '').trim(),
      }
      if (context?.pageContextType && context.pageContextId != null) {
        payload.pageContextType = context.pageContextType
        payload.pageContextId = context.pageContextId
      }
      if (context?.selectedText) {
        payload.selectedText = context.selectedText
      }
      if (webSearchEnabled.value) {
        payload.webSearch = true
      }

      // 登录用户写入持久会话；游客仍走匿名通道
      let url = '/api/v1/ai/chat/stream'
      if (userStore.isLoggedIn) {
        const sessionId = await ensureSession()
        if (!sessionId) return
        url = `/api/v1/chat/sessions/${sessionId}/messages`
      }

      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
      })

      // 业务异常（403/404/校验失败）在建连前以 JSON 返回
      const contentType = response.headers.get('content-type') || ''
      if (contentType.includes('application/json')) {
        let message = '聊天服务暂时不可用'
        try {
          const result = await response.json()
          if (result?.message) message = result.message
        } catch {
          // JSON 解析失败就用默认文案
        }
        throw new Error(message)
      }
      if (!response.ok || !response.body) {
        throw new Error('聊天服务暂时不可用')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      let streamError = ''

      const dispatchSseEvent = (eventName: string, data: string) => {
        if (eventName === 'message') {
          pendingText += data
          startReveal()
        } else if (eventName === 'tool') {
          // 工具开始执行：立即展示在状态区（不进正文）
          assistantMessage.toolSteps = [...(assistantMessage.toolSteps || []), data]
          messages.value = [...messages.value]
        } else if (eventName === 'reset') {
          // 工具调用前的中间轮过渡文字（如英文自言自语）不进正文：
          // 清空已上屏内容与打字机缓冲，等待最终回答重新流入
          pendingText = ''
          assistantMessage.content = ''
        } else if (eventName === 'images') {
          // 配图推送（工作流 imageCollect 节点）：气泡下方图片条，不进正文
          try {
            const parsed = JSON.parse(data)
            if (Array.isArray(parsed) && parsed.length) {
              assistantMessage.images = parsed as AnswerImage[]
              messages.value = [...messages.value]
            }
          } catch {
            // 非法 JSON 忽略，不影响正文
          }
        } else if (eventName === 'moderated') {
          // 违规撤回：清空已流出正文与打字机缓冲，气泡切换违规占位样式
          pendingText = ''
          assistantMessage.content = ''
          assistantMessage.moderated = true
          messages.value = [...messages.value]
        } else if (eventName === 'sources') {
          // 联网搜索来源（智能搜索）：回答下方来源条与来源面板数据
          const parsed = parseSources(data)
          if (parsed) {
            assistantMessage.sources = parsed
            messages.value = [...messages.value]
          }
        } else if (eventName === 'error') {
          streamError = data
        } else if (eventName === 'done') {
          // 收到结束事件即视为回答完毕：主动终止读循环
          streamDone = true
        }
      }

      const parseSseFrame = (raw: string) => {
        let eventName = 'message'
        const dataLines: string[] = []
        for (const line of raw.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).startsWith(' ') ? line.slice(6) : line.slice(5))
          }
        }
        dispatchSseEvent(eventName, dataLines.join('\n'))
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let separator: number
        while ((separator = buffer.indexOf('\n\n')) !== -1) {
          parseSseFrame(buffer.slice(0, separator))
          buffer = buffer.slice(separator + 2)
        }
        if (streamDone) break
      }
      if (streamDone) {
        reader.cancel().catch(() => {})
      }

      // 等缓冲里的剩余内容匀速上屏完毕，再判定流结束
      await waitForRevealDrain()
      stopReveal()

      if (streamError && !assistantMessage.content.trim()) {
        assistantMessage.content = streamError
      }
    } catch (error: any) {
      assistantMessage.content = error?.message || '聊天服务暂时不可用'
    } finally {
      stopReveal()
      assistantMessage.streaming = false
      if (!assistantMessage.moderated && !assistantMessage.content.trim()) {
        assistantMessage.content = '当前没有拿到有效回复，请换个问法再试。'
      }
      messages.value = [...messages.value]
      sending.value = false
      setSelectedText('')
      // 会话列表同步：首轮问答后标题/时间/排序已变化，免手动刷新页面
      if (userStore.isLoggedIn && currentSessionId.value) {
        void loadSessions()
      }
    }
  }

  return {
    // 消息
    messages,
    sending,
    input,
    currentSessionId,
    webSearchEnabled,
    sendMessage,
    // 消息游标翻页
    loadingOlder,
    hasOlderMessages,
    oldestCursor,
    fetchOlderMessages,
    // 会话
    sessions,
    sessionsTotal,
    hasMoreSessions,
    historyLoading,
    loadSessions,
    loadMoreSessions,
    openSession,
    startNewSession,
    removeSession,
    // 重命名
    editingSessionId,
    editingTitle,
    startRename,
    saveRename,
    // 搜索
    searchKeyword,
    searching,
    searchResults,
    runSearch,
    formatTime,
  }
}
