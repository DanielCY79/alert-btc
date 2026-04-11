package com.mobai.alert.access;

import com.mobai.alert.access.capitalflow.rest.BinanceDerivativeRestClient;
import com.mobai.alert.access.capitalflow.service.BinanceDerivativeFeatureService;
import com.mobai.alert.access.capitalflow.stream.BinanceForceOrderWebSocketService;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.rest.BinanceKlineRestClient;
import com.mobai.alert.access.kline.stream.BinanceKlineWebSocketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BinanceApi 访问层测试，覆盖 K 线查询的缓存回退行为。
 */
class BinanceApiTests {

    /**
     * 当 WebSocket 缓存命中时，应优先直接返回缓存数据。
     */
    @Test
    void shouldPreferWebSocketCacheForRecentKlines() {
        BinanceKlineRestClient klineRestClient = mock(BinanceKlineRestClient.class);
        BinanceDerivativeRestClient derivativeRestClient = mock(BinanceDerivativeRestClient.class);
        BinanceKlineWebSocketService webSocketService = mock(BinanceKlineWebSocketService.class);
        BinanceDerivativeFeatureService derivativeFeatureService = mock(BinanceDerivativeFeatureService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceForceOrderWebSocketService> forceOrderProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceKlineWebSocketService> provider = mock(ObjectProvider.class);
        BinanceApi binanceApi = new BinanceApi(klineRestClient, provider, derivativeRestClient, forceOrderProvider, derivativeFeatureService);

        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol("BTCUSDT");
        request.setInterval("4h");
        request.setLimit(180);

        List<BinanceKlineDTO> cached = List.of(new BinanceKlineDTO());
        when(provider.getIfAvailable()).thenReturn(webSocketService);
        when(webSocketService.getRecentKlines(request)).thenReturn(cached);

        List<BinanceKlineDTO> actual = binanceApi.listKline(request);

        assertSame(cached, actual);
        verify(webSocketService).getRecentKlines(request);
    }

    /**
     * 当 WebSocket 已连接但缓存未命中时，应回退到 REST 拉取数据。
     */
    @Test
    void shouldFallbackToRestWhenWebSocketCacheMisses() {
        BinanceKlineRestClient klineRestClient = mock(BinanceKlineRestClient.class);
        BinanceDerivativeRestClient derivativeRestClient = mock(BinanceDerivativeRestClient.class);
        BinanceKlineWebSocketService webSocketService = mock(BinanceKlineWebSocketService.class);
        BinanceDerivativeFeatureService derivativeFeatureService = mock(BinanceDerivativeFeatureService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceForceOrderWebSocketService> forceOrderProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceKlineWebSocketService> provider = mock(ObjectProvider.class);
        BinanceApi binanceApi = new BinanceApi(klineRestClient, provider, derivativeRestClient, forceOrderProvider, derivativeFeatureService);

        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol("BTCUSDT");
        request.setInterval("4h");
        request.setLimit(180);

        List<BinanceKlineDTO> fallback = List.of(new BinanceKlineDTO());
        when(provider.getIfAvailable()).thenReturn(webSocketService);
        when(webSocketService.getRecentKlines(request)).thenReturn(List.of());
        when(klineRestClient.listKline(any(BinanceKlineDTO.class))).thenReturn(fallback);

        List<BinanceKlineDTO> actual = binanceApi.listKline(request);

        assertSame(fallback, actual);
        verify(klineRestClient).listKline(request);
    }

    /**
     * 当 WebSocket 服务不可用时，应直接走 REST 查询。
     */
    @Test
    void shouldFallbackToRestWhenWebSocketServiceIsUnavailable() {
        BinanceKlineRestClient klineRestClient = mock(BinanceKlineRestClient.class);
        BinanceDerivativeRestClient derivativeRestClient = mock(BinanceDerivativeRestClient.class);
        BinanceDerivativeFeatureService derivativeFeatureService = mock(BinanceDerivativeFeatureService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceForceOrderWebSocketService> forceOrderProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceKlineWebSocketService> provider = mock(ObjectProvider.class);
        BinanceApi binanceApi = new BinanceApi(klineRestClient, provider, derivativeRestClient, forceOrderProvider, derivativeFeatureService);

        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol("BTCUSDT");
        request.setInterval("4h");
        request.setLimit(180);

        List<BinanceKlineDTO> fallback = List.of(new BinanceKlineDTO());
        when(provider.getIfAvailable()).thenReturn(null);
        when(klineRestClient.listKline(request)).thenReturn(fallback);

        List<BinanceKlineDTO> actual = binanceApi.listKline(request);

        assertSame(fallback, actual);
        verify(klineRestClient).listKline(request);
    }
}
