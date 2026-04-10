package com.mobai.alert.access.binance.derivative.service;

import com.mobai.alert.access.binance.derivative.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceFundingRateDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceOpenInterestDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTakerBuySellVolumeDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTopTraderRatioDTO;
import com.mobai.alert.access.binance.derivative.rest.BinanceDerivativeRestClient;
import com.mobai.alert.access.binance.derivative.stream.BinanceForceOrderWebSocketService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BinanceDerivativeFeatureServiceTests {

    @Test
    void shouldBuildDerivativeFeaturesFromRawInputs() {
        BinanceDerivativeRestClient derivativeRestClient = mock(BinanceDerivativeRestClient.class);
        BinanceForceOrderWebSocketService forceOrderWebSocketService = mock(BinanceForceOrderWebSocketService.class);
        BinanceDerivativeFeatureService service = new BinanceDerivativeFeatureService(derivativeRestClient, forceOrderWebSocketService);

        ReflectionTestUtils.setField(service, "derivativesEnabled", true);
        ReflectionTestUtils.setField(service, "backtestEnabled", false);
        ReflectionTestUtils.setField(service, "targetSymbol", "BTCUSDT");
        ReflectionTestUtils.setField(service, "oiWindowMs", 100L);
        ReflectionTestUtils.setField(service, "fundingLookback", 3);
        ReflectionTestUtils.setField(service, "takerPeriod", "5m");
        ReflectionTestUtils.setField(service, "takerLimit", 1);
        ReflectionTestUtils.setField(service, "ratioPeriod", "5m");
        ReflectionTestUtils.setField(service, "ratioLimit", 2);
        ReflectionTestUtils.setField(service, "liquidationWindowMs", 300000L);

        BinanceOpenInterestDTO baseline = new BinanceOpenInterestDTO();
        baseline.setOpenInterest("1000");
        ReflectionTestUtils.invokeMethod(service, "cacheOpenInterest", "BTCUSDT", baseline);
        try {
            Thread.sleep(120L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        BinanceOpenInterestDTO latest = new BinanceOpenInterestDTO();
        latest.setOpenInterest("1025");
        when(derivativeRestClient.getOpenInterest("BTCUSDT")).thenReturn(latest);
        when(derivativeRestClient.listFundingRates("BTCUSDT", 3, null, null)).thenReturn(List.of(
                fundingRate("0.0010"),
                fundingRate("0.0020"),
                fundingRate("0.0040")
        ));
        when(derivativeRestClient.listTakerBuySellVolumes("BTCUSDT", "5m", 1, null, null)).thenReturn(List.of(
                takerVolume("120", "80")
        ));
        when(derivativeRestClient.listTopTraderAccountRatios("BTCUSDT", "5m", 2, null, null)).thenReturn(List.of(
                ratio("1.10"),
                ratio("1.25")
        ));
        when(derivativeRestClient.listTopTraderPositionRatios("BTCUSDT", "5m", 2, null, null)).thenReturn(List.of(
                ratio("1.05"),
                ratio("1.20")
        ));
        when(forceOrderWebSocketService.calculateClusterIntensity("BTCUSDT", 300000L))
                .thenReturn(new BigDecimal("250000.50"));

        BinanceDerivativeFeaturesDTO features = service.buildFeatures("BTCUSDT");

        assertNotNull(features);
        assertEquals(new BigDecimal("25"), features.getOiDelta5m());
        assertEquals(new BigDecimal("0.2"), features.getTakerBuySellImbalance().stripTrailingZeros());
        assertEquals(new BigDecimal("0.15"), features.getTopTraderAccountRatioChange().stripTrailingZeros());
        assertEquals(new BigDecimal("0.15"), features.getTopTraderPositionRatioChange().stripTrailingZeros());
        assertEquals(new BigDecimal("250000.50"), features.getLiquidationClusterIntensity());
    }

    private BinanceFundingRateDTO fundingRate(String value) {
        BinanceFundingRateDTO dto = new BinanceFundingRateDTO();
        dto.setFundingRate(value);
        return dto;
    }

    private BinanceTakerBuySellVolumeDTO takerVolume(String buyVol, String sellVol) {
        BinanceTakerBuySellVolumeDTO dto = new BinanceTakerBuySellVolumeDTO();
        dto.setBuyVol(buyVol);
        dto.setSellVol(sellVol);
        return dto;
    }

    private BinanceTopTraderRatioDTO ratio(String longShortRatio) {
        BinanceTopTraderRatioDTO dto = new BinanceTopTraderRatioDTO();
        dto.setLongShortRatio(longShortRatio);
        return dto;
    }
}

