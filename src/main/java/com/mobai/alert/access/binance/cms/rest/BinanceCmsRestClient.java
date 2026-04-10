package com.mobai.alert.access.binance.cms.rest;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Binance CMS REST 客户端。
 * 当前只负责获取 Binance 服务器时间，供 CMS WebSocket 签名时做时间校准。
 */
@Component
public class BinanceCmsRestClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceCmsRestClient.class);
    private static final String SERVER_TIME_URL = "https://api.binance.com/api/v3/time";

    private final RestTemplate restTemplate;

    public BinanceCmsRestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Long getServerTime() {
        try {
            String body = restTemplate.getForObject(SERVER_TIME_URL, String.class);
            JSONObject jsonObject = JSON.parseObject(body);
            return jsonObject == null ? null : jsonObject.getLong("serverTime");
        } catch (Exception e) {
            log.warn("获取 Binance 服务器时间失败", e);
            return null;
        }
    }
}
