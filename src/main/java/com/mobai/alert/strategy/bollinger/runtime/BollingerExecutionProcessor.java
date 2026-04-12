package com.mobai.alert.strategy.bollinger.runtime;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.strategy.bollinger.BollingerSignalEvaluator;
import com.mobai.alert.strategy.bollinger.shared.BollingerSupport;
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
@ConditionalOnProperty(value = "monitoring.strategy.type", havingValue = "bollinger")
public class BollingerExecutionProcessor implements StrategyExecutionProcessor {

    private static final Logger log = LoggerFactory.getLogger(BollingerExecutionProcessor.class);

    @Value("${monitoring.target-symbol:BTCUSDT}")
    private String targetSymbol;

    @Value("${monitoring.strategy.boll.entry-interval:1m}")
    private String entryInterval;

    @Value("${monitoring.strategy.boll.entry-kline-limit:240}")
    private int entryKlineLimit;

    @Value("${monitoring.strategy.boll.context-interval:4h}")
    private String contextInterval;

    @Value("${monitoring.strategy.boll.context-kline-limit:80}")
    private int contextKlineLimit;

    private final BinanceApi binanceApi;
    private final BollingerSignalEvaluator signalEvaluator;
    private final AlertNotificationService notificationService;
    private final Map<String, BollingerRuntimePosition> activePositions = new ConcurrentHashMap<>();

    public BollingerExecutionProcessor(BinanceApi binanceApi,
                                       BollingerSignalEvaluator signalEvaluator,
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
        List<BinanceKlineDTO> contextKlines = loadRecentKlines(symbol, contextInterval, contextKlineLimit);
        if (CollectionUtils.isEmpty(entryKlines) || CollectionUtils.isEmpty(contextKlines)) {
            log.warn("Skip bollinger processing because klines are empty, symbol={}, entryInterval={}, contextInterval={}",
                    symbol,
                    entryInterval,
                    contextInterval);
            return;
        }

        List<BinanceKlineDTO> closedEntryKlines = BollingerSupport.closedKlines(entryKlines);
        List<BinanceKlineDTO> closedContextKlines = BollingerSupport.closedKlines(contextKlines);
        BollingerRuntimePosition activePosition = activePositions.get(symbol);
        if (activePosition != null) {
            Optional<AlertSignal> stopExit = buildStopExitSignal(entryKlines, closedEntryKlines, activePosition);
            if (stopExit.isPresent()) {
                activePositions.remove(symbol);
                notificationService.send(stopExit.get());
                return;
            }
            Optional<AlertSignal> reversalExit = signalEvaluator.evaluateLongExit(
                    closedEntryKlines,
                    closedContextKlines,
                    activePosition.entryPrice(),
                    activePosition.stopPrice()
            );
            if (reversalExit.isPresent()) {
                activePositions.remove(symbol);
                notificationService.send(reversalExit.get());
            }
            return;
        }

        signalEvaluator.evaluateLongEntry(closedEntryKlines, closedContextKlines).ifPresent(signal -> {
            notificationService.send(signal);
            activePositions.put(symbol, new BollingerRuntimePosition(
                    signal.getType(),
                    signal.getKline().getEndTime(),
                    signal.getTriggerPrice(),
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
                                                      List<BinanceKlineDTO> closedEntryKlines,
                                                      BollingerRuntimePosition activePosition) {
        if (CollectionUtils.isEmpty(rawEntryKlines) || activePosition == null || activePosition.stopPrice() == null) {
            return Optional.empty();
        }
        BinanceKlineDTO latestBar = rawEntryKlines.get(rawEntryKlines.size() - 1);
        BigDecimal open = BollingerSupport.valueOf(latestBar.getOpen());
        BigDecimal low = BollingerSupport.valueOf(latestBar.getLow());
        BigDecimal exitPrice = null;
        String reason = null;
        if (open.compareTo(activePosition.stopPrice()) <= 0) {
            exitPrice = open;
            reason = "GAP_STOP";
        } else if (low.compareTo(activePosition.stopPrice()) <= 0) {
            exitPrice = activePosition.stopPrice();
            reason = "STOP_LOSS";
        }
        if (exitPrice == null) {
            return Optional.empty();
        }

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "Bollinger Stop Exit",
                latestBar,
                "EXIT_BOLLINGER_STOP_LONG",
                "Price reached the fixed 10% protective stop after a Bollinger long entry.",
                BollingerSupport.scaleOrNull(exitPrice),
                BollingerSupport.scaleOrNull(activePosition.stopPrice()),
                null,
                BollingerSupport.scaleOrNull(BollingerSupport.volumeRatio(closedEntryKlines, 20)),
                null,
                "entryPrice=" + BollingerSupport.scaleOrNull(activePosition.entryPrice())
                        + " | stop=" + BollingerSupport.scaleOrNull(activePosition.stopPrice())
                        + " | reason=" + reason,
                BollingerSupport.scaleOrNull(activePosition.entryPrice()),
                BollingerSupport.scaleOrNull(activePosition.stopPrice())
        );
        return Optional.of(signal);
    }
}
