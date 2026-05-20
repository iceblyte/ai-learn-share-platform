<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'
import type { User } from '@/types'
import AppModal from '@/components/AppModal.vue'

const users = ref<User[]>([])
const page = ref(1)
const total = ref(0)
const pageSize = 20
const loading = ref(true)

const applications = ref<any[]>([])
const appsLoading = ref(false)
const showAuditModal = ref(false)
const auditTarget = ref<any>(null)
const auditAction = ref('')
const rejectReason = ref('')

onMounted(() => {
  loadUsers()
  loadApplications()
})

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

async function loadApplications() {
  appsLoading.value = true
  try {
    const res = await request.get('/admin/publisher-applications', { params: { status: 'PENDING', page: 1, size: 10 } })
    applications.value = res.data.data.records || []
  } catch {} finally {
    appsLoading.value = false
  }
}

function confirmAppAudit(app: any, action: string) {
  auditTarget.value = app
  auditAction.value = action
  rejectReason.value = ''
  showAuditModal.value = true
}

async function executeAppAudit() {
  if (!auditTarget.value) return
  try {
    const body: any = { action: auditAction.value }
    if (auditAction.value === 'REJECT' && rejectReason.value.trim()) {
      body.reason = rejectReason.value.trim()
    }
    await request.put(`/admin/publisher-applications/${auditTarget.value.id}/audit`, body)
    applications.value = applications.value.filter(a => a.id !== auditTarget.value.id)
  } catch {}
  showAuditModal.value = false
  auditTarget.value = null
}

function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-slate-800 mb-6">用户管理</h1>

    <!-- Pending Publisher Applications -->
    <div v-if="applications.length > 0" class="mb-6">
      <h2 class="text-lg font-semibold text-slate-700 mb-3">待审核发布者申请 ({{ applications.length }})</h2>
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 divide-y divide-slate-100">
        <div v-for="app in applications" :key="app.id" class="p-4 flex items-center justify-between hover:bg-slate-50">
          <div class="flex items-center gap-3 flex-1 min-w-0">
            <div class="w-9 h-9 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0">
              <img v-if="app.avatarUrl" :src="app.avatarUrl" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full bg-amber-100 flex items-center justify-center">
                <span class="text-sm font-medium text-amber-600">{{ app.nickname?.[0] || '用' }}</span>
              </div>
            </div>
            <div class="min-w-0">
              <p class="text-sm font-medium text-slate-800">{{ app.nickname || app.username }}</p>
              <p class="text-xs text-slate-400">@{{ app.username }}</p>
            </div>
            <p class="text-sm text-slate-600 ml-4 truncate flex-1">{{ app.reason || '未填写理由' }}</p>
            <span class="text-xs text-slate-400 ml-4 flex-shrink-0">{{ formatTime(app.createdAt) }}</span>
          </div>
          <div class="flex items-center gap-2 ml-4 flex-shrink-0">
            <button @click="confirmAppAudit(app, 'APPROVE')" class="px-3 py-1.5 text-xs text-white bg-green-500 rounded-lg hover:bg-green-600">通过</button>
            <button @click="confirmAppAudit(app, 'REJECT')" class="px-3 py-1.5 text-xs text-white bg-red-500 rounded-lg hover:bg-red-600">拒绝</button>
          </div>
        </div>
      </div>
    </div>

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

    <!-- Audit Modal -->
    <AppModal :visible="showAuditModal" :title="auditAction === 'APPROVE' ? '确认通过' : '确认拒绝'" @close="showAuditModal = false" @confirm="executeAppAudit">
      <template #body>
        <p class="text-sm text-slate-600 mb-2">
          确定{{ auditAction === 'APPROVE' ? '通过' : '拒绝' }} <strong>{{ auditTarget?.nickname || auditTarget?.username }}</strong> 的发布者申请吗？
        </p>
        <textarea
          v-if="auditAction === 'REJECT'"
          v-model="rejectReason"
          rows="3"
          placeholder="请输入拒绝原因（可选）..."
          class="w-full border border-slate-300 rounded-lg px-4 py-2.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500 resize-none mt-2"
        ></textarea>
      </template>
      <template #footer>
        <button @click="showAuditModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
        <button @click="executeAppAudit" :class="['px-4 py-2 text-sm text-white rounded-lg', auditAction === 'APPROVE' ? 'bg-green-500 hover:bg-green-600' : 'bg-red-500 hover:bg-red-600']">
          {{ auditAction === 'APPROVE' ? '确认通过' : '确认拒绝' }}
        </button>
      </template>
    </AppModal>
  </div>
</template>
