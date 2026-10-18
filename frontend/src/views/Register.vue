<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const form = ref({ username: '', email: '', password: '', confirmPassword: '', nickname: '' })
const error = ref('')
const loading = ref(false)
const showPassword = ref(false)
const showConfirm = ref(false)

async function handleRegister() {
  error.value = ''
  if (form.value.password !== form.value.confirmPassword) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await userStore.register({
      username: form.value.username,
      email: form.value.email,
      password: form.value.password,
      nickname: form.value.nickname || undefined,
    })
    router.push('/')
  } catch (e: any) {
    error.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-[70vh] flex items-center justify-center">
    <div class="w-full max-w-md">
      <div class="card p-8">
        <h1 class="text-2xl font-bold text-center text-slate-800 mb-2">注册</h1>
        <p class="text-center text-slate-500 mb-8">创建账号，开始分享学习资源</p>

        <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
          {{ error }}
        </div>

        <form @submit.prevent="handleRegister" class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">用户名</label>
            <input v-model="form.username" type="text" class="input-field" placeholder="3-20位字符" required />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">邮箱</label>
            <input v-model="form.email" type="email" class="input-field" placeholder="your@email.com" required />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">昵称 <span class="text-slate-400">(可选)</span></label>
            <input v-model="form.nickname" type="text" class="input-field" placeholder="显示名称" />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">密码</label>
            <div class="relative">
              <input
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                class="input-field pr-10"
                placeholder="6-20位"
                required
              />
              <button type="button" @click="showPassword = !showPassword" class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600" :title="showPassword ? '隐藏密码' : '显示密码'">
                <svg v-if="!showPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.542 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/></svg>
              </button>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">确认密码</label>
            <div class="relative">
              <input
                v-model="form.confirmPassword"
                :type="showConfirm ? 'text' : 'password'"
                class="input-field pr-10"
                placeholder="再次输入密码"
                required
              />
              <button type="button" @click="showConfirm = !showConfirm" class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600" :title="showConfirm ? '隐藏密码' : '显示密码'">
                <svg v-if="!showConfirm" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.542 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/></svg>
              </button>
            </div>
          </div>
          <button type="submit" class="btn-primary w-full" :disabled="loading">
            {{ loading ? '注册中...' : '注册' }}
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-500">
          已有账号？
          <router-link to="/login" class="text-primary-600 hover:text-primary-700 font-medium">立即登录</router-link>
        </p>
      </div>
    </div>
  </div>
</template>
