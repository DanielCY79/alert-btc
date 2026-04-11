package com.mobai.alert.access.kline.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;

/**
 * Binance K 线数据映射器。
 * 负责把 REST 接口返回的数组结构、以及 WebSocket 推送的 JSON 结构，
 * 转换成项目内部统一使用的 {@link BinanceKlineDTO}。
 */
public final class BinanceKlineMapper {

    private BinanceKlineMapper() {
    }

    /**
     * 将 REST 接口的一行数组数据映射为标准 K 线对象。
     *
     * @param symbol 交易对代码
     * @param interval K 线周期
     * @param raw Binance REST 原始数组
     * @return 标准化后的 K 线 DTO
     */
    public static BinanceKlineDTO fromRestRow(String symbol, String interval, JSONArray raw) {
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

    /**
     * 将 WebSocket 的 K 线事件映射为标准 K 线对象。
     *
     * @param rawKline Binance WebSocket 原始 K 线节点
     * @return 标准化后的 K 线 DTO
     */
    public static BinanceKlineDTO fromWebSocketEvent(JSONObject rawKline) {
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
