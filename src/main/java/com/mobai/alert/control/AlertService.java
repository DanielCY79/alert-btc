package com.mobai.alert.control;

import com.mobai.alert.access.dto.BinanceSymbolsDTO;
import com.mobai.alert.access.dto.BinanceSymbolsDetailDTO;
import com.mobai.alert.access.market.AlertSymbolCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(value = "backtest.enabled", havingValue = "false", matchIfMissing = true)
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final String TARGET_SYMBOL = "BTCUSDT";
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertSymbolCacheService alertSymbolCacheService;
    private final AlertSymbolProcessor alertSymbolProcessor;

    public AlertService(AlertSymbolCacheService alertSymbolCacheService, AlertSymbolProcessor alertSymbolProcessor) {
        this.alertSymbolCacheService = alertSymbolCacheService;
        this.alertSymbolProcessor = alertSymbolProcessor;
    }

    @Scheduled(fixedRate = 60000)
    public void monitoring() {
        log.info("监控任务开始，开始时间={}，目标交易对={}", LOG_TIME_FORMATTER.format(LocalDateTime.now()), TARGET_SYMBOL);

        BinanceSymbolsDTO symbolsDTO;
        try {
            symbolsDTO = alertSymbolCacheService.loadSymbols();
        } catch (IOException e) {
            log.error("加载交易对缓存失败", e);
            throw new RuntimeException(e);
        }

        if (symbolsDTO == null || CollectionUtils.isEmpty(symbolsDTO.getSymbols())) {
            log.warn("本轮监控未获取到可用交易对，任务结束");
            return;
        }
        log.info("本轮监控共加载 {} 个交易对", symbolsDTO.getSymbols().size());

        List<BinanceSymbolsDetailDTO> targetSymbols = symbolsDTO.getSymbols().stream()
                .filter(symbol -> TARGET_SYMBOL.equals(symbol.getSymbol()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(targetSymbols)) {
            log.warn("交易对列表中未找到目标交易对 {}，任务结束", TARGET_SYMBOL);
            return;
        }

        processSymbols(targetSymbols, 1);
        log.info("监控任务结束，结束时间={}，本轮处理交易对数量={}",
                LOG_TIME_FORMATTER.format(LocalDateTime.now()),
                targetSymbols.size());
    }

    private void processSymbols(List<BinanceSymbolsDetailDTO> symbols, int threadCount) {
        int poolSize = Math.max(1, Math.min(threadCount, symbols.size()));
        log.info("开始分发交易对处理任务，symbols={}，线程数={}", symbols.size(), poolSize);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<CompletableFuture<Void>> futures = symbols.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> alertSymbolProcessor.process(symbol), executor)
                            .exceptionally(ex -> {
                                log.error("处理交易对失败，symbol={}", symbol.getSymbol(), ex);
                                return null;
                            }))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
            log.info("交易对处理任务全部完成，线程池已关闭");
        }
    }
}
