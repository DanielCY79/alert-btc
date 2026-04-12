package com.mobai.alert.access.kline.stream;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.rest.BinanceKlineRestClient;
import com.mobai.alert.access.kline.support.BinanceKlineMapper;
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
 * Binance K 线 WebSocket 缓存服务。
 * 通过维护单一目标交易对的实时 K 线流，在本地保留最近若干根 K 线，
 * 让控制层优先命中内存缓存，减少调度时对 REST 接口的依赖。
 */
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

    @Value("${monitoring.kline.interval:3m}")
    private String klineInterval;

    @Value("${monitoring.market-data.websocket.base-url:wss://fstream.binance.com/ws}")
    private String webSocketBaseUrl;

    @Value("${monitoring.market-data.websocket.cache-size:${monitoring.kline.limit:360}}")
    private int cacheSize;

    @Value("${monitoring.market-data.websocket.bootstrap-limit:${monitoring.kline.limit:360}}")
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

    /**
     * 定时维护连接健康度。
     * 连接断开或缓存陈旧时，会触发重新预热和重连。
     */
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

    /**
     * 应用退出时主动关闭当前 WebSocket 连接。
     */
    @PreDestroy
    public void shutdown() {
        WebSocket current = this.webSocket;
        if (current != null) {
            current.close(1000, "Application shutting down");
        }
    }

    /**
     * 获取最近缓存的 K 线。
     * 仅支持目标交易对、目标周期、且不带起止时间的短窗口查询；否则返回空列表让上层走 REST。
     *
     * @param request K 线请求
     * @return 最近 K 线快照；若缓存不可用则返回空列表
     */
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

    /**
     * 判断当前请求是否满足本地缓存服务能力范围。
     */
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

    /**
     * 基于最近消息时间和最近缓存刷新时间判断缓存是否陈旧。
     */
    private boolean isCacheStale() {
        long referenceTime = Math.max(lastMessageAt, lastCacheRefreshAt);
        return referenceTime == 0 || System.currentTimeMillis() - referenceTime > staleThresholdMs;
    }

    /**
     * 复制最近 limit 根 K 线，避免外部直接操作内部缓存。
     */
    private List<BinanceKlineDTO> snapshot(int limit) {
        synchronized (cacheMonitor) {
            if (CollectionUtils.isEmpty(recentKlines) || recentKlines.size() < limit) {
                return List.of();
            }
            int start = Math.max(0, recentKlines.size() - limit);
            return new ArrayList<>(recentKlines.subList(start, recentKlines.size()));
        }
    }

    /**
     * 刷新 WebSocket 连接。
     * 刷新前会先用 REST 预热缓存，保证断线重连期间也尽量有可用数据。
     */
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
        log.info("准备连接 Binance K 线 WebSocket，reason={}，url={}", reason, url);
        this.webSocket = okHttpClient.newWebSocket(new Request.Builder().url(url).build(), new BinanceKlineListener());
    }

    /**
     * 用 REST 历史数据预热本地缓存，避免首次连上前缓存为空。
     */
    private void bootstrapRecentKlines() {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(targetSymbol);
        request.setInterval(klineInterval);
        request.setLimit(Math.max(bootstrapLimit, cacheSize));
        request.setTimeZone("8");

        List<BinanceKlineDTO> klines = klineRestClient.listKline(request);
        if (CollectionUtils.isEmpty(klines)) {
            log.warn("Binance K 线 WebSocket 预热失败，REST 未返回有效数据，symbol={}，interval={}", targetSymbol, klineInterval);
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
        log.info("Binance K 线缓存预热完成，symbol={}，interval={}，size={}",
                targetSymbol,
                klineInterval,
                recentKlines.size());
    }

    /**
     * 合并一根新 K 线到本地缓存。
     * 若发现同开盘时间的 K 线已存在，则视为同一根 K 线的增量更新。
     */
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

    /**
     * 生成目标交易对的 WebSocket 订阅地址。
     */
    private String buildStreamUrl() {
        return webSocketBaseUrl + "/" + targetSymbol.toLowerCase() + "@kline_" + klineInterval;
    }

    /**
     * WebSocket 监听器，负责接收推送并更新缓存。
     */
    private final class BinanceKlineListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = true;
            log.info("Binance K 线 WebSocket 已连接，symbol={}，interval={}", targetSymbol, klineInterval);
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
                log.error("解析 Binance K 线 WebSocket 消息失败，payload={}", text, e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance K 线 WebSocket 正在关闭，code={}，reason={}", code, reason);
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance K 线 WebSocket 已关闭，code={}，reason={}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (webSocket != BinanceKlineWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            String responseMessage = response == null ? "no_response" : response.message();
            log.error("Binance K 线 WebSocket 连接失败，response={}", responseMessage, t);
        }
    }
}
