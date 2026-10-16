package com.learning.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Consumer;

/**
 * Reactive SSE 流工厂（WebFlux Reactive 栈，混合架构）。
 *
 * <p>整体仍是 Spring MVC/Tomcat 主栈——MyBatis（JDBC）与 Servlet Security
 * 过滤器都是阻塞生态，全量 Reactive 不现实；仅流式聊天端点返回 Reactor
 * 类型。Spring MVC 检测到 classpath 上的 Reactor 适配器后，由
 * {@code ReactiveTypeHandler} 以 {@code text/event-stream} 异步流式写出，
 * 与 {@code SseEmitter} 相比多了背压与组合子语义。</p>
 *
 * <p>事件协议（两条聊天链路共用）：
 * <ul>
 *   <li>{@code event: message} + {@code data: <token 片段>} —— 正文增量转发；</li>
 *   <li>{@code event: tool} + {@code data: <工具名>} —— function calling
 *       工具开始执行（前端展示"调用 xx 工具"状态，不进消息正文）；</li>
 *   <li>{@code event: done} —— 正常结束（连接随后关闭）；</li>
 *   <li>{@code event: error} + {@code data: <文案>} —— 基础设施级失败
 *       （AI 链路自身的失败文案仍走 message 事件，与落库的 FAILED 消息一致）。</li>
 * </ul></p>
 *
 * <p>实现纪律：LangChain4j 的流式回调 + CountDownLatch 是阻塞式 API，
 * 用 {@code Flux.create} 包裹并 {@code subscribeOn(boundedElastic)} 把阻塞
 * 工作移出请求线程；客户端断开时 sink 被 cancel，后续 next 变为 no-op，
 * 服务侧落库记账不受影响。</p>
 */
@Slf4j
@Component
public class ChatStreamFactory {

    /** 聊天调用向流上写事件的双通道：正文增量 + 工具调用通知 */
    public interface ChatStreamEmitter {
        void text(String chunk);

        void tool(String toolName);
    }

    /** 兼容旧签名：只有正文通道的调用 */
    public Flux<ServerSentEvent<String>> stream(Consumer<Consumer<String>> chatInvocation) {
        return streamEvents(emitter -> chatInvocation.accept(emitter::text));
    }

    public Flux<ServerSentEvent<String>> streamEvents(Consumer<ChatStreamEmitter> chatInvocation) {
        return Flux.<ServerSentEvent<String>>create(sink -> {
                    // 阻塞式聊天调用（内部以 CountDownLatch 等待流结束），
                    // 正常返回即代表 AI 回答完毕，必须显式 complete：
                    // Flux.create 的回调返回并不会自动完成 sink，若不调用
                    // complete()，Flux 永不终止——done 事件（concatWith）不发、
                    // SSE 连接不关，前端读循环卡死（发送按钮一直转圈）。
                    chatInvocation.accept(new ChatStreamEmitter() {
                        @Override
                        public void text(String chunk) {
                            sink.next(ServerSentEvent.<String>builder(chunk).event("message").build());
                        }

                        @Override
                        public void tool(String toolName) {
                            sink.next(ServerSentEvent.<String>builder(toolName).event("tool").build());
                        }
                    });
                    sink.complete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .concatWith(Mono.just(ServerSentEvent.<String>builder("done").event("done").build()))
                .onErrorResume(e -> {
                    log.warn("Reactive SSE chat stream failed: {}", e.getMessage());
                    return Flux.just(
                            ServerSentEvent.<String>builder("服务器开小差了，请稍后再试。").event("error").build());
                });
    }
}
