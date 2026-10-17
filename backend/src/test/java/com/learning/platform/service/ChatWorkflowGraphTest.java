package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.ChatToolRegistry;
import com.learning.platform.ai.ChatWorkflowState;
import com.learning.platform.ai.ResourceSearchTool;
import com.learning.platform.ai.image.ImageCollector;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聊天工作流图（Phase 1）回归：route 条件边按划词路由、
 * search 节点确定式调用资源检索、generate 复用流式生成、
 * 失败路径不中断协议。
 */
class ChatWorkflowGraphTest {

    /** 记录型 emitter：收集全部 SSE 事件供断言 */
    static class RecordingEmitter implements ChatStreamFactory.ChatStreamEmitter {
        final List<String> texts = new ArrayList<>();
        final List<String> tools = new ArrayList<>();
        final List<String> images = new ArrayList<>();
        final List<String> moderated = new ArrayList<>();
        int resets;

        @Override
        public void text(String chunk) {
            texts.add(chunk);
        }

        @Override
        public void tool(String toolName) {
            tools.add(toolName);
        }

        @Override
        public void reset() {
            resets++;
        }

        @Override
        public void images(String imagesJson) {
            images.add(imagesJson);
        }

        @Override
        public void moderated(String notice) {
            moderated.add(notice);
        }
    }

    @Mock
    private AiService aiService;

    @Mock
    private ChatToolRegistry chatToolRegistry;

    @Mock
    private ResourceSearchTool resourceSearchTool;

    @Mock
    private ImageCollector imageCollector;

    @Mock
    private ModerationService moderationService;

    @Mock
    private ContentRevisionService contentRevisionService;

    private final ChatPromptBuilder promptBuilder = new ChatPromptBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(chatToolRegistry.specifications()).thenReturn(List.of());
        when(chatToolRegistry.describeTools()).thenReturn(List.of());
        when(resourceSearchTool.name()).thenReturn("search_resources");
        // 默认审查放行；审查相关用例各自覆盖此桩
        when(moderationService.review(anyString()))
                .thenReturn(new ModerationService.ModerationResult(true, List.of(), "local"));
        // Mockito 默认：imageCollector.start(any()) 返回 null → 不进 imageCollect 节点
    }

    private ChatWorkflowService newService(boolean enabled) {
        return new ChatWorkflowService(aiService, chatToolRegistry, promptBuilder,
                resourceSearchTool, objectMapper, imageCollector,
                moderationService, contentRevisionService, enabled);
    }

    private ChatWorkflowService.WorkflowResult run(ChatWorkflowService service, String selectedText) {
        RecordingEmitter emitter = new RecordingEmitter();
        return service.run(1L, null, "解释一下这个概念", "/resources/1", "资源详情",
                "RESOURCE", 9L, selectedText, emitter);
    }

    @Test
    @DisplayName("无划词：直达 generate，全程不调资源检索工具")
    void noSelectedText_skipsSearch() {
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("直接生成的回答", 42, null));

        ChatWorkflowService.WorkflowResult result = run(newService(true), null);

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.content()).isEqualTo("直接生成的回答");
        assertThat(result.tokensUsed()).isEqualTo(42);
        verify(resourceSearchTool, never()).execute(anyString(), any());
        verify(aiService).streamGenerate(isNull(), anyString(), eq("解释一下这个概念"),
                any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("有划词：先确定式调 search_resources，检索结果注入生成提示词")
    void selectedText_runsSearchThenGenerate() {
        when(resourceSearchTool.execute(anyString(), any()))
                .thenReturn("{\"keyword\":\"happens-before\",\"count\":1,"
                        + "\"resources\":[{\"id\":9,\"title\":\"Java并发笔记\"}]}");
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("基于检索结果的回答", 88, null));

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = newService(true).run(1L, null,
                "解释一下这个概念", "/resources/1", "资源详情", "RESOURCE", 9L,
                "happens-before是什么意思？", emitter);

        assertThat(result.status()).isEqualTo("OK");
        // search 节点产出 tool 事件（沿用现有协议），关键词取自划选文本
        assertThat(emitter.tools).containsExactly("search_resources");
        verify(resourceSearchTool).execute(anyString(), any());
        // 检索结果注入生成节点的 system prompt
        verify(aiService).streamGenerate(isNull(), anyString(), eq("解释一下这个概念"),
                any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("有划词时生成节点的 system prompt 包含检索结果与标题")
    void searchResults_injectedIntoSystemPrompt() {
        when(resourceSearchTool.execute(anyString(), any()))
                .thenReturn("{\"resources\":[{\"id\":9,\"title\":\"Java并发笔记\"}]}");
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    String systemPrompt = inv.getArgument(1);
                    assertThat(systemPrompt).contains("Java并发笔记");
                    assertThat(systemPrompt).contains("资源检索结果");
                    return new AiService.StreamingResult("ok", 1, null);
                });

        run(newService(true), "happens-before");
    }

    @Test
    @DisplayName("检索工具失败：错误文本回传不中断，流程继续生成")
    void searchFailure_flowContinues() {
        when(resourceSearchTool.execute(anyString(), any())).thenReturn("工具执行失败：数据库不可用");
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("仍然回答", 10, null));

        ChatWorkflowService.WorkflowResult result = run(newService(true), "某个概念");

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.content()).isEqualTo("仍然回答");
    }

    @Test
    @DisplayName("生成失败：状态 FAILED，错误文案经 text 通道发出（与 V1 行为一致）")
    void generateFailure_reportsFailed() {
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult(null, null, "AI 服务请求失败，请稍后再试。"));

        ChatWorkflowService.WorkflowResult result = run(newService(true), null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.content()).isEqualTo("AI 服务请求失败，请稍后再试。");
    }

    @Test
    @DisplayName("开关关闭：isEnabled() 为 false，run 抛出不适用（控制器走 V1 回退路径）")
    void disabledFlag_exposesFallback() {
        ChatWorkflowService service = newService(false);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("取图会话点火：imageCollect 节点 await 后发 images 事件并写入状态")
    void imageSessionActive_emitsImagesEvent() {
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("带图回答", 10, null));
        ImageCollector.Session session = org.mockito.Mockito.mock(ImageCollector.Session.class);
        when(imageCollector.isActive()).thenReturn(true);
        when(imageCollector.start(anyString())).thenReturn(session);
        when(session.awaitJson()).thenReturn(
                "[{\"source\":\"pexels\",\"url\":\"https://a/1.jpg\",\"alt\":\"A\"}]");

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = newService(true).run(1L, null,
                "讲讲 Java 并发", "/", "AI学习平台", "", null, null, emitter);

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.imagesJson()).contains("pexels");
        assertThat(emitter.images).hasSize(1);
        assertThat(emitter.images.get(0)).contains("pexels");
    }

    @Test
    @DisplayName("取图会话未点火：不发 images 事件，流程直达终局")
    void imageSessionNull_noImagesEvent() {
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("纯文字回答", 10, null));

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = run(newService(true), null);

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.imagesJson()).isNull();
        assertThat(emitter.images).isEmpty();
    }

    @Test
    @DisplayName("关键词提取：剥离问句后缀与标点，保留核心词")
    void keywordExtraction_stripsQuestionSuffixes() {
        assertThat(ChatWorkflowService.extractKeyword("happens-before是什么意思？"))
                .isEqualTo("happens-before");
        assertThat(ChatWorkflowService.extractKeyword("什么是边际效应??"))
                .isEqualTo("边际效应");
        assertThat(ChatWorkflowService.extractKeyword("Java并发编程实战笔记"))
                .isEqualTo("Java并发编程实战笔记");
    }

    // ---------- 资源检索意图路由（无划词） ----------

    private ChatWorkflowService.WorkflowResult runWithMessage(
            ChatWorkflowService service, String message, RecordingEmitter emitter) {
        return service.run(1L, null, message, "/ai-chat", "AI 对话",
                "", null, null, emitter);
    }

    @Test
    @DisplayName("无划词但带资源检索意图：确定式调 search_resources 后再生成")
    void resourceIntentMessage_triggersSearch() {
        when(resourceSearchTool.execute(anyString(), any())).thenReturn(
                "{\"keyword\":\"Java\",\"count\":1,\"resources\":[{\"id\":3,\"title\":\"Java并发实战\"}]}");
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("平台有以下 Java 资源", 42, null));

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = runWithMessage(
                newService(true), "平台有哪些 Java 学习资源？", emitter);

        assertThat(result.status()).isEqualTo("OK");
        verify(resourceSearchTool).execute(contains("Java"), any());
        assertThat(emitter.tools).contains("search_resources");
    }

    @Test
    @DisplayName("无划词且无检索意图（概念解释/学习计划）：不调 search_resources")
    void nonIntentMessages_skipSearch() {
        when(aiService.streamGenerate(isNull(), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("直接回答", 10, null));

        runWithMessage(newService(true), "帮我制定两周的 Python 学习计划", new RecordingEmitter());
        runWithMessage(newService(true), "解释一下 happens-before 的含义", new RecordingEmitter());

        verify(resourceSearchTool, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("消息取词：剥检索意图词与资源名词，保留主题词")
    void searchKeyword_fromMessage() {
        assertThat(ChatWorkflowService.extractSearchKeyword("平台有哪些 Java 学习资源？"))
                .isEqualTo("Java");
        assertThat(ChatWorkflowService.extractSearchKeyword("推荐几本算法入门资料"))
                .isEqualTo("算法入门");
        assertThat(ChatWorkflowService.extractSearchKeyword("帮我找一些 SpringBoot 的视频教程"))
                .isEqualTo("SpringBoot");
        // 全部剥光时退回原消息关键词提取，不返回空串
        assertThat(ChatWorkflowService.extractSearchKeyword("有什么资源？")).isNotBlank();
    }

    // ---------- 取图查询词提取 ----------

    @Test
    @DisplayName("取图查询词：消息先提主题词再点火，整句不再直接透传给图片源")
    void imageQuery_fromMessage_topicWord() {
        // 回归：整句"平台有哪些 Java 学习资源？"丢给 Pexels 会搜出无关图片
        assertThat(ChatWorkflowService.extractImageQuery(null, "平台有哪些 Java 学习资源？"))
                .isEqualTo("Java");
        assertThat(ChatWorkflowService.extractImageQuery(null, "什么是冒泡排序？"))
                .isEqualTo("冒泡排序");
        assertThat(ChatWorkflowService.extractImageQuery(null, "为什么 HashMap 线程不安全？"))
                .isEqualTo("HashMap线程不安全");
    }

    @Test
    @DisplayName("取图查询词：划词优先；提不出干净主题返回空（宁缺毋滥不配图）")
    void imageQuery_selectedTextPriorityAndGuard() {
        assertThat(ChatWorkflowService.extractImageQuery("happens-before是什么意思", null))
                .isEqualTo("happens-before");
        // 疑问词剥光 → 空串，不点火取图
        assertThat(ChatWorkflowService.extractImageQuery(null, "为什么？")).isEmpty();
        // 闲聊长句无主题 → 空串
        assertThat(ChatWorkflowService.extractImageQuery(null, "哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈"))
                .isEmpty();
        assertThat(ChatWorkflowService.extractImageQuery(null, "")).isEmpty();
    }

    // ---------- Phase 3：审查回路 ----------

    /** 审查桩：按回答文本是否含"违规"给出 FLAGGED/PASS */
    private void moderateByContent() {
        when(moderationService.review(anyString())).thenAnswer(inv -> {
            String content = inv.getArgument(0);
            if (content != null && content.contains("违规")) {
                return new ModerationService.ModerationResult(false, List.of("违规片段"), "local");
            }
            return new ModerationService.ModerationResult(true, List.of(), "local");
        });
    }

    @Test
    @DisplayName("审查通过：无 reset/moderated 事件，最终回答写回记忆")
    void moderationPasses_noSideEffects() {
        when(aiService.streamGenerate(eq(7L), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("干净的回答", 10, null));
        moderateByContent();

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = newService(true).run(1L, 7L,
                "解释一下这个概念", "/resources/1", "资源详情", "RESOURCE", 9L, null, emitter);

        assertThat(result.status()).isEqualTo("OK");
        assertThat(emitter.resets).isZero();
        assertThat(emitter.moderated).isEmpty();
        verify(aiService).appendAssistantToMemory(eq(7L), eq("干净的回答"));
        verify(aiService, never()).removeLastExchange(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("违规回答：增量改写成功 → reset 后流式重发修订文本，审查通过收尾")
    void violation_thenRevisionSucceeds() {
        when(aiService.streamGenerate(eq(7L), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("回答里有违规片段", 10, null));
        moderateByContent();
        when(contentRevisionService.revise(anyString(), anyList()))
                .thenReturn("回答已修订为安全表述");

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = newService(true).run(1L, 7L,
                "解释一下这个概念", "/resources/1", "资源详情", "RESOURCE", 9L, null, emitter);

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.content()).isEqualTo("回答已修订为安全表述");
        assertThat(emitter.resets).isEqualTo(1);
        assertThat(emitter.texts).contains("回答已修订为安全表述");
        assertThat(emitter.tools).contains("revise_content");
        assertThat(emitter.moderated).isEmpty();
        verify(aiService).appendAssistantToMemory(eq(7L), eq("回答已修订为安全表述"));
    }

    @Test
    @DisplayName("改写两轮仍违规：重答一次（不重复写 user 消息），重答通过")
    void revisionsExhausted_thenRegenerationPasses() {
        when(aiService.streamGenerate(eq(7L), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("违规的回答", 10, null),
                        new AiService.StreamingResult("重答的安全回答", 12, null));
        moderateByContent();
        when(contentRevisionService.revise(anyString(), anyList()))
                .thenReturn("改写后仍违规的回答");

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = newService(true).run(1L, 7L,
                "解释一下这个概念", "/resources/1", "资源详情", "RESOURCE", 9L, null, emitter);

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.content()).isEqualTo("重答的安全回答");
        assertThat(emitter.resets).isEqualTo(3); // 改写×2 + 重答×1
        assertThat(emitter.moderated).isEmpty();
        verify(aiService).streamGenerate(eq(7L), anyString(), eq("解释一下这个概念"),
                any(), eq(false), any(), any(), any(), any()); // 重答不重复写 user 消息
        verify(aiService).appendAssistantToMemory(eq(7L), eq("重答的安全回答"));
    }

    @Test
    @DisplayName("改写与重答均失败：撤回——moderated 事件 + status=MODERATED + 清理本轮记忆")
    void alwaysViolating_moderated() {
        when(aiService.streamGenerate(eq(7L), anyString(), anyString(), any(), anyBoolean(),
                any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult("违规的回答", 10, null));
        when(moderationService.review(anyString()))
                .thenReturn(new ModerationService.ModerationResult(false, List.of("违规片段"), "local"));
        when(contentRevisionService.revise(anyString(), anyList())).thenReturn(null); // 改写不可用

        RecordingEmitter emitter = new RecordingEmitter();
        ChatWorkflowService.WorkflowResult result = newService(true).run(1L, 7L,
                "解释一下这个概念", "/resources/1", "资源详情", "RESOURCE", 9L, null, emitter);

        assertThat(result.status()).isEqualTo("MODERATED");
        assertThat(result.content()).isEqualTo("该内容涉嫌违规，无法显示");
        assertThat(emitter.moderated).containsExactly("该内容涉嫌违规，无法显示");
        verify(aiService).removeLastExchange(eq(7L));
        verify(aiService, never()).appendAssistantToMemory(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
