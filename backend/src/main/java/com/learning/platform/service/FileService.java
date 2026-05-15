package com.learning.platform.service;

import com.learning.platform.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "video/mp4",
            "application/zip",
            "application/x-zip-compressed"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".pptx", ".mp4", ".zip"
    );

    @Value("${storage.local.path:./uploads}")
    private String storagePath;

    @Value("${storage.local.url-prefix:/files}")
    private String urlPrefix;

    @Value("${storage.max-size:524288000}")
    private long maxSize;

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
        Path dir = Paths.get(storagePath, datePath);
        try {
            Files.createDirectories(dir);
            Path filePath = dir.resolve(newFileName);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }

        String fileUrl = urlPrefix + "/" + datePath + "/" + newFileName;
        return new StoredFile(originalName, fileUrl, file.getSize(), extension.replace(".", "").toUpperCase());
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    public record StoredFile(String fileName, String fileUrl, long fileSize, String fileType) {}
}
