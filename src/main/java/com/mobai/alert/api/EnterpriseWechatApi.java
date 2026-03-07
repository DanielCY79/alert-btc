package com.mobai.alert.api;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class EnterpriseWechatApi {
    @Autowired
    private RestTemplate restTemplate;

    private static final String WEBHOOK_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=b0250fb8-5677-41c1-a914-311008c91ea8";

    public void sendGroupMessage(String msg) {
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");
        Map<String, String> textContent = new HashMap<>();
        textContent.put("content", msg);
        message.put("markdown", textContent);
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 创建 HttpEntity 对象
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
        // 发送 POST 请求
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.postForObject(WEBHOOK_URL, request, String.class);
        System.out.println("Response from WeCom Bot: " + response);
    }
}
