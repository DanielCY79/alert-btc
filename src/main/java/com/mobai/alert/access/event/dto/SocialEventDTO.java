package com.mobai.alert.access.event.dto;

import java.time.Instant;

/**
 * 通用 social_event 输入 DTO。
 * 未来无论接 RSS、Telegram 还是其他社交源，都先落到这里。
 */
public class SocialEventDTO {

    private Instant eventTime;
    private String source;
    private String rawText;
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
