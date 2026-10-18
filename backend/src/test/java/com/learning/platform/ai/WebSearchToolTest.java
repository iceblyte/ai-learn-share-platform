package com.learning.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 联网搜索工具：把 Tavily 来源转成带编号清单供模型引用，
 * 并留存到上下文（SSE sources 事件）；空 query 报参数错误；共享 NONE 不写来源。
 */
@ExtendWith(MockitoExtension.class)
class WebSearchToolTest {

    @Mock private TavilySearchService tavilySearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSearchTool tool() {
        return new WebSearchTool(tavilySearchService, objectMapper);
    }

    @Test
    @DisplayName("执行：返回编号来源清单并写入上下文")
    void execute_returnsNumberedSourcesAndSavesContext() {
        when(tavilySearchService.isConfigured()).thenReturn(true);
        when(tavilySearchService.search("spring 虚拟线程")).thenReturn(List.of(
                new WebSource("官方文档", "https://a", "虚拟线程介绍"),
                new WebSource("博客", "https://b", "实践案例")));

        ChatToolContext context = new ChatToolContext(null, null, null, null);
        String result = tool().execute("{\"query\":\"spring 虚拟线程\"}", context);

        assertThat(result).contains("[1]").contains("官方文档").contains("https://a");
        assertThat(context.getWebSources()).hasSize(2);
        assertThat(context.getWebSources().get(0).url()).isEqualTo("https://a");
    }

    @Test
    @DisplayName("多次调用：来源按 URL 去重累积，不互相覆盖")
    void execute_accumulatesSourcesAcrossCalls() {
        when(tavilySearchService.isConfigured()).thenReturn(true);
        when(tavilySearchService.search("q1")).thenReturn(List.of(
                new WebSource("一", "https://a", "s1")));
        when(tavilySearchService.search("q2")).thenReturn(List.of(
                new WebSource("二", "https://b", "s2"),
                new WebSource("重复", "https://a", "s1-dup")));

        ChatToolContext context = new ChatToolContext(null, null, null, null);
        tool().execute("{\"query\":\"q1\"}", context);
        tool().execute("{\"query\":\"q2\"}", context);

        assertThat(context.getWebSources()).hasSize(2);
        assertThat(context.getWebSources())
                .extracting(WebSource::url)
                .containsExactly("https://a", "https://b");
    }

    @Test
    @DisplayName("未配置 key：返回明确的配置提示而非误导性的无结果")
    void execute_notConfigured() {
        when(tavilySearchService.isConfigured()).thenReturn(false);
        String result = tool().execute("{\"query\":\"q\"}", new ChatToolContext(null, null, null, null));
        assertThat(result).contains("未配置").contains("TAVILY_API_KEY");
    }

    @Test
    @DisplayName("无结果：提示模型按无来源降级回答")
    void execute_noResults() {
        when(tavilySearchService.isConfigured()).thenReturn(true);
        when(tavilySearchService.search(anyString())).thenReturn(List.of());
        String result = tool().execute("{\"query\":\"q\"}", new ChatToolContext(null, null, null, null));
        assertThat(result).contains("没有返回结果");
    }

    @Test
    @DisplayName("空 query：参数错误")
    void execute_blankQuery() {
        String result = tool().execute("{\"query\":\"\"}", ChatToolContext.NONE);
        assertThat(result).contains("参数错误");
    }

    @Test
    @DisplayName("共享 NONE 上下文不写入来源（防跨请求串扰）")
    void execute_doesNotPolluteNone() {
        when(tavilySearchService.isConfigured()).thenReturn(true);
        when(tavilySearchService.search(anyString())).thenReturn(List.of(
                new WebSource("t", "https://a", "s")));
        tool().execute("{\"query\":\"q\"}", ChatToolContext.NONE);
        assertThat(ChatToolContext.NONE.getWebSources()).isNull();
    }
}
