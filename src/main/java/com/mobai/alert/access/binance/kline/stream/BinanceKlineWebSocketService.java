package com.mobai.alert.access.binance.kline.stream;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.binance.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.binance.kline.rest.BinanceKlineRestClient;
import com.mobai.alert.access.binance.kline.support.BinanceKlineMapper;
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

/**
 * K 绾垮眰 WebSocket 鏈嶅姟銆? * 璐熻矗缁存姢瀹炴椂 K 绾跨紦瀛橈紝骞跺湪缂撳瓨澶辨晥鏃惰嚜鍔ㄩ噸杩炲拰鍥炲～銆? */
@Service
public class BinanceKlineWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineWebSocketService.class);

    private final BinanceKlineRestClient klineRestClient;
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

    public BinanceKlineWebSocketService(BinanceKlineRestClient klineRestClient, OkHttpClient okHttpClient) {
        this.klineRestClient = klineRestClient;
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
        log.info("鍑嗗杩炴帴 Binance K绾?WebSocket锛宺eason={}锛寀rl={}", reason, url);
        this.webSocket = okHttpClient.newWebSocket(new Request.Builder().url(url).build(), new BinanceKlineListener());
    }

    private void bootstrapRecentKlines() {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(targetSymbol);
        request.setInterval(klineInterval);
        request.setLimit(Math.max(bootstrapLimit, cacheSize));
        request.setTimeZone("8");

        List<BinanceKlineDTO> klines = klineRestClient.listKline(request);
        if (CollectionUtils.isEmpty(klines)) {
            log.warn("Binance K绾?WebSocket 鍚姩鍓嶇殑 REST 鍥炲～涓虹┖锛宻ymbol={}锛宨nterval={}", targetSymbol, klineInterval);
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
        log.info("Binance K绾跨紦瀛樺凡閫氳繃 REST 鍥炲～锛宻ymbol={}锛宨nterval={}锛宻ize={}",
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
            log.info("Binance K绾?WebSocket 宸茶繛鎺ワ紝symbol={}锛宨nterval={}", targetSymbol, klineInterval);
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
                log.error("瑙ｆ瀽 Binance K绾?WebSocket 娑堟伅澶辫触锛宲ayload={}", text, e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance K绾?WebSocket 姝ｅ湪鍏抽棴锛宑ode={}锛宺eason={}", code, reason);
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance K绾?WebSocket 宸插叧闂紝code={}锛宺eason={}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            String responseMessage = response == null ? "no_response" : response.message();
            log.error("Binance K绾?WebSocket 杩炴帴澶辫触锛宺esponse={}", responseMessage, t);
        }
    }
}

