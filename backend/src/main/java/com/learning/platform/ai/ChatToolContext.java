package com.learning.platform.ai;

/**
 * 工具执行上下文：模型调用工具时可用的"本次提问"环境信息。
 *
 * <p>userId 允许为空（游客提问）；pageContextId 指向用户当前浏览的资源，
 * 供 get_resource_detail 之类的工具在未显式给参时回退使用。</p>
 */
public record ChatToolContext(Long userId, String pageContextType, Long pageContextId, String selectedText) {

    public static final String TYPE_RESOURCE = "RESOURCE";

    public static final ChatToolContext NONE = new ChatToolContext(null, null, null, null);

    /** 当前页面是否关联了资源（工具可将该资源作为缺省查询对象） */
    public boolean isResourcePage() {
        return TYPE_RESOURCE.equals(pageContextType) && pageContextId != null;
    }
}
