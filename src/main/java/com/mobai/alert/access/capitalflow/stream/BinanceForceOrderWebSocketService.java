package com.mobai.alert.access.capitalflow.stream;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.capitalflow.dto.BinanceForceOrderDTO;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Binance 强平订单 WebSocket 服务。
 * 持续监听目标交易对的强平流，并在内存中保留一个短时间窗口，
 * 供衍生品特征服务估算近期强平聚集强度。
 */
@Service
public class BinanceForceOrderWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(BinanceForceOrderWebSocketService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final OkHttpClient okHttpClient;
    private final Object monitor = new Object();
    private final List<BinanceForceOrderDTO> recentForceOrders = new ArrayList<>();

    @Value("${backtest.enabled:false}")
    private boolean backtestEnabled;

    @Value("${monitoring.market-data.force-order.websocket.enabled:true}")
    private boolean webSocketEnabled;

    @Value("${monitoring.target-symbol:BTCUSDT}")
    private String targetSymbol;

    @Value("${monitoring.market-data.force-order.websocket.base-url:wss://fstream.binance.com/ws}")
    private String webSocketBaseUrl;

    @Value("${monitoring.market-data.force-order.websocket.retention-ms:300000}")
    private long retentionMs;

    @Value("${monitoring.market-data.force-order.websocket.reconnect-delay-ms:5000}")
    private long reconnectDelayMs;

    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile long lastConnectAttemptAt;

    public BinanceForceOrderWebSocketService(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * 获取给定时间窗内的强平订单列表。
     * 若当前 symbol 不在监听范围内，则直接返回空列表。
     *
     * @param symbol 交易对
     * @param withinMs 回看窗口长度
     * @return 强平订单快照
     */
    public List<BinanceForceOrderDTO> getRecentForceOrders(String symbol, long withinMs) {
        if (!supports(symbol)) {
            return List.of();
        }
        ensureConnected("on_demand");

        long threshold = System.currentTimeMillis() - Math.max(0L, withinMs);
        List<BinanceForceOrderDTO> snapshot = new ArrayList<>();
        synchronized (monitor) {
            cleanupExpired(System.currentTimeMillis());
            for (BinanceForceOrderDTO dto : recentForceOrders) {
                long eventTime = dto.getEventTime() == null ? 0L : dto.getEventTime();
                if (eventTime >= threshold) {
                    snapshot.add(dto);
                }
            }
        }
        return snapshot;
    }

    /**
     * 计算强平簇强度。
     * 当前近似算法为：对时间窗内每条强平记录累加 {@code 价格 * 累计成交量}。
     *
     * @param symbol 交易对
     * @param withinMs 回看窗口长度
     * @return 强平簇强度
     */
    public BigDecimal calculateClusterIntensity(String symbol, long withinMs) {
        BigDecimal total = ZERO;
        for (BinanceForceOrderDTO dto : getRecentForceOrders(symbol, withinMs)) {
            total = total.add(decimal(preferredPrice(dto)).multiply(decimal(dto.getAccumulatedFilledQuantity())));
        }
        return total;
    }

    /**
     * 确保强平流已连接。
     * 如果当前已连接或功能未开启，则不会重复建连。
     *
     * @param reason 连接原因，用于日志排查
     */
    public synchronized void ensureConnected(String reason) {
        if (!isFeatureActive() || connected) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastConnectAttemptAt < reconnectDelayMs) {
            return;
        }
        lastConnectAttemptAt = now;

        WebSocket current = this.webSocket;
        if (current != null) {
            current.cancel();
        }

        String url = webSocketBaseUrl + "/" + targetSymbol.toLowerCase() + "@forceOrder";
        log.info("准备连接 Binance 强平 WebSocket，reason={}，url={}", reason, url);
        this.webSocket = okHttpClient.newWebSocket(new Request.Builder().url(url).build(), new ForceOrderListener());
    }

    /**
     * 应用关闭时关闭当前连接。
     */
    @PreDestroy
    public void shutdown() {
        WebSocket current = this.webSocket;
        if (current != null) {
            current.close(1000, "Application shutting down");
        }
    }

    private boolean isFeatureActive() {
        return webSocketEnabled && !backtestEnabled && StringUtils.hasText(targetSymbol);
    }

    /**
     * 当前服务只支持配置中的单一目标交易对。
     */
    private boolean supports(String symbol) {
        return isFeatureActive()
                && StringUtils.hasText(symbol)
                && targetSymbol.equalsIgnoreCase(symbol);
    }

    /**
     * 写入新强平事件，并顺手清理过期数据。
     */
    private void mergeForceOrder(BinanceForceOrderDTO forceOrder) {
        synchronized (monitor) {
            recentForceOrders.add(forceOrder);
            cleanupExpired(System.currentTimeMillis());
        }
    }

    /**
     * 清理超出保留窗口的旧强平记录。
     */
    private void cleanupExpired(long now) {
        long threshold = now - retentionMs;
        Iterator<BinanceForceOrderDTO> iterator = recentForceOrders.iterator();
        while (iterator.hasNext()) {
            BinanceForceOrderDTO dto = iterator.next();
            long eventTime = dto.getEventTime() == null ? 0L : dto.getEventTime();
            if (eventTime < threshold) {
                iterator.remove();
            }
        }
    }

    /**
     * 将 Binance WebSocket 事件映射为强平 DTO。
     */
    private BinanceForceOrderDTO mapForceOrder(JSONObject payload) {
        JSONObject forceOrder = payload.getJSONObject("o");
        if (forceOrder == null) {
            return null;
        }

        BinanceForceOrderDTO dto = new BinanceForceOrderDTO();
        dto.setEventTime(payload.getLong("E"));
        dto.setSymbol(forceOrder.getString("s"));
        dto.setSide(forceOrder.getString("S"));
        dto.setOrderType(forceOrder.getString("o"));
        dto.setTimeInForce(forceOrder.getString("f"));
        dto.setOriginalQuantity(forceOrder.getString("q"));
        dto.setPrice(forceOrder.getString("p"));
        dto.setAveragePrice(forceOrder.getString("ap"));
        dto.setOrderStatus(forceOrder.getString("X"));
        dto.setLastFilledQuantity(forceOrder.getString("l"));
        dto.setAccumulatedFilledQuantity(forceOrder.getString("z"));
        dto.setTradeTime(forceOrder.getLong("T"));
        return dto;
    }

    /**
     * 优先取平均成交价，若没有则回退到委托价。
     */
    private String preferredPrice(BinanceForceOrderDTO dto) {
        if (dto == null) {
            return null;
        }
        if (StringUtils.hasText(dto.getAveragePrice()) && !"0".equals(dto.getAveragePrice())) {
            return dto.getAveragePrice();
        }
        return dto.getPrice();
    }

    /**
     * 安全地将文本数值转换为 BigDecimal。
     */
    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return ZERO;
        }
    }

    /**
     * 强平流监听器。
     */
    private final class ForceOrderListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = true;
            log.info("Binance 强平 WebSocket 已连接，symbol={}", targetSymbol);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            try {
                JSONObject payload = JSON.parseObject(text);
                JSONObject event = payload.getJSONObject("data");
                if (event == null) {
                    event = payload;
                }
                BinanceForceOrderDTO forceOrder = mapForceOrder(event);
                if (forceOrder == null) {
                    return;
                }
                mergeForceOrder(forceOrder);
                log.info("Binance 强平事件已接收，symbol={}，side={}，price={}，quantity={}，eventTime={}",
                        forceOrder.getSymbol(),
                        forceOrder.getSide(),
                        preferredPrice(forceOrder),
                        forceOrder.getAccumulatedFilledQuantity(),
                        forceOrder.getEventTime());
            } catch (Exception e) {
                log.error("解析 Binance 强平 WebSocket 消息失败，payload={}", text, e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance 强平 WebSocket 正在关闭，code={}，reason={}", code, reason);
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance 强平 WebSocket 已关闭，code={}，reason={}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            String responseMessage = response == null ? "no_response" : response.message();
            log.error("Binance 强平 WebSocket 连接失败，response={}", responseMessage, t);
        }
    }
}
