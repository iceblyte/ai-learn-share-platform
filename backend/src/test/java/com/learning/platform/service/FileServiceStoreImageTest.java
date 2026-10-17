package com.learning.platform.service;

import com.learning.platform.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FileService 字节落图（AI 配图持久化通道）：
 * local 类型写 ./uploads 子目录并返回 /files 前缀 URL；
 * 空内容与非法扩展名拒绝。
 */
class FileServiceStoreImageTest {

    @TempDir
    Path tempDir;

    private FileService localFileService() {
        FileService fileService = new FileService();
        ReflectionTestUtils.setField(fileService, "storageType", "local");
        ReflectionTestUtils.setField(fileService, "localPath", tempDir.toString());
        ReflectionTestUtils.setField(fileService, "localUrlPrefix", "/files");
        return fileService;
    }

    @Test
    @DisplayName("字节落图：写入 ai-images 子目录，URL 为 /files 前缀")
    void storeImageBytes_writesLocalFile() throws Exception {
        byte[] data = "png-bytes".getBytes(StandardCharsets.UTF_8);

        String url = localFileService().storeImageBytes(data, ".png", "ai-images");

        assertThat(url).startsWith("/files/ai-images/").endsWith(".png");
        // URL 路径去掉 /files 前缀即磁盘相对路径，文件确实写入了
        String relative = url.substring("/files/".length());
        Path written = tempDir.resolve(relative);
        assertThat(written).exists();
        assertThat(Files.readAllBytes(written)).isEqualTo(data);
    }

    @Test
    @DisplayName("空内容抛业务异常")
    void emptyBytes_rejected() {
        assertThatThrownBy(() -> localFileService().storeImageBytes(new byte[0], ".png", "ai-images"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("非法扩展名归一为 .jpg，不透传任意后缀")
    void illegalExtension_normalized() {
        String url = localFileService().storeImageBytes(
                "x".getBytes(StandardCharsets.UTF_8), ".exe", "ai-images");

        assertThat(url).endsWith(".jpg");
    }
}
