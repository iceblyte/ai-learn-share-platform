package com.learning.platform.service;

import com.learning.platform.entity.SystemConfig;
import com.learning.platform.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统配置：管理员后台运行时开关的读写。
 *
 * <p>配置项读取直接查库（仅在资源发布/保存与后台管理操作时触发，
 * 频次极低，不做缓存以保证开关即时生效）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    /** 资源发布/保存后是否自动生成 AI 智能摘要（关闭节省 token） */
    public static final String KEY_AI_SUMMARY_AUTO = "ai_summary_auto_enabled";

    private final SystemConfigMapper systemConfigMapper;

    /** AI 智能摘要自动生成开关；配置行缺失时默认开启（保持历史行为） */
    public boolean isAiSummaryAutoEnabled() {
        return readBoolean(KEY_AI_SUMMARY_AUTO, true);
    }

    public void setAiSummaryAutoEnabled(boolean enabled) {
        writeBoolean(KEY_AI_SUMMARY_AUTO, enabled);
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        try {
            SystemConfig config = systemConfigMapper.selectById(key);
            if (config == null || config.getConfigValue() == null) {
                return defaultValue;
            }
            return "true".equalsIgnoreCase(config.getConfigValue().trim());
        } catch (Exception e) {
            log.warn("Failed to read system config {}: {}, fallback to {}", key, e.getMessage(), defaultValue);
            return defaultValue;
        }
    }

    private void writeBoolean(String key, boolean value) {
        SystemConfig config = systemConfigMapper.selectById(key);
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(String.valueOf(value));
            config.setRemark("");
            systemConfigMapper.insert(config);
        } else {
            config.setConfigValue(String.valueOf(value));
            systemConfigMapper.updateById(config);
        }
        log.info("System config {} set to {}", key, value);
    }
}
