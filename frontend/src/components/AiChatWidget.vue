<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { pageContextStore, setSelectedText } from '@/store/pageContext'
import { useAiChat } from '@/composables/useAiChat'
import AiMessageBubble from '@/components/AiMessageBubble.vue'
import ChatSessionList from '@/components/ChatSessionList.vue'
import AiAvatarMark from '@/components/AiAvatarMark.vue'

const route = useRoute()
const userStore = useUserStore()
const open = ref(false)
const position = ref({ x: 0, y: 0 })
const dragging = ref(false)
const messageViewport = ref<HTMLElement | null>(null)
const chatPanel = ref<HTMLElement | null>(null)
let dragOffsetX = 0
let dragOffsetY = 0
let dragMoved = false

// ---- Bug 1：记录展开前的位置，关闭时还原（贴边展开再关闭能回到边缘） ----
let preOpenPosition: { x: number; y: number } | null = null

// ---- 聊天核心（会话/消息/流式）----
const chat = useAiChat({
  welcome: '我是学习助手。可以问我资源内容、学习路径、面试题思路，或者当前页面相关的问题。',
  getPayloadContext: () => ({
    pageContextType: hasResourceContext.value ? 'RESOURCE' : undefined,
    pageContextId: hasResourceContext.value ? pageContextStore.resourceId ?? undefined : undefined,
    selectedText: pageContextStore.selectedText || undefined,
  }),
})
const {
  messages,
  sending,
  input,
  currentSessionId,
  sendMessage,
  loadingOlder,
  hasOlderMessages,
  fetchOlderMessages,
  loadSessions,
  openSession,
} = chat

// 历史会话抽屉开关（仅悬浮球视图使用）
const showHistoryDrawer = ref(false)

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

// 扩展一：仅在资源详情页携带上下文（离开页面时 ResourceDetail 会清理）
const hasResourceContext = computed(
  () => route.name === 'ResourceDetail' && pageContextStore.type === 'RESOURCE'
)
const selectedTextPreview = computed(() => {
  const text = pageContextStore.selectedText
  if (!text) return ''
  return text.length > 24 ? `${text.slice(0, 24)}…` : text
})
const pageTitle = computed(() => document.title.replace(/\s*-\s*AI学习平台$/, '').trim())
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

// ---- 仅当用户停留在底部附近时才自动滚动，上滑翻历史时不打断 ----
let autoScrollEnabled = true
let suppressAutoScroll = false

function handleViewportScroll() {
  const el = messageViewport.value
  if (!el) return
  autoScrollEnabled = el.scrollHeight - el.scrollTop - el.clientHeight < 48
}

watch(messages, async () => {
  await nextTick()
  // 加载更早消息时保持视口停留原位，不自动滚底
  if (suppressAutoScroll) return
  if (autoScrollEnabled) scrollToBottom()
}, { deep: true })

function scrollToBottom() {
  if (messageViewport.value) {
    messageViewport.value.scrollTop = messageViewport.value.scrollHeight
  }
}

function useSuggestion(text: string) {
  sendMessage(text)
}

/** 扩展一：划词提问——把当前选中文本作为 selectedText 一并上传 */
function askSelectedText() {
  if (!pageContextStore.selectedText || sending.value) return
  sendMessage('请解释我选中的这段内容')
}

/** 提问时若面板未展开先正常展开（走 openPanel，保证关闭能还原位置） */
function sendFromInput() {
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
  position.value = {
    x: event.clientX - dragOffsetX,
    y: event.clientY - dragOffsetY,
  }
  clampWidgetToViewport()
}

function stopDrag() {
  dragging.value = false
  window.removeEventListener('pointermove', handleDrag)
  window.removeEventListener('pointerup', stopDrag)
}

async function toggleOpen() {
  if (dragMoved) {
    dragMoved = false
    return
  }
  if (open.value) {
    closePanel()
  } else {
    openPanel()
  }
}

function openPanel() {
  if (open.value) return
  open.value = true
  // Bug 1：记住展开前的位置，关闭时还原（贴边打开→关闭回到边缘）
  preOpenPosition = { ...position.value }
  // 面板展开后整体尺寸变大：贴边（右/下边缘）时面板会超出屏幕，
  // 等面板渲染完拿到真实高度后重新夹取位置
  nextTick(() => clampWidgetToViewport())
}

function closePanel() {
  if (!open.value) return
  open.value = false
  if (preOpenPosition) {
    position.value = preOpenPosition
    preOpenPosition = null
  }
}

async function toggleHistory() {
  showHistoryDrawer.value = !showHistoryDrawer.value
  if (showHistoryDrawer.value) {
    await loadSessions()
  }
}

function handleOpenSession(sessionId: number) {
  openSession(sessionId).catch((error: any) => {
    messages.value = [
      { id: Date.now(), role: 'assistant', content: error?.message || '加载历史会话失败' },
    ]
  })
  showHistoryDrawer.value = false
}

/** 把组件（面板 + 按钮）整体夹回视口内，贴边时至少留 16px 边距 */
function clampWidgetToViewport() {
  const widgetWidth = open.value ? Math.min(384, window.innerWidth - 32) : 56
  const widgetHeight = (open.value && chatPanel.value ? chatPanel.value.offsetHeight : 0) + 16 + 56
  position.value = {
    x: clamp(position.value.x, 16, Math.max(16, window.innerWidth - widgetWidth - 16)),
    y: clamp(position.value.y, 16, Math.max(16, window.innerHeight - widgetHeight - 16)),
  }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

/** 向上翻更早消息：前插后把视口锚定回原位置，避免跳到底部 */
async function loadOlderAnchored() {
  const viewport = messageViewport.value
  const prevScrollHeight = viewport?.scrollHeight ?? 0
  const prevScrollTop = viewport?.scrollTop ?? 0
  suppressAutoScroll = true
  await fetchOlderMessages()
  await nextTick()
  if (viewport) {
    viewport.scrollTop = viewport.scrollHeight - prevScrollHeight + prevScrollTop
  }
  suppressAutoScroll = false
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
              :title="showHistoryDrawer ? '返回对话' : '历史会话'"
              @click="toggleHistory"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
            </button>
            <button
              class="rounded-md p-1 text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
              @click="closePanel"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
        </header>

        <!-- 历史会话抽屉（仅登录用户）：共用组件，含搜索/重命名/删除/分页 -->
        <div v-if="showHistoryDrawer && userStore.isLoggedIn" class="max-h-[26rem] overflow-y-auto bg-slate-50 px-4 py-3">
          <ChatSessionList
            :chat="chat"
            :current-session-id="currentSessionId"
            @open="handleOpenSession"
          />
        </div>

        <!-- 对话视图 -->
        <template v-else>
          <div
            ref="messageViewport"
            class="h-[26rem] space-y-4 overflow-y-auto bg-slate-50 px-4 py-4"
            @scroll="handleViewportScroll"
          >
            <!-- 向上翻更早消息（游标分页，仅已登录且有更早历史时展示） -->
            <div v-if="currentSessionId && hasOlderMessages" class="flex justify-center">
              <button
                class="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-500 transition hover:border-primary-200 hover:text-primary-600 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="loadingOlder"
                @click="loadOlderAnchored"
              >
                {{ loadingOlder ? '加载中...' : '加载更早的消息' }}
              </button>
            </div>
            <!-- 游客转化提示 -->
            <p
              v-if="!userStore.isLoggedIn"
              class="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-center text-[11px] text-amber-700"
            >
              登录后可保存与复习对话记录
            </p>
            <AiMessageBubble
              v-for="message in messages"
              :key="message.id"
              :message="message"
            />
          </div>

          <div class="border-t border-slate-200 bg-white px-4 py-3">
            <!-- 划词提问入口 -->
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
                @keydown.enter.exact.prevent="sendFromInput"
              ></textarea>
              <button
                :disabled="sending || !input.trim()"
                class="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-600 text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                @click="sendFromInput"
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
      class="group flex h-14 w-14 cursor-move items-center justify-center overflow-hidden rounded-2xl shadow-xl shadow-slate-900/20 transition hover:-translate-y-0.5"
      title="AI 学习助手"
      @pointerdown="startDrag"
      @click="toggleOpen"
    >
      <AiAvatarMark class="h-full w-full transition-transform duration-200 group-hover:scale-105" />
    </button>
  </div>
</template>
