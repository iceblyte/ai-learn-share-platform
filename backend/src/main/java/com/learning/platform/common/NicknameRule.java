package com.learning.platform.common;

/**
 * 昵称规则：不允许任何空白字符（半角/全角空格、Tab、换行、零宽空格等）。
 *
 * <p>背景：评论的 @提及渲染（前端 splitMentions）与通知/触发匹配（后端
 * MENTION_PATTERN）都按「@ + 非空白连续段」提取昵称——带空格的昵称
 * （如"张 三"）永远无法被 @ 完整命中。AI 助手昵称曾因此踩坑，本规则
 * 在注册、资料编辑、管理员建号三个昵称入口统一拦截。</p>
 */
public final class NicknameRule {

    private NicknameRule() {
    }

    /** 是否为合法昵称：不含任何空白类字符（null 视为合法，由调用方决定缺省行为） */
    public static boolean isValid(String nickname) {
        if (nickname == null) {
            return true;
        }
        for (char c : nickname.toCharArray()) {
            if (Character.isWhitespace(c) || c == '\u3000' /* 全角空格 */
                    || c == '\u00A0' /* 不换行空格 */ || c == '\u200B' /* 零宽空格 */) {
                return false;
            }
        }
        return true;
    }

    /** 校验不通过直接抛业务异常（消息面向用户，说明会影响 @提及） */
    public static void validate(String nickname) {
        if (!isValid(nickname)) {
            throw new BusinessException("昵称不能包含空格或制表符，否则无法被 @提及 正确识别");
        }
    }
}
