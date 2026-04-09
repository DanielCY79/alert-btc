package com.mobai.alert.access.exchange;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.dto.BinanceKlineDTO;

final class BinanceKlineMapper {

    private BinanceKlineMapper() {
    }

    static BinanceKlineDTO fromRestRow(String symbol, String interval, JSONArray raw) {
        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol(symbol);
        kline.setInterval(interval);
        kline.setStartTime(raw.getLong(0));
        kline.setOpen(raw.getString(1));
        kline.setHigh(raw.getString(2));
        kline.setLow(raw.getString(3));
        kline.setClose(raw.getString(4));
        kline.setAmount(raw.getString(5));
        kline.setEndTime(raw.getLong(6));
        kline.setVolume(raw.getString(7));
        return kline;
    }

    static BinanceKlineDTO fromWebSocketEvent(JSONObject rawKline) {
        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol(rawKline.getString("s"));
        kline.setInterval(rawKline.getString("i"));
        kline.setStartTime(rawKline.getLong("t"));
        kline.setEndTime(rawKline.getLong("T"));
        kline.setOpen(rawKline.getString("o"));
        kline.setClose(rawKline.getString("c"));
        kline.setHigh(rawKline.getString("h"));
        kline.setLow(rawKline.getString("l"));
        kline.setAmount(rawKline.getString("v"));
        kline.setVolume(rawKline.getString("q"));
        return kline;
    }
}
