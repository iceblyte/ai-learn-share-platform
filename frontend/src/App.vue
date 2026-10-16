<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

// 管理后台与全屏 AI 对话页都是自带布局的独立页面，不能再套前台外壳：
// 管理后台套了会出现双层滚动；/ai-chat 则与悬浮球功能重复且相互遮挡
const isStandalonePage = computed(() =>
  route.path.startsWith('/admin') || route.path === '/ai-chat'
)

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
    <AppHeader v-if="!isStandalonePage" />
    <main class="flex-1">
      <router-view />
    </main>
    <AppFooter v-if="!isStandalonePage" />
    <AiChatWidget v-if="!isStandalonePage" />
  </div>
</template>
