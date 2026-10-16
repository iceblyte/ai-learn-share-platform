package com.learning.platform.service;

import com.learning.platform.ai.ChatToolRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 扩展三：AiService 接入 LangChain4j 真流式（function calling 改造后回归）。
 *
 * <p>注意：1.x 的 MessageWindowChatMemory 直读直写 ChatMemoryStore
 * （messages() 每次从 store 重读并裁窗），因此这里用内存 Map 的
 * fake store 模拟真实行为，而不是固定返回值的 mock。</p>
 */
class AiServiceStreamTest {

    @Mock
    private StreamingChatModel streamingModel;

    @Mock
    private ChatMemoryStore memoryStore;

    /** 模拟真实 store 的读/写/删行为（MessageWindowChatMemory 直读直写） */
    private final Map<Object, List<ChatMessage>> memoryBacklog = new HashMap<>();

    private AiService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doAnswer(inv -> memoryBacklog.getOrDefault(inv.getArgument(0), List.of()))
                .when(memoryStore).getMessages(any());
        doAnswer(inv -> {
            memoryBacklog.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(memoryStore).updateMessages(any(), any());
        doAnswer(inv -> {
            memoryBacklog.remove(inv.getArgument(0));
            return null;
        }).when(memoryStore).deleteMessages(any());

        service = new AiService(null, null, 3000, 8000, streamingModel, memoryStore,
                new ChatToolRegistry(List.of()));
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost");
        ReflectionTestUtils.setField(service, "model", "deepseek-test");
        ReflectionTestUtils.setField(service, "chatTimeoutMs", 30000);
    }

    private void modelReplies(String fullText, int inputTokens, int outputTokens) {
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(1);
            handler.onPartialResponse(fullText.substring(0, 1));
            handler.onPartialResponse(fullText.substring(1));
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(fullText))
                    .metadata(ChatResponseMetadata.builder()
                            .tokenUsage(new TokenUsage(inputTokens, outputTokens))
                            .build())
                    .build());
            return null;
        }).when(streamingModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    private ArgumentCaptor<ChatRequest> requestCaptor() {
        return ArgumentCaptor.forClass(ChatRequest.class);
    }

    @Test
    @DisplayName("匿名单轮：恰好 system + user 两条消息，token 顺序转发，返回全文与 token 用量")
    void anonymousChat_singleTurn() {
        modelReplies("你好，同学", 10, 5);
        StringBuilder streamed = new StringBuilder();

        AiService.StreamingResult result =
                service.streamChat("什么是A？", "你是学习助手", streamed::append);

        assertThat(streamed.toString()).isEqualTo("你好，同学");
        assertThat(result.content()).isEqualTo("你好，同学");
        assertThat(result.tokensUsed()).isEqualTo(15);

        ArgumentCaptor<ChatRequest> captor = requestCaptor();
        verify(streamingModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));
        List<ChatMessage> sent = captor.getValue().messages();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) sent.get(0)).text()).isEqualTo("你是学习助手");
        assertThat(((UserMessage) sent.get(1)).singleText()).isEqualTo("什么是A？");
    }

    @Test
    @DisplayName("会话内提问：先加载记忆（历史 + 新消息），完成后把 AI 回答写回记忆")
    void sessionChat_usesMemoryAndWritesBack() {
        memoryBacklog.put(5L, List.of(
                UserMessage.from("什么是A？"), AiMessage.from("A 是……")));
        modelReplies("B 是另一个概念", 30, 8);
        StringBuilder streamed = new StringBuilder();

        AiService.StreamingResult result =
                service.streamChat(5L, "那B呢？", "你是学习助手", streamed::append);

        assertThat(streamed.toString()).isEqualTo("B 是另一个概念");
        assertThat(result.failed()).isFalse();

        ArgumentCaptor<ChatRequest> captor = requestCaptor();
        verify(streamingModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));
        List<ChatMessage> sent = captor.getValue().messages();
        // system + 历史2条 + 新 user 消息
        assertThat(sent).hasSize(4);
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(sent.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sent.get(1)).singleText()).isEqualTo("什么是A？");
        assertThat(((AiMessage) sent.get(2)).text()).isEqualTo("A 是……");
        assertThat(((UserMessage) sent.get(3)).singleText()).isEqualTo("那B呢？");

        // 完成后写回：含新 AiMessage，全量交给 store
        List<ChatMessage> written = memoryBacklog.get(5L);
        ChatMessage last = written.get(written.size() - 1);
        assertThat(last).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) last).text()).isEqualTo("B 是另一个概念");
    }

    @Test
    @DisplayName("记忆窗口 20 条：30 条历史裁剪后仍带上 system 与新消息")
    void sessionChat_windowCapsAt20() {
        List<ChatMessage> longHistory = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            longHistory.add(UserMessage.from("问题" + i));
        }
        memoryBacklog.put(5L, longHistory);
        modelReplies("ok", 1, 1);

        service.streamChat(5L, "最新问题", "你是学习助手", chunk -> {});

        ArgumentCaptor<ChatRequest> captor = requestCaptor();
        verify(streamingModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));
        List<ChatMessage> sent = captor.getValue().messages();
        assertThat(sent).hasSize(20);
        // system 置顶（alwaysKeepSystemMessageFirst），随后是保留的最近历史
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((UserMessage) sent.get(1)).singleText()).isEqualTo("问题13");
        // 最新问题在窗口内
        assertThat(((UserMessage) sent.get(sent.size() - 1)).singleText()).isEqualTo("最新问题");
    }

    @Test
    @DisplayName("模型报错：返回 failed 结果，降级文案通过 onChunk 送达前端")
    void streamError_degradesGracefully() {
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(1);
            handler.onError(new IllegalStateException("HTTP 403 Forbidden"));
            return null;
        }).when(streamingModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        StringBuilder streamed = new StringBuilder();

        AiService.StreamingResult result =
                service.streamChat("hi", "你是学习助手", streamed::append);

        assertThat(result.failed()).isTrue();
        assertThat(streamed.toString()).contains("403");
        assertThat(result.errorMessage()).contains("403");
    }

    @Test
    @DisplayName("API key 未配置：不调用模型，直接返回未配置降级")
    void unconfiguredKey_shortCircuits() {
        ReflectionTestUtils.setField(service, "apiKey", "your-api-key-here");
        StringBuilder streamed = new StringBuilder();

        AiService.StreamingResult result =
                service.streamChat("hi", "你是学习助手", streamed::append);

        assertThat(result.failed()).isTrue();
        assertThat(streamed.toString()).contains("AI 服务未配置");
        verify(streamingModel, never()).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    @Test
    @DisplayName("onComplete 未携带 token 用量时 tokensUsed 为 null，调用方按 0 记账")
    void missingTokenUsage_returnsNull() {
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(1);
            handler.onPartialResponse("回");
            handler.onPartialResponse("答");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("回答"))
                    .build());
            return null;
        }).when(streamingModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        AiService.StreamingResult result =
                service.streamChat("hi", "你是学习助手", chunk -> {});

        assertThat(result.content()).isEqualTo("回答");
        assertThat(result.tokensUsed()).isNull();
    }

    @Test
    @DisplayName("会话记忆第二轮：能带着第一轮问答继续（多轮一致性的数据基础）")
    void sessionChat_secondTurnSeesFirstTurn() {
        modelReplies("第二回答", 10, 10);
        service.streamChat(5L, "第一问", "你是学习助手", chunk -> {});
        // 第一轮完成后，store 中应有 4 条：system + user + ai（第二轮 load 会看到）
        modelReplies("第二轮回答", 20, 10);

        ArgumentCaptor<ChatRequest> captor = requestCaptor();
        Mockito.clearInvocations(streamingModel);
        service.streamChat(5L, "第二问", "你是学习助手", chunk -> {});
        verify(streamingModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));

        List<ChatMessage> sent = captor.getValue().messages();
        // system + 第一问 + 第一答 + 第二问
        assertThat(sent).hasSize(4);
        assertThat(((UserMessage) sent.get(1)).singleText()).isEqualTo("第一问");
        assertThat(((AiMessage) sent.get(2)).text()).isEqualTo("第二回答");
        assertThat(((UserMessage) sent.get(3)).singleText()).isEqualTo("第二问");
    }
}
