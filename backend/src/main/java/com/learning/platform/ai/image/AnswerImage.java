package com.learning.platform.ai.image;

/**
 * 回答配图：来源（pexels/undraw/wanx）、图片 URL（可相对）、替代文本。
 */
public record AnswerImage(String source, String url, String alt) {
}
