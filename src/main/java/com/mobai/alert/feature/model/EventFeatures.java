package com.mobai.alert.feature.model;

import java.math.BigDecimal;

/**
 * 事件特征对象。
 * 保存相关事件数量、最新事件信息以及多空偏置与冲击强度。
 */
public class EventFeatures {
    /**
     * 是否存在相关事件。
     */
    private boolean hasRelevantEvents;
    /**
     * 相关事件总数。
     */
    private Integer relevantEventCount;
    /**
     * 利多事件数量。
     */
    private Integer bullishEventCount;
    /**
     * 利空事件数量。
     */
    private Integer bearishEventCount;
    /**
     * 中性事件数量。
     */
    private Integer neutralEventCount;
    /**
     * 最新事件类型。
     */
    private String latestEventType;
    /**
     * 最新事件情绪。
     */
    private String latestEventSentiment;
    /**
     * 最新事件来源。
     */
    private String latestEventSource;
    /**
     * 最新事件置信度。
     */
    private Double latestEventConfidence;
    /**
     * 最新相关事件距今时长。
     */
    private Long latestEventAgeMs;
    /**
     * 利多事件累计分数。
     */
    private BigDecimal bullishScore;
    /**
     * 利空事件累计分数。
     */
    private BigDecimal bearishScore;
    /**
     * 事件方向偏置分数。
     */
    private BigDecimal eventBiasScore;
    /**
     * 事件冲击强度分数。
     */
    private BigDecimal eventShockScore;

    public boolean isHasRelevantEvents() {
        return hasRelevantEvents;
    }

    public void setHasRelevantEvents(boolean hasRelevantEvents) {
        this.hasRelevantEvents = hasRelevantEvents;
    }

    public Integer getRelevantEventCount() {
        return relevantEventCount;
    }

    public void setRelevantEventCount(Integer relevantEventCount) {
        this.relevantEventCount = relevantEventCount;
    }

    public Integer getBullishEventCount() {
        return bullishEventCount;
    }

    public void setBullishEventCount(Integer bullishEventCount) {
        this.bullishEventCount = bullishEventCount;
    }

    public Integer getBearishEventCount() {
        return bearishEventCount;
    }

    public void setBearishEventCount(Integer bearishEventCount) {
        this.bearishEventCount = bearishEventCount;
    }

    public Integer getNeutralEventCount() {
        return neutralEventCount;
    }

    public void setNeutralEventCount(Integer neutralEventCount) {
        this.neutralEventCount = neutralEventCount;
    }

    public String getLatestEventType() {
        return latestEventType;
    }

    public void setLatestEventType(String latestEventType) {
        this.latestEventType = latestEventType;
    }

    public String getLatestEventSentiment() {
        return latestEventSentiment;
    }

    public void setLatestEventSentiment(String latestEventSentiment) {
        this.latestEventSentiment = latestEventSentiment;
    }

    public String getLatestEventSource() {
        return latestEventSource;
    }

    public void setLatestEventSource(String latestEventSource) {
        this.latestEventSource = latestEventSource;
    }

    public Double getLatestEventConfidence() {
        return latestEventConfidence;
    }

    public void setLatestEventConfidence(Double latestEventConfidence) {
        this.latestEventConfidence = latestEventConfidence;
    }

    public Long getLatestEventAgeMs() {
        return latestEventAgeMs;
    }

    public void setLatestEventAgeMs(Long latestEventAgeMs) {
        this.latestEventAgeMs = latestEventAgeMs;
    }

    public BigDecimal getBullishScore() {
        return bullishScore;
    }

    public void setBullishScore(BigDecimal bullishScore) {
        this.bullishScore = bullishScore;
    }

    public BigDecimal getBearishScore() {
        return bearishScore;
    }

    public void setBearishScore(BigDecimal bearishScore) {
        this.bearishScore = bearishScore;
    }

    public BigDecimal getEventBiasScore() {
        return eventBiasScore;
    }

    public void setEventBiasScore(BigDecimal eventBiasScore) {
        this.eventBiasScore = eventBiasScore;
    }

    public BigDecimal getEventShockScore() {
        return eventShockScore;
    }

    public void setEventShockScore(BigDecimal eventShockScore) {
        this.eventShockScore = eventShockScore;
    }
}
