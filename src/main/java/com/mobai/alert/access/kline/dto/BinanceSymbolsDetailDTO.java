package com.mobai.alert.access.kline.dto;

/**
 * 单个 Binance 交易对明细对象。
 * 目前业务侧主要关心交易对代码和状态，
 * 用于判断目标品种是否存在且处于可交易状态。
 */
public class BinanceSymbolsDetailDTO {

    /**
     * 交易对代码，例如 {@code BTCUSDT}。
     */
    private String symbol;

    /**
     * 交易对状态，例如 {@code TRADING}。
     * 非交易状态时，控制层会主动跳过该交易对的监控处理。
     */
    private String status;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
