package com.mobai.alert.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
public class BinanceApi {
    private static final String BASE_URL = "https://api.binance.com/api/v3/ticker/price";
    private static final String KLINE_BASE_URL = "https://fapi.binance.com/fapi/v1/klines";
    private static final String SYMBOLS_BASE_URL = "https://fapi.binance.com/fapi/v1/exchangeInfo";

    @Autowired
    private RestTemplate restTemplate;

    private String apiKey = "6JyypvY7m4zramFJkkWbgy";

    /**
     * 测试代码
     */
    public void testTime() {
        // 创建 HttpHeaders 并添加自定义 Header
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey); // 替换为你的API Key

        // 使用 HttpEntity 包装请求头
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = BASE_URL + "?symbol=BTCUSDT";

        // 发送请求并接收响应
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        try {
            // 使用 FastJSON 解析响应体
            JSONObject jsonObject = JSON.parseObject(response.getBody());

            System.out.println("Symbol: " + jsonObject.getString("symbol"));
            System.out.println("Price: " + jsonObject.getString("price"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        // 使用 HttpEntity 包装请求头
        HttpEntity<String> entity = new HttpEntity<>(headers);
        // 组装参数
        String url = KLINE_BASE_URL + "?symbol=" + reqDTO.getSymbol();
        url = url + "&interval=" + reqDTO.getInterval();
//        url = url + "&startTime=" + reqDTO.getStartTime();
//        url = url + "&endTime=" + reqDTO.getEndTime();
        url = url + "&limit=" + reqDTO.getLimit();
//        url = url + "&timeZone=" + reqDTO.getTimeZone();

        List<BinanceKlineDTO> binanceKlineDTOS = new ArrayList<>();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            JSONArray objects = JSON.parseArray(body);
            for (Object object : objects) {
                BinanceKlineDTO tmpDTO = new BinanceKlineDTO();
                JSONArray tmpArr = JSON.parseArray(object.toString());
                tmpDTO.setSymbol(reqDTO.getSymbol());
                tmpDTO.setInterval(reqDTO.getInterval());
                tmpDTO.setStartTime(Long.parseLong(tmpArr.get(0).toString()));
                tmpDTO.setHigh(tmpArr.get(2).toString());
                tmpDTO.setLow(tmpArr.get(3).toString());
                tmpDTO.setOpen(tmpArr.get(1).toString());
                tmpDTO.setClose(tmpArr.get(4).toString());
                tmpDTO.setAmount(tmpArr.get(5).toString());
                tmpDTO.setVolume(tmpArr.get(7).toString());
                tmpDTO.setEndTime(Long.parseLong(tmpArr.get(6).toString()));
                binanceKlineDTOS.add(tmpDTO);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return binanceKlineDTOS;
    }

    public BinanceSymbolsDTO listSymbols() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        // 使用 HttpEntity 包装请求头
        HttpEntity<String> entity = new HttpEntity<>(headers);
        // 组装参数
        String url = SYMBOLS_BASE_URL;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            System.out.println("listSymbols res:" + response.getBody());
            BinanceSymbolsDTO symbolsDTO = JSON.parseObject(response.getBody(), BinanceSymbolsDTO.class);
            return symbolsDTO;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new BinanceSymbolsDTO();
    }
}
