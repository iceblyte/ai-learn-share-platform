<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { pageContextStore, setSelectedText } from '@/store/pageContext'
import { chatApi, type ChatSessionItem } from '@/api/chat'

type ChatRole = 'user' | 'assistant'

interface ChatMessage {
  id: number
  role: ChatRole
  content: string
  streaming?: boolean
}

const route = useRoute()
const userStore = useUserStore()
const open = ref(false)
const sending = ref(false)
const input = ref('')
const position = ref({ x: 0, y: 0 })
const dragging = ref(false)
const messages = ref<ChatMessage[]>([
  {
    id: 1,
    role: 'assistant',
    content: '我是学习助手。可以问我资源内容、学习路径、面试题思路，或者当前页面相关的问题。',
  },
])
const messageViewport = ref<HTMLElement | null>(null)
const chatPanel = ref<HTMLElement | null>(null)
let messageId = 2
let dragOffsetX = 0
let dragOffsetY = 0
let dragMoved = false

// ---- 扩展二：会话持久化状态 ----
const currentSessionId = ref<number | null>(null)
const showHistory = ref(false)
const sessions = ref<ChatSessionItem[]>([])
const historyLoading = ref(false)
const searchKeyword = ref('')
const searching = ref(false)
const searchResults = ref<{ sessionId: number; sessionTitle: string; content: string }[]>([])

// ---- 扩展四：游标翻页状态 ----
const loadingOlder = ref(false)
const hasOlderMessages = ref(false)
const oldestCursor = ref<number | null>(null)
let suppressAutoScroll = false

onMounted(() => {
  const width = window.innerWidth
  const height = window.innerHeight
  position.value = {
    x: Math.max(16, width - 80),
    y: Math.max(16, height - 80),
  }
})

onBeforeUnmount(() => {
  stopDrag()
})

const pageTitle = computed(() => document.title.replace(/\s*-\s*AI学习平台$/, '').trim())
// 扩展一：仅在资源详情页携带上下文（离开页面时 ResourceDetail 会清理）
const hasResourceContext = computed(
  () => route.name === 'ResourceDetail' && pageContextStore.type === 'RESOURCE'
)
const selectedTextPreview = computed(() => {
  const text = pageContextStore.selectedText
  if (!text) return ''
  return text.length > 24 ? `${text.slice(0, 24)}…` : text
})
const suggestions = computed(() => {
  if (route.name === 'ResourceDetail') {
    return ['帮我概括这份资源适合谁', '给我一个学习顺序', '提炼这份资源的重点']
  }
  if (route.name === 'Search') {
    return ['怎么更快找到高分资源', '帮我规划 Java 并发学习路径', '推荐适合面试冲刺的内容']
  }
  return ['帮我制定一周学习计划', '推荐适合入门的资源方向', '解释一个难点给我听']
})

watch(open, async (value) => {
  if (value) {
    await nextTick()
    scrollToBottom()
  }
})

watch(messages, async () => {
  await nextTick()
  // 加载更早消息时保持视口停留原位，不自动滚底
  if (suppressAutoScroll) return
  scrollToBottom()
}, { deep: true })

function scrollToBottom() {
  if (messageViewport.value) {
    messageViewport.value.scrollTop = messageViewport.value.scrollHeight
  }
}

function useSuggestion(text: string) {
  input.value = text
  sendMessage()
}

/** 扩展一：划词提问——把当前选中文本作为 selectedText 一并上传 */
function askSelectedText() {
  if (!pageContextStore.selectedText || sending.value) return
  input.value = '请解释我选中的这段内容'
  sendMessage()
}

function startDrag(event: PointerEvent) {
  dragging.value = true
  dragMoved = false
  dragOffsetX = event.clientX - position.value.x
  dragOffsetY = event.clientY - position.value.y
  window.addEventListener('pointermove', handleDrag)
  window.addEventListener('pointerup', stopDrag)
}

function handleDrag(event: PointerEvent) {
  if (!dragging.value) return
  dragMoved = true
  const panelWidth = open.value ? Math.min(384, window.innerWidth - 32) : 56
  const panelHeight = open.value && chatPanel.value ? chatPanel.value.offsetHeight + 16 : 56
  position.value = {
    x: clamp(event.clientX - dragOffsetX, 16, window.innerWidth - panelWidth - 16),
    y: clamp(event.clientY - dragOffsetY, 16, window.innerHeight - panelHeight - 16),
  }
}

function stopDrag() {
  dragging.value = false
  window.removeEventListener('pointermove', handleDrag)
  window.removeEventListener('pointerup', stopDrag)
}

function toggleOpen() {
  if (dragMoved) {
    dragMoved = false
    return
  }
  open.value = !open.value
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

// ---- 扩展二：会话管理 ----

async function toggleHistory() {
  showHistory.value = !showHistory.value
  if (showHistory.value) {
    await loadSessions()
  }
}

async function loadSessions() {
  historyLoading.value = true
  try {
    const res = await chatApi.listSessions(1, 20)
    sessions.value = res.data.data.records
  } catch {
    sessions.value = []
  } finally {
    historyLoading.value = false
  }
}

/**
 * 扩展四：进入会话加载最新一页（游标分页，后端按 id 倒序、前端转回正序）。
 * 保留真实消息 id 作为向上翻页的游标；本地新消息的 id 从最大真实 id 之后分配，保证 key 唯一。
 */
async function loadSessionMessages(sessionId: number) {
  const res = await chatApi.listMessagesByCursor(sessionId, null, 20)
  const { records, nextCursor, hasMore } = res.data.data
  const ascending = [...records].reverse()
  const restored: ChatMessage[] = ascending.map((item) => ({
    id: item.id,
    role: item.role,
    content: item.content,
  }))
  messages.value = restored.length
    ? restored
    : [{ id: 1, role: 'assistant', content: '这个会话还没有消息。' }]
  messageId = restored.length
    ? Math.max(...restored.map((item) => item.id)) + 1
    : 2
  oldestCursor.value = nextCursor
  hasOlderMessages.value = hasMore
}

/**
 * 扩展四：向上翻更早消息——传上一页 nextCursor 作为 beforeId，无 offset。
 * 前插后把视口锚定回原位置，避免跳到底部。
 */
async function loadOlderMessages() {
  if (!currentSessionId.value || !hasOlderMessages.value || loadingOlder.value || !oldestCursor.value) return
  loadingOlder.value = true
  suppressAutoScroll = true
  const viewport = messageViewport.value
  const prevScrollHeight = viewport?.scrollHeight ?? 0
  const prevScrollTop = viewport?.scrollTop ?? 0
  try {
    const res = await chatApi.listMessagesByCursor(currentSessionId.value, oldestCursor.value, 20)
    const { records, nextCursor, hasMore } = res.data.data
    const ascending = [...records].reverse()
    const older: ChatMessage[] = ascending.map((item) => ({
      id: item.id,
      role: item.role,
      content: item.content,
    }))
    messages.value = [...older, ...messages.value]
    oldestCursor.value = nextCursor
    hasOlderMessages.value = hasMore
    await nextTick()
    if (viewport) {
      viewport.scrollTop = viewport.scrollHeight - prevScrollHeight + prevScrollTop
    }
  } catch {
    // 静默失败：翻页失败不影响已展示的消息
  } finally {
    loadingOlder.value = false
    suppressAutoScroll = false
  }
}

async function openSession(sessionId: number) {
  try {
    await loadSessionMessages(sessionId)
    currentSessionId.value = sessionId
    showHistory.value = false
    input.value = ''
  } catch (error: any) {
    messages.value = [
      { id: messageId++, role: 'assistant', content: error?.message || '加载历史会话失败' },
    ]
  }
}

async function startNewSession() {
  currentSessionId.value = null
  showHistory.value = false
  oldestCursor.value = null
  hasOlderMessages.value = false
  messages.value = [
    {
      id: messageId++,
      role: 'assistant',
      content: '我是学习助手。可以问我资源内容、学习路径、面试题思路，或者当前页面相关的问题。',
    },
  ]
  input.value = ''
}

async function removeSession(sessionId: number) {
  try {
    await chatApi.deleteSession(sessionId)
    sessions.value = sessions.value.filter((item) => item.id !== sessionId)
    if (currentSessionId.value === sessionId) {
      await startNewSession()
    }
  } catch (error: any) {
    messages.value = [
      { id: messageId++, role: 'assistant', content: error?.message || '删除会话失败' },
    ]
  }
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
  try {
    const contextType = hasResourceContext.value ? 'RESOURCE' : undefined
    const contextId = hasResourceContext.value ? pageContextStore.resourceId ?? undefined : undefined
    const res = await chatApi.createSession(contextType, contextId)
    currentSessionId.value = res.data.data.id
    return currentSessionId.value
  } catch (error: any) {
    messages.value = [
      { id: messageId++, role: 'assistant', content: error?.message || '创建会话失败' },
    ]
    return null
  }
}

async function sendMessage() {
  const question = input.value.trim()
  if (!question || sending.value) return

  if (!open.value) open.value = true

  messages.value.push({
    id: messageId++,
    role: 'user',
    content: question,
  })

  const assistantMessage: ChatMessage = {
    id: messageId++,
    role: 'assistant',
    content: '',
    streaming: true,
  }
  messages.value.push(assistantMessage)
  input.value = ''
  sending.value = true

  try {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }
    if (userStore.token) {
      headers.Authorization = `Bearer ${userStore.token}`
    }

    const payload: Record<string, unknown> = {
      message: question,
      route: route.fullPath,
      pageTitle: pageTitle.value,
    }
    if (hasResourceContext.value && pageContextStore.resourceId != null) {
      payload.pageContextType = 'RESOURCE'
      payload.pageContextId = pageContextStore.resourceId
    }
    // 划选文本只在本次提问携带，发送后立即清除
    if (pageContextStore.selectedText) {
      payload.selectedText = pageContextStore.selectedText
    }

    // 扩展二：登录用户写入持久会话；游客仍走匿名通道
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

    if (!response.ok || !response.body) {
      throw new Error('聊天服务暂时不可用')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      assistantMessage.content += decoder.decode(value, { stream: true })
      messages.value = [...messages.value]
    }
  } catch (error: any) {
    assistantMessage.content = error?.message || '聊天服务暂时不可用'
  } finally {
    assistantMessage.streaming = false
    if (!assistantMessage.content.trim()) {
      assistantMessage.content = '当前没有拿到有效回复，请换个问法再试。'
    }
    messages.value = [...messages.value]
    sending.value = false
    setSelectedText('')
  }
}
</script>

<template>
  <div
    class="fixed z-[60]"
    :style="{ left: `${position.x}px`, top: `${position.y}px` }"
  >
    <Transition
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0 translate-y-4 scale-95"
      enter-to-class="opacity-100 translate-y-0 scale-100"
      leave-active-class="transition-all duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0 scale-100"
      leave-to-class="opacity-0 translate-y-4 scale-95"
    >
      <section
        v-if="open"
        ref="chatPanel"
        class="mb-4 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl shadow-slate-900/12"
      >
        <header
          class="flex cursor-move items-center justify-between border-b border-slate-200 bg-slate-900 px-4 py-3 text-white"
          @pointerdown="startDrag"
        >
          <div class="min-w-0">
            <p class="text-sm font-semibold">AI 学习助手</p>
            <p class="truncate text-[11px] text-slate-300">{{ pageTitle }}</p>
          </div>
          <div class="flex shrink-0 items-center gap-1">
            <button
              v-if="userStore.isLoggedIn"
              class="rounded-md p-1 text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
              :title="showHistory ? '返回对话' : '历史会话'"
              @click="toggleHistory"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
            </button>
            <button
              class="rounded-md p-1 text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
              @click="open = false"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
        </header>

        <!-- 扩展二：历史会话抽屉（仅登录用户） -->
        <div v-if="showHistory && userStore.isLoggedIn" class="max-h-[26rem] overflow-y-auto bg-slate-50 px-4 py-3">
          <button
            class="mb-3 w-full rounded-xl border border-dashed border-slate-300 px-3 py-2 text-xs text-slate-500 transition hover:border-primary-300 hover:text-primary-600"
            @click="startNewSession"
          >
            ＋ 新对话
          </button>

          <div class="mb-3 flex gap-2">
            <input
              v-model="searchKeyword"
              placeholder="搜索问过的问题..."
              class="min-w-0 flex-1 rounded-lg border border-slate-300 px-2 py-1.5 text-xs outline-none focus:border-primary-400"
              @keydown.enter="runSearch"
            />
            <button
              class="rounded-lg bg-slate-800 px-2.5 py-1.5 text-xs text-white transition hover:bg-primary-600 disabled:bg-slate-300"
              :disabled="searching"
              @click="runSearch"
            >
              搜索
            </button>
          </div>

          <ul v-if="searchResults.length" class="mb-3 space-y-2">
            <li
              v-for="result in searchResults"
              :key="`${result.sessionId}-${result.content}`"
              class="cursor-pointer rounded-xl border border-slate-200 bg-white px-3 py-2 transition hover:border-primary-300"
              @click="openSession(result.sessionId)"
            >
              <p class="truncate text-xs font-medium text-slate-700">{{ result.content }}</p>
              <p class="truncate text-[11px] text-slate-400">{{ result.sessionTitle }}</p>
            </li>
          </ul>

          <p v-if="historyLoading" class="py-4 text-center text-xs text-slate-400">加载中...</p>
          <p v-else-if="!sessions.length" class="py-4 text-center text-xs text-slate-400">还没有会话记录，问第一个问题吧</p>
          <ul v-else class="space-y-2">
            <li
              v-for="session in sessions"
              :key="session.id"
              class="group flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 transition hover:border-primary-300"
              :class="{ 'border-primary-300 bg-primary-50': session.id === currentSessionId }"
            >
              <button class="min-w-0 flex-1 text-left" @click="openSession(session.id)">
                <p class="truncate text-xs font-medium text-slate-700">{{ session.title || '未命名会话' }}</p>
                <p class="text-[11px] text-slate-400">{{ formatTime(session.lastMessageAt) }} · {{ session.messageCount }} 条</p>
              </button>
              <button
                class="shrink-0 rounded p-1 text-slate-300 opacity-0 transition group-hover:opacity-100 hover:text-red-500"
                title="删除会话"
                @click.stop="removeSession(session.id)"
              >
                <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                </svg>
              </button>
            </li>
          </ul>
        </div>

        <!-- 对话视图 -->
        <template v-else>
          <div ref="messageViewport" class="max-h-[26rem] space-y-4 overflow-y-auto bg-slate-50 px-4 py-4">
            <!-- 扩展四：向上翻更早消息（游标分页，仅已登录且有更早历史时展示） -->
            <div v-if="currentSessionId && hasOlderMessages" class="flex justify-center">
              <button
                class="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-500 transition hover:border-primary-200 hover:text-primary-600 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="loadingOlder"
                @click="loadOlderMessages"
              >
                {{ loadingOlder ? '加载中...' : '加载更早的消息' }}
              </button>
            </div>
            <!-- 扩展二：游客转化提示 -->
            <p
              v-if="!userStore.isLoggedIn"
              class="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-center text-[11px] text-amber-700"
            >
              登录后可保存与复习对话记录
            </p>
            <div v-for="message in messages" :key="message.id" :class="message.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
              <div
                :class="[
                  'max-w-[85%] rounded-2xl px-3 py-2 text-sm leading-6 shadow-sm',
                  message.role === 'user'
                    ? 'bg-primary-600 text-white'
                    : 'border border-slate-200 bg-white text-slate-700'
                ]"
              >
                <p class="whitespace-pre-wrap break-words">{{ message.content }}</p>
                <span v-if="message.streaming" class="mt-1 inline-block h-2 w-2 animate-pulse rounded-full bg-emerald-500"></span>
              </div>
            </div>
          </div>

          <div class="border-t border-slate-200 bg-white px-4 py-3">
            <!-- 扩展一：划词提问入口 -->
            <div
              v-if="selectedTextPreview"
              class="mb-3 flex items-center justify-between gap-2 rounded-xl border border-primary-200 bg-primary-50 px-3 py-2"
            >
              <button
                class="min-w-0 flex-1 truncate text-left text-xs text-primary-700 transition hover:text-primary-800"
                :disabled="sending"
                title="解释这一段"
                @click="askSelectedText"
              >
                解释这一段：{{ selectedTextPreview }}
              </button>
              <button
                class="shrink-0 rounded p-0.5 text-slate-400 transition hover:bg-white hover:text-slate-600"
                title="清除选区"
                @click="setSelectedText('')"
              >
                <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>

            <div class="mb-3 flex flex-wrap gap-2">
              <button
                v-for="suggestion in suggestions"
                :key="suggestion"
                class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-600 transition-colors hover:border-primary-200 hover:bg-primary-50 hover:text-primary-700"
                @click="useSuggestion(suggestion)"
              >
                {{ suggestion }}
              </button>
            </div>

            <div class="flex items-end gap-2">
              <textarea
                v-model="input"
                rows="3"
                placeholder="输入你的问题..."
                class="min-h-[5.5rem] flex-1 resize-none rounded-2xl border border-slate-300 px-3 py-2 text-sm text-slate-700 outline-none transition focus:border-primary-400 focus:ring-2 focus:ring-primary-100"
                @keydown.enter.exact.prevent="sendMessage"
              ></textarea>
              <button
                :disabled="sending || !input.trim()"
                class="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-600 text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                @click="sendMessage"
              >
                <svg v-if="!sending" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-9.193-5.106A1 1 0 004 6.944v10.112a1 1 0 001.559.832l9.193-5.106a1 1 0 000-1.664z"/>
                </svg>
                <div v-else class="h-5 w-5 animate-spin rounded-full border-2 border-white/35 border-t-white"></div>
              </button>
            </div>
          </div>
        </template>
      </section>
    </Transition>

    <button
      class="group flex h-14 w-14 cursor-move items-center justify-center rounded-2xl bg-slate-900 text-white shadow-xl shadow-slate-900/20 transition hover:-translate-y-0.5 hover:bg-primary-600"
      @pointerdown="startDrag"
      @click="toggleOpen"
    >
      <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M8 10h8M8 14h5m-9 6l2.4-3.2A2 2 0 017 16h10a2 2 0 002-2V6a2 2 0 00-2-2H7a2 2 0 00-2 2v14z"/>
      </svg>
    </button>
  </div>
</template>
