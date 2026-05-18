<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'
import type { User } from '@/types'

const users = ref<User[]>([])
const page = ref(1)
const total = ref(0)
const pageSize = 20
const loading = ref(true)

onMounted(() => loadUsers())

async function loadUsers() {
  loading.value = true
  try {
    const res = await request.get('/admin/users', { params: { page: page.value, size: pageSize } })
    users.value = res.data.data.records
    total.value = res.data.data.total
  } catch {} finally {
    loading.value = false
  }
}

async function updateRole(id: number, role: string) {
  try {
    await request.put(`/admin/users/${id}/role`, { role })
    loadUsers()
  } catch {}
}

function nextPage() {
  if (page.value * pageSize < total.value) {
    page.value++
    loadUsers()
  }
}

function prevPage() {
  if (page.value > 1) {
    page.value--
    loadUsers()
  }
}

function getRoleLabel(role: string) {
  const map: Record<string, string> = { USER: '普通用户', PUBLISHER: '发布者', ADMIN: '管理员' }
  return map[role] || role
}

function getRoleClass(role: string) {
  const map: Record<string, string> = {
    ADMIN: 'bg-red-100 text-red-700',
    PUBLISHER: 'bg-blue-100 text-blue-700',
    USER: 'bg-slate-100 text-slate-600',
  }
  return map[role] || 'bg-slate-100 text-slate-600'
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-slate-800 mb-6">用户管理</h1>

    <div v-if="loading" class="flex justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else>
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <table class="w-full">
          <thead class="bg-slate-50">
            <tr>
              <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">ID</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">用户</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">邮箱</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">角色</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">积分</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">注册时间</th>
              <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="user in users" :key="user.id" class="hover:bg-slate-50">
              <td class="px-4 py-3 text-sm text-slate-500">{{ user.id }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <div class="w-7 h-7 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0">
                    <img v-if="user.avatarUrl" :src="user.avatarUrl" class="w-full h-full object-cover" />
                    <div v-else class="w-full h-full bg-primary-100 flex items-center justify-center">
                      <span class="text-xs font-medium text-primary-600">{{ user.nickname?.[0] || '用' }}</span>
                    </div>
                  </div>
                  <div>
                    <p class="text-sm font-medium">{{ user.nickname || '-' }}</p>
                    <p class="text-xs text-slate-400">{{ user.username }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3 text-sm text-slate-600">{{ user.email || '-' }}</td>
              <td class="px-4 py-3">
                <select
                  :value="user.role"
                  @change="updateRole(user.id, ($event.target as HTMLSelectElement).value)"
                  :class="['text-xs px-2 py-1 rounded-full border-0 focus:ring-2 focus:ring-primary-500', getRoleClass(user.role)]"
                >
                  <option value="USER">普通用户</option>
                  <option value="PUBLISHER">发布者</option>
                  <option value="ADMIN">管理员</option>
                </select>
              </td>
              <td class="px-4 py-3 text-sm text-slate-600">{{ user.points || 0 }}</td>
              <td class="px-4 py-3 text-xs text-slate-400">{{ user.createdAt?.split('T')[0] }}</td>
              <td class="px-4 py-3 text-sm text-slate-500">
                <span :class="['text-xs px-2 py-0.5 rounded-full', getRoleClass(user.role)]">
                  {{ getRoleLabel(user.role) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="flex items-center justify-between mt-4">
        <p class="text-sm text-slate-500">共 {{ total }} 位用户，第 {{ page }} 页</p>
        <div class="flex items-center gap-2">
          <button
            @click="prevPage"
            :disabled="page <= 1"
            class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            上一页
          </button>
          <button
            @click="nextPage"
            :disabled="page * pageSize >= total"
            class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
