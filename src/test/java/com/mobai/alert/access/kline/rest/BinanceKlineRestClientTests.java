package com.mobai.alert.access.kline.rest;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Binance K 线 REST 客户端测试，验证 K 线接口解析逻辑。
 */
class BinanceKlineRestClientTests {

    /**
     * 调用 K 线查询接口时，应正确解析返回结果。
     */
    @Test
    void shouldFetchKlines() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BinanceKlineRestClient client = new BinanceKlineRestClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "demo-key");

        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol("BTCUSDT");
        request.setInterval("15m");
        request.setLimit(1);

        when(restTemplate.exchange(
                eq("https://fapi.binance.com/fapi/v1/klines?symbol=BTCUSDT&interval=15m&limit=1"),
                eq(org.springframework.http.HttpMethod.GET),
                org.mockito.ArgumentMatchers.any(),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("[[1712736000000,\"68000.1\",\"68100.2\",\"67950.3\",\"68080.4\",\"12.5\",1712736899999,\"850000.0\"]]"));

        BinanceKlineDTO dto = client.listKline(request).get(0);

        assertEquals("BTCUSDT", dto.getSymbol());
        assertEquals("15m", dto.getInterval());
        assertEquals(1712736000000L, dto.getStartTime());
        assertEquals("68080.4", dto.getClose());
    }
}

