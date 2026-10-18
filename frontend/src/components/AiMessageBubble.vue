<script setup lang="ts">
/**
 * AI 消息气泡：用户消息靠右（头像在右），助手消息 markdown 渲染靠左
 * （品牌头像在左），并在回答上方展示"调用 xx 工具"状态（SSE tool 事件）。
 * 智能搜索来源：回答下方来源条，点击展开来源列表；sidePanel 模式下
 * 由父页面打开右侧来源面板（emit viewSources）。
 */
import { computed, ref } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { useUserStore } from '@/store/user'
import AiAvatarMark from '@/components/AiAvatarMark.vue'
import type { ChatMessage } from '@/composables/useAiChat'

const props = defineProps<{
  message: ChatMessage
  /** 全屏对话页模式：点击"查看来源"交给父组件开右侧面板 */
  sidePanel?: boolean
}>()

const emit = defineEmits<{
  viewSources: [sources: ChatMessage['sources']]
}>()

const userStore = useUserStore()

const expanded = ref(false)

const TOOL_LABELS: Record<string, string> = {
  search_resources: '搜索资源库',
  get_resource_detail: '查询资源详情',
  revise_content: '修订回答内容',
  web_search: '联网搜索',
}

const html = computed(() => (props.message.role === 'assistant' ? renderMarkdown(props.message.content) : ''))

const moderatedNotice = '该内容涉嫌违规，无法显示'

const toolLabel = (name: string) => TOOL_LABELS[name] || name

const hasSources = computed(() => !!props.message.sources?.length)

function onSourcesClick() {
  if (props.sidePanel) {
    emit('viewSources', props.message.sources)
  } else {
    expanded.value = !expanded.value
  }
}

function hostOf(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

const userInitial = computed(
  () =>
    userStore.userInfo?.nickname?.[0] ||
    userStore.userInfo?.username?.[0] ||
    '我'
)
</script>

<template>
  <div :class="message.role === 'user' ? 'flex justify-end gap-2.5' : 'flex justify-start gap-2.5'">
    <!-- 助手头像（星辉机器人） -->
    <AiAvatarMark v-if="message.role === 'assistant'" class="h-8 w-8 shrink-0" />

    <div
      :class="[
        'max-w-[85%] rounded-2xl px-3 py-2 text-sm leading-6 shadow-sm',
        message.role === 'user'
          ? 'bg-primary-600 text-white'
          : 'border border-slate-200 bg-white text-slate-700'
      ]"
    >
      <!-- 工具调用状态（仅助手消息） -->
      <div v-if="message.toolSteps?.length" class="mb-2 space-y-1">
        <div
          v-for="(tool, index) in message.toolSteps"
          :key="`${tool}-${index}`"
          class="flex items-center gap-1.5 rounded-lg bg-slate-50 px-2 py-1 text-[11px] text-slate-500"
        >
          <svg
            class="h-3 w-3 shrink-0"
            :class="message.streaming && index === message.toolSteps.length - 1 && !message.content ? 'animate-spin' : ''"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
          <span>调用 {{ toolLabel(tool) }}</span>
        </div>
      </div>

      <!-- 违规撤回占位：正文已清空，展示统一提示样式 -->
      <div v-if="message.moderated" class="flex items-center gap-2 py-1 text-slate-400">
        <svg class="h-4 w-4 shrink-0 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
        </svg>
        <span>{{ message.content || moderatedNotice }}</span>
      </div>
      <!-- 服务中断占位：历史消息里后端占位未回填（回答完成前服务中断） -->
      <div v-else-if="message.interrupted" class="flex items-center gap-2 py-1 text-slate-400">
        <svg class="h-4 w-4 shrink-0 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
        </svg>
        <span>该回答因服务中断未完成，请重新提问</span>
      </div>
      <!-- 用户消息纯文本；助手消息 markdown 渲染（撤回时不渲染正文） -->
      <p v-else-if="message.role === 'user'" class="whitespace-pre-wrap break-words">{{ message.content }}</p>
      <!-- eslint-disable-next-line vue/no-v-html —— 内容已经 DOMPurify 净化 -->
      <div v-else class="markdown-body break-words" v-html="html"></div>

      <!-- 联网搜索来源条（智能搜索开启且有结果时） -->
      <div v-if="hasSources" class="mt-2 border-t border-slate-100 pt-2">
        <button
          class="flex items-center gap-1.5 text-[11px] text-primary-600 hover:text-primary-700"
          @click="onSourcesClick"
        >
          <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
          <span>已搜索 {{ message.sources!.length }} 个网页 · 查看来源</span>
          <svg v-if="!sidePanel" class="h-3 w-3 transition-transform" :class="expanded ? 'rotate-180' : ''" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
          </svg>
        </button>
        <!-- 内联展开来源列表（悬浮窗 / 未开启侧栏模式） -->
        <div v-if="expanded && !sidePanel" class="mt-2 space-y-1.5">
          <a
            v-for="(source, index) in message.sources"
            :key="`${source.url}-${index}`"
            :href="source.url"
            target="_blank"
            rel="noopener noreferrer"
            class="block rounded-lg border border-slate-100 bg-slate-50 px-2.5 py-2 hover:border-primary-200 hover:bg-primary-50 transition-colors"
          >
            <p class="text-xs font-medium text-slate-700 truncate">[{{ index + 1 }}] {{ source.title }}</p>
            <p class="text-[11px] text-primary-500 truncate">{{ hostOf(source.url) }}</p>
          </a>
        </div>
      </div>

      <!-- 配图条（工作流 imageCollect 产出 / 历史快照）：不进正文，正文下方横向排布 -->
      <div v-if="message.images?.length" class="mt-2 flex flex-wrap gap-2">
        <a
          v-for="(image, index) in message.images"
          :key="`${image.url}-${index}`"
          :href="image.url"
          target="_blank"
          rel="noopener noreferrer"
          class="group relative block h-20 w-28 overflow-hidden rounded-lg border border-slate-200 bg-slate-50"
          :title="image.alt"
        >
          <img
            :src="image.url"
            :alt="image.alt"
            loading="lazy"
            class="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
          />
          <span class="absolute bottom-0 right-0 rounded-tl-md bg-black/45 px-1 text-[10px] text-white">
            {{ image.source }}
          </span>
        </a>
      </div>

      <span v-if="message.streaming" class="mt-1 inline-block h-2 w-2 animate-pulse rounded-full bg-emerald-500"></span>
    </div>

    <!-- 用户头像（昵称首字） -->
    <div
      v-if="message.role === 'user'"
      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-100 text-xs font-medium text-primary-600"
    >
      {{ userInitial }}
    </div>
  </div>
</template>

<style scoped>
/* markdown 渲染的最小排版（Tailwind preflight 会重置默认样式） */
.markdown-body :deep(p) {
  margin: 0.25rem 0;
}
.markdown-body :deep(p:first-child) {
  margin-top: 0;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.375rem 0;
  padding-left: 1.25rem;
}
.markdown-body :deep(ul) {
  list-style: disc;
}
.markdown-body :deep(ol) {
  list-style: decimal;
}
.markdown-body :deep(li) {
  margin: 0.125rem 0;
}
.markdown-body :deep(code) {
  background: #f1f5f9;
  border-radius: 0.25rem;
  padding: 0.1rem 0.3rem;
  font-size: 0.8125rem;
}
.markdown-body :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 0.5rem;
  padding: 0.625rem 0.75rem;
  overflow-x: auto;
  margin: 0.375rem 0;
}
.markdown-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: 0.8125rem;
}
.markdown-body :deep(strong) {
  font-weight: 600;
  color: inherit;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding-left: 0.625rem;
  color: #64748b;
  margin: 0.375rem 0;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.375rem 0;
  font-size: 0.8125rem;
  display: block;
  overflow-x: auto;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 0.25rem 0.5rem;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  font-weight: 600;
  margin: 0.5rem 0 0.25rem;
  font-size: 0.9375rem;
}
.markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}
.markdown-body :deep(hr) {
  border-color: #e2e8f0;
  margin: 0.5rem 0;
}
</style>
