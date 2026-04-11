package com.mobai.alert.access.kline.dto;

import java.util.List;

/**
 * Binance 交易对列表响应对象。
 * 对应 {@code exchangeInfo} 接口中的 {@code symbols} 节点，
 * 主要供监控任务筛选目标交易对和读取交易状态。
 */
public class BinanceSymbolsDTO {

    /**
     * Binance 返回的交易对明细列表。
     */
    private List<BinanceSymbolsDetailDTO> symbols;

    public List<BinanceSymbolsDetailDTO> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<BinanceSymbolsDetailDTO> symbols) {
        this.symbols = symbols;
    }
}
