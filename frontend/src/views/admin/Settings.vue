<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const autoSummaryEnabled = ref(true)
const recommendReasonEnabled = ref(true)
const loading = ref(true)
const saving = ref(false)
const savingReason = ref(false)
const toast = ref<{ message: string; type: 'success' | 'error' } | null>(null)

function showToast(message: string, type: 'success' | 'error' = 'success') {
  toast.value = { message, type }
  setTimeout(() => { toast.value = null }, 2500)
}

onMounted(async () => {
  try {
    const [summaryRes, reasonRes] = await Promise.all([
      request.get('/admin/config/ai-summary'),
      request.get('/admin/config/ai-recommend-reason'),
    ])
    autoSummaryEnabled.value = summaryRes.data.data?.enabled ?? true
    recommendReasonEnabled.value = reasonRes.data.data?.enabled ?? true
  } catch {} finally {
    loading.value = false
  }
})

async function toggleAutoSummary() {
  const next = !autoSummaryEnabled.value
  saving.value = true
  try {
    await request.put('/admin/config/ai-summary', { enabled: next })
    autoSummaryEnabled.value = next
    showToast(next ? '已开启 AI 智能摘要自动生成' : '已关闭 AI 智能摘要自动生成')
  } catch {
    showToast('保存失败，请重试', 'error')
  } finally {
    saving.value = false
  }
}

async function toggleRecommendReason() {
  const next = !recommendReasonEnabled.value
  savingReason.value = true
  try {
    await request.put('/admin/config/ai-recommend-reason', { enabled: next })
    recommendReasonEnabled.value = next
    showToast(next ? '已开启 AI 推荐理由' : '已关闭 AI 推荐理由（改用本地模板）')
  } catch {
    showToast('保存失败，请重试', 'error')
  } finally {
    savingReason.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-slate-800 mb-6">系统管理</h1>

    <div v-if="loading" class="flex justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else class="max-w-3xl space-y-4">
      <!-- AI 智能摘要开关 -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <div class="flex items-start justify-between gap-6">
          <div class="min-w-0">
            <h2 class="font-semibold text-slate-800">AI 智能摘要自动生成</h2>
            <p class="text-sm text-slate-500 mt-2 leading-relaxed">
              开启后，用户每次发布或保存资源都会自动调用大模型生成 AI 摘要（消耗 token）。
              关闭后不再自动生成，资源列表与详情页将展示用户填写的原始描述。
            </p>
            <p
              :class="['text-xs mt-2 font-medium', autoSummaryEnabled ? 'text-emerald-600' : 'text-slate-400']"
            >
              当前状态：{{ autoSummaryEnabled ? '开启（每次发布消耗 token）' : '关闭（不消耗摘要 token）' }}
            </p>
          </div>
          <button
            @click="toggleAutoSummary"
            :disabled="saving"
            role="switch"
            :aria-checked="autoSummaryEnabled"
            :class="[
              'relative inline-flex h-7 w-12 flex-shrink-0 items-center rounded-full transition-colors disabled:opacity-50',
              autoSummaryEnabled ? 'bg-primary-600' : 'bg-slate-300'
            ]"
          >
            <span
              :class="[
                'inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform',
                autoSummaryEnabled ? 'translate-x-6' : 'translate-x-1'
              ]"
            ></span>
          </button>
        </div>
      </div>

      <!-- AI 推荐理由开关 -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <div class="flex items-start justify-between gap-6">
          <div class="min-w-0">
            <h2 class="font-semibold text-slate-800">AI 推荐理由</h2>
            <p class="text-sm text-slate-500 mt-2 leading-relaxed">
              开启后，首页「为你推荐」的推荐语由大模型根据用户兴趣生成（消耗 token，
              结果缓存 24 小时）。关闭后改用本地模板理由（如「匹配你关注的 Java」），
              不消耗 token，且已生成的 AI 理由仍会展示。
            </p>
            <p
              :class="['text-xs mt-2 font-medium', recommendReasonEnabled ? 'text-emerald-600' : 'text-slate-400']"
            >
              当前状态：{{ recommendReasonEnabled ? '开启（LLM 生成，缓存 24h）' : '关闭（本地模板，不消耗 token）' }}
            </p>
          </div>
          <button
            @click="toggleRecommendReason"
            :disabled="savingReason"
            role="switch"
            :aria-checked="recommendReasonEnabled"
            :class="[
              'relative inline-flex h-7 w-12 flex-shrink-0 items-center rounded-full transition-colors disabled:opacity-50',
              recommendReasonEnabled ? 'bg-primary-600' : 'bg-slate-300'
            ]"
          >
            <span
              :class="[
                'inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform',
                recommendReasonEnabled ? 'translate-x-6' : 'translate-x-1'
              ]"
            ></span>
          </button>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <Transition
      enter-active-class="transition-all duration-300 ease-out"
      enter-from-class="opacity-0 -translate-y-4"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition-all duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-4"
    >
      <div
        v-if="toast"
        :class="[
          'fixed top-6 left-1/2 -translate-x-1/2 z-50 flex items-center gap-3 px-5 py-3 rounded-xl shadow-lg text-sm font-medium',
          toast.type === 'error' ? 'bg-red-50 text-red-700 border border-red-200' : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
        ]"
      >
        <span>{{ toast.message }}</span>
      </div>
    </Transition>
  </div>
</template>
