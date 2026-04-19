package com.mobai.alert.strategy.delphi2.runtime;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.strategy.delphi2.Delphi2SignalEvaluator;
import com.mobai.alert.strategy.delphi2.shared.Delphi2Support;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import com.mobai.alert.strategy.runtime.StrategyExecutionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(value = "monitoring.strategy.type", havingValue = "delphi2")
public class Delphi2ExecutionProcessor implements StrategyExecutionProcessor {

    private static final Logger log = LoggerFactory.getLogger(Delphi2ExecutionProcessor.class);

    @Value("${monitoring.target-symbol:BTCUSDT}")
    private String targetSymbol;

    @Value("${monitoring.strategy.delphi.entry-interval:1h}")
    private String entryInterval;

    @Value("${monitoring.strategy.delphi.entry-kline-limit:240}")
    private int entryKlineLimit;

    @Value("${monitoring.strategy.delphi.trend-interval:1d}")
    private String trendInterval;

    @Value("${monitoring.strategy.delphi.trend-kline-limit:120}")
    private int trendKlineLimit;

    private final BinanceApi binanceApi;
    private final Delphi2SignalEvaluator signalEvaluator;
    private final AlertNotificationService notificationService;
    private final Map<String, Delphi2RuntimePosition> activePositions = new ConcurrentHashMap<>();

    public Delphi2ExecutionProcessor(BinanceApi binanceApi,
                                     Delphi2SignalEvaluator signalEvaluator,
                                     AlertNotificationService notificationService) {
        this.binanceApi = binanceApi;
        this.signalEvaluator = signalEvaluator;
        this.notificationService = notificationService;
    }

    @Override
    public void process(String symbol) {
        if (shouldSkip(symbol)) {
            return;
        }

        List<BinanceKlineDTO> entryKlines = loadRecentKlines(symbol, entryInterval, entryKlineLimit);
        List<BinanceKlineDTO> trendKlines = loadRecentKlines(symbol, trendInterval, trendKlineLimit);
        if (CollectionUtils.isEmpty(entryKlines) || CollectionUtils.isEmpty(trendKlines)) {
            log.warn("Skip delphi2 processing because klines are empty, symbol={}, entryInterval={}, trendInterval={}",
                    symbol,
                    entryInterval,
                    trendInterval);
            return;
        }

        List<BinanceKlineDTO> closedEntryKlines = Delphi2Support.closedKlines(entryKlines);
        List<BinanceKlineDTO> closedTrendKlines = Delphi2Support.closedKlines(trendKlines);
        if (CollectionUtils.isEmpty(closedEntryKlines) || CollectionUtils.isEmpty(closedTrendKlines)) {
            return;
        }

        Delphi2RuntimePosition activePosition = activePositions.get(symbol);
        if (activePosition != null) {
            Optional<AlertSignal> stopExit = buildStopExitSignal(entryKlines, activePosition);
            if (stopExit.isPresent()) {
                activePositions.remove(symbol);
                notificationService.send(stopExit.get());
                return;
            }
            if (signalEvaluator.hasTrendReversed(closedTrendKlines, activePosition.direction())) {
                AlertSignal trendExit = signalEvaluator.buildTrendExitSignal(
                        Delphi2Support.last(closedEntryKlines),
                        activePosition.direction(),
                        activePosition.entryPrice(),
                        activePosition.initialStopPrice()
                );
                activePositions.remove(symbol);
                notificationService.send(trendExit);
                return;
            }
            BigDecimal currentAtr = signalEvaluator.currentAtr(closedEntryKlines);
            activePosition.updateAfterClosedBar(
                    Delphi2Support.last(closedEntryKlines),
                    currentAtr,
                    signalEvaluator.trailingActivationAtrMultiple(),
                    signalEvaluator.trailingDistanceAtrMultiple()
            );
            return;
        }

        signalEvaluator.evaluateEntry(closedEntryKlines, closedTrendKlines).ifPresent(signal -> {
            BigDecimal signalAtr = signalEvaluator.currentAtr(closedEntryKlines);
            if (signalAtr == null || signalAtr.compareTo(Delphi2Support.ZERO) <= 0) {
                return;
            }
            notificationService.send(signal);
            activePositions.put(symbol, new Delphi2RuntimePosition(
                    signal.getType(),
                    signal.getDirection(),
                    signal.getKline().getEndTime(),
                    signal.getTriggerPrice(),
                    signalAtr,
                    signal.getInvalidationPrice()
            ));
        });
    }

    private boolean shouldSkip(String symbol) {
        return !StringUtils.hasText(symbol) || !targetSymbol.equalsIgnoreCase(symbol);
    }

    private List<BinanceKlineDTO> loadRecentKlines(String symbol, String interval, int limit) {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(symbol);
        request.setInterval(interval);
        request.setLimit(limit);
        request.setTimeZone("8");
        return binanceApi.listKline(request);
    }

    private Optional<AlertSignal> buildStopExitSignal(List<BinanceKlineDTO> rawEntryKlines,
                                                      Delphi2RuntimePosition activePosition) {
        if (CollectionUtils.isEmpty(rawEntryKlines) || activePosition == null) {
            return Optional.empty();
        }
        BinanceKlineDTO latestBar = rawEntryKlines.get(rawEntryKlines.size() - 1);
        BigDecimal open = Delphi2Support.valueOf(latestBar.getOpen());
        BigDecimal low = Delphi2Support.valueOf(latestBar.getLow());
        BigDecimal high = Delphi2Support.valueOf(latestBar.getHigh());
        BigDecimal stop = activePosition.stopPrice();

        BigDecimal exitPrice = null;
        String reason = null;
        if (activePosition.direction() == TradeDirection.LONG) {
            if (open.compareTo(stop) <= 0) {
                exitPrice = open;
                reason = "GAP_STOP";
            } else if (low.compareTo(stop) <= 0) {
                exitPrice = stop;
                reason = activePosition.trailingActive() ? "TRAILING_STOP" : "STOP_LOSS";
            }
        } else {
            if (open.compareTo(stop) >= 0) {
                exitPrice = open;
                reason = "GAP_STOP";
            } else if (high.compareTo(stop) >= 0) {
                exitPrice = stop;
                reason = activePosition.trailingActive() ? "TRAILING_STOP" : "STOP_LOSS";
            }
        }
        if (exitPrice == null) {
            return Optional.empty();
        }

        String typePrefix = activePosition.trailingActive() ? "EXIT_DELPHI2_TRAILING_STOP_" : "EXIT_DELPHI2_STOP_";
        String summary = activePosition.trailingActive()
                ? "浮盈达到阈值后，ATR 移动止损已经被触发，当前应按系统规则结束仓位。"
                : "价格已经回到初始 ATR 止损位，当前应按系统规则结束仓位。";
        return Optional.of(new AlertSignal(
                activePosition.direction(),
                activePosition.direction() == TradeDirection.LONG ? "Delphi II 止损离场" : "Delphi II 止损回补",
                latestBar,
                typePrefix + activePosition.direction().name(),
                summary,
                Delphi2Support.scaleOrNull(exitPrice),
                Delphi2Support.scaleOrNull(activePosition.stopPrice()),
                null,
                null,
                null,
                "entryType=" + activePosition.signalType() + " | reason=" + reason,
                Delphi2Support.scaleOrNull(activePosition.entryPrice()),
                Delphi2Support.scaleOrNull(activePosition.initialStopPrice())
        ));
    }
}
