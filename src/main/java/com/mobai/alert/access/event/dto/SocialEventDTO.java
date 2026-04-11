package com.mobai.alert.access.event.dto;

import java.time.Instant;

/**
 * 通用社交事件输入对象。
 * 未来无论事件来自 RSS、Telegram、X 或其他文本流，
 * 都可以先归一到这个 DTO，再交由 {@code MarketEventService} 做标准化解析。
 */
public class SocialEventDTO {

    /**
     * 事件发生时间。
     */
    private Instant eventTime;

    /**
     * 来源名称或来源标识。
     */
    private String source;

    /**
     * 原始文本内容。
     */
    private String rawText;

    /**
     * 来源质量评分，通常取值在 0 到 1 之间。
     */
    private Double sourceQuality;

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public Double getSourceQuality() {
        return sourceQuality;
    }

    public void setSourceQuality(Double sourceQuality) {
        this.sourceQuality = sourceQuality;
    }
}
