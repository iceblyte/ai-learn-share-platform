package com.learning.platform.ai;

import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天工具注册表：自动收集容器内所有 {@link ChatTool} Bean。
 *
 * <p>扩展方式：写一个 ChatTool 子类并标注 @Component 即可上线，
 * 注册表负责去重校验、生成 ToolSpecification 列表与按名执行。</p>
 */
@Slf4j
@Component
public class ChatToolRegistry {

    private final Map<String, ChatTool> tools = new LinkedHashMap<>();

    public ChatToolRegistry(List<ChatTool> chatTools) {
        for (ChatTool tool : chatTools) {
            ChatTool previous = tools.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate chat tool name: " + tool.name()
                                + " (" + previous.getClass().getSimpleName()
                                + " vs " + tool.getClass().getSimpleName() + ")");
            }
            log.info("Registered chat tool: {} ({})", tool.name(), tool.getClass().getSimpleName());
        }
    }

    /** 全部工具规格（发给模型做 function calling） */
    public List<ToolSpecification> specifications() {
        return tools.values().stream().map(ChatTool::toSpecification).toList();
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /** 按模型给出的工具名执行；未知工具返回错误文本而非抛异常 */
    public String execute(String toolName, String argumentsJson, ChatToolContext context) {
        ChatTool tool = toolName == null ? null : tools.get(toolName);
        if (tool == null) {
            return "未知的工具：" + toolName;
        }
        return tool.execute(argumentsJson, context);
    }

    /** 供提示词动态生成工具清单：名称 + 用途描述 */
    public List<String> describeTools() {
        return tools.values().stream()
                .map(tool -> tool.name() + "：" + tool.description())
                .toList();
    }
}
