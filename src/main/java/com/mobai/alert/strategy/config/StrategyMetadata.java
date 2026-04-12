package com.mobai.alert.strategy.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 当前运行策略的稳定元数据。
 * 用它给运行态缓存、通知去重以及导出文件提供统一命名空间，
 * 避免未来新增策略时互相覆盖状态。
 */
@Component
public class StrategyMetadata {

    private static final String DEFAULT_STRATEGY_TYPE = "priceaction";
    private static final String DEFAULT_STRATEGY_ID = "priceaction-3m-4h";
    private static final String DEFAULT_STRATEGY_LABEL = "Price Action 3m/4h";

    private final String id;
    private final String label;
    private final boolean standaloneEnabled;

    public StrategyMetadata(String id, String label, boolean standaloneEnabled) {
        this(DEFAULT_STRATEGY_TYPE, id, label, "", "", standaloneEnabled);
    }

    @Autowired
    public StrategyMetadata(
            @Value("${monitoring.strategy.type:priceaction}") String strategyType,
            @Value("${monitoring.strategy.id:priceaction-3m-4h}") String id,
            @Value("${monitoring.strategy.label:Price Action 3m/4h}") String label,
            @Value("${monitoring.strategy.default-id:}") String defaultId,
            @Value("${monitoring.strategy.default-label:}") String defaultLabel,
            @Value("${monitoring.strategy.standalone-enabled:true}") boolean standaloneEnabled
    ) {
        this.id = resolveId(strategyType, id, defaultId);
        this.label = resolveLabel(strategyType, label, defaultLabel);
        this.standaloneEnabled = standaloneEnabled;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public boolean standaloneEnabled() {
        return standaloneEnabled;
    }

    public String displayName() {
        return StringUtils.hasText(label) ? label : id;
    }

    public String namespace(String localKey) {
        if (!StringUtils.hasText(localKey)) {
            return id;
        }
        return id + "|" + localKey.trim();
    }

    private String resolveId(String strategyType, String configuredId, String defaultId) {
        String normalizedType = StringUtils.hasText(strategyType) ? strategyType.trim() : DEFAULT_STRATEGY_TYPE;
        String normalizedConfiguredId = StringUtils.hasText(configuredId) ? configuredId.trim() : "";
        if (StringUtils.hasText(normalizedConfiguredId)
                && !shouldReplaceLegacyDefault(normalizedType, normalizedConfiguredId, DEFAULT_STRATEGY_ID)) {
            return normalizedConfiguredId;
        }
        if (StringUtils.hasText(defaultId)) {
            return defaultId.trim();
        }
        return DEFAULT_STRATEGY_TYPE.equalsIgnoreCase(normalizedType) ? DEFAULT_STRATEGY_ID : normalizedType;
    }

    private String resolveLabel(String strategyType, String configuredLabel, String defaultLabel) {
        String normalizedType = StringUtils.hasText(strategyType) ? strategyType.trim() : DEFAULT_STRATEGY_TYPE;
        String normalizedConfiguredLabel = StringUtils.hasText(configuredLabel) ? configuredLabel.trim() : "";
        if (StringUtils.hasText(normalizedConfiguredLabel)
                && !shouldReplaceLegacyDefault(normalizedType, normalizedConfiguredLabel, DEFAULT_STRATEGY_LABEL)) {
            return normalizedConfiguredLabel;
        }
        if (StringUtils.hasText(defaultLabel)) {
            return defaultLabel.trim();
        }
        return DEFAULT_STRATEGY_TYPE.equalsIgnoreCase(normalizedType) ? DEFAULT_STRATEGY_LABEL : normalizedType;
    }

    private boolean shouldReplaceLegacyDefault(String strategyType, String configuredValue, String legacyDefaultValue) {
        return !DEFAULT_STRATEGY_TYPE.equalsIgnoreCase(strategyType) && legacyDefaultValue.equals(configuredValue);
    }
}
