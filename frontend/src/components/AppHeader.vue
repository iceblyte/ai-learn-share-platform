<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { notificationApi } from '@/api/notification'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const searchQuery = ref('')
const showUserMenu = ref(false)

// 通知未读数：登录时每 30s 轮询一次，点击铃铛进入通知页
const unreadCount = ref(0)
let unreadTimer: ReturnType<typeof setInterval> | null = null

async function refreshUnread() {
  if (!userStore.isLoggedIn) {
    unreadCount.value = 0
    return
  }
  try {
    const res = await notificationApi.unreadCount()
    unreadCount.value = Number(res.data.data) || 0
  } catch {}
}

onMounted(() => {
  refreshUnread()
  unreadTimer = setInterval(refreshUnread, 30000)
})

onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
})

// 离开通知页时刷新未读数（已读操作后回来红点要消失）
watch(() => route.path, (p) => {
  if (p !== '/notifications') refreshUnread()
})

// 搜索页有自己的大搜索框，头部再放一个会重复
const onSearchPage = computed(() => route.path === '/search')

function handleSearch() {
  if (searchQuery.value.trim()) {
    router.push({ name: 'Search', query: { q: searchQuery.value.trim() } })
  }
}

function handleLogout() {
  userStore.logout()
  showUserMenu.value = false
  router.push('/')
}
</script>

<template>
  <header class="bg-white shadow-sm sticky top-0 z-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center justify-between h-16">
        <!-- Logo -->
        <router-link to="/" class="flex items-center gap-2">
          <BrandMark class="h-8 w-8" />
          <span class="text-lg font-bold text-slate-800 hidden sm:block">LearnShare AI</span>
        </router-link>

        <!-- Search Bar（搜索页隐藏，避免与页面内搜索框重复） -->
        <div v-if="!onSearchPage" class="flex-1 max-w-xl mx-8">
          <div class="relative">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索学习资源，或输入自然语言描述..."
              class="w-full pl-10 pr-12 py-2 border border-slate-300 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              @focus="router.push({ name: 'Search', query: searchQuery.trim() ? { q: searchQuery.trim() } : {} })"
              @keyup.enter="handleSearch"
            />
            <svg class="absolute left-3 top-2.5 w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
            </svg>
            <span class="absolute right-3 top-2 text-xs bg-primary-100 text-primary-600 px-2 py-0.5 rounded-full font-medium">AI</span>
          </div>
        </div>

        <!-- Quick Actions -->
        <div class="flex items-center gap-4">
          <router-link
            to="/ai-chat"
            class="inline-flex items-center gap-1.5 rounded-full border border-primary-200 bg-primary-50 px-3.5 py-1.5 text-sm font-medium text-primary-600 transition-colors hover:border-primary-300 hover:bg-primary-100"
            title="AI 对话"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h8M8 14h5m-9 6l2.4-3.2A2 2 0 017 16h10a2 2 0 002-2V6a2 2 0 00-2-2H7a2 2 0 00-2 2v14z"/>
            </svg>
            <span class="hidden sm:inline">AI 对话</span>
          </router-link>

          <router-link
            v-if="userStore.isLoggedIn && userStore.isPublisher"
            to="/publish"
            class="inline-flex items-center gap-1.5 rounded-full bg-primary-500 px-3.5 py-1.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-primary-600"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
            </svg>
            <span class="hidden sm:inline">发布资源</span>
          </router-link>

          <template v-if="userStore.isLoggedIn">
            <!-- 通知铃铛 -->
            <router-link to="/notifications" class="relative p-1.5 text-slate-500 hover:text-primary-600 transition-colors" title="通知">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1h6z"/>
              </svg>
              <span
                v-if="unreadCount > 0"
                class="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-red-500 text-white text-[10px] leading-4 text-center font-medium"
              >{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </router-link>
            <div class="relative">
              <button
                @click="showUserMenu = !showUserMenu"
                class="flex items-center gap-2"
              >
                <div class="w-8 h-8 bg-primary-100 rounded-full flex items-center justify-center overflow-hidden">
                  <img v-if="userStore.userInfo?.avatarUrl || userStore.userInfo?.avatar" :src="userStore.userInfo.avatarUrl || userStore.userInfo.avatar" class="w-full h-full object-cover" />
                  <span v-else class="text-sm font-medium text-primary-600">
                    {{ userStore.userInfo?.nickname?.[0] || userStore.userInfo?.username?.[0] || '用' }}
                  </span>
                </div>
                <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                </svg>
              </button>

              <!-- Dropdown -->
              <div
                v-if="showUserMenu"
                class="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-slate-200 py-1 z-50"
              >
                <router-link to="/profile" class="block px-4 py-2 text-sm text-slate-700 hover:bg-slate-50" @click="showUserMenu = false">
                  个人中心
                </router-link>
                <router-link
                  v-if="userStore.isAdmin"
                  to="/admin"
                  class="block px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
                  @click="showUserMenu = false"
                >
                  管理后台
                </router-link>
                <hr class="my-1 border-slate-200" />
                <button @click="handleLogout" class="block w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50">
                  退出登录
                </button>
              </div>
            </div>
          </template>

          <template v-else>
            <router-link to="/login" class="text-slate-600 hover:text-primary-600 font-medium text-sm">登录</router-link>
            <router-link to="/register" class="bg-primary-500 text-white px-4 py-2 rounded-full text-sm hover:bg-primary-600 transition-colors">注册</router-link>
          </template>
        </div>
      </div>
    </div>
  </header>
</template>
