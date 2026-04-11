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
 * 企业微信通知实现。
 * 通过机器人 Webhook 发送 Markdown 形式的消息。
 */
@Component
@ConditionalOnProperty(value = "notification.enterprise-wechat.enabled", havingValue = "true")
public class EnterpriseWechatNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseWechatNotifier.class);

    private final RestTemplate restTemplate;
    private final String webhookUrl;

    public EnterpriseWechatNotifier(RestTemplate restTemplate,
                                    @Value("${notification.enterprise-wechat.webhook-url}") String webhookUrl) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
    }

    /**
     * 返回企业微信通道标识。
     */
    @Override
    public String channelName() {
        return "enterprise-wechat";
    }

    /**
     * 将通知消息封装为企业微信请求体并发送。
     */
    @Override
    public void send(NotificationMessage message) {
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");

        Map<String, String> markdown = new HashMap<>();
        markdown.put("content", message.markdownContent());
        body.put("markdown", markdown);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String response = restTemplate.postForObject(webhookUrl, request, String.class);
        log.info("企业微信告警发送完成，响应：{}", response);
    }
}
