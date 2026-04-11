package com.mobai.alert.access.capitalflow.rest;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BinanceDerivativeRestClientTests {

    @Test
    void shouldFetchTakerBuySellVolumesFromCurrentBinancePath() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        BinanceDerivativeRestClient client = new BinanceDerivativeRestClient(restTemplate);
        when(restTemplate.getForObject(
                "https://fapi.binance.com/futures/data/takerlongshortRatio?symbol=BTCUSDT&period=5m&limit=2",
                String.class))
                .thenReturn("[{\"buyVol\":\"120\",\"sellVol\":\"80\",\"timestamp\":1}]");

        var result = client.listTakerBuySellVolumes("BTCUSDT", "5m", 2, null, null);

        assertEquals(1, result.size());
        assertEquals("120", result.get(0).getBuyVol());
        assertTrue(result.get(0).getTimestamp() == 1L);
    }
}

