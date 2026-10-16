package com.learning.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 流工厂回归测试：验证流必须以 done 事件正常收尾。
 * 修复前 Flux.create 回调返回后 sink 未 complete，collectList 会一直
 * 挂起直到超时——正是"AI 回复完了但发送按钮一直转圈"的根因。
 */
class ChatStreamFactoryTest {

    private final ChatStreamFactory factory = new ChatStreamFactory();

    @Test
    void streamEmitsMessageChunksThenDoneAndTerminates() {
        Flux<ServerSentEvent<String>> flux = factory.stream(onChunk -> {
            onChunk.accept("你");
            onChunk.accept("好");
        });

        // 修复前：流永不结束，这里会在 5 秒后超时抛异常
        List<ServerSentEvent<String>> events = flux.collectList().block(Duration.ofSeconds(5));

        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals("message", events.get(0).event());
        assertEquals("你", events.get(0).data());
        assertEquals("message", events.get(1).event());
        assertEquals("好", events.get(1).data());
        assertEquals("done", events.get(2).event());
    }

    @Test
    void streamEmitsErrorEventWhenChatInvocationFails() {
        Flux<ServerSentEvent<String>> flux = factory.stream(onChunk -> {
            throw new IllegalStateException("boom");
        });

        List<ServerSentEvent<String>> events = flux.collectList().block(Duration.ofSeconds(5));

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("error", events.get(0).event());
        assertNotNull(events.get(0).data());
        assertTrue(events.get(0).data().contains("服务器"));
    }
}
