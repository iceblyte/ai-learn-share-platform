package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.ChatToolContext;
import com.learning.platform.ai.ChatToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Service
public class AiService {

    /** 路径拆分结果：content 为完整回答；failed 时 errorMessage 非空 */
    public record StreamingResult(String content, Integer tokensUsed, String errorMessage) {
        public boolean failed() {
            return errorMessage != null;
        }
    }

    static final int MEMORY_WINDOW = 20;

    /** function calling 单次提问最多允许的模型往返轮数（首轮 + 工具后续轮） */
    static final int MAX_TOOL_ROUNDS = 4;

    private final RedisTemplate<String, Object> redisTemplate;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryStore chatMemoryStore;
    private final ChatToolRegistry chatToolRegistry;
    private final int readTimeoutMs;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Value("${ai.client.temperature:0.2}")
    private double temperature;

    @Value("${ai.client.max-tokens:512}")
    private int maxTokens;

    @Value("${ai.chat.timeout-ms:120000}")
    private int chatTimeoutMs;

    public AiService(RedisTemplate<String, Object> redisTemplate,
                     ObjectMapper objectMapper,
                     @Value("${ai.client.connect-timeout-ms:3000}") int connectTimeoutMs,
                     @Value("${ai.client.read-timeout-ms:8000}") int readTimeoutMs,
                     StreamingChatModel streamingChatModel,
                     ChatMemoryStore chatMemoryStore,
                     ChatToolRegistry chatToolRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryStore = chatMemoryStore;
        this.chatToolRegistry = chatToolRegistry;
    }

    private static final String CACHE_PREFIX_SUMMARY = "ai:summary:";
    private static final String CACHE_PREFIX_NL = "ai:nl:";
    private static final String CACHE_PREFIX_REASON = "ai:reason:";
    private static final long SUMMARY_TTL_HOURS = 24;
    private static final long NL_TTL_HOURS = 1;
    private static final long REASON_TTL_HOURS = 6;

    public String generateSummary(String title, String description) {
        String cacheKey = CACHE_PREFIX_SUMMARY + hash(title + "|" + description);
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        String prompt = String.format(
                "请为以下学习资源生成一段约100字的精准摘要，突出核心内容和学习价值：\n\n标题：%s\n描述：%s\n\n要求：简洁明了，突出重点，约100字。",
                title, description.length() > 2000 ? description.substring(0, 2000) : description
        );
        String result = callAi(prompt);
        if (result == null) {
            result = buildLocalSummary(title, description);
        }
        if (result != null) cache(cacheKey, result, SUMMARY_TTL_HOURS);
        return result;
    }

    public String generateLocalSummary(String title, String description) {
        return buildLocalSummary(title, description);
    }

    public String parseNaturalLanguageQuery(String query) {
        String cacheKey = CACHE_PREFIX_NL + hash(query);
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        String prompt = String.format(
                "请将以下自然语言搜索请求解析为JSON格式的结构化查询参数。\n\n" +
                "用户输入：\"%s\"\n\n" +
                "请输出以下JSON格式（不要输出其他内容）：\n" +
                "{\"keywords\": [\"关键词1\", \"关键词2\"], \"category\": \"分类名或null\", \"tags\": [\"标签1\"], " +
                "\"sortBy\": \"relevance/latest/hot/rating\", \"limit\": 数字, \"minRating\": 数字或null}\n\n" +
                "注意：\n" +
                "- keywords 必须是简短的核心搜索词（2-6个字），用于数据库模糊匹配，不要包含动词、虚词或修饰语\n" +
                "- 例如用户说\"推荐关于Java并发且评分最高的前5个资源\"，keywords应为[\"Java并发\"]，而不是[\"推荐关于Java并发且评分最高的前5个资源\"]\n" +
                "- sortBy默认为relevance，limit默认为10", query
        );
        String result = callAi(prompt);
        if (result != null) cache(cacheKey, result, NL_TTL_HOURS);
        return result;
    }

    public String generateRecommendReason(String userInterests, String resourceTitle, String resourceDescription) {
        String cacheKey = CACHE_PREFIX_REASON + hash(userInterests + "|" + resourceTitle);
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        String prompt = String.format(
                "请为用户生成一段个性化的推荐理由（50字以内），让用户觉得这个资源很适合他。\n\n" +
                "用户兴趣：%s\n资源标题：%s\n资源简介：%s\n\n" +
                "要求：语气亲切，直接说明为什么适合该用户，50字以内。",
                userInterests, resourceTitle,
                resourceDescription.length() > 200 ? resourceDescription.substring(0, 200) : resourceDescription
        );
        String result = callAi(prompt);
        if (result != null) cache(cacheKey, result, REASON_TTL_HOURS);
        return result;
    }

    /**
     * 匿名单轮流式（游客通道）：无记忆，system + user 单轮。
     */
    public StreamingResult streamChat(String message, String systemPrompt, Consumer<String> onChunk) {
        return streamChat(null, message, systemPrompt, ChatToolContext.NONE, onChunk, chunk -> {});
    }

    /** 匿名 + 工具上下文（游客在资源页提问时，工具可回退查询当前页面资源） */
    public StreamingResult streamChat(String message, String systemPrompt,
                                      ChatToolContext toolContext, Consumer<String> onChunk) {
        return streamChat(null, message, systemPrompt, toolContext, onChunk, chunk -> {});
    }

    /** 匿名 + 工具上下文 + 工具调用通知（SSE tool 事件） */
    public StreamingResult streamChat(String message, String systemPrompt,
                                      ChatToolContext toolContext, Consumer<String> onChunk,
                                      Consumer<String> onTool) {
        return streamChat(null, message, systemPrompt, toolContext, onChunk, onTool, () -> {});
    }

    /** 匿名 + 工具上下文 + 工具调用通知 + 正文回滚通知（SSE reset 事件） */
    public StreamingResult streamChat(String message, String systemPrompt,
                                      ChatToolContext toolContext, Consumer<String> onChunk,
                                      Consumer<String> onTool, Runnable onReset) {
        return streamChat(null, message, systemPrompt, toolContext, onChunk, onTool, onReset);
    }

    /**
     * 扩展三真流式聊天（无工具上下文的兼容重载）。
     */
    public StreamingResult streamChat(Long sessionId, String message, String systemPrompt, Consumer<String> onChunk) {
        return streamChat(sessionId, message, systemPrompt, ChatToolContext.NONE, onChunk, chunk -> {});
    }

    /**
     * 扩展三真流式聊天 + function calling。
     *
     * <p>流程：用户提问 → 模型决定是否调工具 → 注册表执行工具（查库）→
     * 模型再次生成 → 直至给出最终回答。最终轮 token 实时经 onChunk 转发；
     * 每次工具开始执行时经 onTool 通知工具名（供前端展示"调用 xx 工具"
     * 状态，不进正文）。中间轮（以工具请求结束的轮）的过渡文字不进正文：
     * 结束时回滚累积文本并触发 onReset 通知前端清空已流出片段。</p>
     *
     * <p>sessionId 非空时启用会话记忆（{@link MessageWindowChatMemory} 20 条窗口，
     * 底层 {@link ChatMemoryStore} 为 Redis，miss/故障回填 MySQL）；为空时匿名单轮。
     * 记忆只落 user + 最终 assistant 回答，工具中间轮不进记忆（与 MySQL 会话
     * 历史重建逻辑保持一致）。失败/未配置时降级文案也走 onChunk，
     * 同时以返回值告知调用方（供落库记账）。</p>
     */
    public StreamingResult streamChat(Long sessionId, String message, String systemPrompt,
                                      ChatToolContext toolContext, Consumer<String> onChunk,
                                      Consumer<String> onTool) {
        return streamChat(sessionId, message, systemPrompt, toolContext, onChunk, onTool, () -> {});
    }

    /** 同上，onReset 额外通知"清空已流出正文"（SSE reset 事件） */
    public StreamingResult streamChat(Long sessionId, String message, String systemPrompt,
                                      ChatToolContext toolContext, Consumer<String> onChunk,
                                      Consumer<String> onTool, Runnable onReset) {
        return streamChat(sessionId, message, systemPrompt, toolContext,
                chatToolRegistry.specifications(), onChunk, onTool, onReset);
    }

    /** 显式传入工具规格（智能搜索门控：未开启时排除 web_search），其余同上 */
    public StreamingResult streamChat(Long sessionId, String message, String systemPrompt,
                                      ChatToolContext toolContext, List<ToolSpecification> toolSpecs,
                                      Consumer<String> onChunk, Consumer<String> onTool, Runnable onReset) {
        StreamingResult result = streamGenerate(sessionId, systemPrompt, message,
                toolSpecs, true, toolContext, onChunk, onTool, onReset);
        // V1 行为保持：成功回答写回记忆窗口（工作流路径由调用方审查后再写）
        if (!result.failed() && result.content() != null) {
            appendAssistantToMemory(sessionId, result.content());
        }
        return result;
    }

    /**
     * 工作流 generate 节点入口：显式传入工具规格与记忆写入策略。
     *
     * <p>与 {@link #streamChat} 的区别仅两点：toolSpecs 由调用方决定
     * （工作流模式下 search_resources 由图路由确定式调用，不再暴露给模型）；
     * appendUserToMemory 供"重新回答"复用记忆而不重复写入同一条用户消息。
     * 最终回答写回记忆由调用方在审查通过后经 {@link #appendAssistantToMemory}
     * 完成——撤回场景不应把违规正文写进记忆。</p>
     */
    public StreamingResult streamGenerate(Long sessionId, String systemPrompt, String message,
                                          List<ToolSpecification> toolSpecs, boolean appendUserToMemory,
                                          ChatToolContext toolContext, Consumer<String> onChunk,
                                          Consumer<String> onTool, Runnable onReset) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            String notice = "AI 服务未配置，请先设置有效的 AI_API_KEY。";
            onChunk.accept(notice);
            return new StreamingResult(null, null, notice);
        }

        ChatMemory memory = null;
        List<ChatMessage> baseMessages;
        if (sessionId != null) {
            memory = MessageWindowChatMemory.builder()
                    .id(sessionId)
                    .maxMessages(MEMORY_WINDOW)
                    .chatMemoryStore(chatMemoryStore)
                    .alwaysKeepSystemMessageFirst(true)
                    .build();
            if (appendUserToMemory) {
                memory.add(SystemMessage.from(systemPrompt));
                memory.add(UserMessage.from(message));
            }
            baseMessages = memory.messages();
        } else {
            baseMessages = List.of(SystemMessage.from(systemPrompt), UserMessage.from(message));
        }

        StringBuilder answer = new StringBuilder();
        AtomicReference<TokenUsage> usageRef = new AtomicReference<>();
        String error = runToolLoop(baseMessages, toolSpecs, toolContext, answer, usageRef, onChunk, onTool, onReset);

        String errorResult = error;
        if (errorResult != null) {
            onChunk.accept(errorResult);
            return new StreamingResult(null, null, errorResult);
        }

        String content = answer.toString();
        if (content.isBlank()) {
            String notice = "AI 服务没有返回有效内容，请稍后再试。";
            onChunk.accept(notice);
            return new StreamingResult(null, null, notice);
        }

        TokenUsage usage = usageRef.get();
        Integer tokensUsed = usage == null ? null : usage.totalTokenCount();
        return new StreamingResult(content, tokensUsed, null);
    }

    /**
     * 供工作流在审查通过后把最终回答写回记忆窗口（store 内部吞 Redis 异常）。
     * 与生成时同 id 重建窗口实例：MessageWindowChatMemory 的 add 会先从
     * store 加载已有消息再追加，实例间无状态。
     */
    public void appendAssistantToMemory(Long sessionId, String content) {
        if (sessionId == null || content == null || content.isBlank()) {
            return;
        }
        MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(MEMORY_WINDOW)
                .chatMemoryStore(chatMemoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build()
                .add(AiMessage.from(content));
    }

    /**
     * 撤回场景的会话记忆清理：移除尾部一对 user(+Ai) 消息。
     *
     * <p>回答违规被撤回时，本轮 user 提问不应留在记忆里（模型对"被撤回
     * 的提问"一无所知，下轮会答非所问）；生成路径从不把违规回答写入
     * 记忆，故通常只需移除 user。防御性兼容尾部残留 AiMessage 的情形。
     * store 故障吞掉不外抛——撤回流程不因 Redis 异常中断。</p>
     */
    public void removeLastExchange(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            List<ChatMessage> messages = new ArrayList<>(chatMemoryStore.getMessages(sessionId));
            if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof AiMessage) {
                messages.remove(messages.size() - 1);
            }
            if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage) {
                messages.remove(messages.size() - 1);
            }
            chatMemoryStore.updateMessages(sessionId, messages);
        } catch (Exception e) {
            log.warn("Failed to remove last exchange from memory: {}", e.getMessage());
        }
    }

    /**
     * 阻塞式单轮调用（JSON 输出专用）：响应按 {...} 截取，
     * 供审查等结构化判定使用。失败/未配置返回 null。
     */
    public String completeJson(String prompt, int maxTokens, int timeoutMs) {
        return complete(prompt, maxTokens, timeoutMs, true);
    }

    /**
     * 阻塞式单轮调用（纯文本输出）：不做 {...} 截取——改写原文含
     * 代码块时大括号不能被误当 JSON 边界。失败/未配置返回 null。
     */
    public String completeText(String prompt, int maxTokens, int timeoutMs) {
        return complete(prompt, maxTokens, timeoutMs, false);
    }

    private String complete(String prompt, int maxTokens, int timeoutMs, boolean extractJson) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            log.warn("AI API key is not configured, skipping remote call");
            return null;
        }
        try {
            return callChatCompletions(List.of(Map.of("role", "user", "content", prompt)),
                    maxTokens, timeoutMs, extractJson);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("429") || msg.contains("quota") || msg.contains("RESOURCE_EXHAUSTED"))) {
                log.warn("AI API quota exhausted (429), skipping: {}", msg.length() > 100 ? msg.substring(0, 100) : msg);
            } else {
                log.error("AI API call error: {}", msg);
            }
            return null;
        }
    }

    /**
     * function calling 主循环：模型 ↔ 工具 多轮往返。
     *
     * <p>每轮把当前对话（含工具结果）发给模型；响应携带工具调用请求时，
     * 经注册表执行并把 {@link ToolExecutionResultMessage} 追加进对话后继续下一轮；
     * 无工具请求即最终回答。轮数达到 {@link #MAX_TOOL_ROUNDS} 或总超时则终止，
     * 终止时已有流式输出则按已完成处理，否则返回错误文案。</p>
     *
     * <p>中间轮（以工具请求结束的轮）流出的过渡文字（模型调工具前的
     * "自言自语"，常见为英文）不属于正式回答：回滚累积文本并触发
     * {@code onReset}，前端据此清空已渲染片段，落库内容同样干净。</p>
     */
    private String runToolLoop(List<ChatMessage> baseMessages, List<ToolSpecification> toolSpecs,
                               ChatToolContext toolContext, StringBuilder answer,
                               AtomicReference<TokenUsage> usageRef, Consumer<String> onChunk,
                               Consumer<String> onTool, Runnable onReset) {
        List<ChatMessage> conversation = new ArrayList<>(baseMessages);
        long deadline = System.currentTimeMillis() + Math.max(chatTimeoutMs, 30000);

        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return "AI 服务响应超时，请稍后再试。";
            }

            ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(conversation);
            if (!toolSpecs.isEmpty()) {
                requestBuilder.toolSpecifications(toolSpecs);
            }

            int answerMark = answer.length();
            RoundOutcome outcome = runRound(requestBuilder.build(), answer, onChunk, remaining);
            if (outcome.error != null) {
                return outcome.error;
            }
            if (outcome.usage != null) {
                usageRef.set(outcome.usage);
            }
            conversation.add(outcome.aiMessage);

            if (!outcome.aiMessage.hasToolExecutionRequests()) {
                return null; // 最终回答已完成
            }
            if (round == MAX_TOOL_ROUNDS) {
                // 已到轮次上限：工具仍被请求时终止，已有输出按最终结果处理
                log.warn("Tool round limit reached ({}), stopping", MAX_TOOL_ROUNDS);
                return null;
            }

            // 中间轮：撤销该轮已流出的过渡文字（前端清空 + 不进落库内容）
            if (answer.length() > answerMark) {
                answer.setLength(answerMark);
                onReset.run();
            }

            for (ToolExecutionRequest request : outcome.aiMessage.toolExecutionRequests()) {
                log.info("Chat tool invocation: {} (round {})", request.name(), round + 1);
                // 通知前端开始执行该工具（SSE tool 事件，不进正文）
                onTool.accept(request.name());
                String toolResult = chatToolRegistry.execute(request.name(), request.arguments(), toolContext);
                conversation.add(ToolExecutionResultMessage.from(request, toolResult));
            }
        }
        return "AI 服务没有返回有效内容，请稍后再试。";
    }

    /** 单轮流式调用：token 实时转发，阻塞等待本轮完成（受剩余总超时约束） */
    private RoundOutcome runRound(ChatRequest request, StringBuilder answer,
                                  Consumer<String> onChunk, long timeoutMs) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<RoundOutcome> outcomeRef = new AtomicReference<>();

        try {
            streamingChatModel.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    answer.append(token);
                    onChunk.accept(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    TokenUsage usage = response != null && response.metadata() != null
                            ? response.metadata().tokenUsage()
                            : null;
                    AiMessage aiMessage = response == null ? AiMessage.from("") : response.aiMessage();
                    outcomeRef.set(new RoundOutcome(aiMessage, usage, null));
                    done.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Streaming chat failed: {}", error.getMessage());
                    outcomeRef.set(new RoundOutcome(null, null, describeStreamError(error)));
                    done.countDown();
                }
            });

            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return new RoundOutcome(null, null, "AI 服务响应超时，请稍后再试。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RoundOutcome(null, null, "AI 服务请求被中断，请稍后再试。");
        } catch (Exception e) {
            log.error("Streaming chat invocation failed", e);
            return new RoundOutcome(null, null, describeStreamError(e));
        }

        RoundOutcome outcome = outcomeRef.get();
        if (outcome == null) {
            return new RoundOutcome(null, null, "AI 服务请求失败，请稍后再试。");
        }
        return outcome;
    }

    private record RoundOutcome(AiMessage aiMessage, TokenUsage usage, String error) {
    }

    private String describeStreamError(Throwable error) {
        String msg = error == null ? "" : String.valueOf(error.getMessage());
        if (msg.contains("403")) {
            return "AI 服务鉴权失败（403）。请检查 AI_API_KEY、AI_MODEL，以及 DeepSeek 账号是否已开通对应模型权限。";
        }
        return "AI 服务请求失败，请稍后再试。";
    }

    private String callAi(String prompt) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("your-")) {
            log.warn("AI API key is not configured, skipping remote call");
            return null;
        }
        try {
            return callChatCompletions(List.of(Map.of("role", "user", "content", prompt)), maxTokens, readTimeoutMs, true);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("429") || msg.contains("quota") || msg.contains("RESOURCE_EXHAUSTED"))) {
                log.warn("AI API quota exhausted (429), skipping: {}", msg.length() > 100 ? msg.substring(0, 100) : msg);
            } else {
                log.error("AI API call error: {}", msg);
            }
            return null;
        }
    }

    private String callChatCompletions(List<Map<String, Object>> messages, int requestedMaxTokens,
                                       int timeoutMs, boolean extractJson) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(messages, requestedMaxTokens), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            String body = response.body();
            String snippet = body == null ? "" : body.replaceAll("\\s+", " ").trim();
            if (snippet.length() > 400) {
                snippet = snippet.substring(0, 400);
            }
            log.warn("AI provider API returned status: {}, body: {}", response.statusCode(), snippet);
            throw new IllegalStateException("AI provider API returned status " + response.statusCode() + (snippet.isBlank() ? "" : (": " + snippet)));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.warn("AI provider API returned no choices");
            return null;
        }

        String content = choices.get(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            log.warn("AI provider API returned empty content");
            return null;
        }

        content = content.trim();
        if (extractJson) {
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                content = content.substring(jsonStart, jsonEnd + 1);
            }
        }
        return content;
    }

    private String buildRequestBody(List<Map<String, Object>> messages, int requestedMaxTokens) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", requestedMaxTokens);
        return objectMapper.writeValueAsString(body);
    }

    private String getCached(String key) {
        try {
            Object val = redisTemplate.opsForValue().get(key);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.warn("Redis read failed, skipping cache: {}", e.getMessage());
            return null;
        }
    }

    private void cache(String key, String value, long ttlHours) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis write failed, skipping cache: {}", e.getMessage());
        }
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    private String buildLocalSummary(String title, String description) {
        if ((title == null || title.isBlank()) && (description == null || description.isBlank())) {
            return null;
        }

        String normalized = description == null ? "" : description
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`", " ")
                .replaceAll("#{1,6}\\s*", " ")
                .replaceAll("\\[(.*?)]\\((.*?)\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> parts = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            parts.add(title.trim());
        }
        if (!normalized.isBlank()) {
            int firstBreak = Math.min(normalized.length(), 80);
            parts.add(normalized.substring(0, firstBreak));
        }

        String summary = String.join("：", parts);
        summary = summary.replaceAll("\\s+", " ").trim();
        if (summary.length() > 110) {
            summary = summary.substring(0, 110);
        }
        return summary;
    }

}
