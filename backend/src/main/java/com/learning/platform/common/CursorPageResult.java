package com.learning.platform.common;

import lombok.Data;

import java.util.List;

/**
 * 游标分页结果（扩展四）：适配"进入会话加载最新一页 + 向上翻更早消息"
 * 的无偏移翻页，替代深翻页场景下的 offset 分页。
 *
 * <p>records 按 id 倒序返回（最新在前）；nextCursor 为本页最旧一条消息
 * 的 id，作为下一页（更早消息）的查询游标；hasMore=false 表示已翻到
 * 会话开头，此时 nextCursor 为 null。</p>
 */
@Data
public class CursorPageResult<T> {

    private List<T> records;
    private Long nextCursor;
    private boolean hasMore;

    public static <T> CursorPageResult<T> of(List<T> records, Long nextCursor, boolean hasMore) {
        CursorPageResult<T> result = new CursorPageResult<>();
        result.setRecords(records);
        result.setNextCursor(nextCursor);
        result.setHasMore(hasMore);
        return result;
    }
}
