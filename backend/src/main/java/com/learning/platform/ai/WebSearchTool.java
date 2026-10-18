package com.learning.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
                + "平台资源覆盖不到的内容时调用。query 必须是结合对话上下文改写出的独立查询词"
                + "（把\"还有其他推荐吗\"这类指代补全为具体主题），不要原样照搬用户口语。"
                + "返回带编号的来源清单，回答时用 [n] 标注引用。";
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
        // 未配置 key 时明确告知模型（而不是误报"无结果"），方便使用者发现配置缺失
        if (!tavilySearchService.isConfigured()) {
            return "联网搜索服务未配置（缺少 TAVILY_API_KEY），请基于已有知识回答，"
                    + "并提示使用者：在 application-local.yml 填入 tavily.api-key 后重启后端即可开启联网搜索。";
        }
        List<WebSource> sources = tavilySearchService.search(query);
        if (sources.isEmpty()) {
            return "联网搜索没有返回结果（可尝试换更具体的关键词再调用一次），请基于已有知识回答，并说明未找到联网来源。";
        }
        // 留存来源：多次调用累积（模型可能分主题搜索多次），SSE sources 事件一并推给前端
        if (context != null && context != ChatToolContext.NONE) {
            List<WebSource> merged = new ArrayList<>(context.getWebSources() == null ? List.of() : context.getWebSources());
            for (WebSource s : sources) {
                if (merged.stream().noneMatch(e -> e.url().equals(s.url()))) {
                    merged.add(s);
                }
            }
            // 来源条最多展示 8 条，避免多轮搜索后面板过长
            context.setWebSources(merged.size() > 8 ? merged.subList(0, 8) : merged);
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
