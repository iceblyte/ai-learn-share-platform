<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { resourceApi } from '@/api/resource'
import type { Resource } from '@/types'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const activeTab = ref('favorites')
const favorites = ref<Resource[]>([])
const myResources = ref<Resource[]>([])

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  await userStore.fetchUserInfo()
  loadFavorites()
})

async function loadFavorites() {
  activeTab.value = 'favorites'
  // This would call a dedicated API endpoint
}

async function loadMyResources() {
  activeTab.value = 'published'
  // This would call a dedicated API endpoint
}
</script>

<template>
  <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8" v-if="userStore.userInfo">
    <!-- Profile Card -->
    <div class="card p-6 mb-8">
      <div class="flex items-center gap-6">
        <img :src="userStore.userInfo.avatar || '/default-avatar.png'" class="w-20 h-20 rounded-full" alt="" />
        <div>
          <h1 class="text-2xl font-bold text-slate-800">{{ userStore.userInfo.nickname }}</h1>
          <p class="text-slate-500">@{{ userStore.userInfo.username }}</p>
          <p v-if="userStore.userInfo.bio" class="text-sm text-slate-600 mt-1">{{ userStore.userInfo.bio }}</p>
          <div class="flex items-center gap-4 mt-2 text-sm text-slate-400">
            <span>积分: {{ userStore.userInfo.points }}</span>
            <span>角色: {{ userStore.userInfo.role === 'ADMIN' ? '管理员' : userStore.userInfo.role === 'PUBLISHER' ? '发布者' : '用户' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex gap-4 mb-6 border-b border-slate-200">
      <button
        @click="activeTab = 'favorites'"
        :class="['pb-3 px-1 text-sm font-medium border-b-2 transition-colors', activeTab === 'favorites' ? 'border-primary-600 text-primary-600' : 'border-transparent text-slate-500 hover:text-slate-700']"
      >
        我的收藏
      </button>
      <button
        @click="activeTab = 'published'"
        :class="['pb-3 px-1 text-sm font-medium border-b-2 transition-colors', activeTab === 'published' ? 'border-primary-600 text-primary-600' : 'border-transparent text-slate-500 hover:text-slate-700']"
      >
        我的发布
      </button>
    </div>

    <!-- Content -->
    <div class="text-center py-12 text-slate-400">
      <p>{{ activeTab === 'favorites' ? '暂无收藏的资源' : '暂无发布的资源' }}</p>
    </div>
  </div>
</template>
