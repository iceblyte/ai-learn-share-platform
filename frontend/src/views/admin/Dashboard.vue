<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { categoryApi, tagApi } from '@/api/category'
import type { Category, Tag } from '@/types'
import AppModal from '@/components/AppModal.vue'

const router = useRouter()

const stats = ref({
  totalUsers: 0,
  totalResources: 0,
  pendingResources: 0,
  pendingApplications: 0,
  todayActive: 0,
})

const pendingList = ref<any[]>([])
const recentUsers = ref<any[]>([])
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(true)

const pendingApps = ref<any[]>([])
const showAuditModal = ref(false)
const auditTarget = ref<any>(null)
const auditAction = ref<'APPROVE' | 'REJECT'>('APPROVE')

const showAppAuditModal = ref(false)
const appAuditTarget = ref<any>(null)
const appAuditAction = ref('')
const appRejectReason = ref('')

onMounted(async () => {
  try {
    const [statsRes, pendingRes, usersRes, catRes, tagRes, appsRes] = await Promise.all([
      request.get('/admin/statistics'),
      request.get('/admin/resources', { params: { status: 'PENDING', size: 5 } }),
      request.get('/admin/users', { params: { size: 4, sort: 'latest' } }),
      categoryApi.getTree(),
      tagApi.getHot(),
      request.get('/admin/publisher-applications', { params: { status: 'PENDING', size: 5 } }),
    ])
    stats.value = statsRes.data.data
    pendingList.value = pendingRes.data.data?.records || []
    recentUsers.value = usersRes.data.data?.records || []
    categories.value = catRes.data.data || []
    tags.value = tagRes.data.data || []
    pendingApps.value = appsRes.data.data?.records || []
  } catch {} finally {
    loading.value = false
  }
})

function confirmAudit(item: any, action: 'APPROVE' | 'REJECT') {
  auditTarget.value = item
  auditAction.value = action
  showAuditModal.value = true
}

async function executeAudit() {
  if (!auditTarget.value) return
  try {
    await request.put(`/admin/resources/${auditTarget.value.id}/audit`, { action: auditAction.value })
    pendingList.value = pendingList.value.filter(r => r.id !== auditTarget.value.id)
    stats.value.pendingResources--
  } catch {}
  showAuditModal.value = false
  auditTarget.value = null
}

function confirmAppAudit(app: any, action: string) {
  appAuditTarget.value = app
  appAuditAction.value = action
  appRejectReason.value = ''
  showAppAuditModal.value = true
}

async function executeAppAudit() {
  if (!appAuditTarget.value) return
  try {
    const body: any = { action: appAuditAction.value }
    if (appAuditAction.value === 'REJECT' && appRejectReason.value.trim()) {
      body.reason = appRejectReason.value.trim()
    }
    await request.put(`/admin/publisher-applications/${appAuditTarget.value.id}/audit`, body)
    pendingApps.value = pendingApps.value.filter(a => a.id !== appAuditTarget.value.id)
    stats.value.pendingApplications--
  } catch {}
  showAppAuditModal.value = false
  appAuditTarget.value = null
}

function formatTimeAgo(dateStr: string): string {
  const now = Date.now()
  const date = new Date(dateStr).getTime()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return new Date(dateStr).toLocaleDateString()
}
</script>

<template>
  <div v-if="loading" class="flex justify-center py-20">
    <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600"></div>
  </div>

  <div v-else>
    <!-- Stats Cards -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-slate-500">总用户数</p>
            <p class="text-2xl font-bold mt-1">{{ stats.totalUsers.toLocaleString() }}</p>
          </div>
          <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center">
            <svg class="w-6 h-6 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/>
            </svg>
          </div>
        </div>
      </div>
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-slate-500">总资源数</p>
            <p class="text-2xl font-bold mt-1">{{ stats.totalResources.toLocaleString() }}</p>
          </div>
          <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center">
            <svg class="w-6 h-6 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
          </div>
        </div>
      </div>
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-slate-500">待审核</p>
            <p class="text-2xl font-bold mt-1 text-amber-500">{{ stats.pendingResources + (stats.pendingApplications || 0) }}</p>
            <p class="text-xs text-slate-400 mt-1">{{ stats.pendingResources }} 资源 / {{ stats.pendingApplications || 0 }} 申请</p>
          </div>
          <div class="w-12 h-12 bg-amber-100 rounded-xl flex items-center justify-center">
            <svg class="w-6 h-6 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
          </div>
        </div>
      </div>
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-slate-500">今日新增</p>
            <p class="text-2xl font-bold mt-1">{{ stats.todayActive }}</p>
          </div>
          <div class="w-12 h-12 bg-purple-100 rounded-xl flex items-center justify-center">
            <svg class="w-6 h-6 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
            </svg>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Pending Resources -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200">
        <div class="flex items-center justify-between p-4 border-b border-slate-100">
          <h2 class="font-semibold">待审核资源</h2>
          <router-link to="/admin/resources" class="text-sm text-primary-500 hover:text-primary-600">查看全部</router-link>
        </div>
        <div v-if="pendingList.length === 0" class="p-8 text-center text-slate-400 text-sm">
          暂无待审核资源
        </div>
        <div v-else class="divide-y divide-slate-100">
          <div v-for="item in pendingList" :key="item.id" class="flex items-center gap-3 p-4">
            <div class="w-10 h-10 bg-amber-100 rounded-lg flex items-center justify-center flex-shrink-0">
              <svg class="w-5 h-5 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
              </svg>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate">{{ item.title }}</p>
              <p class="text-xs text-slate-400">{{ item.publisher?.nickname }} &middot; {{ formatTimeAgo(item.createdAt) }}</p>
            </div>
            <div class="flex items-center gap-1">
              <button @click="router.push(`/resource/${item.id}`)" class="px-2 py-1 text-xs bg-slate-100 text-slate-600 rounded hover:bg-slate-200">查看</button>
              <button @click="confirmAudit(item, 'APPROVE')" class="px-2 py-1 text-xs bg-green-500 text-white rounded hover:bg-green-600">通过</button>
              <button @click="confirmAudit(item, 'REJECT')" class="px-2 py-1 text-xs bg-red-500 text-white rounded hover:bg-red-600">拒绝</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pending Publisher Applications -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200">
        <div class="flex items-center justify-between p-4 border-b border-slate-100">
          <h2 class="font-semibold">待审核申请</h2>
          <router-link to="/admin/users" class="text-sm text-primary-500 hover:text-primary-600">查看全部</router-link>
        </div>
        <div v-if="pendingApps.length === 0" class="p-8 text-center text-slate-400 text-sm">
          暂无待审核申请
        </div>
        <div v-else class="divide-y divide-slate-100">
          <div v-for="app in pendingApps" :key="app.id" class="flex items-center gap-3 p-4">
            <div class="w-8 h-8 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0">
              <img v-if="app.avatarUrl" :src="app.avatarUrl" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full bg-amber-100 flex items-center justify-center">
                <span class="text-xs font-medium text-amber-600">{{ app.nickname?.[0] || '用' }}</span>
              </div>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium truncate">{{ app.nickname || app.username }}</p>
              <p class="text-xs text-slate-400 truncate">{{ app.reason || '未填写理由' }}</p>
            </div>
            <div class="flex items-center gap-1">
              <button @click="confirmAppAudit(app, 'APPROVE')" class="px-2 py-1 text-xs bg-green-500 text-white rounded hover:bg-green-600">通过</button>
              <button @click="confirmAppAudit(app, 'REJECT')" class="px-2 py-1 text-xs bg-red-500 text-white rounded hover:bg-red-600">拒绝</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Recent Users -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200">
        <div class="flex items-center justify-between p-4 border-b border-slate-100">
          <h2 class="font-semibold">最近注册用户</h2>
          <router-link to="/admin/users" class="text-sm text-primary-500 hover:text-primary-600">查看全部</router-link>
        </div>
        <div v-if="recentUsers.length === 0" class="p-8 text-center text-slate-400 text-sm">
          暂无用户
        </div>
        <div v-else class="divide-y divide-slate-100">
          <div v-for="user in recentUsers" :key="user.id" class="flex items-center gap-3 p-4">
            <div class="w-8 h-8 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0">
              <img v-if="user.avatarUrl" :src="user.avatarUrl" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full bg-blue-100 flex items-center justify-center">
                <span class="text-xs font-medium text-blue-600">{{ user.nickname?.[0] || '用' }}</span>
              </div>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium">{{ user.nickname || user.username }}</p>
              <p class="text-xs text-slate-400">{{ user.email || user.username }}</p>
            </div>
            <span class="text-xs text-slate-400">{{ formatTimeAgo(user.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Category & Tag Management -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-6">
      <!-- Categories -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200">
        <div class="flex items-center justify-between p-4 border-b border-slate-100">
          <h2 class="font-semibold">分类管理</h2>
          <router-link to="/admin/categories" class="text-sm text-primary-500 hover:text-primary-600">管理全部</router-link>
        </div>
        <div class="p-4">
          <div class="space-y-2">
            <template v-for="cat in categories" :key="cat.id">
              <div class="flex items-center justify-between p-2 rounded-lg hover:bg-slate-50">
                <div class="flex items-center gap-2">
                  <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"/>
                  </svg>
                  <span class="text-sm">{{ cat.name }}</span>
                </div>
              </div>
              <div
                v-for="child in cat.children"
                :key="child.id"
                class="flex items-center justify-between p-2 rounded-lg hover:bg-slate-50 pl-8"
              >
                <span class="text-sm text-slate-600">{{ child.name }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- Tags -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200">
        <div class="flex items-center justify-between p-4 border-b border-slate-100">
          <h2 class="font-semibold">热门标签</h2>
          <router-link to="/admin/tags" class="text-sm text-primary-500 hover:text-primary-600">管理全部</router-link>
        </div>
        <div class="p-4">
          <div class="flex flex-wrap gap-2">
            <span
              v-for="tag in tags"
              :key="tag.id"
              class="inline-flex items-center gap-1.5 text-sm bg-slate-100 text-slate-700 px-3 py-1.5 rounded-lg"
            >
              {{ tag.name }}
              <span class="text-xs text-slate-400">({{ tag.usageCount || 0 }})</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Audit Confirmation Modal -->
  <AppModal
    :visible="showAuditModal"
    :title="auditAction === 'APPROVE' ? '确认通过' : '确认拒绝'"
    @close="showAuditModal = false"
    @confirm="executeAudit"
  >
    <template #body>
      <p class="text-sm text-slate-600">
        确定要{{ auditAction === 'APPROVE' ? '通过' : '拒绝' }}资源「{{ auditTarget?.title }}」吗？
      </p>
    </template>
    <template #footer>
      <button @click="showAuditModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
      <button
        @click="executeAudit"
        :class="['px-4 py-2 text-sm text-white rounded-lg', auditAction === 'APPROVE' ? 'bg-green-500 hover:bg-green-600' : 'bg-red-500 hover:bg-red-600']"
      >
        {{ auditAction === 'APPROVE' ? '确认通过' : '确认拒绝' }}
      </button>
    </template>
  </AppModal>

  <!-- Publisher Application Audit Modal -->
  <AppModal
    :visible="showAppAuditModal"
    :title="appAuditAction === 'APPROVE' ? '确认通过' : '确认拒绝'"
    @close="showAppAuditModal = false"
    @confirm="executeAppAudit"
  >
    <template #body>
      <p class="text-sm text-slate-600 mb-2">
        确定{{ appAuditAction === 'APPROVE' ? '通过' : '拒绝' }} <strong>{{ appAuditTarget?.nickname || appAuditTarget?.username }}</strong> 的发布者申请吗？
      </p>
      <textarea
        v-if="appAuditAction === 'REJECT'"
        v-model="appRejectReason"
        rows="3"
        placeholder="请输入拒绝原因（可选）..."
        class="w-full border border-slate-300 rounded-lg px-4 py-2.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500 resize-none mt-2"
      ></textarea>
    </template>
    <template #footer>
      <button @click="showAppAuditModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
      <button
        @click="executeAppAudit"
        :class="['px-4 py-2 text-sm text-white rounded-lg', appAuditAction === 'APPROVE' ? 'bg-green-500 hover:bg-green-600' : 'bg-red-500 hover:bg-red-600']"
      >
        {{ appAuditAction === 'APPROVE' ? '确认通过' : '确认拒绝' }}
      </button>
    </template>
  </AppModal>
</template>
