<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = ref({ username: '', password: '' })
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await userStore.login(form.value)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-[70vh] flex items-center justify-center">
    <div class="w-full max-w-md">
      <div class="card p-8">
        <h1 class="text-2xl font-bold text-center text-slate-800 mb-2">登录</h1>
        <p class="text-center text-slate-500 mb-8">欢迎回来，请登录您的账号</p>

        <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
          {{ error }}
        </div>

        <form @submit.prevent="handleLogin" class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">用户名</label>
            <input v-model="form.username" type="text" class="input-field" placeholder="请输入用户名" required />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">密码</label>
            <input v-model="form.password" type="password" class="input-field" placeholder="请输入密码" required />
          </div>
          <button type="submit" class="btn-primary w-full" :disabled="loading">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-500">
          还没有账号？
          <router-link to="/register" class="text-primary-600 hover:text-primary-700 font-medium">立即注册</router-link>
        </p>
      </div>
    </div>
  </div>
</template>
