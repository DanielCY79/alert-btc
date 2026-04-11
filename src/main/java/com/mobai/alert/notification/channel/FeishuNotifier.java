package com.mobai.alert.notification.channel;

import com.mobai.alert.notification.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 飞书通知实现。
 * 当前使用简单文本消息格式推送交易提醒。
 */
@Component
@ConditionalOnProperty(value = "notification.feishu.enabled", havingValue = "true")
public class FeishuNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(FeishuNotifier.class);

    private final RestTemplate restTemplate;
    private final String webhookUrl;

    public FeishuNotifier(RestTemplate restTemplate,
                          @Value("${notification.feishu.webhook-url}") String webhookUrl) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
    }

    /**
     * 返回飞书通道标识。
     */
    @Override
    public String channelName() {
        return "feishu";
    }

    /**
     * 将通知消息转换为飞书文本请求并发送。
     */
    @Override
    public void send(NotificationMessage message) {
        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "text");

        Map<String, String> content = new HashMap<>();
        content.put("text", message.plainTextContent());
        body.put("content", content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String response = restTemplate.postForObject(webhookUrl, request, String.class);
        log.info("飞书告警发送完成，响应：{}", response);
    }
}
