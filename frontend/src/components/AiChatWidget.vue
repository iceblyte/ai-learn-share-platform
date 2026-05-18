<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'

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

    const response = await fetch('/api/v1/ai/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        message: question,
        route: route.fullPath,
        pageTitle: pageTitle.value,
      }),
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
          <div>
            <p class="text-sm font-semibold">AI 学习助手</p>
            <p class="text-[11px] text-slate-300">{{ pageTitle }}</p>
          </div>
          <button
            class="rounded-md p-1 text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
            @click="open = false"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </header>

        <div ref="messageViewport" class="max-h-[26rem] space-y-4 overflow-y-auto bg-slate-50 px-4 py-4">
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
