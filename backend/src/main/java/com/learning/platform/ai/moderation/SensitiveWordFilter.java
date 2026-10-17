package com.learning.platform.ai.moderation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 本地敏感词过滤器（审查回路第一通道）。
 *
 * <p>词表来自 classpath {@code moderation/sensitive-words.txt}（一行一词，
 * {@code #} 注释）。词表规模为演示级（几十词），线性包含匹配即可，
 * 无需 Aho-Corasick；词表升级到千级时再换自动机实现，接口不变。</p>
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private final List<String> words;

    public SensitiveWordFilter(Resource wordListResource) {
        this.words = load(wordListResource);
        log.info("Sensitive word filter initialized: {} words", words.size());
    }

    private List<String> load(Resource resource) {
        if (!resource.exists()) {
            log.warn("Sensitive word list missing: {}, local filter inactive", resource.getDescription());
            return List.of();
        }
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to load sensitive word list: {}, local filter inactive", e.getMessage());
            return List.of();
        }
    }

    /** 返回文本中命中的敏感词（保留词表原样大小写），无命中返回空列表 */
    public List<String> findViolations(String text) {
        if (text == null || text.isBlank() || words.isEmpty()) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return words.stream()
                .filter(word -> normalized.contains(word.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
