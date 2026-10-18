package com.learning.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具：联网搜索（Tavily）。仅在用户打开"智能搜索"开关的请求中暴露给模型，
 * 返回带 [n] 编号的来源清单（标题/URL/摘要），并在
 * {@link ChatToolContext} 上留存来源供 SSE sources 事件推送给前端展示。
 */
@Component
public class WebSearchTool extends ChatTool {

    public static final String TOOL_NAME = "web_search";
    private static final int SNIPPET_LENGTH = 160;

    private final TavilySearchService tavilySearchService;

    public WebSearchTool(TavilySearchService tavilySearchService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.tavilySearchService = tavilySearchService;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "联网搜索最新资料（Tavily）。当用户打开联网搜索、问题涉及最新资讯/外部知识/"
                + "平台资源覆盖不到的内容时调用。返回带编号的来源清单，回答时用 [n] 标注引用。";
    }

    @Override
    protected JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("query", "搜索查询词，尽量具体（如：Spring Boot 3 虚拟线程 官方文档）")
                .required("query")
                .build();
    }

    @Override
    protected String doExecute(JsonNode arguments, ChatToolContext context) {
        String query = arguments.path("query").asText("").trim();
        if (query.isEmpty()) {
            return paramError("query 不能为空");
        }
        List<WebSource> sources = tavilySearchService.search(query);
        if (sources.isEmpty()) {
            return "联网搜索没有返回结果，请基于已有知识回答，并说明未找到联网来源。";
        }
        // 留存来源：SSE sources 事件推给前端展示（NONE 为共享实例，跳过防串扰）
        if (context != null && context != ChatToolContext.NONE) {
            context.setWebSources(sources);
        }
        StringBuilder sb = new StringBuilder("已联网搜索，共 %d 条来源，回答时请用 [n] 标注引用：\n".formatted(sources.size()));
        for (int i = 0; i < sources.size(); i++) {
            WebSource s = sources.get(i);
            sb.append('[').append(i + 1).append("] ").append(s.title())
                    .append(" — ").append(s.url()).append('\n')
                    .append(s.snippet()).append('\n');
        }
        return sb.toString();
    }
}
