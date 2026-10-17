package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.moderation.SensitiveWordFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 回答审查服务（双通道）。
 *
 * <p>① 本地敏感词表（{@link SensitiveWordFilter}）先行：命中即违规，
 * 不消耗模型调用；② 本地干净且 AI 审查开启时，交给模型按 JSON 判定
 * 违规片段。审查自身失败（网络/超时/输出非法）一律<b>降级放行</b>——
 * 审查是增强能力，不能反过来卡死聊天主链路。</p>
 */
@Slf4j
@Service
public class ModerationService {

    /** 审查结论：clean=放行；fragments 为待改写的违规片段（供增量修改） */
    public record ModerationResult(boolean clean, List<String> fragments, String channel) {
        static ModerationResult pass(String channel) {
            return new ModerationResult(true, List.of(), channel);
        }
    }

    /** 单次送审正文上限（超出截断，防止审查提示词撑爆上下文） */
    private static final int REVIEW_CONTENT_LIMIT = 4000;
    private static final int AI_REVIEW_MAX_TOKENS = 256;
    private static final int MAX_FRAGMENTS = 5;
    private static final int FRAGMENT_MAX_LENGTH = 100;

    private final SensitiveWordFilter sensitiveWordFilter;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final boolean aiReviewEnabled;
    private final int aiReviewTimeoutMs;
    private final int revisionMaxTokens;

    public ModerationService(SensitiveWordFilter sensitiveWordFilter,
                             AiService aiService,
                             ObjectMapper objectMapper,
                             @Value("${ai.moderation.ai-review-enabled:true}") boolean aiReviewEnabled,
                             @Value("${ai.moderation.ai-review-timeout-ms:15000}") int aiReviewTimeoutMs,
                             @Value("${ai.moderation.revision-max-tokens:1024}") int revisionMaxTokens) {
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.aiReviewEnabled = aiReviewEnabled;
        this.aiReviewTimeoutMs = aiReviewTimeoutMs;
        this.revisionMaxTokens = revisionMaxTokens;
    }

    /** 审查一段 AI 回答；永不抛异常 */
    public ModerationResult review(String content) {
        if (content == null || content.isBlank()) {
            return ModerationResult.pass("empty");
        }
        // 通道一：本地敏感词表
        List<String> localHits = sensitiveWordFilter.findViolations(content);
        if (!localHits.isEmpty()) {
            return new ModerationResult(false, localHits, "local");
        }
        // 通道二：AI 审查（失败降级放行）
        if (!aiReviewEnabled) {
            return ModerationResult.pass("local-only");
        }
        String response = aiService.completeJson(reviewPrompt(content), AI_REVIEW_MAX_TOKENS, aiReviewTimeoutMs);
        if (response == null) {
            log.info("AI review unavailable, degrading to pass");
            return ModerationResult.pass("degraded");
        }
        return parseVerdict(response);
    }

    private ModerationResult parseVerdict(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.path("violation").asBoolean(false)) {
                return ModerationResult.pass("ai");
            }
            List<String> fragments = new ArrayList<>();
            for (JsonNode node : root.path("fragments")) {
                if (node.isTextual() && !node.asText().isBlank()) {
                    fragments.add(node.asText());
                }
                if (fragments.size() >= MAX_FRAGMENTS) {
                    break;
                }
            }
            if (fragments.isEmpty()) {
                // 模型判定违规但未给片段：整段作为待改写对象
                fragments = List.of("全文");
            }
            return new ModerationResult(false, fragments, "ai");
        } catch (Exception e) {
            log.info("AI review response unparsable, degrading to pass: {}",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ModerationResult.pass("degraded");
        }
    }

    private String reviewPrompt(String content) {
        String trimmed = content.length() > REVIEW_CONTENT_LIMIT
                ? content.substring(0, REVIEW_CONTENT_LIMIT) : content;
        return """
                你是学习平台的内容安全审查员。请判断以下 AI 回答是否包含违规内容：\
                违法违规、色情低俗、暴力恐吓、歧视辱骂、危险行为指导等。正常的技术、学习、科普内容一律放行。\
                只输出 JSON（不要任何其他文字）：\
                {"violation": true或false, "fragments": ["违规片段原文", ...], "reason": "简短理由"}\
                violation 为 true 时 fragments 必须给出违规片段原文（最多 5 条）。

                待审查文本：
                %s""".formatted(trimmed);
    }

    /** 改写调用的 max_tokens 上限（与审查共用配置） */
    int revisionMaxTokens() {
        return revisionMaxTokens;
    }
}
