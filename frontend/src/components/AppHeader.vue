<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const searchQuery = ref('')
const showUserMenu = ref(false)

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
          <div class="w-8 h-8 bg-primary-500 rounded-lg flex items-center justify-center">
            <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
            </svg>
          </div>
          <span class="text-lg font-bold text-slate-800 hidden sm:block">LearnShare AI</span>
        </router-link>

        <!-- Search Bar -->
        <div class="flex-1 max-w-xl mx-8">
          <div class="relative">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索学习资源，或输入自然语言描述..."
              class="w-full pl-10 pr-12 py-2 border border-slate-300 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              @keyup.enter="handleSearch"
            />
            <svg class="absolute left-3 top-2.5 w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
            </svg>
            <span class="absolute right-3 top-2 text-xs bg-primary-100 text-primary-600 px-2 py-0.5 rounded-full font-medium">AI</span>
          </div>
        </div>

        <!-- User Menu -->
        <div class="flex items-center gap-4">
          <router-link
            v-if="userStore.isLoggedIn && userStore.isPublisher"
            to="/publish"
            class="text-sm text-slate-600 hover:text-primary-500 font-medium"
          >
            发布资源
          </router-link>

          <template v-if="userStore.isLoggedIn">
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
