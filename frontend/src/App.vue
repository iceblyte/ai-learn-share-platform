<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

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
    <AppHeader />
    <main class="flex-1">
      <router-view />
    </main>
    <AppFooter />
    <AiChatWidget />
  </div>
</template>
