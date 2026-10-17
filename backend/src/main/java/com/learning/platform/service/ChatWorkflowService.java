package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.ChatToolContext;
import com.learning.platform.ai.ChatToolRegistry;
import com.learning.platform.ai.ChatWorkflowState;
import com.learning.platform.ai.ResourceSearchTool;
import com.learning.platform.ai.image.ImageCollector;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * AI 聊天工作流（LangGraph4j 图编排）。
 *
 * <p>链路：START → route（条件边：有划词走 search，否则直达）
 * → generate（复用 {@link AiService#streamGenerate} 流式桥）
 * → [imageCollect（有取图会话时）] → moderate（审查回路：
 * 违规 → revise_content 增量改写 ×2 → 重答 ×1 → 仍违规则撤回）→ END。
 * search 节点确定式调用 {@link ResourceSearchTool}（不再依赖模型自觉），
 * 结果经 {@link ChatPromptBuilder#appendSearchResults} 注入生成提示词。</p>
 *
 * <p><b>记忆写入纪律</b>：生成路径不写记忆——最终回答只在审查通过后
 * 经 {@link AiService#appendAssistantToMemory} 落记忆；撤回时经
 * {@link AiService#removeLastExchange} 移除本轮 user 提问，违规内容
 * 不会污染后续对话上下文。</p>
 *
 * <p><b>序列化边界</b>：langgraph4j 在节点间对状态做深拷贝，图状态
 * （{@link ChatWorkflowState}）只承载可序列化数据；SSE emitter 与
 * {@link ChatToolContext} 等运行期对象经 {@link RunContext} 闭包进节点。
 * 因此图按请求编译——图极小，编译成本相对一次 LLM 调用可忽略。</p>
 */
@Slf4j
@Service
public class ChatWorkflowService {

    static final int KEYWORD_MAX_LENGTH = 20;
    static final String MODERATION_NOTICE = "该内容涉嫌违规，无法显示";
    /** 审查回路重试上限：增量改写 ×2 → 重答 ×1 → 撤回 */
    static final int MAX_REVISE_ATTEMPTS = 2;
    static final int MAX_REGEN_ATTEMPTS = 1;

    /** 划选文本常见问句后缀（剥掉再检索，提高 LIKE 命中率） */
    private static final String[] QUESTION_SUFFIXES = {
            "是什么意思", "什么意思", "指的是什么", "指什么", "是什么", "解释一下", "解释",
            "怎么理解", "如何理解", "是什么概念", "怎么用", "如何使用", "的含义", "的意思",
    };
    private static final String[] QUESTION_PREFIXES = {
            "什么是", "什么叫", "解释一下", "说明一下", "介绍一下", "帮我解释",
    };

    private final AiService aiService;
    private final ChatToolRegistry chatToolRegistry;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ResourceSearchTool resourceSearchTool;
    private final ObjectMapper objectMapper;
    private final ImageCollector imageCollector;
    private final ModerationService moderationService;
    private final ContentRevisionService contentRevisionService;
    private final boolean enabled;

    public ChatWorkflowService(AiService aiService,
                               ChatToolRegistry chatToolRegistry,
                               ChatPromptBuilder chatPromptBuilder,
                               ResourceSearchTool resourceSearchTool,
                               ObjectMapper objectMapper,
                               ImageCollector imageCollector,
                               ModerationService moderationService,
                               ContentRevisionService contentRevisionService,
                               @Value("${ai.workflow.enabled:true}") boolean enabled) {
        this.aiService = aiService;
        this.chatToolRegistry = chatToolRegistry;
        this.chatPromptBuilder = chatPromptBuilder;
        this.resourceSearchTool = resourceSearchTool;
        this.objectMapper = objectMapper;
        this.imageCollector = imageCollector;
        this.moderationService = moderationService;
        this.contentRevisionService = contentRevisionService;
        this.enabled = enabled;
        log.info("Chat workflow service initialized (enabled={})", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 工作流模式暴露给模型的工具：search_resources 由图确定式调用，不重复暴露 */
    List<ToolSpecification> generateToolSpecs() {
        return chatToolRegistry.specifications().stream()
                .filter(spec -> !ResourceSearchTool.TOOL_NAME.equals(spec.name()))
                .toList();
    }

    /**
     * 本次提问的运行期上下文：图状态之外的一切（身份、SSE emitter、工具上下文、
     * 并发取图会话）。节点 lambda 经编译期闭包读取，不进序列化状态。
     */
    record RunContext(Long userId, Long sessionId, String route,
                      ChatToolContext toolContext, ChatStreamFactory.ChatStreamEmitter emitter,
                      ImageCollector.Session imageSession) {
    }

    /**
     * imageCollect 节点：等待并发收集会话收尾（预算内），有图则发 SSE
     * images 事件并把结果写入状态（供落库）。
     */
    private Map<String, Object> imageCollectNode(ChatWorkflowState state, RunContext ctx) {
        ImageCollector.Session session = ctx.imageSession();
        if (session == null) {
            return Map.of();
        }
        String imagesJson = session.awaitJson();
        if (imagesJson == null) {
            return Map.of();
        }
        ctx.emitter().images(imagesJson);
        return Map.of(ChatWorkflowState.IMAGES_JSON, imagesJson);
    }

    // ---------- 执行入口 ----------

    public record WorkflowResult(String content, Integer tokensUsed, String status,
                                 String imagesJson, String error) {
        public boolean moderated() {
            return "MODERATED".equals(status);
        }
    }

    /**
     * 组装初始状态并同步执行整张图（在 SSE 的 boundedElastic 线程上阻塞运行）。
     * 图按请求编译，节点闭包捕获 {@link RunContext}。
     */
    public WorkflowResult run(Long userId, Long sessionId, String message, String route,
                              String pageTitle, String pageContextType, Long pageContextId,
                              String selectedText, ChatStreamFactory.ChatStreamEmitter emitter) {
        if (!enabled) {
            throw new IllegalStateException("聊天工作流未启用");
        }
        RunContext ctx = new RunContext(userId, sessionId, route,
                new ChatToolContext(userId, pageContextType, pageContextId, selectedText), emitter,
                imageCollector.start(selectedText == null || selectedText.isBlank() ? message : selectedText));

        Map<String, Object> initial = new HashMap<>();
        initial.put(ChatWorkflowState.MESSAGE, message == null ? "" : message);
        initial.put(ChatWorkflowState.ROUTE, route == null ? "/" : route);
        initial.put(ChatWorkflowState.PAGE_TITLE, pageTitle == null ? "" : pageTitle);
        initial.put(ChatWorkflowState.PAGE_CONTEXT_TYPE, pageContextType == null ? "" : pageContextType);
        initial.put(ChatWorkflowState.PAGE_CONTEXT_ID, pageContextId);
        initial.put(ChatWorkflowState.SELECTED_TEXT, selectedText == null ? "" : selectedText);
        initial.put(ChatWorkflowState.SEARCH_RESULTS_JSON, "");
        initial.put(ChatWorkflowState.STATUS, "");

        try {
            CompiledGraph<ChatWorkflowState> graph = compileGraph(ctx);
            ChatWorkflowState finalState = graph.invoke(initial)
                    .orElseThrow(() -> new IllegalStateException("工作流执行无结果"));
            String status = finalState.status().isBlank() ? "FAILED" : finalState.status();
            String content = switch (status) {
                case "OK" -> finalState.answer();
                case "MODERATED" -> MODERATION_NOTICE;
                default -> finalState.error().isBlank() ? "AI 服务请求失败，请稍后再试。" : finalState.error();
            };
            // 记忆收尾：通过才写回答；撤回则移除本轮提问（违规内容不进上下文）
            if (sessionId != null) {
                if ("OK".equals(status)) {
                    aiService.appendAssistantToMemory(sessionId, content);
                } else if ("MODERATED".equals(status)) {
                    aiService.removeLastExchange(sessionId);
                }
            }
            return new WorkflowResult(content, finalState.tokensUsed(), status,
                    finalState.imagesJson().isBlank() ? null : finalState.imagesJson(),
                    "FAILED".equals(status) ? content : null);
        } catch (Exception e) {
            log.error("Chat workflow execution failed", e);
            return new WorkflowResult("服务器开小差了，请稍后再试。", null, "FAILED", null,
                    "服务器开小差了，请稍后再试。");
        }
    }

    // ---------- 图装配（按请求编译，闭包捕获 ctx） ----------

    private CompiledGraph<ChatWorkflowState> compileGraph(RunContext ctx) {
        try {
            StateGraph<ChatWorkflowState> workflow = new StateGraph<>(ChatWorkflowState::new)
                    .addNode("route", node_async(this::routeNode))
                    .addNode("search", node_async(state -> searchNode(state, ctx)))
                    .addNode("generate", node_async(state -> generateNode(state, ctx)))
                    .addNode("imageCollect", node_async(state -> imageCollectNode(state, ctx)))
                    .addNode("moderate", node_async(this::moderateNode))
                    .addNode("revise", node_async(state -> reviseNode(state, ctx)))
                    .addNode("regen", node_async(state -> regenNode(state, ctx)))
                    .addNode("moderated", node_async(state -> moderatedNode(state, ctx)))
                    .addEdge(START, "route")
                    .addConditionalEdges("route", edge_async(this::routeAfter),
                            Map.of("search", "search", "generate", "generate"))
                    .addEdge("search", "generate")
                    // 取图会话已点火才进 imageCollect（节点内 await 收尾），否则直达审查
                    .addConditionalEdges("generate", edge_async(state -> ctx.imageSession() != null
                                    ? "imageCollect" : "moderate"),
                            Map.of("imageCollect", "imageCollect", "moderate", "moderate"))
                    .addEdge("imageCollect", "moderate")
                    .addConditionalEdges("moderate", edge_async(this::afterModerate),
                            Map.of("pass", END, "revise", "revise", "regen", "regen",
                                    "moderated", "moderated"))
                    // revise/regen 产物重新送审（回路出口仅 pass/moderated，不无限循环）
                    .addEdge("revise", "moderate")
                    .addEdge("regen", "moderate")
                    .addEdge("moderated", END);
            return workflow.compile();
        } catch (Exception e) {
            throw new IllegalStateException("聊天工作流图装配失败", e);
        }
    }

    /** route 节点：纯透传（条件边挂在它后面），把控制流与业务节点解耦 */
    private Map<String, Object> routeNode(ChatWorkflowState state) {
        return Map.of();
    }

    private String routeAfter(ChatWorkflowState state) {
        return state.selectedText().isBlank() ? "generate" : "search";
    }

    /** search 节点：划选文本提取关键词 → 确定式调 search_resources → 结果入状态 */
    private Map<String, Object> searchNode(ChatWorkflowState state, RunContext ctx) {
        String keyword = extractKeyword(state.selectedText());
        ctx.emitter().tool(resourceSearchTool.name());
        String arguments;
        try {
            arguments = objectMapper.writeValueAsString(Map.of("keyword", keyword, "limit", 5));
        } catch (Exception e) {
            arguments = "{\"keyword\":\"" + keyword.replace("\"", "") + "\"}";
        }
        String result = resourceSearchTool.execute(arguments, ctx.toolContext());
        // 工具失败时结果为错误文本：不入提示词检索块，生成照常进行
        boolean failed = result.startsWith("工具执行失败") || result.startsWith("参数错误");
        return Map.of(ChatWorkflowState.SEARCH_RESULTS_JSON, failed ? "" : result);
    }

    /** generate/regen 共用：基础提示词（页面上下文 + 工具清单）+ 检索结果注入 */
    private String buildSystemPrompt(ChatWorkflowState state) {
        boolean onResourcePage = ChatContext.TYPE_RESOURCE.equals(state.pageContextType());
        Long pageResourceId = onResourcePage ? state.pageContextId().orElse(null) : null;
        String basePrompt = chatPromptBuilder.buildToolMode(state.route(), state.pageTitle(),
                state.selectedText(), pageResourceId, chatToolRegistry.describeTools());
        return chatPromptBuilder.appendSearchResults(basePrompt, state.searchResultsJson());
    }

    /** generate 节点：组装提示词（基础 + 检索结果）→ 复用 AiService 流式生成 */
    private Map<String, Object> generateNode(ChatWorkflowState state, RunContext ctx) {
        AiService.StreamingResult result = aiService.streamGenerate(
                ctx.sessionId(),
                buildSystemPrompt(state),
                state.message(),
                generateToolSpecs(),
                true,
                ctx.toolContext(),
                ctx.emitter()::text,
                ctx.emitter()::tool,
                ctx.emitter()::reset);

        if (result.failed() || result.content() == null) {
            return Map.of(ChatWorkflowState.STATUS, "FAILED",
                    ChatWorkflowState.ERROR, result.errorMessage() == null ? "" : result.errorMessage());
        }
        return Map.of(ChatWorkflowState.ANSWER, result.content(),
                ChatWorkflowState.TOKENS_USED, result.tokensUsed() == null ? 0 : result.tokensUsed(),
                ChatWorkflowState.STATUS, "OK");
    }

    // ---------- 审查回路节点 ----------

    /** moderate 节点：双通道审查；失败/空回答不进审查（保持既有状态直通终局） */
    private Map<String, Object> moderateNode(ChatWorkflowState state) {
        if (!"OK".equals(state.status()) || state.answer().isBlank()) {
            return Map.of(ChatWorkflowState.VERDICT, "PASS");
        }
        ModerationService.ModerationResult result = moderationService.review(state.answer());
        if (result.clean()) {
            return Map.of(ChatWorkflowState.VERDICT, "PASS");
        }
        return Map.of(ChatWorkflowState.VERDICT, "FLAGGED",
                ChatWorkflowState.FLAGGED_SEGMENTS, result.fragments(),
                ChatWorkflowState.FLAG_REASON, result.channel());
    }

    /** moderate 条件边：PASS 直通终局；FLAGGED 按剩余重试额度走 revise/regen/moderated */
    private String afterModerate(ChatWorkflowState state) {
        if (!"FLAGGED".equals(state.verdict())) {
            return "pass";
        }
        if (state.reviseAttempts() < MAX_REVISE_ATTEMPTS) {
            return "revise";
        }
        if (state.regenAttempts() < MAX_REGEN_ATTEMPTS) {
            return "regen";
        }
        return "moderated";
    }

    /** revise 节点（revise_content 工具）：增量改写违规片段 → reset 后整段重发 */
    private Map<String, Object> reviseNode(ChatWorkflowState state, RunContext ctx) {
        int attempt = state.reviseAttempts() + 1;
        ctx.emitter().tool("revise_content");
        String revised = contentRevisionService.revise(state.answer(), state.flaggedSegments());
        if (revised == null || revised.isBlank()) {
            // 改写不可用：保留原答案与 verdict，下轮审查后按剩余额度走重答
            return Map.of(ChatWorkflowState.REVISE_ATTEMPTS, attempt);
        }
        ctx.emitter().reset();
        ctx.emitter().text(revised);
        return Map.of(ChatWorkflowState.ANSWER, revised,
                ChatWorkflowState.REVISE_ATTEMPTS, attempt);
    }

    /** regen 节点：重新回答（不重复写 user 消息——首轮提问已在记忆中） */
    private Map<String, Object> regenNode(ChatWorkflowState state, RunContext ctx) {
        int attempt = state.regenAttempts() + 1;
        ctx.emitter().reset();
        AiService.StreamingResult result = aiService.streamGenerate(
                ctx.sessionId(),
                buildSystemPrompt(state),
                state.message(),
                generateToolSpecs(),
                false,
                ctx.toolContext(),
                ctx.emitter()::text,
                ctx.emitter()::tool,
                ctx.emitter()::reset);
        if (result.failed() || result.content() == null) {
            // 基础设施失败：错误文案已随流发出，FAILED 状态经 moderate 直通终局
            return Map.of(ChatWorkflowState.REGEN_ATTEMPTS, attempt,
                    ChatWorkflowState.STATUS, "FAILED",
                    ChatWorkflowState.ERROR, result.errorMessage() == null ? "" : result.errorMessage());
        }
        return Map.of(ChatWorkflowState.ANSWER, result.content(),
                ChatWorkflowState.TOKENS_USED, result.tokensUsed() == null ? 0 : result.tokensUsed(),
                ChatWorkflowState.REGEN_ATTEMPTS, attempt);
    }

    /** moderated 节点：终审不过——撤回正文，前端显示违规提示 */
    private Map<String, Object> moderatedNode(ChatWorkflowState state, RunContext ctx) {
        ctx.emitter().moderated(MODERATION_NOTICE);
        return Map.of(ChatWorkflowState.STATUS, "MODERATED");
    }

    // ---------- 关键词提取 ----------

    /**
     * 划选文本 → 检索关键词：剥标点/空白（保留字母数字与连字符）、
     * 去问句前后缀、截断 20 字。纯规则实现，不为取词额外调 LLM。
     */
    static String extractKeyword(String selectedText) {
        if (selectedText == null) {
            return "";
        }
        String cleaned = selectedText.replaceAll("[^\\p{L}\\p{N}\\-]+", " ").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : QUESTION_SUFFIXES) {
                if (cleaned.endsWith(suffix) && cleaned.length() > suffix.length()) {
                    cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
                    changed = true;
                }
            }
            for (String prefix : QUESTION_PREFIXES) {
                if (cleaned.startsWith(prefix) && cleaned.length() > prefix.length()) {
                    cleaned = cleaned.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        return cleaned.length() > KEYWORD_MAX_LENGTH ? cleaned.substring(0, KEYWORD_MAX_LENGTH) : cleaned;
    }
}
