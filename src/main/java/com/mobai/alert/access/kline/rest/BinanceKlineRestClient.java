package com.mobai.alert.access.kline.rest;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.support.BinanceKlineMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Binance kline REST client.
 */
@Component
public class BinanceKlineRestClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineRestClient.class);
    private static final String FUTURES_BASE_URL = "https://fapi.binance.com";
    private static final String PRICE_URL = "https://api.binance.com/api/v3/ticker/price";
    private static final String KLINE_URL = FUTURES_BASE_URL + "/fapi/v1/klines";

    private final RestTemplate restTemplate;

    @Value("${binance.api.key:}")
    private String apiKey;

    public BinanceKlineRestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void testTime() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                PRICE_URL + "?symbol=BTCUSDT",
                HttpMethod.GET,
                entity,
                String.class
        );

        try {
            var jsonObject = JSON.parseObject(response.getBody());
            log.info("Binance latest price query succeeded, symbol={}, price={}",
                    jsonObject.getString("symbol"),
                    jsonObject.getString("price"));
        } catch (Exception e) {
            log.error("Failed to parse Binance latest price response", e);
        }
    }

    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
        try {
            return doListKline(reqDTO);
        } catch (Exception e) {
            log.error("Binance kline query failed, symbol={}, interval={}, limit={}, startTime={}, endTime={}",
                    reqDTO.getSymbol(),
                    reqDTO.getInterval(),
                    reqDTO.getLimit(),
                    reqDTO.getStartTime(),
                    reqDTO.getEndTime(),
                    e);
            return List.of();
        }
    }

    public List<BinanceKlineDTO> listKlineStrict(BinanceKlineDTO reqDTO) {
        try {
            return doListKline(reqDTO);
        } catch (Exception e) {
            log.error("Strict Binance kline query failed, symbol={}, interval={}, limit={}, startTime={}, endTime={}",
                    reqDTO.getSymbol(),
                    reqDTO.getInterval(),
                    reqDTO.getLimit(),
                    reqDTO.getStartTime(),
                    reqDTO.getEndTime(),
                    e);
            throw new IllegalStateException("Failed to fetch Binance klines", e);
        }
    }

    private List<BinanceKlineDTO> doListKline(BinanceKlineDTO reqDTO) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        StringBuilder urlBuilder = new StringBuilder(KLINE_URL)
                .append("?symbol=").append(reqDTO.getSymbol())
                .append("&interval=").append(reqDTO.getInterval())
                .append("&limit=").append(reqDTO.getLimit());

        if (reqDTO.getStartTime() != null) {
            urlBuilder.append("&startTime=").append(reqDTO.getStartTime());
        }
        if (reqDTO.getEndTime() != null) {
            urlBuilder.append("&endTime=").append(reqDTO.getEndTime());
        }
        if (reqDTO.getTimeZone() != null) {
            urlBuilder.append("&timeZone=").append(reqDTO.getTimeZone());
        }

        ResponseEntity<String> response = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity, String.class);
        JSONArray rows = JSON.parseArray(response.getBody());
        List<BinanceKlineDTO> klines = new ArrayList<>(rows.size());
        for (Object row : rows) {
            klines.add(BinanceKlineMapper.fromRestRow(reqDTO.getSymbol(), reqDTO.getInterval(), JSON.parseArray(row.toString())));
        }
        return klines;
    }
}
