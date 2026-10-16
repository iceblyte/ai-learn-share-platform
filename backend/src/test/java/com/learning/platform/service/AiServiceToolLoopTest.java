package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.ChatTool;
import com.learning.platform.ai.ChatToolContext;
import com.learning.platform.ai.ChatToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * function calling 工具循环回归：模型决定调工具 → 注册表执行 →
 * 结果回填 → 模型再回答；覆盖正常、异常工具与轮次上限三条路径。
 */
class AiServiceToolLoopTest {

    @Mock
    private StreamingChatModel streamingModel;

    private AiService service;

    private AutoCloseable mocks;

    /** 测试工具：记录参数并返回固定文本；failures>0 时抛异常模拟工具故障 */
    static class RecordingTool extends ChatTool {
        String lastArgumentsJson;
        JsonNode lastArguments;
        int failures;

        RecordingTool(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public String name() {
            return "echo_tool";
        }

        @Override
        public String description() {
            return "测试工具：原样返回固定结果";
        }

        @Override
        protected dev.langchain4j.model.chat.request.json.JsonObjectSchema parameters() {
            return dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                    .addStringProperty("keyword", "测试关键词")
                    .required("keyword")
                    .build();
        }

        @Override
        protected String doExecute(JsonNode arguments, ChatToolContext context) {
            if (failures > 0) {
                failures--;
                throw new IllegalStateException("数据库连接失败");
            }
            this.lastArgumentsJson = arguments.toString();
            this.lastArguments = arguments;
            return "{\"result\":\"Java 并发编程\",\"id\":49}";
        }
    }

    private final RecordingTool tool = new RecordingTool(new ObjectMapper());

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new AiService(null, new ObjectMapper(), 3000, 8000, streamingModel,
                org.mockito.Mockito.mock(dev.langchain4j.store.memory.chat.ChatMemoryStore.class),
                new ChatToolRegistry(List.of(tool)));
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost");
        ReflectionTestUtils.setField(service, "model", "deepseek-test");
        ReflectionTestUtils.setField(service, "chatTimeoutMs", 30000);
    }

    /** 依次弹出预设响应：先吐 token 再 onComplete */
    private void enqueueResponses(AiMessage... aiMessages) {
        Deque<AiMessage> queue = new ArrayDeque<>(List.of(aiMessages));
        doAnswer(inv -> {
            StreamingChatResponseHandler handler = inv.getArgument(1);
            AiMessage message = queue.poll();
            handler.onPartialResponse(message.text() == null ? "" : message.text());
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(message).build());
            return null;
        }).when(streamingModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    @Test
    @DisplayName("模型调工具：工具执行结果回填后模型再回答，最终答案正确返回")
    void toolLoop_executesToolThenAnswers() {
        enqueueResponses(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("echo_tool")
                        .arguments("{\"keyword\":\"Java\"}")
                        .build()),
                AiMessage.from("《Java 并发编程》是平台内的高分资源。"));
        StringBuilder streamed = new StringBuilder();

        AiService.StreamingResult result =
                service.streamChat("介绍下这个资源", "你是学习助手", streamed::append);

        assertThat(result.failed()).isFalse();
        assertThat(result.content()).isEqualTo("《Java 并发编程》是平台内的高分资源。");
        assertThat(streamed.toString()).isEqualTo("《Java 并发编程》是平台内的高分资源。");
        assertThat(tool.lastArgumentsJson).contains("Java");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(streamingModel, times(2)).chat(captor.capture(), any(StreamingChatResponseHandler.class));

        // 两次请求都携带工具规格
        assertThat(captor.getAllValues().get(0).toolSpecifications()).isNotEmpty();
        assertThat(captor.getAllValues().get(1).toolSpecifications()).isNotEmpty();

        // 第二次请求：system + user + AiMessage(工具调用) + ToolExecutionResultMessage
        List<dev.langchain4j.data.message.ChatMessage> secondRound = captor.getAllValues().get(1).messages();
        assertThat(secondRound).hasSize(4);
        assertThat(secondRound.get(2).type()).isEqualTo(ChatMessageType.AI);
        assertThat(secondRound.get(3).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
        assertThat(((ToolExecutionResultMessage) secondRound.get(3)).text()).contains("Java 并发编程");
    }

    @Test
    @DisplayName("工具抛异常：错误文本回传给模型，聊天链路不中断")
    void toolFailure_containedAndReported() {
        tool.failures = 1;
        enqueueResponses(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("echo_tool")
                        .arguments("{\"keyword\":\"Java\"}")
                        .build()),
                AiMessage.from("工具暂时不可用，我根据已有知识回答。"));

        AiService.StreamingResult result =
                service.streamChat("hi", "你是学习助手", chunk -> {});

        assertThat(result.failed()).isFalse();
        assertThat(result.content()).isEqualTo("工具暂时不可用，我根据已有知识回答。");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(streamingModel, times(2)).chat(captor.capture(), any(StreamingChatResponseHandler.class));
        List<dev.langchain4j.data.message.ChatMessage> secondRound = captor.getAllValues().get(1).messages();
        assertThat(((ToolExecutionResultMessage) secondRound.get(3)).text()).contains("工具执行失败");
    }

    @Test
    @DisplayName("未知工具名：注册表返回错误文本，循环继续到模型给出回答")
    void unknownTool_reportedToModel() {
        enqueueResponses(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("no_such_tool")
                        .arguments("{}")
                        .build()),
                AiMessage.from("好的，我直接回答。"));

        AiService.StreamingResult result =
                service.streamChat("hi", "你是学习助手", chunk -> {});

        assertThat(result.failed()).isFalse();
        assertThat(result.content()).isEqualTo("好的，我直接回答。");
    }

    @Test
    @DisplayName("工具轮次上限：模型持续要求调工具时循环终止，不再无限往返")
    void toolLoop_terminatesAtRoundLimit() {
        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-loop")
                .name("echo_tool")
                .arguments("{\"keyword\":\"x\"}")
                .build());
        enqueueResponses(toolCall, toolCall, toolCall, toolCall, toolCall, toolCall);

        AiService.StreamingResult result =
                service.streamChat("hi", "你是学习助手", chunk -> {});

        // MAX_TOOL_ROUNDS=4：首轮 + 4 轮工具 = 5 次模型调用后终止
        verify(streamingModel, times(5)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        assertThat(result.failed()).isTrue();
        assertThat(result.errorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("匿名单轮无工具调用：一次模型往返直接完成（不额外调轮）")
    void directAnswer_singleRound() {
        enqueueResponses(AiMessage.from("直接回答"));
        AiService.StreamingResult result =
                service.streamChat("hi", "你是学习助手", chunk -> {});

        assertThat(result.content()).isEqualTo("直接回答");
        verify(streamingModel, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }
}
