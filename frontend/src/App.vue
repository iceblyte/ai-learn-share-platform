<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

// 管理后台是自带布局的独立页面，套前台外壳会出现双层滚动；
// 全屏 AI 对话页保留顶部导航（否则无法返回主站），但隐藏底部 footer
// 和悬浮球（与全屏对话功能重复且相互遮挡）
const isAdminLayout = computed(() => route.path.startsWith('/admin'))
const isAiChatPage = computed(() => route.path === '/ai-chat')

onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.clearAuth()
    }
  }
})
</script>

<template>
  <div class="min-h-screen flex flex-col bg-slate-50">
    <AppHeader v-if="!isAdminLayout" />
    <main class="flex-1">
      <router-view />
    </main>
    <AppFooter v-if="!isAdminLayout && !isAiChatPage" />
    <AiChatWidget v-if="!isAdminLayout && !isAiChatPage" />
  </div>
</template>
