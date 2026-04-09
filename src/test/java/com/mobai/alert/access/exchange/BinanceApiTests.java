package com.mobai.alert.access.exchange;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceApiTests {

    @Test
    void shouldPreferWebSocketCacheForRecentKlines() {
        BinanceRestApiClient restApiClient = mock(BinanceRestApiClient.class);
        BinanceKlineWebSocketService webSocketService = mock(BinanceKlineWebSocketService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceKlineWebSocketService> provider = mock(ObjectProvider.class);
        BinanceApi binanceApi = new BinanceApi(restApiClient, provider);

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

    @Test
    void shouldFallbackToRestWhenWebSocketCacheMisses() {
        BinanceRestApiClient restApiClient = mock(BinanceRestApiClient.class);
        BinanceKlineWebSocketService webSocketService = mock(BinanceKlineWebSocketService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceKlineWebSocketService> provider = mock(ObjectProvider.class);
        BinanceApi binanceApi = new BinanceApi(restApiClient, provider);

        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol("BTCUSDT");
        request.setInterval("4h");
        request.setLimit(180);

        List<BinanceKlineDTO> fallback = List.of(new BinanceKlineDTO());
        when(provider.getIfAvailable()).thenReturn(webSocketService);
        when(webSocketService.getRecentKlines(request)).thenReturn(List.of());
        when(restApiClient.listKline(any(BinanceKlineDTO.class))).thenReturn(fallback);

        List<BinanceKlineDTO> actual = binanceApi.listKline(request);

        assertSame(fallback, actual);
        verify(restApiClient).listKline(request);
    }

    @Test
    void shouldFallbackToRestWhenWebSocketServiceIsUnavailable() {
        BinanceRestApiClient restApiClient = mock(BinanceRestApiClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BinanceKlineWebSocketService> provider = mock(ObjectProvider.class);
        BinanceApi binanceApi = new BinanceApi(restApiClient, provider);

        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol("BTCUSDT");
        request.setInterval("4h");
        request.setLimit(180);

        List<BinanceKlineDTO> fallback = List.of(new BinanceKlineDTO());
        when(provider.getIfAvailable()).thenReturn(null);
        when(restApiClient.listKline(request)).thenReturn(fallback);

        List<BinanceKlineDTO> actual = binanceApi.listKline(request);

        assertSame(fallback, actual);
        verify(restApiClient).listKline(request);
    }
}
