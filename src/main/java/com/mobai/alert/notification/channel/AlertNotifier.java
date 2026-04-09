package com.mobai.alert.notification.channel;

import com.mobai.alert.notification.model.NotificationMessage;

public interface AlertNotifier {

    String channelName();

    void send(NotificationMessage message);
}
