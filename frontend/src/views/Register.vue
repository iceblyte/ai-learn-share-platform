<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const form = ref({ username: '', email: '', password: '', confirmPassword: '', nickname: '' })
const error = ref('')
const loading = ref(false)

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
            <input v-model="form.password" type="password" class="input-field" placeholder="6-20位" required />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">确认密码</label>
            <input v-model="form.confirmPassword" type="password" class="input-field" placeholder="再次输入密码" required />
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
