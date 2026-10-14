package com.learning.platform.service;

/**
 * 聊天请求携带的页面上下文（扩展一）。
 * EMPTY 表示无上下文——此时聊天行为与 V1 完全一致。
 */
public record ChatContext(
        String type,
        Long resourceId,
        String title,
        String summary,
        String excerpt,
        String selectedText) {

    public static final String TYPE_NONE = "NONE";
    public static final String TYPE_RESOURCE = "RESOURCE";

    public static final ChatContext EMPTY =
            new ChatContext(TYPE_NONE, null, null, null, null, null);

    public boolean hasContext() {
        return type != null && !TYPE_NONE.equals(type);
    }
}
