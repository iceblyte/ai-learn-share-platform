<script setup lang="ts">
/**
 * 评论 @ 提及选择器：@ 按钮 + 关注用户下拉列表（带搜索过滤）。
 * AI 助手固定置顶展示（@ 它可让 AI 总结整篇资源）。
 * 选中后向父组件 emit 所选用户，由父组件把 "@昵称 " 插入输入框。
 */
import { ref, computed, onMounted } from 'vue'
import type { FollowedUser } from '@/types'
import { aiAssistant, ensureAiAssistant } from '@/utils/aiAssistant'

const props = defineProps<{
  /** 我关注的用户列表（与 AI 助手合并后为空列表时按钮隐藏） */
  users: FollowedUser[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [user: FollowedUser]
}>()

const open = ref(false)
const filter = ref('')

onMounted(ensureAiAssistant)

/** 关注列表 + AI 助手固定项（置顶，去重） */
const allUsers = computed<FollowedUser[]>(() => {
  const list = [...props.users]
  if (aiAssistant.value) {
    const { id, nickname } = aiAssistant.value
    if (!list.some(u => u.id === id)) {
      list.unshift({ id, nickname, avatarUrl: '' })
    }
  }
  return list
})

const filtered = computed(() => {
  const kw = filter.value.trim().toLowerCase()
  if (!kw) return allUsers.value
  return allUsers.value.filter(u => u.nickname.toLowerCase().includes(kw))
})

function isAi(user: FollowedUser) {
  return aiAssistant.value?.id === user.id
}

function toggle() {
  open.value = !open.value
  if (open.value) filter.value = ''
}

function pick(user: FollowedUser) {
  open.value = false
  emit('select', user)
}
</script>

<template>
  <div v-if="allUsers.length > 0 || aiAssistant" class="relative">
    <button
      @click="toggle"
      :disabled="disabled"
      title="提及我关注的人或 AI 助手"
      class="px-2 py-1 text-xs rounded border border-slate-200 text-slate-500 hover:bg-primary-50 hover:text-primary-600 hover:border-primary-200 transition-colors disabled:opacity-50"
    >
      @ 提及
    </button>

    <!-- 下拉：搜索 + AI 助手 + 关注用户列表 -->
    <div
      v-if="open"
      class="absolute bottom-full left-0 mb-2 w-64 bg-white rounded-xl shadow-lg border border-slate-200 z-20 overflow-hidden"
    >
      <div class="p-2 border-b border-slate-100">
        <input
          v-model="filter"
          placeholder="搜索要提及的人..."
          class="w-full px-2 py-1.5 text-xs border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-primary-400"
        />
      </div>
      <div class="max-h-48 overflow-y-auto">
        <button
          v-for="user in filtered"
          :key="user.id"
          @click="pick(user)"
          class="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-primary-50 transition-colors"
        >
          <img
            v-if="user.avatarUrl"
            :src="user.avatarUrl"
            class="w-6 h-6 rounded-full object-cover flex-shrink-0"
            alt="avatar"
          />
          <span
            v-else-if="isAi(user)"
            class="w-6 h-6 rounded-full bg-primary-500 flex items-center justify-center flex-shrink-0 text-white"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19.8 15.3M14.25 3.104c.251.023.501.05.75.082M19.8 15.3l-1.57.393A9.065 9.065 0 0112 15a9.065 9.065 0 00-6.23-.693L5 14.5m14.8.8l1.402 1.402c1.232 1.232.65 3.318-1.067 3.611A48.309 48.309 0 0112 21c-2.773 0-5.491-.235-8.135-.687-1.718-.293-2.3-2.379-1.067-3.61L5 14.5"/>
            </svg>
          </span>
          <span
            v-else
            class="w-6 h-6 rounded-full bg-primary-100 flex items-center justify-center flex-shrink-0 text-[10px] text-primary-600"
          >{{ user.nickname?.[0] || '用' }}</span>
          <span class="text-sm text-slate-700 truncate">{{ user.nickname }}</span>
          <span
            v-if="isAi(user)"
            class="ml-auto text-[10px] bg-primary-100 text-primary-600 px-1.5 py-0.5 rounded flex-shrink-0"
          >帮我总结</span>
        </button>
        <p v-if="filtered.length === 0" class="px-3 py-4 text-xs text-slate-400 text-center">
          没有匹配的用户
        </p>
      </div>
    </div>
  </div>
</template>
