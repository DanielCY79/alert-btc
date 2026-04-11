package com.mobai.alert.access.event.binance.cms.rest;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Binance 公告 REST 客户端测试，覆盖服务端时间拉取逻辑。
 */
class BinanceCmsRestClientTests {

    /**
     * 正常返回时，应解析出 Binance 服务端时间戳。
     */
    @Test
    void shouldReturnBinanceServerTime() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BinanceCmsRestClient client = new BinanceCmsRestClient(restTemplate);
        when(restTemplate.getForObject("https://api.binance.com/api/v3/time", String.class))
                .thenReturn("{\"serverTime\":1744287500123}");

        Long serverTime = client.getServerTime();

        assertEquals(1744287500123L, serverTime);
    }

    /**
     * 发生异常时，应返回空值而不是向上抛出错误。
     */
    @Test
    void shouldReturnNullWhenServerTimeFetchFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BinanceCmsRestClient client = new BinanceCmsRestClient(restTemplate);
        when(restTemplate.getForObject("https://api.binance.com/api/v3/time", String.class))
                .thenThrow(new IllegalStateException("boom"));

        Long serverTime = client.getServerTime();

        assertNull(serverTime);
    }
}

