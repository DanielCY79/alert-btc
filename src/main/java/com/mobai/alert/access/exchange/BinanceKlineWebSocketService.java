package com.mobai.alert.access.exchange;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.dto.BinanceKlineDTO;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BinanceKlineWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineWebSocketService.class);

    private final BinanceRestApiClient restApiClient;
    private final OkHttpClient okHttpClient;
    private final Object cacheMonitor = new Object();
    private final List<BinanceKlineDTO> recentKlines = new ArrayList<>();

    @Value("${backtest.enabled:false}")
    private boolean backtestEnabled;

    @Value("${monitoring.market-data.websocket.enabled:true}")
    private boolean webSocketEnabled;

    @Value("${monitoring.target-symbol:BTCUSDT}")
    private String targetSymbol;

    @Value("${monitoring.kline.interval:15m}")
    private String klineInterval;

    @Value("${monitoring.market-data.websocket.base-url:wss://fstream.binance.com/ws}")
    private String webSocketBaseUrl;

    @Value("${monitoring.market-data.websocket.cache-size:${monitoring.kline.limit:80}}")
    private int cacheSize;

    @Value("${monitoring.market-data.websocket.bootstrap-limit:${monitoring.kline.limit:80}}")
    private int bootstrapLimit;

    @Value("${monitoring.market-data.websocket.stale-threshold-ms:180000}")
    private long staleThresholdMs;

    @Value("${monitoring.market-data.websocket.reconnect-delay-ms:5000}")
    private long reconnectDelayMs;

    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile long lastConnectAttemptAt;
    private volatile long lastCacheRefreshAt;
    private volatile long lastMessageAt;

    public BinanceKlineWebSocketService(BinanceRestApiClient restApiClient, OkHttpClient okHttpClient) {
        this.restApiClient = restApiClient;
        this.okHttpClient = okHttpClient;
    }

    @Scheduled(fixedDelayString = "${monitoring.market-data.websocket.health-check-ms:30000}")
    public void maintainConnection() {
        if (!isFeatureActive()) {
            return;
        }
        if (connected && !isCacheStale()) {
            return;
        }
        refreshConnection(connected ? "stale_cache" : "disconnected");
    }

    @PreDestroy
    public void shutdown() {
        WebSocket current = this.webSocket;
        if (current != null) {
            current.close(1000, "Application shutting down");
        }
    }

    public List<BinanceKlineDTO> getRecentKlines(BinanceKlineDTO request) {
        if (!supports(request)) {
            return List.of();
        }

        List<BinanceKlineDTO> snapshot = snapshot(request.getLimit());
        if (!snapshot.isEmpty() && !isCacheStale()) {
            return snapshot;
        }

        refreshConnection("on_demand");
        snapshot = snapshot(request.getLimit());
        if (!snapshot.isEmpty() && !isCacheStale()) {
            return snapshot;
        }

        return List.of();
    }

    private boolean supports(BinanceKlineDTO request) {
        return isFeatureActive()
                && request != null
                && request.getStartTime() == null
                && request.getEndTime() == null
                && request.getLimit() != null
                && request.getLimit() > 0
                && request.getLimit() <= cacheSize
                && StringUtils.hasText(request.getSymbol())
                && StringUtils.hasText(request.getInterval())
                && targetSymbol.equalsIgnoreCase(request.getSymbol())
                && klineInterval.equalsIgnoreCase(request.getInterval());
    }

    private boolean isFeatureActive() {
        return webSocketEnabled && !backtestEnabled;
    }

    private boolean isCacheStale() {
        long referenceTime = Math.max(lastMessageAt, lastCacheRefreshAt);
        return referenceTime == 0 || System.currentTimeMillis() - referenceTime > staleThresholdMs;
    }

    private List<BinanceKlineDTO> snapshot(int limit) {
        synchronized (cacheMonitor) {
            if (CollectionUtils.isEmpty(recentKlines) || recentKlines.size() < limit) {
                return List.of();
            }
            int start = Math.max(0, recentKlines.size() - limit);
            return new ArrayList<>(recentKlines.subList(start, recentKlines.size()));
        }
    }

    private synchronized void refreshConnection(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastConnectAttemptAt < reconnectDelayMs) {
            return;
        }
        lastConnectAttemptAt = now;

        bootstrapRecentKlines();

        WebSocket current = this.webSocket;
        if (current != null) {
            current.cancel();
        }

        String url = buildStreamUrl();
        log.info("准备连接 Binance WebSocket 行情流，reason={}，url={}", reason, url);
        this.webSocket = okHttpClient.newWebSocket(new Request.Builder().url(url).build(), new BinanceKlineListener());
    }

    private void bootstrapRecentKlines() {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(targetSymbol);
        request.setInterval(klineInterval);
        request.setLimit(Math.max(bootstrapLimit, cacheSize));
        request.setTimeZone("8");

        List<BinanceKlineDTO> klines = restApiClient.listKline(request);
        if (CollectionUtils.isEmpty(klines)) {
            log.warn("Binance WebSocket 启动前的 REST 回填为空，symbol={}，interval={}", targetSymbol, klineInterval);
            return;
        }

        List<BinanceKlineDTO> sorted = klines.stream()
                .sorted(Comparator.comparingLong(BinanceKlineDTO::getStartTime))
                .toList();
        int start = Math.max(0, sorted.size() - Math.max(cacheSize, bootstrapLimit));

        synchronized (cacheMonitor) {
            recentKlines.clear();
            recentKlines.addAll(sorted.subList(start, sorted.size()));
        }
        lastCacheRefreshAt = System.currentTimeMillis();
        log.info("Binance WebSocket 本地缓存已通过 REST 回填，symbol={}，interval={}，size={}",
                targetSymbol,
                klineInterval,
                recentKlines.size());
    }

    private void mergeKline(BinanceKlineDTO incoming) {
        synchronized (cacheMonitor) {
            int existingIndex = -1;
            for (int i = 0; i < recentKlines.size(); i++) {
                if (recentKlines.get(i).getStartTime().equals(incoming.getStartTime())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                recentKlines.set(existingIndex, incoming);
            } else {
                recentKlines.add(incoming);
                recentKlines.sort(Comparator.comparingLong(BinanceKlineDTO::getStartTime));
            }

            while (recentKlines.size() > cacheSize) {
                recentKlines.remove(0);
            }
        }
        lastCacheRefreshAt = System.currentTimeMillis();
    }

    private String buildStreamUrl() {
        return webSocketBaseUrl + "/" + targetSymbol.toLowerCase() + "@kline_" + klineInterval;
    }

    private final class BinanceKlineListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = true;
            log.info("Binance WebSocket 已连接，symbol={}，interval={}", targetSymbol, klineInterval);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            try {
                JSONObject payload = JSON.parseObject(text);
                JSONObject event = payload.getJSONObject("data");
                if (event == null) {
                    event = payload;
                }
                JSONObject kline = event == null ? null : event.getJSONObject("k");
                if (kline == null) {
                    return;
                }
                mergeKline(BinanceKlineMapper.fromWebSocketEvent(kline));
                lastMessageAt = System.currentTimeMillis();
            } catch (Exception e) {
                log.error("解析 Binance WebSocket K 线消息失败，payload={}", text, e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance WebSocket 正在关闭，code={}，reason={}", code, reason);
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance WebSocket 已关闭，code={}，reason={}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            String responseMessage = response == null ? "no_response" : response.message();
            log.error("Binance WebSocket 连接失败，response={}", responseMessage, t);
        }
    }
}
