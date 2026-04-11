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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书通知实现。
 * 通过机器人 Webhook 发送交互式卡片消息。
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

    @Override
    public String channelName() {
        return "feishu";
    }

    @Override
    public void send(NotificationMessage message) {
        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", buildCard(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String response = restTemplate.postForObject(webhookUrl, request, String.class);
        log.info("飞书告警发送完成，响应：{}", response);
    }

    private Map<String, Object> buildCard(NotificationMessage message) {
        Map<String, Object> card = new HashMap<>();

        Map<String, Object> config = new HashMap<>();
        config.put("wide_screen_mode", true);
        config.put("enable_forward", true);
        card.put("config", config);

        Map<String, Object> header = new HashMap<>();
        Map<String, String> title = new HashMap<>();
        title.put("tag", "plain_text");
        title.put("content", resolveCardTitle(message));
        header.put("title", title);
        header.put("template", resolveHeaderTemplate(message));
        card.put("header", header);

        card.put("elements", List.of(buildMarkdownElement(message)));
        return card;
    }

    private Map<String, Object> buildMarkdownElement(NotificationMessage message) {
        Map<String, Object> element = new HashMap<>();
        element.put("tag", "div");

        Map<String, String> text = new HashMap<>();
        text.put("tag", "lark_md");
        text.put("content", resolveCardContent(message));
        element.put("text", text);

        return element;
    }

    private String resolveCardTitle(NotificationMessage message) {
        if (message != null && StringUtils.hasText(message.cardTitle())) {
            return message.cardTitle();
        }
        String fallback = message == null ? "" : message.plainTextContent();
        if (!StringUtils.hasText(fallback)) {
            return "交易提醒";
        }
        int firstLineBreak = fallback.indexOf('\n');
        return firstLineBreak >= 0 ? fallback.substring(0, firstLineBreak).trim() : fallback.trim();
    }

    private String resolveCardContent(NotificationMessage message) {
        if (message != null && StringUtils.hasText(message.markdownContent())) {
            return message.markdownContent();
        }
        return message == null ? "" : defaultText(message.plainTextContent(), "");
    }

    private String resolveHeaderTemplate(NotificationMessage message) {
        NotificationMessage.HeaderTemplate headerTemplate = message == null
                ? NotificationMessage.HeaderTemplate.BLUE
                : message.headerTemplate();
        return headerTemplate == null
                ? NotificationMessage.HeaderTemplate.BLUE.template()
                : headerTemplate.template();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
