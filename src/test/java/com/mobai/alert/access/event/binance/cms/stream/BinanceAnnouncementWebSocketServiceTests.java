package com.mobai.alert.access.event.binance.cms.stream;

import com.mobai.alert.access.event.binance.cms.rest.BinanceCmsRestClient;
import com.mobai.alert.access.event.binance.cms.support.BinanceCmsSigner;
import com.mobai.alert.access.event.service.MarketEventService;
import com.mobai.alert.notification.AlertNotificationService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceAnnouncementWebSocketServiceTests {

    @Test
    void shouldSyncServerTimeBeforeConnecting() {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        WebSocket webSocket = mock(WebSocket.class);
        BinanceCmsRestClient cmsRestClient = mock(BinanceCmsRestClient.class);
        MarketEventService marketEventService = mock(MarketEventService.class);
        AlertNotificationService alertNotificationService = mock(AlertNotificationService.class);
        when(cmsRestClient.getServerTime()).thenReturn(1744287500123L);
        when(okHttpClient.newWebSocket(any(Request.class), any(WebSocketListener.class))).thenReturn(webSocket);

        BinanceAnnouncementWebSocketService service = new BinanceAnnouncementWebSocketService(
                okHttpClient,
                new BinanceCmsSigner(),
                cmsRestClient,
                marketEventService,
                alertNotificationService
        );
        ReflectionTestUtils.setField(service, "configuredTopics", "com_announcement_en");
        ReflectionTestUtils.setField(service, "recvWindow", 30000L);
        ReflectionTestUtils.setField(service, "reconnectDelayMs", 0L);
        ReflectionTestUtils.setField(service, "cmsBaseUrl", "wss://api.binance.com/sapi/wss");
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "apiSecret", "test-api-secret");

        ReflectionTestUtils.invokeMethod(service, "refreshConnection", "test");

        verify(cmsRestClient).getServerTime();
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient).newWebSocket(requestCaptor.capture(), any(WebSocketListener.class));

        Request request = requestCaptor.getValue();
        assertEquals("test-api-key", request.header("X-MBX-APIKEY"));
        long timestamp = extractQueryValue(request, "timestamp");
        assertTrue(Math.abs(timestamp - 1744287500123L) < 250L, "timestamp should be close to Binance server time");
    }

    private long extractQueryValue(Request request, String key) {
        String query = request.url().query();
        for (String part : query.split("&")) {
            if (part.startsWith(key + "=")) {
                return Long.parseLong(part.substring((key + "=").length()));
            }
        }
        throw new AssertionError("Missing query param: " + key);
    }
}

