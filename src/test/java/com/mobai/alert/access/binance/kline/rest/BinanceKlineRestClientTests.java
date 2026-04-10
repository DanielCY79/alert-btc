package com.mobai.alert.access.binance.kline.rest;

import com.mobai.alert.access.binance.kline.dto.BinanceSymbolsDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BinanceKlineRestClientTests {

    @Test
    void shouldFetchSymbols() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BinanceKlineRestClient client = new BinanceKlineRestClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "demo-key");
        when(restTemplate.exchange(
                eq("https://fapi.binance.com/fapi/v1/exchangeInfo"),
                eq(org.springframework.http.HttpMethod.GET),
                org.mockito.ArgumentMatchers.any(),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"symbols\":[{\"symbol\":\"BTCUSDT\",\"status\":\"TRADING\"}]}"));

        BinanceSymbolsDTO dto = client.listSymbols();

        assertEquals(1, dto.getSymbols().size());
        assertEquals("BTCUSDT", dto.getSymbols().get(0).getSymbol());
    }
}

