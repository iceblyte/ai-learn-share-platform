<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

const menuItems = [
  {
    name: '仪表盘',
    path: '/admin',
    icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
  },
  {
    name: '用户管理',
    path: '/admin/users',
    icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z',
  },
  {
    name: '资源审核',
    path: '/admin/resources',
    icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  {
    name: '分类管理',
    path: '/admin/categories',
    icon: 'M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z',
  },
  {
    name: '标签管理',
    path: '/admin/tags',
    icon: 'M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z',
  },
]

function isActive(path: string) {
  if (path === '/admin') return route.path === '/admin'
  return route.path.startsWith(path)
}
</script>

<template>
  <div class="flex h-screen bg-slate-50">
    <!-- Dark Sidebar -->
    <aside class="w-64 bg-slate-900 text-white flex-shrink-0 flex flex-col">
      <!-- Logo -->
      <div class="h-16 flex items-center gap-2 px-6 border-b border-slate-800">
        <div class="w-8 h-8 bg-primary-500 rounded-lg flex items-center justify-center">
          <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
          </svg>
        </div>
        <span class="text-lg font-bold">管理后台</span>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          :class="[
            'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors',
            isActive(item.path)
              ? 'bg-primary-600 text-white font-medium'
              : 'text-slate-300 hover:bg-slate-800 hover:text-white'
          ]"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="item.icon"/>
          </svg>
          {{ item.name }}
        </router-link>
      </nav>

      <!-- User Info -->
      <div class="p-4 border-t border-slate-800">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 bg-red-500 rounded-full flex items-center justify-center">
            <span class="text-xs font-medium">{{ userStore.userInfo?.nickname?.[0] || '管' }}</span>
          </div>
          <div>
            <p class="text-sm font-medium">{{ userStore.userInfo?.nickname || '管理员' }}</p>
            <p class="text-xs text-slate-400">{{ userStore.userInfo?.username || 'admin' }}</p>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <div class="flex-1 overflow-y-auto">
      <!-- Top Bar -->
      <header class="h-16 bg-white border-b border-slate-200 flex items-center justify-between px-6 sticky top-0 z-10">
        <h1 class="text-lg font-semibold text-slate-800">
          {{ menuItems.find(m => isActive(m.path))?.name || '管理后台' }}
        </h1>
        <div class="flex items-center gap-4">
          <button class="relative p-2 text-slate-400 hover:text-slate-600">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
            </svg>
            <span class="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full"></span>
          </button>
          <router-link to="/" class="text-sm text-primary-500 hover:text-primary-600">返回前台</router-link>
        </div>
      </header>

      <!-- Page Content -->
      <div class="p-6">
        <router-view />
      </div>
    </div>
  </div>
</template>
