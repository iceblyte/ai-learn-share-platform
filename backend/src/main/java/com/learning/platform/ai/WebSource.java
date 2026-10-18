package com.learning.platform.ai;

/**
 * 联网搜索来源（Tavily）：标题 + 链接 + 内容摘要，
 * 经 SSE sources 事件推给前端，在回答下方展示并支持点击跳转。
 */
public record WebSource(String title, String url, String snippet) {
}
