package com.learning.platform.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.learning.platform.common.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".pptx", ".mp4", ".zip"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final long AVATAR_MAX_SIZE = 5 * 1024 * 1024; // 5MB

    @Value("${storage.type:local}")
    private String storageType;

    @Value("${storage.local.path:./uploads}")
    private String localPath;

    @Value("${storage.local.url-prefix:/files}")
    private String localUrlPrefix;

    @Value("${storage.oss.endpoint:}")
    private String ossEndpoint;

    @Value("${storage.oss.bucket:}")
    private String ossBucket;

    @Value("${storage.oss.access-key-id:}")
    private String ossAccessKeyId;

    @Value("${storage.oss.access-key-secret:}")
    private String ossAccessKeySecret;

    @Value("${storage.oss.url-prefix:}")
    private String ossUrlPrefix;

    @Value("${storage.max-size:524288000}")
    private long maxSize;

    private OSS ossClient;

    private OSS getOssClient() {
        if (ossClient == null) {
            ossClient = new OSSClientBuilder().build(ossEndpoint, ossAccessKeyId, ossAccessKeySecret);
        }
        return ossClient;
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException("文件大小超过限制（最大500MB）");
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("不支持的文件类型，仅支持 PDF/DOCX/PPT/MP4/ZIP");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String newFileName = UUID.randomUUID() + extension;
        String objectKey = "resources/" + datePath + "/" + newFileName;

        String fileUrl;
        if ("oss".equalsIgnoreCase(storageType)) {
            fileUrl = uploadToOss(file, objectKey);
        } else {
            fileUrl = saveToLocal(file, datePath, newFileName, "resources");
        }

        return new StoredFile(originalName, fileUrl, file.getSize(), extension.replace(".", "").toUpperCase());
    }

    public String storeAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new BusinessException("头像大小不能超过5MB");
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (!IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("仅支持 JPG/PNG/GIF/WEBP 格式");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String newFileName = "avatar_" + UUID.randomUUID() + extension;
        String objectKey = "avatars/" + datePath + "/" + newFileName;

        if ("oss".equalsIgnoreCase(storageType)) {
            return uploadToOss(file, objectKey);
        } else {
            return saveToLocal(file, datePath, newFileName, "avatars");
        }
    }

    private String uploadToOss(MultipartFile file, String objectKey) {
        try (InputStream inputStream = file.getInputStream()) {
            getOssClient().putObject(ossBucket, objectKey, inputStream);
            return ossUrlPrefix + "/" + objectKey;
        } catch (IOException e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    private String saveToLocal(MultipartFile file, String datePath, String newFileName, String subDir) {
        Path dir = Paths.get(localPath, subDir, datePath);
        try {
            Files.createDirectories(dir);
            Path filePath = dir.resolve(newFileName);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
        return localUrlPrefix + "/" + subDir + "/" + datePath + "/" + newFileName;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    public record StoredFile(String fileName, String fileUrl, long fileSize, String fileType) {}
}
