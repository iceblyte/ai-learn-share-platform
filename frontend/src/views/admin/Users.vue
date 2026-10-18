<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import type { User } from '@/types'
import AppModal from '@/components/AppModal.vue'
import AppToast from '@/components/AppToast.vue'

const users = ref<User[]>([])
const page = ref(1)
const total = ref(0)
const pageSize = ref(20)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const keyword = ref('')
const loading = ref(true)

// 新增用户
const showCreateModal = ref(false)
const createForm = ref({ username: '', email: '', nickname: '', role: 'USER' })
const createSubmitting = ref(false)
const toast = ref<InstanceType<typeof AppToast> | null>(null)

// 删除用户
const showDeleteModal = ref(false)
const deleteTarget = ref<User | null>(null)
const deleteSubmitting = ref(false)

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
    const res = await request.get('/admin/users', { params: { page: page.value, size: pageSize.value, keyword: keyword.value || undefined } })
    users.value = res.data.data.records
    total.value = res.data.data.total
  } catch {} finally {
    loading.value = false
  }
}

function searchUsers() {
  page.value = 1
  loadUsers()
}

async function createUser() {
  if (!createForm.value.username.trim() || !createForm.value.email.trim()) {
    toast.value?.show('用户名和邮箱不能为空', 'error')
    return
  }
  createSubmitting.value = true
  try {
    await request.post('/admin/users', {
      username: createForm.value.username.trim(),
      email: createForm.value.email.trim(),
      nickname: createForm.value.nickname.trim() || undefined,
      role: createForm.value.role,
    })
    toast.value?.show('创建成功，初始密码为 123456，请通知用户及时修改', 'success')
    showCreateModal.value = false
    createForm.value = { username: '', email: '', nickname: '', role: 'USER' }
    page.value = 1
    keyword.value = ''
    loadUsers()
  } catch (e: any) {
    toast.value?.show(e?.response?.data?.message || '创建失败', 'error')
  } finally {
    createSubmitting.value = false
  }
}

function confirmDelete(user: User) {
  deleteTarget.value = user
  showDeleteModal.value = true
}

async function executeDelete() {
  if (!deleteTarget.value) return
  deleteSubmitting.value = true
  try {
    await request.delete(`/admin/users/${deleteTarget.value.id}`)
    toast.value?.show('已删除（逻辑删除，历史内容保留）', 'success')
    showDeleteModal.value = false
    if (users.value.length === 1 && page.value > 1) page.value--
    loadUsers()
  } catch (e: any) {
    toast.value?.show(e?.response?.data?.message || '删除失败', 'error')
  } finally {
    deleteSubmitting.value = false
    deleteTarget.value = null
  }
}

function canDelete(user: User) {
  return user.role !== 'ADMIN'
}

async function updateRole(id: number, role: string) {
  try {
    await request.put(`/admin/users/${id}/role`, { role })
    loadUsers()
  } catch {}
}

function goToPage(p: number) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  loadUsers()
}

/** 生成页码列表（最多 7 个，带省略号） */
function pageNumbers() {
  const tp = totalPages.value
  const cur = page.value
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  const pages = new Set<number>([1, tp, cur, cur - 1, cur + 1])
  const list = [...pages].filter(p => p >= 1 && p <= tp).sort((a, b) => a - b)
  const result: (number | string)[] = []
  let prev = 0
  for (const p of list) {
    if (p - prev > 1) result.push('...')
    result.push(p)
    prev = p
  }
  return result
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
      <!-- Toolbar -->
      <div class="flex items-center justify-between mb-4 gap-3">
        <div class="relative flex-1 max-w-xs">
          <input
            v-model="keyword"
            @keyup.enter="searchUsers"
            placeholder="搜索用户名 / 昵称 / 邮箱"
            class="input-field pl-9 text-sm"
          />
          <svg class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>
        <button @click="searchUsers" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">搜索</button>
        <button @click="showCreateModal = true" class="px-4 py-2 text-sm text-white bg-primary-600 rounded-lg hover:bg-primary-700 flex items-center gap-1">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          新增用户
        </button>
      </div>

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
              <td class="px-4 py-3 text-sm">
                <button
                  v-if="canDelete(user)"
                  @click="confirmDelete(user)"
                  class="text-xs px-2.5 py-1 text-red-600 border border-red-200 rounded-lg hover:bg-red-50"
                >
                  删除
                </button>
                <span v-else class="text-xs text-slate-300">—</span>
              </td>
            </tr>
            <tr v-if="users.length === 0">
              <td colspan="7" class="px-4 py-10 text-center text-sm text-slate-400">没有匹配的用户</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="flex items-center justify-between mt-4">
        <p class="text-sm text-slate-500">共 {{ total }} 位用户，第 {{ page }} / {{ totalPages }} 页</p>
        <div class="flex items-center gap-1">
          <button
            @click="goToPage(page - 1)"
            :disabled="page <= 1"
            class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            上一页
          </button>
          <template v-for="(p, i) in pageNumbers()" :key="i">
            <span v-if="p === '...'" class="px-2 text-slate-400 text-sm">…</span>
            <button
              v-else
              @click="goToPage(p as number)"
              :class="['min-w-[2.25rem] px-2 py-1.5 text-sm rounded-lg border', p === page ? 'bg-primary-600 text-white border-primary-600' : 'border-slate-300 hover:bg-slate-50']"
            >
              {{ p }}
            </button>
          </template>
          <button
            @click="goToPage(page + 1)"
            :disabled="page >= totalPages"
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

    <!-- Create User Modal -->
    <AppModal :visible="showCreateModal" title="新增用户" @close="showCreateModal = false" @confirm="createUser">
      <template #body>
        <div class="space-y-3">
          <div>
            <label class="block text-sm text-slate-600 mb-1">用户名 <span class="text-red-500">*</span></label>
            <input v-model="createForm.username" placeholder="3-20位字母/数字/下划线/短横线" class="input-field text-sm" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">邮箱 <span class="text-red-500">*</span></label>
            <input v-model="createForm.email" type="email" placeholder="user@example.com" class="input-field text-sm" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">昵称（可选，默认同用户名）</label>
            <input v-model="createForm.nickname" class="input-field text-sm" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">角色</label>
            <select v-model="createForm.role" class="input-field text-sm">
              <option value="USER">普通用户</option>
              <option value="PUBLISHER">发布者</option>
              <option value="ADMIN">管理员</option>
            </select>
          </div>
          <p class="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
            初始密码为 <strong>123456</strong>，请通知用户登录后自行修改。
          </p>
        </div>
      </template>
      <template #footer>
        <button @click="showCreateModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
        <button @click="createUser" :disabled="createSubmitting" class="px-4 py-2 text-sm text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50">
          {{ createSubmitting ? '创建中…' : '创建' }}
        </button>
      </template>
    </AppModal>

    <!-- Delete User Modal -->
    <AppModal :visible="showDeleteModal" title="确认删除" @close="showDeleteModal = false" @confirm="executeDelete">
      <template #body>
        <p class="text-sm text-slate-600">
          确定删除用户 <strong>{{ deleteTarget?.nickname || deleteTarget?.username }}</strong>（@{{ deleteTarget?.username }}）吗？
        </p>
        <p class="text-xs text-slate-400 mt-2">将进行逻辑删除：该用户无法再登录，其发布的资源与评论会保留并显示为「已注销用户」。</p>
      </template>
      <template #footer>
        <button @click="showDeleteModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
        <button @click="executeDelete" :disabled="deleteSubmitting" class="px-4 py-2 text-sm text-white bg-red-500 rounded-lg hover:bg-red-600 disabled:opacity-50">
          {{ deleteSubmitting ? '删除中…' : '确认删除' }}
        </button>
      </template>
    </AppModal>

    <AppToast ref="toast" />
  </div>
</template>
