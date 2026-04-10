package com.mobai.alert.access.facade;

import com.mobai.alert.access.binance.derivative.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceForceOrderDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceFundingRateDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceOpenInterestDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTakerBuySellVolumeDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTopTraderRatioDTO;
import com.mobai.alert.access.binance.derivative.rest.BinanceDerivativeRestClient;
import com.mobai.alert.access.binance.derivative.service.BinanceDerivativeFeatureService;
import com.mobai.alert.access.binance.derivative.stream.BinanceForceOrderWebSocketService;
import com.mobai.alert.access.binance.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.binance.kline.dto.BinanceSymbolsDTO;
import com.mobai.alert.access.binance.kline.rest.BinanceKlineRestClient;
import com.mobai.alert.access.binance.kline.stream.BinanceKlineWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * access 灞傜粺涓€闂ㄩ潰銆? * 涓婂眰妯″潡鍙緷璧栬繖閲岋紝搴曚笅鐨?K 绾裤€丆MS銆佽鐢熷搧鍒嗗眰閮藉湪鍐呴儴鏀舵暃銆? */
@Component
public class BinanceApi {

    private static final Logger log = LoggerFactory.getLogger(BinanceApi.class);

    private final BinanceKlineRestClient klineRestClient;
    private final ObjectProvider<BinanceKlineWebSocketService> klineWebSocketServiceProvider;
    private final BinanceDerivativeRestClient derivativeRestClient;
    private final ObjectProvider<BinanceForceOrderWebSocketService> forceOrderWebSocketServiceProvider;
    private final BinanceDerivativeFeatureService derivativeFeatureService;

    public BinanceApi(BinanceKlineRestClient klineRestClient,
                      ObjectProvider<BinanceKlineWebSocketService> klineWebSocketServiceProvider,
                      BinanceDerivativeRestClient derivativeRestClient,
                      ObjectProvider<BinanceForceOrderWebSocketService> forceOrderWebSocketServiceProvider,
                      BinanceDerivativeFeatureService derivativeFeatureService) {
        this.klineRestClient = klineRestClient;
        this.klineWebSocketServiceProvider = klineWebSocketServiceProvider;
        this.derivativeRestClient = derivativeRestClient;
        this.forceOrderWebSocketServiceProvider = forceOrderWebSocketServiceProvider;
        this.derivativeFeatureService = derivativeFeatureService;
    }

    public void testTime() {
        klineRestClient.testTime();
    }

    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
        BinanceKlineWebSocketService webSocketService = klineWebSocketServiceProvider.getIfAvailable();
        if (webSocketService != null) {
            List<BinanceKlineDTO> klines = webSocketService.getRecentKlines(reqDTO);
            if (!CollectionUtils.isEmpty(klines)) {
                log.info("K绾胯姹傚懡涓?Binance WebSocket 鏈湴缂撳瓨锛宻ymbol={}锛宨nterval={}锛宭imit={}",
                        reqDTO.getSymbol(),
                        reqDTO.getInterval(),
                        reqDTO.getLimit());
                return klines;
            }
        }
        return klineRestClient.listKline(reqDTO);
    }

    public BinanceSymbolsDTO listSymbols() {
        return klineRestClient.listSymbols();
    }

    public BinanceOpenInterestDTO getOpenInterest(String symbol) {
        return derivativeRestClient.getOpenInterest(symbol);
    }

    public List<BinanceFundingRateDTO> listFundingRates(String symbol, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listFundingRates(symbol, limit, startTime, endTime);
    }

    public List<BinanceTakerBuySellVolumeDTO> listTakerBuySellVolumes(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listTakerBuySellVolumes(symbol, period, limit, startTime, endTime);
    }

    public List<BinanceTopTraderRatioDTO> listTopTraderAccountRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listTopTraderAccountRatios(symbol, period, limit, startTime, endTime);
    }

    public List<BinanceTopTraderRatioDTO> listTopTraderPositionRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listTopTraderPositionRatios(symbol, period, limit, startTime, endTime);
    }

    public List<BinanceForceOrderDTO> getRecentForceOrders(String symbol, long withinMs) {
        BinanceForceOrderWebSocketService webSocketService = forceOrderWebSocketServiceProvider.getIfAvailable();
        if (webSocketService == null) {
            return List.of();
        }
        return webSocketService.getRecentForceOrders(symbol, withinMs);
    }

    public BinanceDerivativeFeaturesDTO buildDerivativeFeatures(String symbol) {
        return derivativeFeatureService.buildFeatures(symbol);
    }
}

