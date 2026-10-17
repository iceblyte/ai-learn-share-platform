package com.learning.platform.ai.image;

import com.learning.platform.service.FileService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * AI 配图持久化：外链图（万相生成图 24h 过期、Pexels 第三方热链）下载落到
 * 平台存储并替换 URL；同源相对路径跳过；单图失败保留原 URL 兜底。
 * 下载用真实 HTTP（内建 HttpServer 起本地桩服务），存储用 Mockito 桩。
 */
class AiImageStorageTest {

    private FileService fileService;
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        fileService = Mockito.mock(FileService.class);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serve(String path, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("外链图下载落存储并替换为永久 URL，alt 保留")
    void externalUrl_downloadedAndReplaced() {
        byte[] bytes = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
        serve("/img.png", "image/png", bytes);
        String tempUrl = baseUrl() + "/img.png";
        when(fileService.storeImageBytes(any(), anyString(), anyString()))
                .thenReturn("/files/ai-images/2026/10/x.png");

        List<AnswerImage> result = new AiImageStorage(fileService, true)
                .persist(List.of(new AnswerImage("wanx", tempUrl, "Java 配图")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url()).isEqualTo("/files/ai-images/2026/10/x.png");
        assertThat(result.get(0).source()).isEqualTo("wanx");
        assertThat(result.get(0).alt()).isEqualTo("Java 配图");
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileService).storeImageBytes(dataCaptor.capture(), eq(".png"), eq("ai-images"));
        assertThat(dataCaptor.getValue()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("同源相对路径（unDraw）不下载、URL 原样")
    void relativeUrl_skipped() {
        List<AnswerImage> images = List.of(new AnswerImage("undraw", "/undraw/code-thinking.svg", "插图"));

        List<AnswerImage> result = new AiImageStorage(fileService, true).persist(images);

        assertThat(result).isEqualTo(images);
        verify(fileService, never()).storeImageBytes(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("下载/存储失败：保留原 URL 兜底，不抛出")
    void storeFailure_keepsOriginalUrl() {
        serve("/broken.jpg", "image/jpeg", "x".getBytes(StandardCharsets.UTF_8));
        String url = baseUrl() + "/broken.jpg";
        when(fileService.storeImageBytes(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("存储不可用"));

        List<AnswerImage> result = new AiImageStorage(fileService, true)
                .persist(List.of(new AnswerImage("wanx", url, "配图")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url()).isEqualTo(url);
    }

    @Test
    @DisplayName("持久化开关关闭：全部原样透传")
    void disabled_passthrough() {
        List<AnswerImage> images = List.of(new AnswerImage("wanx", "https://b/2.png", "B"));

        List<AnswerImage> result = new AiImageStorage(fileService, false).persist(images);

        assertThat(result).isEqualTo(images);
        verify(fileService, never()).storeImageBytes(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Content-Type → 扩展名映射：jpeg/webp/gif/png/未知")
    void extFromContentType_maps() {
        assertThat(AiImageStorage.extFromContentType("image/png")).isEqualTo(".png");
        assertThat(AiImageStorage.extFromContentType("image/jpeg")).isEqualTo(".jpg");
        assertThat(AiImageStorage.extFromContentType("image/webp")).isEqualTo(".webp");
        assertThat(AiImageStorage.extFromContentType("image/gif")).isEqualTo(".gif");
        assertThat(AiImageStorage.extFromContentType("")).isEqualTo(".jpg");
    }
}
