package com.mobai.alert.notification.channel;

import com.mobai.alert.notification.model.NotificationMessage;

/**
 * 通知通道统一接口。
 * 各个平台实现只需暴露通道标识并实现发送逻辑。
 */
public interface AlertNotifier {

    /**
     * 返回通道名称，用于按配置选择实际发送器。
     */
    String channelName();

    /**
     * 发送统一结构的通知消息。
     */
    void send(NotificationMessage message);
}
