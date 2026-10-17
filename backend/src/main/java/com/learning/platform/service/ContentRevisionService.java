package com.learning.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 增量改写服务（审查回路的"revise_content 工具"实现）。
 *
 * <p>只改违规片段、其余内容原样保留——比起整段重答，改动小、
 * 语义漂移少。模型失败（网络/超时/未配置）返回 null，由工作流
 * 决定进入下一轮审查/重答，本服务不重试。</p>
 *
 * <p>注意走 {@link AiService#completeText}（不做 JSON 大括号截取）：
 * 改写输出是普通文本，原文含代码块时大括号不能被误当 JSON 边界。</p>
 */
@Slf4j
@Service
public class ContentRevisionService {

    private static final int REVISION_CONTENT_LIMIT = 4000;
    private static final int FRAGMENT_LIMIT = 5;

    private final AiService aiService;
    private final int maxTokens;

    public ContentRevisionService(AiService aiService,
                                  @Value("${ai.moderation.revision-max-tokens:1024}") int maxTokens) {
        this.aiService = aiService;
        this.maxTokens = maxTokens;
    }

    /** 增量改写；不可用（入参非法或模型失败）返回 null */
    public String revise(String content, List<String> fragments) {
        if (content == null || content.isBlank() || fragments == null || fragments.isEmpty()) {
            return null;
        }
        String response = aiService.completeText(prompt(content, fragments), maxTokens, 20000);
        if (response == null || response.isBlank()) {
            log.info("Content revision unavailable (model returned nothing)");
            return null;
        }
        String revised = response.trim();
        // 模型原样退回视为改写失败，避免死循环消耗改写轮次
        return revised.equals(content.trim()) ? null : revised;
    }

    private String prompt(String content, List<String> fragments) {
        String trimmed = content.length() > REVISION_CONTENT_LIMIT
                ? content.substring(0, REVISION_CONTENT_LIMIT) : content;
        StringBuilder fragmentList = new StringBuilder();
        for (int i = 0; i < fragments.size() && i < FRAGMENT_LIMIT; i++) {
            fragmentList.append(i + 1).append(". ").append(fragments.get(i)).append("\n");
        }
        return """
                以下 AI 回答经内容审查发现违规片段。请只修改这些违规片段，使其合规且语义尽量不变，\
                其余内容必须逐字保留。直接输出修改后的完整回答，不要任何解释、前后缀或代码围栏。

                违规片段：
                %s
                原文：
                %s""".formatted(fragmentList.toString(), trimmed);
    }
}
