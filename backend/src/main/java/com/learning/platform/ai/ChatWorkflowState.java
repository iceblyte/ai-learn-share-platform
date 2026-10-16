package com.learning.platform.ai;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 聊天工作流图状态（LangGraph4j {@link AgentState}）。
 *
 * <p><b>只放可序列化数据</b>：langgraph4j 在节点间对状态做深拷贝（Java 序列化），
 * emitter / ChatToolContext 等运行期对象一律经图外闭包传递（见
 * {@code ChatWorkflowService.RunContext}），不进状态。</p>
 *
 * <p>节点只读输入键、返回增量更新键。</p>
 */
public class ChatWorkflowState extends AgentState {

    public static final String MESSAGE = "message";
    public static final String ROUTE = "route";
    public static final String PAGE_TITLE = "pageTitle";
    public static final String PAGE_CONTEXT_TYPE = "pageContextType";
    public static final String PAGE_CONTEXT_ID = "pageContextId";
    public static final String SELECTED_TEXT = "selectedText";

    /** search 节点产出：search_resources 的 JSON 结果 */
    public static final String SEARCH_RESULTS_JSON = "searchResultsJson";
    /** generate/imageCollect/moderate 各节点产出 */
    public static final String ANSWER = "answer";
    public static final String TOKENS_USED = "tokensUsed";
    public static final String IMAGES_JSON = "imagesJson";
    /** 审查判定：PASS / FLAGGED */
    public static final String VERDICT = "verdict";
    public static final String FLAGGED_SEGMENTS = "flaggedSegments";
    public static final String FLAG_REASON = "flagReason";
    public static final String REVISE_ATTEMPTS = "reviseAttempts";
    public static final String REGEN_ATTEMPTS = "regenAttempts";
    /** 终态：OK / MODERATED / FAILED */
    public static final String STATUS = "status";
    public static final String ERROR = "error";

    public ChatWorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    public String message() {
        return text(MESSAGE);
    }

    public String route() {
        return text(ROUTE);
    }

    public String pageTitle() {
        return text(PAGE_TITLE);
    }

    public String pageContextType() {
        return text(PAGE_CONTEXT_TYPE);
    }

    public Optional<Long> pageContextId() {
        Object v = value(PAGE_CONTEXT_ID).orElse(null);
        return v instanceof Number number ? Optional.of(number.longValue()) : Optional.empty();
    }

    public String selectedText() {
        return text(SELECTED_TEXT);
    }

    public String searchResultsJson() {
        return text(SEARCH_RESULTS_JSON);
    }

    public String answer() {
        return text(ANSWER);
    }

    public Integer tokensUsed() {
        Object v = value(TOKENS_USED).orElse(null);
        return v instanceof Number number ? number.intValue() : null;
    }

    public String imagesJson() {
        return text(IMAGES_JSON);
    }

    public String verdict() {
        return text(VERDICT);
    }

    @SuppressWarnings("unchecked")
    public List<String> flaggedSegments() {
        Object v = value(FLAGGED_SEGMENTS).orElse(null);
        return v instanceof List<?> list ? (List<String>) list : List.of();
    }

    public String flagReason() {
        return text(FLAG_REASON);
    }

    public int reviseAttempts() {
        return intValue(REVISE_ATTEMPTS);
    }

    public int regenAttempts() {
        return intValue(REGEN_ATTEMPTS);
    }

    public String status() {
        return text(STATUS);
    }

    public String error() {
        return text(ERROR);
    }

    private String text(String key) {
        Object v = value(key).orElse(null);
        return v instanceof String s ? s : "";
    }

    private int intValue(String key) {
        Object v = value(key).orElse(null);
        return v instanceof Number number ? number.intValue() : 0;
    }
}
