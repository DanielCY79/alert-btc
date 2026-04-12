package com.mobai.alert.strategy.config;

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

    private static final String DEFAULT_STRATEGY_ID = "priceaction-3m-4h";

    private final String id;
    private final String label;
    private final boolean standaloneEnabled;

    public StrategyMetadata(
            @Value("${monitoring.strategy.id:priceaction-3m-4h}") String id,
            @Value("${monitoring.strategy.label:Price Action 3m/4h}") String label,
            @Value("${monitoring.strategy.standalone-enabled:true}") boolean standaloneEnabled
    ) {
        this.id = StringUtils.hasText(id) ? id.trim() : DEFAULT_STRATEGY_ID;
        this.label = StringUtils.hasText(label) ? label.trim() : "";
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
}
