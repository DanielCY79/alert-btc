package com.mobai.alert.notification.channel;

import com.mobai.alert.notification.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuNotifierTests {

    @Test
    void shouldSendInteractiveCardWithGreenHeaderForLongSignal() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":0}");
        FeishuNotifier notifier = new FeishuNotifier(restTemplate, "https://example.com/feishu");

        NotificationMessage message = new NotificationMessage(
                "**做多信号**",
                "做多信号",
                "BTCUSDT 交易信号 | 做多",
                NotificationMessage.HeaderTemplate.GREEN
        );

        notifier.send(message);

        Map<String, Object> body = captureBody(restTemplate);
        assertThat(body).containsEntry("msg_type", "interactive");

        Map<String, Object> card = mapValue(body, "card");
        Map<String, Object> header = mapValue(card, "header");
        Map<String, Object> title = mapValue(header, "title");
        List<Map<String, Object>> elements = listValue(card, "elements");
        Map<String, Object> firstElement = elements.get(0);
        Map<String, Object> text = mapValue(firstElement, "text");

        assertThat(header).containsEntry("template", "green");
        assertThat(title).containsEntry("content", "BTCUSDT 交易信号 | 做多");
        assertThat(firstElement).containsEntry("tag", "div");
        assertThat(text).containsEntry("tag", "lark_md");
        assertThat(text).containsEntry("content", "**做多信号**");
    }

    @Test
    void shouldSendInteractiveCardWithRedHeaderForShortSignal() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":0}");
        FeishuNotifier notifier = new FeishuNotifier(restTemplate, "https://example.com/feishu");

        NotificationMessage message = new NotificationMessage(
                "**做空信号**",
                "做空信号",
                "BTCUSDT 交易信号 | 做空",
                NotificationMessage.HeaderTemplate.RED
        );

        notifier.send(message);

        Map<String, Object> body = captureBody(restTemplate);
        Map<String, Object> card = mapValue(body, "card");
        Map<String, Object> header = mapValue(card, "header");

        assertThat(header).containsEntry("template", "red");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBody(RestTemplate restTemplate) {
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("https://example.com/feishu"), captor.capture(), eq(String.class));
        return (Map<String, Object>) captor.getValue().getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.get(key);
    }
}
