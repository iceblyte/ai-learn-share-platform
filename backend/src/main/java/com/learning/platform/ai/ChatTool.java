package com.learning.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天工具基类（function calling）。
 *
 * <p>扩展纪律：新增工具只需继承本类并注册为 Spring Bean——
 * {@link ChatToolRegistry} 会自动收集、生成 ToolSpecification 并纳入模型可选工具集，
 * 提示词中的工具清单也由注册表动态生成，无需改动 AiService / 提示词代码。</p>
 *
 * <p>基类统一负责：参数 JSON 解析、异常兜底（工具失败以错误文本返回给模型，
 * 由模型决定如何向用户解释，聊天链路不因此中断）、结果截断（防止超长结果
 * 撑爆上下文窗口）以及 {@link ToolSpecification} 的转换。</p>
 */
@Slf4j
public abstract class ChatTool {

    /** 单次工具结果注入对话的最大字符数 */
    static final int MAX_RESULT_LENGTH = 4000;

    protected final ObjectMapper objectMapper;

    protected ChatTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 工具名（OpenAI function name 规范：字母/数字/下划线/连字符） */
    public abstract String name();

    /** 给模型看的工具用途描述（中文，说明何时该用） */
    public abstract String description();

    /** 参数 JSON Schema（LangChain4j JsonObjectSchema 形式） */
    protected abstract JsonObjectSchema parameters();

    /**
     * 工具实际执行逻辑。抛出的任何异常都会被基类捕获，
     * 以"工具执行失败"文本回传给模型。
     */
    protected abstract String doExecute(JsonNode arguments, ChatToolContext context) throws Exception;

    /** 解析参数节点（空参时回退为空对象），子类用 path()/asText 等安全取值 */
    protected final JsonNode parseArguments(String argumentsJson) throws Exception {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        JsonNode node = objectMapper.readTree(argumentsJson);
        return node == null || node.isNull() ? objectMapper.createObjectNode() : node;
    }

    /** 结果截断，超长部分以省略提示结尾 */
    protected final String truncate(String result) {
        if (result == null) {
            return "";
        }
        if (result.length() <= MAX_RESULT_LENGTH) {
            return result;
        }
        return result.substring(0, MAX_RESULT_LENGTH) + "\n…（结果过长已截断，请缩小查询范围）";
    }

    /** 模型可读的取参错误提示 */
    protected final String paramError(String message) {
        return "参数错误：" + message;
    }

    /**
     * 注册表统一入口：解析参数 → 执行 → 异常兜底 → 截断。
     * 永不抛异常，保证工具调用失败不会中断聊天流。
     */
    public final String execute(String argumentsJson, ChatToolContext context) {
        try {
            JsonNode arguments = parseArguments(argumentsJson);
            String result = doExecute(arguments, context == null ? ChatToolContext.NONE : context);
            return truncate(result);
        } catch (Exception e) {
            log.warn("Chat tool {} failed: {}", name(), e.getMessage());
            return "工具执行失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 转换为 LangChain4j 工具规格，交给模型做 function calling */
    public ToolSpecification toSpecification() {
        return ToolSpecification.builder()
                .name(name())
                .description(description())
                .parameters(parameters())
                .build();
    }
}
