package com.mobai.alert.notification;

import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.notification.channel.AlertNotifier;
import com.mobai.alert.notification.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知服务测试，校验市场事件消息会发送到选定渠道。
 */
class AlertNotificationServiceTests {

    /**
     * 市场事件通知应包含标题与原始链接，方便快速追踪来源。
     */
    @Test
    void shouldSendMarketEventMessageToSelectedChannel() {
        AlertNotifier notifier = mock(AlertNotifier.class);
        when(notifier.channelName()).thenReturn("feishu");
        AlertNotificationService service = new AlertNotificationService(List.of(notifier));
        ReflectionTestUtils.setField(service, "notificationChannel", "feishu");

        MarketEventDTO event = new MarketEventDTO();
        event.setSource("gdelt_doc");
        event.setEventTime(Instant.parse("2026-04-10T15:30:00Z"));
        event.setEntity("BTC");
        event.setEventType("regulation");
        event.setSentiment("bearish");
        event.setConfidence(0.92);
        event.setNovelty(1.0);
        event.setMentionVelocity(2.0);
        event.setRawText("SEC delays Bitcoin ETF decision example.com English United States");

        service.sendMarketEvent(event, "SEC delays Bitcoin ETF decision", "https://example.com/btc-etf");

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifier).send(captor.capture());
        assertTrue(captor.getValue().plainTextContent().contains("SEC delays Bitcoin ETF decision"));
        assertTrue(captor.getValue().plainTextContent().contains("https://example.com/btc-etf"));
    }
}
