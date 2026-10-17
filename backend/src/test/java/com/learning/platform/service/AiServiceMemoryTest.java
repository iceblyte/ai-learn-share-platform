package com.learning.platform.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话记忆维护：撤回场景移除最后一轮 user(+Ai) 消息，system 消息保留。
 */
class AiServiceMemoryTest {

    private ChatMemoryStore store;
    private AiService service;
    private final List<ChatMessage> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store = mock(ChatMemoryStore.class);
        service = new AiService(null, null, 1000, 8000, null, store, null);
        when(store.getMessages(7L)).thenReturn(messages);
        messages.clear();
    }

    @Test
    @DisplayName("removeLastExchange：尾部 user+Ai 成对移除，system 与更早轮次保留")
    void removesLastUserAndAiPair() {
        SystemMessage system = SystemMessage.from("sys");
        UserMessage oldUser = UserMessage.from("旧的提问");
        AiMessage oldAi = AiMessage.from("旧的回答");
        UserMessage lastUser = UserMessage.from("违规提问");
        AiMessage lastAi = AiMessage.from("违规回答");
        messages.addAll(List.of(system, oldUser, oldAi, lastUser, lastAi));

        service.removeLastExchange(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).updateMessages(eq(7L), captor.capture());
        List<ChatMessage> saved = captor.getValue();
        assertThat(saved).containsExactly(system, oldUser, oldAi);
    }

    @Test
    @DisplayName("记忆中只有 system + user（回答未写入）：仅移除 user")
    void removesTrailingUserWithoutAi() {
        SystemMessage system = SystemMessage.from("sys");
        UserMessage user = UserMessage.from("违规提问");
        messages.addAll(List.of(system, user));

        service.removeLastExchange(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).updateMessages(eq(7L), captor.capture());
        assertThat(captor.getValue()).containsExactly(system);
    }

    @Test
    @DisplayName("store 抛异常：吞掉不外抛（撤回流程不因 Redis 故障中断）")
    void storeFailure_swallowed() {
        when(store.getMessages(7L)).thenThrow(new IllegalStateException("redis down"));
        service.removeLastExchange(7L); // 不抛异常即通过
    }
}
