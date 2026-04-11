package com.mobai.alert.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 实时监控任务调度入口。
 * 负责定时加载目标交易对，并把实际处理工作交给 {@link AlertSymbolProcessor}。
 */
@Service
@ConditionalOnProperty(value = "backtest.enabled", havingValue = "false", matchIfMissing = true)
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${monitoring.target-symbol:BTCUSDT}")
    private String targetSymbol;

    private final AlertSymbolProcessor alertSymbolProcessor;

    public AlertService(AlertSymbolProcessor alertSymbolProcessor) {
        this.alertSymbolProcessor = alertSymbolProcessor;
    }

    /**
     * 按固定周期执行实时监控流程。
     */
    @Scheduled(
            fixedRateString = "${monitoring.cycle:60000}",
            initialDelayString = "${monitoring.initial-delay:0}"
    )
    public void monitoring() {
        log.info("监控任务开始，开始时间={}，目标交易对={}", LOG_TIME_FORMATTER.format(LocalDateTime.now()), targetSymbol);

        if (!StringUtils.hasText(targetSymbol)) {
            log.warn("未配置目标交易对，任务结束");
            return;
        }
        try {
            alertSymbolProcessor.process(targetSymbol);
        } catch (Exception e) {
            log.error("处理目标交易对失败，symbol={}", targetSymbol, e);
        }
        log.info("监控任务结束，结束时间={}，本轮处理交易对数量={}",
                LOG_TIME_FORMATTER.format(LocalDateTime.now()),
                1);
    }
}
