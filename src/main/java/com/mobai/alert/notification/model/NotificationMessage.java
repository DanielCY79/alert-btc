package com.mobai.alert.notification.model;

/**
 * 标准通知消息载体。
 * 同时保存 Markdown / 纯文本内容，以及卡片标题与标题颜色，便于不同渠道复用。
 */
public record NotificationMessage(String markdownContent,
                                  String plainTextContent,
                                  String cardTitle,
                                  HeaderTemplate headerTemplate) {

    public NotificationMessage(String markdownContent, String plainTextContent) {
        this(markdownContent, plainTextContent, null, HeaderTemplate.BLUE);
    }

    public enum HeaderTemplate {
        BLUE("blue"),
        GREEN("green"),
        RED("red");

        private final String template;

        HeaderTemplate(String template) {
            this.template = template;
        }

        public String template() {
            return template;
        }
    }
}
