<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import type { FollowPageItem } from '@/types'

/**
 * 粉丝/关注分页列表（用户主页与个人中心共用）。
 * 登录用户可对列表中的人直接关注/取关（isFollowing 是 viewer 视角）。
 */
const props = defineProps<{
  userId: number
  mode: 'followers' | 'following'
}>()

const router = useRouter()
const userStore = useUserStore()

const items = ref<FollowPageItem[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(true)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const api = props.mode === 'followers' ? userApi.getFollowers : userApi.getFollowingPage
    const res = await api(props.userId, page.value, size)
    items.value = res.data.data.records
    total.value = res.data.data.total
  } catch {} finally {
    loading.value = false
  }
}

function goTo(p: number) {
  const totalPages = Math.max(1, Math.ceil(total.value / size))
  if (p < 1 || p > totalPages || p === page.value) return
  page.value = p
  load()
}

async function toggleFollow(item: FollowPageItem) {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  try {
    const res = await userApi.toggleFollow(item.id)
    item.isFollowing = res.data.data.following
  } catch {}
}

function goUserHome(id: number) {
  router.push(`/user/${id}`)
}
</script>

<template>
  <div>
    <div v-if="loading" class="flex justify-center py-10">
      <div class="animate-spin rounded-full h-7 w-7 border-b-2 border-primary-600"></div>
    </div>

    <div v-else-if="items.length === 0" class="text-center py-10 text-sm text-slate-400">
      {{ mode === 'followers' ? '还没有粉丝' : '还没有关注任何人' }}
    </div>

    <div v-else class="divide-y divide-slate-100 bg-white rounded-xl border border-slate-200 overflow-hidden">
      <div v-for="item in items" :key="item.id" class="flex items-center gap-3 p-4 hover:bg-slate-50">
        <div
          @click="goUserHome(item.id)"
          class="w-10 h-10 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0 cursor-pointer"
        >
          <img v-if="item.avatarUrl" :src="item.avatarUrl" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full bg-primary-100 flex items-center justify-center">
            <span class="text-sm font-medium text-primary-600">{{ item.nickname?.[0] || '用' }}</span>
          </div>
        </div>
        <div class="min-w-0 flex-1 cursor-pointer" @click="goUserHome(item.id)">
          <p class="text-sm font-medium text-slate-800 truncate">{{ item.nickname || '已注销用户' }}</p>
          <p class="text-xs text-slate-400 truncate">{{ item.bio || '这个人很懒，什么都没写' }}</p>
        </div>
        <button
          v-if="userStore.userInfo?.id !== item.id"
          @click="toggleFollow(item)"
          :class="[
            'px-3 py-1.5 text-xs rounded-lg border transition-colors flex-shrink-0',
            item.isFollowing
              ? 'border-slate-300 text-slate-600 hover:bg-slate-100'
              : 'border-primary-600 text-primary-600 hover:bg-primary-50',
          ]"
        >
          {{ item.isFollowing ? '已关注' : '关注' }}
        </button>
      </div>
    </div>

    <div v-if="total > size" class="flex items-center justify-center gap-3 mt-4">
      <button
        @click="goTo(page - 1)"
        :disabled="page <= 1"
        class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >上一页</button>
      <span class="text-sm text-slate-500">{{ page }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
      <button
        @click="goTo(page + 1)"
        :disabled="page >= Math.ceil(total / size)"
        class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >下一页</button>
    </div>
  </div>
</template>
