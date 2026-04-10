package com.mobai.alert.access.binance.derivative.stream;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.binance.derivative.dto.BinanceForceOrderDTO;
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
 * 琛嶇敓鍝佸眰寮哄钩 WebSocket 鏈嶅姟銆? * 璐熻矗鎺ユ敹寮哄钩蹇収娴侊紝骞剁淮鎶ゆ渶杩戜竴娈垫椂闂寸殑寮哄钩浜嬩欢缂撳瓨銆? */
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

    public BigDecimal calculateClusterIntensity(String symbol, long withinMs) {
        BigDecimal total = ZERO;
        for (BinanceForceOrderDTO dto : getRecentForceOrders(symbol, withinMs)) {
            total = total.add(decimal(preferredPrice(dto)).multiply(decimal(dto.getAccumulatedFilledQuantity())));
        }
        return total;
    }

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
        log.info("鍑嗗杩炴帴 Binance 寮哄钩 WebSocket锛宺eason={}锛寀rl={}", reason, url);
        this.webSocket = okHttpClient.newWebSocket(new Request.Builder().url(url).build(), new ForceOrderListener());
    }

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

    private boolean supports(String symbol) {
        return isFeatureActive()
                && StringUtils.hasText(symbol)
                && targetSymbol.equalsIgnoreCase(symbol);
    }

    private void mergeForceOrder(BinanceForceOrderDTO forceOrder) {
        synchronized (monitor) {
            recentForceOrders.add(forceOrder);
            cleanupExpired(System.currentTimeMillis());
        }
    }

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

    private String preferredPrice(BinanceForceOrderDTO dto) {
        if (dto == null) {
            return null;
        }
        if (StringUtils.hasText(dto.getAveragePrice()) && !"0".equals(dto.getAveragePrice())) {
            return dto.getAveragePrice();
        }
        return dto.getPrice();
    }

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

    private final class ForceOrderListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = true;
            log.info("Binance 寮哄钩 WebSocket 宸茶繛鎺ワ紝symbol={}", targetSymbol);
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
                log.info("Binance 寮哄钩浜嬩欢宸叉帴鏀讹紝symbol={}锛宻ide={}锛宲rice={}锛宷uantity={}锛宔ventTime={}",
                        forceOrder.getSymbol(),
                        forceOrder.getSide(),
                        preferredPrice(forceOrder),
                        forceOrder.getAccumulatedFilledQuantity(),
                        forceOrder.getEventTime());
            } catch (Exception e) {
                log.error("瑙ｆ瀽 Binance 寮哄钩娑堟伅澶辫触锛宲ayload={}", text, e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance 寮哄钩 WebSocket 姝ｅ湪鍏抽棴锛宑ode={}锛宺eason={}", code, reason);
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            log.warn("Binance 寮哄钩 WebSocket 宸插叧闂紝code={}锛宺eason={}", code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (webSocket != BinanceForceOrderWebSocketService.this.webSocket) {
                return;
            }
            connected = false;
            String responseMessage = response == null ? "no_response" : response.message();
            log.error("Binance 寮哄钩 WebSocket 杩炴帴澶辫触锛宺esponse={}", responseMessage, t);
        }
    }
}

