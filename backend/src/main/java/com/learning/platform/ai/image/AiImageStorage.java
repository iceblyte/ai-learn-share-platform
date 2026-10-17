package com.learning.platform.ai.image;

import com.learning.platform.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 配图持久化：外链图下载落到平台存储（storage.type 决定 local/OSS），
 * 替换为永久 URL 后再随 images_json 落库——否则万相生成图（DashScope
 * 结果链接 24 小时过期）在历史记录里必然失效。
 *
 * <p>持久化发生在收集线程上，与正文流式并行，不增加回答延迟；
 * 同源相对路径（/undraw/*.svg，随前端打包）跳过；
 * 单图下载/存储失败保留原 URL 兜底，绝不阻塞、绝不抛出。</p>
 */
@Slf4j
@Component
public class AiImageStorage {

    /** 单图大小上限：超过视为异常文件，不落存储 */
    static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    static final String STORAGE_SUB_DIR = "ai-images";

    private final FileService fileService;
    private final boolean enabled;
    private final HttpClient httpClient;

    public AiImageStorage(FileService fileService,
                          @Value("${ai.image.persist:true}") boolean enabled) {
        this.fileService = fileService;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 是否需要持久化：http(s) 绝对地址需要，同源相对路径不需要 */
    static boolean needsPersist(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /** Content-Type → 扩展名（未知默认 .jpg，与 jpeg 一致） */
    static String extFromContentType(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (type.contains("png")) return ".png";
        if (type.contains("webp")) return ".webp";
        if (type.contains("gif")) return ".gif";
        return ".jpg";
    }

    /**
     * 逐图持久化：外链下载 → FileService 落存储 → 替换 URL。
     * 任一图失败保留原 URL，不影响其余图片与调用方。
     */
    public List<AnswerImage> persist(List<AnswerImage> images) {
        if (!enabled || images == null || images.isEmpty()) {
            return images == null ? List.of() : images;
        }
        List<AnswerImage> result = new ArrayList<>(images.size());
        for (AnswerImage image : images) {
            result.add(persistOne(image));
        }
        return result;
    }

    private AnswerImage persistOne(AnswerImage image) {
        if (image == null || !needsPersist(image.url())) {
            return image;
        }
        try {
            String permanentUrl = downloadAndStore(image.url());
            return new AnswerImage(image.source(), permanentUrl, image.alt());
        } catch (Exception e) {
            log.warn("AI image persist failed, keep original url: {} - {}",
                    image.url(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return image;
        }
    }

    /** 下载图片（8s 超时、10MB 上限），交给 FileService 落存储并返回永久 URL */
    private String downloadAndStore(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(8000))
                .GET()
                .build();
        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("下载失败 status=" + response.statusCode());
        }
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("下载内容为空");
        }
        if (body.length > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("图片超过大小上限 " + MAX_IMAGE_BYTES);
        }
        String ext = extFromContentType(
                response.headers().firstValue("Content-Type").orElse(""));
        return fileService.storeImageBytes(body, ext, STORAGE_SUB_DIR);
    }
}
