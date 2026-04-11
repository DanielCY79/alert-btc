package com.mobai.alert.feature.model;

/**
 * 特征快照质量信息。
 * 用于描述价格、衍生品和事件数据的完整性与时效性。
 */
public class FeatureQuality {
    /**
     * 原始 K 线数量。
     */
    private Integer rawKlineCount;
    /**
     * 已收盘 K 线数量。
     */
    private Integer closedKlineCount;
    /**
     * 价格特征是否就绪。
     */
    private boolean priceReady;
    /**
     * 衍生品特征是否就绪。
     */
    private boolean derivativeReady;
    /**
     * 事件特征是否就绪。
     */
    private boolean eventReady;
    /**
     * 相关事件数量。
     */
    private Integer relevantEventCount;
    /**
     * 价格数据距今延迟。
     */
    private Long priceAgeMs;
    /**
     * 衍生品数据距今延迟。
     */
    private Long derivativeAgeMs;
    /**
     * 最新相关事件距今延迟。
     */
    private Long latestRelevantEventAgeMs;
    /**
     * 是否构成完整快照。
     */
    private boolean completeSnapshot;

    public Integer getRawKlineCount() {
        return rawKlineCount;
    }

    public void setRawKlineCount(Integer rawKlineCount) {
        this.rawKlineCount = rawKlineCount;
    }

    public Integer getClosedKlineCount() {
        return closedKlineCount;
    }

    public void setClosedKlineCount(Integer closedKlineCount) {
        this.closedKlineCount = closedKlineCount;
    }

    public boolean isPriceReady() {
        return priceReady;
    }

    public void setPriceReady(boolean priceReady) {
        this.priceReady = priceReady;
    }

    public boolean isDerivativeReady() {
        return derivativeReady;
    }

    public void setDerivativeReady(boolean derivativeReady) {
        this.derivativeReady = derivativeReady;
    }

    public boolean isEventReady() {
        return eventReady;
    }

    public void setEventReady(boolean eventReady) {
        this.eventReady = eventReady;
    }

    public Integer getRelevantEventCount() {
        return relevantEventCount;
    }

    public void setRelevantEventCount(Integer relevantEventCount) {
        this.relevantEventCount = relevantEventCount;
    }

    public Long getPriceAgeMs() {
        return priceAgeMs;
    }

    public void setPriceAgeMs(Long priceAgeMs) {
        this.priceAgeMs = priceAgeMs;
    }

    public Long getDerivativeAgeMs() {
        return derivativeAgeMs;
    }

    public void setDerivativeAgeMs(Long derivativeAgeMs) {
        this.derivativeAgeMs = derivativeAgeMs;
    }

    public Long getLatestRelevantEventAgeMs() {
        return latestRelevantEventAgeMs;
    }

    public void setLatestRelevantEventAgeMs(Long latestRelevantEventAgeMs) {
        this.latestRelevantEventAgeMs = latestRelevantEventAgeMs;
    }

    public boolean isCompleteSnapshot() {
        return completeSnapshot;
    }

    public void setCompleteSnapshot(boolean completeSnapshot) {
        this.completeSnapshot = completeSnapshot;
    }
}
