package com.mobai.alert.access.event.binance.cms.stream;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.event.binance.cms.dto.BinanceAnnouncementDTO;
import com.mobai.alert.access.event.binance.cms.rest.BinanceCmsRestClient;
import com.mobai.alert.access.event.binance.cms.support.BinanceCmsSigner;
import com.mobai.alert.access.event.service.MarketEventService;
import com.mobai.alert.notification.AlertNotificationService;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binance CMS 公告 WebSocket 服务。
 * 负责建立公告流连接、完成签名鉴权、做公告去重，并把公告同步送往
 * 市场事件标准化服务和通知服务。
 */
@Service
public class BinanceAnnouncementWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(BinanceAnnouncementWebSocketService.class);

    private final OkHttpClient cmsOkHttpClient;
    private final BinanceCmsSigner signer;
    private final BinanceCmsRestClient cmsRestClient;
    private final MarketEventService marketEventService;
    private final AlertNotificationService alertNotificationService;

    /**
     * 已处理公告键集合，值为处理时间，用于短中期内去重。
     */
    private final Map<String, Long> processedAnnouncements = new ConcurrentHashMap<>();

    @Value("${binance.cms.websocket.enabled:false}")
    private boolean cmsEnabled;

    @Value("${binance.cms.websocket.base-url:wss://api.binance.com/sapi/wss}")
    private String cmsBaseUrl;

    @Value("${binance.cms.websocket.topics:com_announcement_en}")
    private String configuredTopics;

    @Value("${binance.cms.websocket.recv-window:30000}")
    private long recvWindow;

    @Value("${binance.cms.websocket.reconnect-delay-ms:5000}")
    private long reconnectDelayMs;

    @Value("${binance.cms.websocket.max-session-ms:86340000}")
    private long maxSessionMs;

    @Value("${binance.cms.websocket.processed-ttl-ms:604800000}")
    private long processedTtlMs;

    @Value("${binance.api.key:}")
    private String apiKey;

    @Value("${binance.api.secret:}")
    private String apiSecret;

    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile long lastConnectAttemptAt;
    private volatile long connectedAt;
    private volatile long serverTimeOffsetMs;

    public BinanceAnnouncementWebSocketService(@Qualifier("cmsOkHttpClient") OkHttpClient cmsOkHttpClient,
                                               BinanceCmsSigner signer,
                                               BinanceCmsRestClient cmsRestClient,
                                               MarketEventService marketEventService,
                                               AlertNotificationService alertNotificationService) {
        this.cmsOkHttpClient = cmsOkHttpClient;
        this.signer = signer;
        this.cmsRestClient = cmsRestClient;
        this.marketEventService = marketEventService;
        this.alertNotificationService = alertNotificationService;
    }

    /**
     * 定时维护公告流连接。
     * 当连接断开或会话达到 Binance 允许的最长时长时，自动重连。
     */
    @Scheduled(fixedDelayString = "${binance.cms.websocket.health-check-ms:30000}")
    public void maintainConnection() {
        if (!isFeatureActive()) {
            return;
        }
        if (connected && !isSessionExpired()) {
            return;
        }
        refreshConnection(connected ? "session_expired" : "disconnected");
    }

    /**
     * 周期清理已经过期的公告去重记录，避免 Map 无限增长。
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void cleanupProcessedAnnouncements() {
        long currentTime = System.currentTimeMillis();
        processedAnnouncements.entrySet().removeIf(entry -> currentTime - entry.getValue() > processedTtlMs);
    }

    /**
     * 应用关闭时主动关闭公告流连接。
     */
    @PreDestroy
    public void shutdown() {
        WebSocket current = this.webSocket;
        if (current != null) {
            current.close(1000, "Application shutting down");
        }
    }

    /**
     * 只有在功能开关、API Key、API Secret 和订阅主题都准备好时才启用公告流。
     */
    private boolean isFeatureActive() {
        return cmsEnabled
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(apiSecret)
                && StringUtils.hasText(configuredTopics);
    }

    /**
     * Binance CMS 会话是否已经超时。
     */
    private boolean isSessionExpired() {
        return connectedAt > 0 && System.currentTimeMillis() - connectedAt >= maxSessionMs;
    }

    /**
     * 刷新连接并重新鉴权建链。
     */
    private synchronized void refreshConnection(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastConnectAttemptAt < reconnectDelayMs) {
            return;
        }
        lastConnectAttemptAt = now;

        WebSocket current = this.webSocket;
        if (current != null) {
            current.cancel();
        }

        long timestamp = synchronizeServerTime(now);
        String url = buildConnectionUrl(timestamp);
        log.info("准备连接 Binance CMS WebSocket，reason={}，topics={}", reason, configuredTopics);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-MBX-APIKEY", apiKey)
                .build();
        this.webSocket = cmsOkHttpClient.newWebSocket(request, new BinanceAnnouncementListener());
    }

    /**
     * 使用服务端时间修正本地时间，减少签名时间漂移。
     */
    private long synchronizeServerTime(long localTime) {
        Long serverTime = cmsRestClient.getServerTime();
        if (serverTime == null) {
            log.warn("Binance CMS 时间同步失败，回退到最近一次偏移量={}ms", serverTimeOffsetMs);
            return localTime + serverTimeOffsetMs;
        }

        serverTimeOffsetMs = serverTime - localTime;
        log.info("Binance CMS 时间同步完成，serverTime={}，localTime={}，offset={}ms",
                serverTime, localTime, serverTimeOffsetMs);
        return localTime + serverTimeOffsetMs;
    }

    /**
     * 构建带签名的 CMS WebSocket 连接地址。
     */
    private String buildConnectionUrl(long timestamp) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("random", UUID.randomUUID().toString().replace("-", ""));
        params.put("topic", configuredTopics);
        params.put("recvWindow", String.valueOf(recvWindow));
        params.put("timestamp", String.valueOf(timestamp));

        String payload = signer.buildPayload(params);
        String signature = signer.sign(payload, apiSecret);
        return cmsBaseUrl + "?" + payload + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8);
    }

    /**
     * 处理单条 WebSocket 文本消息。
     */
    private void handleMessage(String text) {
        JSONObject payload = JSON.parseObject(text);
        String type = payload.getString("type");
        if ("COMMAND".equalsIgnoreCase(type)) {
            log.info("Binance CMS 命令响应，subType={}，code={}，data={}",
                    payload.getString("subType"),
                    payload.getString("code"),
                    payload.getString("data"));
            return;
        }
        if (!"DATA".equalsIgnoreCase(type)) {
            log.debug("忽略 Binance CMS 非数据消息，type={}", type);
            return;
        }

        String rawData = payload.getString("data");
        if (!StringUtils.hasText(rawData)) {
            return;
        }

        BinanceAnnouncementDTO announcement = JSON.parseObject(rawData, BinanceAnnouncementDTO.class);
        announcement.setTopic(payload.getString("topic"));
        if (shouldSkip(announcement)) {
            return;
        }

        processedAnnouncements.put(buildAnnouncementKey(announcement), System.currentTimeMillis());
        log.info("Binance CMS 公告已接收，topic={}，catalog={}，publishDate={}，title={}，body={}，disclaimer={}",
                announcement.getTopic(),
                announcement.getCatalogName(),
                announcement.getPublishDate(),
                announcement.getTitle(),
                sanitizeAnnouncementText(announcement.getBody()),
                sanitizeAnnouncementText(announcement.getDisclaimer()));
        marketEventService.ingestBinanceAnnouncement(announcement);
        alertNotificationService.sendAnnouncement(announcement);
    }

    /**
     * 判断公告是否应被跳过。
     * 跳过条件包括空公告以及近期已处理过的重复公告。
     */
    private boolean shouldSkip(BinanceAnnouncementDTO announcement) {
        if (announcement == null || !StringUtils.hasText(announcement.getTitle())) {
            return true;
        }
        return processedAnnouncements.containsKey(buildAnnouncementKey(announcement));
    }

    /**
     * 为公告构建稳定的去重键。
     */
    private String buildAnnouncementKey(BinanceAnnouncementDTO announcement) {
        long publishDate = announcement.getPublishDate() == null ? 0L : announcement.getPublishDate();
        String topic = normalize(announcement.getTopic());
        String title = normalize(announcement.getTitle());
        return topic + "|" + publishDate + "|" + title;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 压缩正文中的连续空白，便于写日志和发通知。
     */
    private String sanitizeAnnouncementText(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * CMS WebSocket 监听器。
     */
    private final class BinanceAnnouncementListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (webSocket != BinanceAnnouncementWebSocketService.this.webSocket) {
                return;
            }
            connected = true;
            connectedAt = System.currentTimeMillis();
            log.info("Binance CMS WebSocket 已连接，topics={}", configuredTopics);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (webSocket != BinanceAnnouncementWebSocketService.this.webSocket) {
                return;
            }
            try {
                handleMessage(text);
            } catch (Exception e) {
                log.error("解析 Binance CMS WebSocket 消息失败，payload={}", text, e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceAnnouncementWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance CMS WebSocket 正在关闭，code={}，reason={}", code, reason);
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceAnnouncementWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance CMS WebSocket 已关闭，code={}，reason={}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (webSocket != BinanceAnnouncementWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            String responseMessage = response == null ? "no_response" : response.message();
            log.error("Binance CMS WebSocket 连接失败，response={}", responseMessage, t);
        }
    }
}
