package com.mobai.alert.access.capitalflow.rest;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.access.capitalflow.dto.BinanceFundingRateDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceOpenInterestDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceTakerBuySellVolumeDTO;
import com.mobai.alert.access.capitalflow.dto.BinanceTopTraderRatioDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Binance 衍生品 REST 客户端。
 * 统一封装持仓量、资金费率、主动买卖量以及头部交易者多空比等接口，
 * 供衍生品特征服务进一步做聚合计算。
 */
@Component
public class BinanceDerivativeRestClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceDerivativeRestClient.class);
    private static final String FUTURES_BASE_URL = "https://fapi.binance.com";
    private static final String OPEN_INTEREST_URL = FUTURES_BASE_URL + "/fapi/v1/openInterest";
    private static final String FUNDING_RATE_URL = FUTURES_BASE_URL + "/fapi/v1/fundingRate";

    /**
     * 实盘验证时该路径可用；旧文档里也出现过 {@code takerBuySellVol}，
     * 但当前环境下该旧路径返回 404，因此这里保留已验证可用的路径。
     */
    private static final String TAKER_BUY_SELL_VOLUME_URL = FUTURES_BASE_URL + "/futures/data/takerlongshortRatio";
    private static final String TOP_LONG_SHORT_ACCOUNT_RATIO_URL = FUTURES_BASE_URL + "/futures/data/topLongShortAccountRatio";
    private static final String TOP_LONG_SHORT_POSITION_RATIO_URL = FUTURES_BASE_URL + "/futures/data/topLongShortPositionRatio";

    private final RestTemplate restTemplate;

    public BinanceDerivativeRestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 获取当前持仓量。
     *
     * @param symbol 交易对
     * @return 持仓量 DTO；失败时返回 {@code null}
     */
    public BinanceOpenInterestDTO getOpenInterest(String symbol) {
        try {
            String body = restTemplate.getForObject(OPEN_INTEREST_URL + "?symbol=" + symbol, String.class);
            return JSON.parseObject(body, BinanceOpenInterestDTO.class);
        } catch (Exception e) {
            log.warn("获取 Binance 持仓量失败，symbol={}", symbol, e);
            return null;
        }
    }

    /**
     * 获取资金费率序列。
     */
    public List<BinanceFundingRateDTO> listFundingRates(String symbol, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(FUNDING_RATE_URL, symbol, null, limit, startTime, endTime),
                BinanceFundingRateDTO.class,
                "资金费率"
        );
    }

    /**
     * 获取主动买卖量序列。
     */
    public List<BinanceTakerBuySellVolumeDTO> listTakerBuySellVolumes(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(TAKER_BUY_SELL_VOLUME_URL, symbol, period, limit, startTime, endTime),
                BinanceTakerBuySellVolumeDTO.class,
                "主动买卖量"
        );
    }

    /**
     * 获取头部账户多空比序列。
     */
    public List<BinanceTopTraderRatioDTO> listTopTraderAccountRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(TOP_LONG_SHORT_ACCOUNT_RATIO_URL, symbol, period, limit, startTime, endTime),
                BinanceTopTraderRatioDTO.class,
                "头部账户多空比"
        );
    }

    /**
     * 获取头部持仓多空比序列。
     */
    public List<BinanceTopTraderRatioDTO> listTopTraderPositionRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(TOP_LONG_SHORT_POSITION_RATIO_URL, symbol, period, limit, startTime, endTime),
                BinanceTopTraderRatioDTO.class,
                "头部持仓多空比"
        );
    }

    /**
     * 构建通用时间序列接口 URL。
     */
    private String buildSeriesUrl(String baseUrl, String symbol, String period, Integer limit, Long startTime, Long endTime) {
        StringBuilder builder = new StringBuilder(baseUrl).append("?symbol=").append(symbol);
        if (period != null) {
            builder.append("&period=").append(period);
        }
        if (limit != null) {
            builder.append("&limit=").append(limit);
        }
        if (startTime != null) {
            builder.append("&startTime=").append(startTime);
        }
        if (endTime != null) {
            builder.append("&endTime=").append(endTime);
        }
        return builder.toString();
    }

    /**
     * 统一处理数组型 REST 响应。
     */
    private <T> List<T> getSeries(String url, Class<T> type, String description) {
        try {
            String body = restTemplate.getForObject(url, String.class);
            return JSON.parseArray(body, type);
        } catch (Exception e) {
            log.warn("获取 Binance {}失败，url={}", description, url, e);
            return List.of();
        }
    }
}
