<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { name: '仪表盘', path: '/admin', icon: '📊' },
  { name: '用户管理', path: '/admin/users', icon: '👥' },
  { name: '资源审核', path: '/admin/resources', icon: '📋' },
  { name: '分类管理', path: '/admin/categories', icon: '📂' },
  { name: '标签管理', path: '/admin/tags', icon: '🏷️' },
]
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="flex gap-8">
      <!-- Sidebar -->
      <aside class="w-56 flex-shrink-0">
        <div class="card p-4">
          <h2 class="font-semibold text-slate-700 mb-4">管理后台</h2>
          <nav class="space-y-1">
            <router-link
              v-for="item in menuItems"
              :key="item.path"
              :to="item.path"
              :class="['flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors',
                route.path === item.path ? 'bg-primary-50 text-primary-700' : 'text-slate-600 hover:bg-slate-50']"
            >
              <span>{{ item.icon }}</span>
              <span>{{ item.name }}</span>
            </router-link>
          </nav>
        </div>
      </aside>

      <!-- Content -->
      <div class="flex-1">
        <router-view />
      </div>
    </div>
  </div>
</template>
