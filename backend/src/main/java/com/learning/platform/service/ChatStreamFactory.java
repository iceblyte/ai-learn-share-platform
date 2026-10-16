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

    public Flux<ServerSentEvent<String>> stream(Consumer<Consumer<String>> chatInvocation) {
        return Flux.<String>create(sink -> chatInvocation.accept(sink::next))
                .subscribeOn(Schedulers.boundedElastic())
                .map(chunk -> ServerSentEvent.<String>builder(chunk).event("message").build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder("done").event("done").build()))
                .onErrorResume(e -> {
                    log.warn("Reactive SSE chat stream failed: {}", e.getMessage());
                    return Mono.just(ServerSentEvent.<String>builder("服务器开小差了，请稍后再试。")
                            .event("error")
                            .build());
                });
    }
}
