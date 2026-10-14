package com.learning.platform.service;

import com.learning.platform.dto.AiChatRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 页面上下文解析（扩展一）：把前端透传的 pageContextType/pageContextId
 * 富化为服务端可信的上下文快照——标题、AI 摘要、正文截断。
 *
 * 纪律：资源缺失或类型未知时一律降级为空上下文，聊天链路不因此失败；
 * 正文超长时按用户问题关键词做粗排截断（约 2000 字预算）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatContextService {

    /** 正文注入模型窗口的字符预算（约 2000 字，见扩展设计 §3.3） */
    static final int EXCERPT_BUDGET = 2000;

    private final ResourceMapper resourceMapper;

    public ChatContext resolve(AiChatRequest request) {
        if (request == null || !ChatContext.TYPE_RESOURCE.equals(request.getPageContextType())) {
            return ChatContext.EMPTY;
        }
        Long resourceId = request.getPageContextId();
        if (resourceId == null) {
            return ChatContext.EMPTY;
        }

        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || (resource.getIsDeleted() != null && resource.getIsDeleted() == 1)) {
            log.info("Page context resource {} not found, degrading to no-context chat", resourceId);
            return ChatContext.EMPTY;
        }

        return new ChatContext(
                ChatContext.TYPE_RESOURCE,
                resourceId,
                resource.getTitle(),
                resource.getAiSummary(),
                buildExcerpt(resource.getDescription(), request.getMessage()),
                request.getSelectedText());
    }

    /**
     * 正文截断：不超预算直接返回；超预算时按问题关键词重合度给段落打分，
     * 优先保留相关段落（保持原文段落顺序），无命中的关键词则回退取开头。
     */
    String buildExcerpt(String description, String message) {
        if (description == null || description.isBlank()) {
            return "";
        }
        if (description.length() <= EXCERPT_BUDGET) {
            return description;
        }

        List<String> paragraphs = splitParagraphs(description);
        Set<String> keywords = extractKeywords(message);
        if (keywords.isEmpty()) {
            return description.substring(0, EXCERPT_BUDGET);
        }

        List<ScoredParagraph> scored = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            scored.add(new ScoredParagraph(i, paragraphs.get(i), score(paragraphs.get(i), keywords)));
        }

        // 相关度高的段落优先入选，直至预算用尽
        List<ScoredParagraph> ranked = new ArrayList<>(scored);
        ranked.sort(Comparator.comparingInt((ScoredParagraph p) -> p.score).reversed());
        if (ranked.isEmpty() || ranked.get(0).score <= 0) {
            return description.substring(0, EXCERPT_BUDGET);
        }

        List<ScoredParagraph> chosen = new ArrayList<>();
        int used = 0;
        for (ScoredParagraph paragraph : ranked) {
            int cost = paragraph.text.length() + 1;
            if (used + cost > EXCERPT_BUDGET) {
                continue;
            }
            chosen.add(paragraph);
            used += cost;
        }
        if (chosen.isEmpty()) {
            return description.substring(0, EXCERPT_BUDGET);
        }

        chosen.sort(Comparator.comparingInt(p -> p.index));
        StringBuilder excerpt = new StringBuilder();
        for (ScoredParagraph paragraph : chosen) {
            if (excerpt.length() > 0) {
                excerpt.append('\n');
            }
            excerpt.append(paragraph.text);
        }
        return excerpt.toString();
    }

    private record ScoredParagraph(int index, String text, int score) {
    }

    private List<String> splitParagraphs(String description) {
        List<String> paragraphs = new ArrayList<>();
        for (String part : description.split("\n+")) {
            if (!part.isBlank()) {
                paragraphs.add(part.trim());
            }
        }
        return paragraphs;
    }

    private int score(String paragraph, Set<String> keywords) {
        int total = 0;
        for (String keyword : keywords) {
            int index = 0;
            while ((index = paragraph.indexOf(keyword, index)) != -1) {
                total++;
                index += keyword.length();
            }
        }
        return total;
    }

    /**
     * 从用户问题中提取粗排关键词：ASCII 词（长度≥2）+ 中文长词及其二元组。
     * 简单可预期即可——粗排只需要相对排序，不需要精准分词。
     */
    Set<String> extractKeywords(String message) {
        Set<String> keywords = new LinkedHashSet<>();
        if (message == null || message.isBlank()) {
            return keywords;
        }

        // 在中英文边界插入分隔，避免"什么是happens"被当成一个混合词整体丢弃
        String normalized = message
                .replaceAll("(?<=\\p{IsHan})(?=\\p{IsAlphabetic}|\\p{IsDigit})", " ")
                .replaceAll("(?<=\\p{IsAlphabetic}|\\p{IsDigit})(?=\\p{IsHan})", " ");

        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() < 2) {
                continue;
            }
            if (isPureCjk(token)) {
                keywords.add(token);
                for (int i = 0; i + 2 <= token.length(); i++) {
                    keywords.add(token.substring(i, i + 2));
                }
            } else {
                keywords.add(token.toLowerCase());
            }
        }
        return keywords;
    }

    private boolean isPureCjk(String token) {
        return token.codePoints().allMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }
}
