<script setup lang="ts">
/**
 * 评论 @ 提及选择器：@ 按钮 + 关注用户下拉列表（带搜索过滤）。
 * 选中后向父组件 emit 所选用户，由父组件把 "@昵称 " 插入输入框。
 */
import { ref, computed } from 'vue'
import type { FollowedUser } from '@/types'

const props = defineProps<{
  /** 我关注的用户列表（空列表时按钮隐藏） */
  users: FollowedUser[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [user: FollowedUser]
}>()

const open = ref(false)
const filter = ref('')

const filtered = computed(() => {
  const kw = filter.value.trim().toLowerCase()
  if (!kw) return props.users
  return props.users.filter(u => u.nickname.toLowerCase().includes(kw))
})

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
  <div v-if="users.length > 0" class="relative">
    <button
      @click="toggle"
      :disabled="disabled"
      title="提及我关注的人"
      class="px-2 py-1 text-xs rounded border border-slate-200 text-slate-500 hover:bg-primary-50 hover:text-primary-600 hover:border-primary-200 transition-colors disabled:opacity-50"
    >
      @ 提及
    </button>

    <!-- 下拉：搜索 + 关注用户列表 -->
    <div
      v-if="open"
      class="absolute bottom-full left-0 mb-2 w-64 bg-white rounded-xl shadow-lg border border-slate-200 z-20 overflow-hidden"
    >
      <div class="p-2 border-b border-slate-100">
        <input
          v-model="filter"
          placeholder="搜索关注的人..."
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
            v-else
            class="w-6 h-6 rounded-full bg-primary-100 flex items-center justify-center flex-shrink-0 text-[10px] text-primary-600"
          >{{ user.nickname?.[0] || '用' }}</span>
          <span class="text-sm text-slate-700 truncate">{{ user.nickname }}</span>
        </button>
        <p v-if="filtered.length === 0" class="px-3 py-4 text-xs text-slate-400 text-center">
          没有匹配的关注用户
        </p>
      </div>
    </div>
  </div>
</template>
