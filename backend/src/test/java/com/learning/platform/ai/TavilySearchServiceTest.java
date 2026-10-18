package com.learning.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

/**
 * Tavily 联网搜索：key 缺失降级为空结果、解析响应、失败吞异常返回空列表。
 */
@ExtendWith(MockitoExtension.class)
class TavilySearchServiceTest {

    @Mock private TavilySearchService.HttpPoster poster;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TavilySearchService service(String apiKey) {
        return new TavilySearchService(objectMapper, poster, apiKey, 5);
    }

    @Test
    @DisplayName("未配置 key：直接返回空列表，不发 HTTP")
    void search_skipsWhenNoKey() {
        List<WebSource> result = service("").search("java 并发");
        assertThat(result).isEmpty();
        List<WebSource> result2 = service(null).search("java 并发");
        assertThat(result2).isEmpty();
    }

    @Test
    @DisplayName("正常响应：解析出 title/url/snippet 列表")
    void search_parsesResponse() throws Exception {
        String resp = """
                {"results":[
                  {"title":"Java 并发指南","url":"https://a.com/1","content":"并发基础","score":0.9},
                  {"title":"JUC 详解","url":"https://b.com/2","content":"线程池","score":0.8}
                ]}
                """;
        when(poster.post(contains("tavily.com"), anyString(), anyString())).thenReturn(resp);

        List<WebSource> result = service("tvly-key").search("java 并发");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Java 并发指南");
        assertThat(result.get(0).url()).isEqualTo("https://a.com/1");
        assertThat(result.get(0).snippet()).isEqualTo("并发基础");
    }

    @Test
    @DisplayName("HTTP 失败：吞异常返回空列表")
    void search_swallowsFailure() throws Exception {
        when(poster.post(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        assertThat(service("tvly-key").search("q")).isEmpty();
    }

    @Test
    @DisplayName("非法响应体：返回空列表")
    void search_handlesMalformed() throws Exception {
        when(poster.post(anyString(), anyString(), anyString())).thenReturn("not-json");
        assertThat(service("tvly-key").search("q")).isEmpty();
    }
}
