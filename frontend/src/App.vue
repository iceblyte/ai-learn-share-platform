<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

// 管理后台自带全屏布局（侧边栏 + 独立滚动区），不能再套前台外壳：
// 否则 body 与内部容器双层滚动，滚轮行为错乱、出现双导航/双滚动条
const isAdminLayout = computed(() => route.path.startsWith('/admin'))

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
    <AppFooter v-if="!isAdminLayout" />
    <AiChatWidget v-if="!isAdminLayout" />
  </div>
</template>
