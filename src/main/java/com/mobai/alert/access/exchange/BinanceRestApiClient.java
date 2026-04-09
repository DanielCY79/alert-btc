package com.mobai.alert.access.exchange;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.access.dto.BinanceSymbolsDTO;
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

@Component
public class BinanceRestApiClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceRestApiClient.class);
    private static final String BASE_URL = "https://api.binance.com/api/v3/ticker/price";
    private static final String KLINE_BASE_URL = "https://fapi.binance.com/fapi/v1/klines";
    private static final String SYMBOLS_BASE_URL = "https://fapi.binance.com/fapi/v1/exchangeInfo";

    private final RestTemplate restTemplate;

    @Value("${binance.api.key:6JyypvY7m4zramFJkkWbgy}")
    private String apiKey;

    public BinanceRestApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void testTime() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = BASE_URL + "?symbol=BTCUSDT";

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        try {
            JSONObject jsonObject = JSON.parseObject(response.getBody());
            log.info("Binance 最新价格查询成功，交易对：{}，价格：{}",
                    jsonObject.getString("symbol"),
                    jsonObject.getString("price"));
        } catch (Exception e) {
            log.error("解析 Binance 最新价格响应失败", e);
        }
    }

    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        StringBuilder urlBuilder = new StringBuilder(KLINE_BASE_URL)
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

    public BinanceSymbolsDTO listSymbols() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(SYMBOLS_BASE_URL, HttpMethod.GET, entity, String.class);
            BinanceSymbolsDTO symbolsDTO = JSON.parseObject(response.getBody(), BinanceSymbolsDTO.class);
            int symbolCount = symbolsDTO == null || symbolsDTO.getSymbols() == null ? 0 : symbolsDTO.getSymbols().size();
            log.info("Binance 交易对信息拉取完成，共 {} 个交易对", symbolCount);
            return symbolsDTO;
        } catch (Exception e) {
            log.error("拉取 Binance 交易对信息失败", e);
        }

        return new BinanceSymbolsDTO();
    }
}
