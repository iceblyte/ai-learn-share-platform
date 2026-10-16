package com.learning.platform.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具一：按关键词搜索平台内已发布的学习资源。
 *
 * <p>模型在用户问"有没有 Java 相关资源""推荐几本算法书"时调用，
 * 返回候选列表（id/标题/类型/评分/摘要片段），需要细节时再调 get_resource_detail。</p>
 */
@Component
public class ResourceSearchTool extends ChatTool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final int SNIPPET_LENGTH = 80;

    private final ResourceMapper resourceMapper;

    public ResourceSearchTool(ResourceMapper resourceMapper, ObjectMapper objectMapper) {
        super(objectMapper);
        this.resourceMapper = resourceMapper;
    }

    @Override
    public String name() {
        return "search_resources";
    }

    @Override
    public String description() {
        return "按关键词搜索平台内已发布的学习资源，返回候选资源列表（含 id、标题、类型、评分、摘要片段）。"
                + "当用户询问平台上有什么资源、想找某类学习资料、或需要推荐时调用。";
    }

    @Override
    protected JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addStringProperty("keyword", "搜索关键词，2-6 个核心字词，如：Java并发、考研数学")
                .addIntegerProperty("limit", "返回条数，1-10，默认 5")
                .required("keyword")
                .build();
    }

    @Override
    protected String doExecute(JsonNode arguments, ChatToolContext context) {
        String keyword = arguments.path("keyword").asText("").trim();
        if (keyword.isEmpty()) {
            return paramError("keyword 不能为空");
        }
        int limit = clampLimit(arguments.path("limit").asInt(DEFAULT_LIMIT));

        List<Resource> resources = resourceMapper.selectList(new QueryWrapper<Resource>()
                .eq("status", "PUBLISHED")
                .eq("is_deleted", 0)
                .and(w -> w.like("title", keyword)
                        .or().like("ai_summary", keyword)
                        .or().like("description", keyword))
                .orderByDesc("hot_score")
                .last("LIMIT " + limit));

        ObjectNode result = objectMapper.createObjectNode();
        result.put("keyword", keyword);
        result.put("count", resources.size());
        ArrayNode items = result.putArray("resources");
        for (Resource resource : resources) {
            ObjectNode item = items.addObject();
            item.put("id", resource.getId());
            item.put("title", resource.getTitle());
            item.put("resourceType", resource.getResourceType());
            item.put("avgRating", resource.getAvgRating() == null ? null : resource.getAvgRating().doubleValue());
            item.put("viewCount", resource.getViewCount());
            item.put("summary", snippet(resource.getAiSummary(), resource.getDescription()));
        }
        return result.toString();
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private String snippet(String aiSummary, String description) {
        String source = aiSummary != null && !aiSummary.isBlank() ? aiSummary : description;
        if (source == null) {
            return "";
        }
        return source.length() <= SNIPPET_LENGTH ? source : source.substring(0, SNIPPET_LENGTH) + "…";
    }
}
