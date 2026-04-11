package com.mobai.alert.access;

import com.mobai.alert.access.capitalflow.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceForceOrderDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceFundingRateDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceOpenInterestDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceTakerBuySellVolumeDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceTopTraderRatioDTO;
import com.mobai.alert.access.capitalflow.rest.BinanceDerivativeRestClient;
import com.mobai.alert.access.capitalflow.service.BinanceDerivativeFeatureService;
import com.mobai.alert.access.capitalflow.stream.BinanceForceOrderWebSocketService;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.dto.BinanceSymbolsDTO;
import com.mobai.alert.access.kline.rest.BinanceKlineRestClient;
import com.mobai.alert.access.kline.stream.BinanceKlineWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Binance 接入门面。
 * 用统一入口封装 K 线、衍生品指标和强平流访问细节，
 * 让控制层无需直接关心底层到底走 REST、WebSocket 还是聚合服务。
 */
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

    /**
     * 通过公开接口验证 Binance 连通性。
     */
    public void testTime() {
        klineRestClient.testTime();
    }

    /**
     * 获取 K 线列表。
     * 优先尝试从 WebSocket 本地缓存读取，缓存不可用时自动降级到 REST。
     *
     * @param reqDTO K 线查询参数
     * @return K 线列表
     */
    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
        BinanceKlineWebSocketService webSocketService = klineWebSocketServiceProvider.getIfAvailable();
        if (webSocketService != null) {
            List<BinanceKlineDTO> klines = webSocketService.getRecentKlines(reqDTO);
            if (!CollectionUtils.isEmpty(klines)) {
                log.info("K 线数据命中 Binance WebSocket 缓存，symbol={}，interval={}，limit={}",
                        reqDTO.getSymbol(),
                        reqDTO.getInterval(),
                        reqDTO.getLimit());
                return klines;
            }
        }
        return klineRestClient.listKline(reqDTO);
    }

    /**
     * 获取 Binance 交易对列表。
     *
     * @return 交易对列表 DTO
     */
    public BinanceSymbolsDTO listSymbols() {
        return klineRestClient.listSymbols();
    }

    /**
     * 获取当前持仓量。
     *
     * @param symbol 交易对
     * @return 持仓量 DTO
     */
    public BinanceOpenInterestDTO getOpenInterest(String symbol) {
        return derivativeRestClient.getOpenInterest(symbol);
    }

    /**
     * 获取资金费率序列。
     */
    public List<BinanceFundingRateDTO> listFundingRates(String symbol, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listFundingRates(symbol, limit, startTime, endTime);
    }

    /**
     * 获取主动买卖量序列。
     */
    public List<BinanceTakerBuySellVolumeDTO> listTakerBuySellVolumes(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listTakerBuySellVolumes(symbol, period, limit, startTime, endTime);
    }

    /**
     * 获取头部账户多空比序列。
     */
    public List<BinanceTopTraderRatioDTO> listTopTraderAccountRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listTopTraderAccountRatios(symbol, period, limit, startTime, endTime);
    }

    /**
     * 获取头部持仓多空比序列。
     */
    public List<BinanceTopTraderRatioDTO> listTopTraderPositionRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return derivativeRestClient.listTopTraderPositionRatios(symbol, period, limit, startTime, endTime);
    }

    /**
     * 获取最近时间窗内的强平订单列表。
     * 这里完全依赖 WebSocket 内存缓存，没有缓存服务时返回空列表。
     */
    public List<BinanceForceOrderDTO> getRecentForceOrders(String symbol, long withinMs) {
        BinanceForceOrderWebSocketService webSocketService = forceOrderWebSocketServiceProvider.getIfAvailable();
        if (webSocketService == null) {
            return List.of();
        }
        return webSocketService.getRecentForceOrders(symbol, withinMs);
    }

    /**
     * 生成衍生品特征快照。
     *
     * @param symbol 交易对
     * @return 聚合特征 DTO
     */
    public BinanceDerivativeFeaturesDTO buildDerivativeFeatures(String symbol) {
        return derivativeFeatureService.buildFeatures(symbol);
    }
}
