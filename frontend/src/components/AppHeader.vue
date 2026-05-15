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
  <header class="bg-white border-b border-slate-200 sticky top-0 z-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center justify-between h-16">
        <!-- Logo -->
        <router-link to="/" class="flex items-center space-x-2">
          <div class="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center">
            <span class="text-white font-bold text-sm">AI</span>
          </div>
          <span class="font-semibold text-lg text-slate-800 hidden sm:block">学习资源平台</span>
        </router-link>

        <!-- Search -->
        <div class="flex-1 max-w-xl mx-4">
          <div class="relative">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索资源... (支持自然语言)"
              class="input-field pl-10 pr-4"
              @keyup.enter="handleSearch"
            />
            <svg class="absolute left-3 top-2.5 h-5 w-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
        </div>

        <!-- Navigation -->
        <nav class="flex items-center space-x-4">
          <router-link to="/" class="text-slate-600 hover:text-primary-600 font-medium text-sm hidden md:block">首页</router-link>

          <template v-if="userStore.isLoggedIn">
            <router-link
              v-if="userStore.isPublisher"
              to="/publish"
              class="btn-primary text-sm"
            >
              发布资源
            </router-link>

            <!-- User Menu -->
            <div class="relative">
              <button
                @click="showUserMenu = !showUserMenu"
                class="flex items-center space-x-2 text-slate-600 hover:text-slate-800"
              >
                <img
                  :src="userStore.userInfo?.avatar || '/default-avatar.png'"
                  class="w-8 h-8 rounded-full object-cover"
                  alt="avatar"
                />
                <span class="text-sm font-medium hidden sm:block">{{ userStore.userInfo?.nickname }}</span>
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
            <router-link to="/register" class="btn-primary text-sm">注册</router-link>
          </template>
        </nav>
      </div>
    </div>
  </header>
</template>
