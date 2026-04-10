package com.mobai.alert.access.binance.cms.dto;

/**
 * Binance CMS 鍏憡 DTO銆? */
public class BinanceAnnouncementDTO {

    /**
     * 鍏憡鍒嗙被 ID銆?     */
    private Integer catalogId;

    /**
     * 鍏憡鍒嗙被鍚嶇О锛屼緥濡?Delisting銆丯ew Listings銆?     */
    private String catalogName;

    /**
     * 鍏憡鍙戝竷鏃堕棿锛屾绉掓椂闂存埑銆?     */
    private Long publishDate;

    /**
     * 鍏憡鏍囬銆?     */
    private String title;

    /**
     * 鍏憡姝ｆ枃銆?     */
    private String body;

    /**
     * 鍏憡闄勫甫鍏嶈矗澹版槑銆?     */
    private String disclaimer;

    /**
     * 鎺ㄩ€佷富棰橈紝渚嬪 com_announcement_en銆?     */
    private String topic;

    public Integer getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(Integer catalogId) {
        this.catalogId = catalogId;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public void setCatalogName(String catalogName) {
        this.catalogName = catalogName;
    }

    public Long getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(Long publishDate) {
        this.publishDate = publishDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}

