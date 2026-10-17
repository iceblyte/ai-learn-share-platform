<script setup lang="ts">
/**
 * AI 助手头像（方案 A「星辉」机器人）：品牌渐变底 + 天线星 + 屏幕笑脸。
 * 与 BrandMark 同一渐变体系；消息气泡 / AI 对话页欢迎区共用。
 */
</script>

<template>
  <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <defs>
      <linearGradient id="aiBg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#2E7CF6" />
        <stop offset="1" stop-color="#7C3AED" />
      </linearGradient>
      <linearGradient id="aiFace" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#FFFFFF" />
        <stop offset="1" stop-color="#DCE7FD" />
      </linearGradient>
    </defs>
    <rect width="64" height="64" rx="15" fill="url(#aiBg)" />
    <ellipse cx="22" cy="10" rx="26" ry="10" fill="#fff" opacity=".13" />
    <!-- 天线顶星 -->
    <path d="M32 6.5l1.1 2.8 2.8 1.1-2.8 1.1-1.1 2.8-1.1-2.8-2.8-1.1 2.8-1.1 1.1-2.8z" fill="#FFD54A" />
    <path d="M32 14.3v5.2" stroke="#E3ECFE" stroke-width="2.2" stroke-linecap="round" />
    <!-- 侧耳 -->
    <rect x="12.5" y="29" width="4.5" height="9" rx="2.2" fill="#C9D8FA" />
    <rect x="47" y="29" width="4.5" height="9" rx="2.2" fill="#C9D8FA" />
    <!-- 头与屏幕 -->
    <rect x="16.5" y="20.5" width="31" height="26" rx="9" fill="url(#aiFace)" />
    <rect x="20.5" y="24.5" width="23" height="17.5" rx="6" fill="#1E3A8A" />
    <!-- 笑脸 -->
    <path d="M26.5 30.5q2.2-2.6 4.4 0" stroke="#7DD3FC" stroke-width="1.9" stroke-linecap="round" fill="none" />
    <path d="M33.1 30.5q2.2-2.6 4.4 0" stroke="#7DD3FC" stroke-width="1.9" stroke-linecap="round" fill="none" />
    <path d="M27.5 36.2q4.5 3.9 9 0" stroke="#7DD3FC" stroke-width="1.9" stroke-linecap="round" fill="none" />
    <circle cx="23.5" cy="38.6" r="1.3" fill="#FCA5A5" opacity=".85" />
    <circle cx="40.5" cy="38.6" r="1.3" fill="#FCA5A5" opacity=".85" />
  </svg>
</template>
