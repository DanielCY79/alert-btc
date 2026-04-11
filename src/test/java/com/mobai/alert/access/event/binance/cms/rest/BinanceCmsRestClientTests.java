package com.mobai.alert.access.event.binance.cms.rest;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BinanceCmsRestClientTests {

    @Test
    void shouldReturnBinanceServerTime() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BinanceCmsRestClient client = new BinanceCmsRestClient(restTemplate);
        when(restTemplate.getForObject("https://api.binance.com/api/v3/time", String.class))
                .thenReturn("{\"serverTime\":1744287500123}");

        Long serverTime = client.getServerTime();

        assertEquals(1744287500123L, serverTime);
    }

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

