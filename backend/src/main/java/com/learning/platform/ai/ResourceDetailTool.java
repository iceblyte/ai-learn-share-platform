package com.learning.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.service.ChatContextService;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.springframework.stereotype.Component;

/**
 * 工具二：获取单个学习资源的详情（function calling 改造前
 * 由 {@link ChatContextService} 预取注入，现改为模型按需调用）。
 *
 * <p>resourceId 省略时回退到用户当前浏览的资源（pageContextId），
 * 即"解释这一段"等页面内提问的默认事实来源。</p>
 */
@Component
public class ResourceDetailTool extends ChatTool {

    private final ResourceMapper resourceMapper;
    private final ChatContextService chatContextService;

    public ResourceDetailTool(ResourceMapper resourceMapper,
                              ChatContextService chatContextService,
                              ObjectMapper objectMapper) {
        super(objectMapper);
        this.resourceMapper = resourceMapper;
        this.chatContextService = chatContextService;
    }

    @Override
    public String name() {
        return "get_resource_detail";
    }

    @Override
    public String description() {
        return "获取某个学习资源的详情（标题、AI 摘要、正文、链接、评分等）。"
                + "当用户的问题涉及具体资料内容时调用；不传 resourceId 时查询用户当前浏览的页面资源。";
    }

    @Override
    protected JsonObjectSchema parameters() {
        return JsonObjectSchema.builder()
                .addIntegerProperty("resourceId", "资源 ID；省略时查询当前页面资源")
                .build();
    }

    @Override
    protected String doExecute(JsonNode arguments, ChatToolContext context) throws Exception {
        Long resourceId = arguments.hasNonNull("resourceId")
                ? arguments.get("resourceId").asLong()
                : null;
        if (resourceId == null && context.isResourcePage()) {
            resourceId = context.pageContextId();
        }
        if (resourceId == null) {
            return paramError("未提供 resourceId，且当前页面没有关联资源");
        }

        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || (resource.getIsDeleted() != null && resource.getIsDeleted() == 1)) {
            return "资源不存在或已删除：" + resourceId;
        }
        if (!"PUBLISHED".equals(resource.getStatus())) {
            return "资源未发布，暂不可查询：" + resourceId;
        }

        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", resource.getId());
        item.put("title", resource.getTitle());
        item.put("resourceType", resource.getResourceType());
        item.put("externalUrl", resource.getExternalUrl());
        item.put("aiSummary", resource.getAiSummary());
        item.put("avgRating", resource.getAvgRating() == null ? null : resource.getAvgRating().doubleValue());
        item.put("ratingCount", resource.getRatingCount());
        item.put("viewCount", resource.getViewCount());
        item.put("likeCount", resource.getLikeCount());
        item.put("description", chatContextService.buildExcerpt(resource.getDescription(), context.selectedText()));
        return item.toString();
    }
}
