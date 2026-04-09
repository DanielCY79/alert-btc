package com.mobai.alert.access.exchange;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.access.dto.BinanceSymbolsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class BinanceApi {

    private static final Logger log = LoggerFactory.getLogger(BinanceApi.class);

    private final BinanceRestApiClient restApiClient;
    private final ObjectProvider<BinanceKlineWebSocketService> webSocketServiceProvider;

    public BinanceApi(BinanceRestApiClient restApiClient,
                      ObjectProvider<BinanceKlineWebSocketService> webSocketServiceProvider) {
        this.restApiClient = restApiClient;
        this.webSocketServiceProvider = webSocketServiceProvider;
    }

    public void testTime() {
        restApiClient.testTime();
    }

    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
        BinanceKlineWebSocketService webSocketService = webSocketServiceProvider.getIfAvailable();
        if (webSocketService != null) {
            List<BinanceKlineDTO> klines = webSocketService.getRecentKlines(reqDTO);
            if (!CollectionUtils.isEmpty(klines)) {
                log.info("K 线请求命中 Binance WebSocket 本地缓存，symbol={}，interval={}，limit={}",
                        reqDTO.getSymbol(),
                        reqDTO.getInterval(),
                        reqDTO.getLimit());
                return klines;
            }
        }
        return restApiClient.listKline(reqDTO);
    }

    public BinanceSymbolsDTO listSymbols() {
        return restApiClient.listSymbols();
    }
}
