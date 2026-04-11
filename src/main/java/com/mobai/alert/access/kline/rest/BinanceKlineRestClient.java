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
 * Binance K 线 REST 客户端。
 * 负责直接访问 Binance Futures HTTP 接口，拉取历史 K 线和交易对元数据。
 * 这是 WebSocket 缓存失效或启动预热时的兜底数据来源。
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

    /**
     * 通过查询 BTCUSDT 最新价格验证 REST 连通性。
     */
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
            log.info("Binance 最新价格查询成功，symbol={}，price={}",
                    jsonObject.getString("symbol"),
                    jsonObject.getString("price"));
        } catch (Exception e) {
            log.error("解析 Binance 最新价格响应失败", e);
        }
    }

    /**
     * 通过 REST 接口拉取 K 线数据。
     *
     * @param reqDTO 查询参数
     * @return 标准化后的 K 线列表
     */
    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
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

        List<BinanceKlineDTO> klines = new ArrayList<>();
        try {
            ResponseEntity<String> response = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity, String.class);
            JSONArray rows = JSON.parseArray(response.getBody());
            for (Object row : rows) {
                klines.add(BinanceKlineMapper.fromRestRow(reqDTO.getSymbol(), reqDTO.getInterval(), JSON.parseArray(row.toString())));
            }
        } catch (Exception e) {
            log.error("拉取 Binance K 线失败，symbol={}，interval={}，limit={}，startTime={}，endTime={}",
                    reqDTO.getSymbol(),
                    reqDTO.getInterval(),
                    reqDTO.getLimit(),
                    reqDTO.getStartTime(),
                    reqDTO.getEndTime(),
                    e);
        }

        return klines;
    }
}
