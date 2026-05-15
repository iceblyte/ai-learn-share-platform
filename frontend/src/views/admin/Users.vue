<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'
import type { User } from '@/types'

const users = ref<User[]>([])
const page = ref(1)
const total = ref(0)

onMounted(() => loadUsers())

async function loadUsers() {
  const res = await request.get('/admin/users', { params: { page: page.value, size: 20 } })
  users.value = res.data.data.records
  total.value = res.data.data.total
}

async function updateRole(id: number, role: string) {
  await request.put(`/admin/users/${id}/role`, { role })
  loadUsers()
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-slate-800 mb-6">用户管理</h1>
    <div class="card overflow-hidden">
      <table class="w-full">
        <thead class="bg-slate-50">
          <tr>
            <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">ID</th>
            <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">用户名</th>
            <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">昵称</th>
            <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">邮箱</th>
            <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">角色</th>
            <th class="px-4 py-3 text-left text-sm font-medium text-slate-600">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-for="user in users" :key="user.id" class="hover:bg-slate-50">
            <td class="px-4 py-3 text-sm">{{ user.id }}</td>
            <td class="px-4 py-3 text-sm">{{ user.username }}</td>
            <td class="px-4 py-3 text-sm">{{ user.nickname }}</td>
            <td class="px-4 py-3 text-sm">{{ user.email }}</td>
            <td class="px-4 py-3 text-sm">
              <select :value="user.role" @change="updateRole(user.id, ($event.target as HTMLSelectElement).value)" class="input-field w-auto text-sm py-1">
                <option value="USER">普通用户</option>
                <option value="PUBLISHER">发布者</option>
                <option value="ADMIN">管理员</option>
              </select>
            </td>
            <td class="px-4 py-3 text-sm text-slate-500">{{ user.points }} 积分</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
