package com.mobai.alert.notification.model;

/**
 * 标准通知消息载体。
 * 同时保存 Markdown 和纯文本两种格式，便于不同通道复用。
 */
public record NotificationMessage(String markdownContent,
                                  String plainTextContent) {
}
