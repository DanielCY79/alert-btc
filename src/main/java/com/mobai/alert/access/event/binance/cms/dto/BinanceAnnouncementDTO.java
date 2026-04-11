package com.mobai.alert.access.event.binance.cms.dto;

/**
 * Binance CMS 公告消息对象。
 * 该 DTO 用于承接 Binance CMS WebSocket 推送的公告正文，
 * 后续会被送入事件识别服务以及通知服务。
 */
public class BinanceAnnouncementDTO {

    /**
     * 公告分类 ID。
     */
    private Integer catalogId;

    /**
     * 公告分类名称，例如 {@code Delisting}、{@code New Listings}。
     */
    private String catalogName;

    /**
     * 公告发布时间，毫秒时间戳。
     */
    private Long publishDate;

    /**
     * 公告标题。
     */
    private String title;

    /**
     * 公告正文内容。
     */
    private String body;

    /**
     * 公告附带的免责声明或补充说明。
     */
    private String disclaimer;

    /**
     * WebSocket 订阅主题，例如 {@code com_announcement_en}。
     */
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
