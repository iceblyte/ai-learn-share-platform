package com.learning.platform.ai;

import com.learning.platform.ai.WebSource;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 工具执行上下文：模型调用工具时可用的"本次提问"环境信息。
 *
 * <p>userId 允许为空（游客提问）；pageContextId 指向用户当前浏览的资源，
 * 供 get_resource_detail 之类的工具在未显式给参时回退使用。</p>
 *
 * <p>webSources 是工具执行期的可变槽：web_search 执行后把来源留存于此，
 * 调用方在流结束后读取并经 SSE sources 事件推给前端展示。
 * {@link #NONE} 是跨请求共享的空实例，工具不得向其写入（防串扰）。</p>
 */
@Getter
public class ChatToolContext {

    public static final String TYPE_RESOURCE = "RESOURCE";

    public static final ChatToolContext NONE = new ChatToolContext(null, null, null, null);

    private final Long userId;
    private final String pageContextType;
    private final Long pageContextId;
    private final String selectedText;

    @Setter
    private List<WebSource> webSources;

    public ChatToolContext(Long userId, String pageContextType, Long pageContextId, String selectedText) {
        this.userId = userId;
        this.pageContextType = pageContextType;
        this.pageContextId = pageContextId;
        this.selectedText = selectedText;
    }

    /** 当前页面是否关联了资源（工具可将该资源作为缺省查询对象） */
    public boolean isResourcePage() {
        return TYPE_RESOURCE.equals(pageContextType) && pageContextId != null;
    }

    // 兼容原 record 风格的访问器（工具代码沿用 selectedText()/pageContextId() 等）
    public Long userId() { return userId; }

    public String pageContextType() { return pageContextType; }

    public Long pageContextId() { return pageContextId; }

    public String selectedText() { return selectedText; }
}
