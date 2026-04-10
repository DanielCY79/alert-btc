package com.mobai.alert.access.binance.derivative.rest;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.access.binance.derivative.dto.BinanceFundingRateDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceOpenInterestDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTakerBuySellVolumeDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTopTraderRatioDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Binance 资金衍生 REST 客户端。
 * 统一封装持仓量、资金费率、主动买卖量、头部账户多空比等接口。
 */
@Component
public class BinanceDerivativeRestClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceDerivativeRestClient.class);
    private static final String FUTURES_BASE_URL = "https://fapi.binance.com";
    private static final String OPEN_INTEREST_URL = FUTURES_BASE_URL + "/fapi/v1/openInterest";
    private static final String FUNDING_RATE_URL = FUTURES_BASE_URL + "/fapi/v1/fundingRate";
    /**
     * 实盘验证时该路径可用；历史文档里出现过 takerBuySellVol，但当前返回 404。
     */
    private static final String TAKER_BUY_SELL_VOLUME_URL = FUTURES_BASE_URL + "/futures/data/takerlongshortRatio";
    private static final String TOP_LONG_SHORT_ACCOUNT_RATIO_URL = FUTURES_BASE_URL + "/futures/data/topLongShortAccountRatio";
    private static final String TOP_LONG_SHORT_POSITION_RATIO_URL = FUTURES_BASE_URL + "/futures/data/topLongShortPositionRatio";

    private final RestTemplate restTemplate;

    public BinanceDerivativeRestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public BinanceOpenInterestDTO getOpenInterest(String symbol) {
        try {
            String body = restTemplate.getForObject(OPEN_INTEREST_URL + "?symbol=" + symbol, String.class);
            return JSON.parseObject(body, BinanceOpenInterestDTO.class);
        } catch (Exception e) {
            log.warn("获取 Binance 持仓量失败，symbol={}", symbol, e);
            return null;
        }
    }

    public List<BinanceFundingRateDTO> listFundingRates(String symbol, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(FUNDING_RATE_URL, symbol, null, limit, startTime, endTime),
                BinanceFundingRateDTO.class,
                "资金费率"
        );
    }

    public List<BinanceTakerBuySellVolumeDTO> listTakerBuySellVolumes(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(TAKER_BUY_SELL_VOLUME_URL, symbol, period, limit, startTime, endTime),
                BinanceTakerBuySellVolumeDTO.class,
                "主动买卖量"
        );
    }

    public List<BinanceTopTraderRatioDTO> listTopTraderAccountRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(TOP_LONG_SHORT_ACCOUNT_RATIO_URL, symbol, period, limit, startTime, endTime),
                BinanceTopTraderRatioDTO.class,
                "头部账户多空比"
        );
    }

    public List<BinanceTopTraderRatioDTO> listTopTraderPositionRatios(String symbol, String period, Integer limit, Long startTime, Long endTime) {
        return getSeries(
                buildSeriesUrl(TOP_LONG_SHORT_POSITION_RATIO_URL, symbol, period, limit, startTime, endTime),
                BinanceTopTraderRatioDTO.class,
                "头部仓位多空比"
        );
    }

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
