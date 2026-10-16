package com.learning.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工具注册表：自动收集、重名校验、未知工具兜底与描述生成。
 */
class ChatToolRegistryTest {

    static class StubTool extends ChatTool {
        private final String name;
        String lastArguments;
        String result = "ok";

        StubTool(ObjectMapper mapper, String name) {
            super(mapper);
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "测试工具 " + name;
        }

        @Override
        protected JsonObjectSchema parameters() {
            return JsonObjectSchema.builder()
                    .addStringProperty("keyword", "关键词")
                    .build();
        }

        @Override
        protected String doExecute(JsonNode arguments, ChatToolContext context) {
            lastArguments = arguments.toString();
            if ("boom".equals(arguments.path("keyword").asText())) {
                throw new IllegalStateException("模拟故障");
            }
            return result;
        }
    }

    @Test
    @DisplayName("自动收集工具：生成 ToolSpecification，描述清单可用于提示词")
    void collectsToolsAndBuildsSpecifications() {
        StubTool a = new StubTool(new ObjectMapper(), "tool_a");
        StubTool b = new StubTool(new ObjectMapper(), "tool_b");

        ChatToolRegistry registry = new ChatToolRegistry(List.of(a, b));

        List<ToolSpecification> specs = registry.specifications();
        assertThat(specs).extracting(ToolSpecification::name).containsExactly("tool_a", "tool_b");
        assertThat(specs.get(0).description()).isEqualTo("测试工具 tool_a");
        assertThat(registry.describeTools()).containsExactly("tool_a：测试工具 tool_a", "tool_b：测试工具 tool_b");
        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("重名工具：构造时直接失败，避免模型侧出现歧义")
    void rejectsDuplicateNames() {
        assertThatThrownBy(() -> new ChatToolRegistry(List.of(
                new StubTool(new ObjectMapper(), "same"),
                new StubTool(new ObjectMapper(), "same"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    @Test
    @DisplayName("未知工具：返回错误文本而非抛异常，聊天链路不受影响")
    void unknownToolReturnsErrorText() {
        ChatToolRegistry registry = new ChatToolRegistry(List.of(new StubTool(new ObjectMapper(), "tool_a")));

        assertThat(registry.execute("nope", "{}", ChatToolContext.NONE)).contains("未知的工具");
    }

    @Test
    @DisplayName("执行包装：异常被兜底为错误文本，结果超长被截断")
    void executeWrapsErrorsAndTruncates() {
        ObjectMapper mapper = new ObjectMapper();
        StubTool tool = new StubTool(mapper, "tool_a");
        ChatToolRegistry registry = new ChatToolRegistry(List.of(tool));

        tool.result = "boom";
        assertThat(registry.execute("tool_a", "{\"keyword\":\"boom\"}", ChatToolContext.NONE))
                .contains("工具执行失败").contains("模拟故障");

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            huge.append('字');
        }
        tool.result = huge.toString();
        String output = registry.execute("tool_a", "{}", ChatToolContext.NONE);
        assertThat(output).startsWith("字").contains("结果过长已截断");
    }
}
