package com.mobai.alert.strategy.bollinger;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.bollinger.shared.BollingerSupport;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class BollingerSignalEvaluator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Value("${monitoring.strategy.boll.period:20}")
    private int bollPeriod;

    @Value("${monitoring.strategy.boll.stddev-multiplier:2.0}")
    private BigDecimal stddevMultiplier;

    @Value("${monitoring.strategy.boll.stop-loss-pct:0.10}")
    private BigDecimal stopLossPct;

    @Value("${monitoring.strategy.boll.entry-volume-lookback:20}")
    private int entryVolumeLookback;

    public Optional<AlertSignal> evaluateLongEntry(List<BinanceKlineDTO> closedEntryKlines,
                                                   List<BinanceKlineDTO> closedContextKlines) {
        if (!hasEnoughBars(closedEntryKlines) || !hasEnoughBars(closedContextKlines)) {
            return Optional.empty();
        }

        BollingerBandLevels entryBands = BollingerSupport.calculateBands(closedEntryKlines, bollPeriod, stddevMultiplier);
        BollingerBandLevels contextBands = BollingerSupport.calculateBands(closedContextKlines, bollPeriod, stddevMultiplier);
        if (entryBands == null || contextBands == null) {
            return Optional.empty();
        }

        BinanceKlineDTO entryBar = BollingerSupport.last(closedEntryKlines);
        BinanceKlineDTO contextBar = BollingerSupport.last(closedContextKlines);
        BigDecimal entryClose = BollingerSupport.valueOf(entryBar.getClose());
        BigDecimal contextClose = BollingerSupport.valueOf(contextBar.getClose());
        if (contextClose.compareTo(contextBands.middle()) <= 0) {
            return Optional.empty();
        }
        if (entryClose.compareTo(entryBands.middle()) < 0 || entryClose.compareTo(entryBands.upper()) > 0) {
            return Optional.empty();
        }

        BigDecimal stopPrice = entryClose.multiply(BigDecimal.ONE.subtract(stopLossPct)).setScale(8, RoundingMode.HALF_UP);
        String summary = "4h close is above the Bollinger middle band, while 1m close stays between the middle and upper bands. "
                + "This keeps the setup aligned with higher-timeframe strength without chasing a 1m close beyond the upper band.";
        return Optional.of(new AlertSignal(
                TradeDirection.LONG,
                "4h/1m Bollinger Long",
                entryBar,
                "BOLLINGER_LONG_ENTRY",
                summary,
                BollingerSupport.scaleOrNull(entryClose),
                BollingerSupport.scaleOrNull(stopPrice),
                null,
                BollingerSupport.scaleOrNull(BollingerSupport.volumeRatio(closedEntryKlines, entryVolumeLookback)),
                null,
                buildEntryContext(entryClose, contextClose, entryBands, contextBands),
                BollingerSupport.scaleOrNull(entryClose),
                BollingerSupport.scaleOrNull(stopPrice)
        ));
    }

    public Optional<AlertSignal> evaluateLongExit(List<BinanceKlineDTO> closedEntryKlines,
                                                  List<BinanceKlineDTO> closedContextKlines,
                                                  BigDecimal entryPrice,
                                                  BigDecimal stopPrice) {
        if (!hasEnoughBars(closedEntryKlines) || !hasEnoughBars(closedContextKlines)) {
            return Optional.empty();
        }

        BollingerBandLevels entryBands = BollingerSupport.calculateBands(closedEntryKlines, bollPeriod, stddevMultiplier);
        BollingerBandLevels contextBands = BollingerSupport.calculateBands(closedContextKlines, bollPeriod, stddevMultiplier);
        if (entryBands == null || contextBands == null) {
            return Optional.empty();
        }

        BinanceKlineDTO entryBar = BollingerSupport.last(closedEntryKlines);
        BinanceKlineDTO contextBar = BollingerSupport.last(closedContextKlines);
        BigDecimal entryClose = BollingerSupport.valueOf(entryBar.getClose());
        BigDecimal contextClose = BollingerSupport.valueOf(contextBar.getClose());
        if (contextClose.compareTo(contextBands.middle()) >= 0) {
            return Optional.empty();
        }
        if (entryClose.compareTo(entryBands.lower()) > 0) {
            return Optional.empty();
        }

        String summary = "4h close drops back below the Bollinger middle band, and 1m close is already pressing the lower band. "
                + "That combination marks a clean loss of the long-side multi-timeframe structure.";
        return Optional.of(new AlertSignal(
                TradeDirection.LONG,
                "4h/1m Bollinger Exit",
                entryBar,
                "EXIT_BOLLINGER_REVERSAL_LONG",
                summary,
                BollingerSupport.scaleOrNull(entryClose),
                BollingerSupport.scaleOrNull(stopPrice),
                null,
                BollingerSupport.scaleOrNull(BollingerSupport.volumeRatio(closedEntryKlines, entryVolumeLookback)),
                null,
                buildExitContext(entryClose, contextClose, entryBands, contextBands),
                BollingerSupport.scaleOrNull(entryPrice),
                BollingerSupport.scaleOrNull(stopPrice)
        ));
    }

    public BigDecimal stopLossPct() {
        return stopLossPct;
    }

    private boolean hasEnoughBars(List<BinanceKlineDTO> closedKlines) {
        return !CollectionUtils.isEmpty(closedKlines) && closedKlines.size() >= bollPeriod;
    }

    private String buildEntryContext(BigDecimal entryClose,
                                     BigDecimal contextClose,
                                     BollingerBandLevels entryBands,
                                     BollingerBandLevels contextBands) {
        return "4h close=" + scaled(contextClose)
                + " > mid=" + scaled(contextBands.middle())
                + " | 1m close=" + scaled(entryClose)
                + " within [" + scaled(entryBands.middle()) + ", " + scaled(entryBands.upper()) + "]"
                + " | stop=" + percent(stopLossPct.multiply(ONE_HUNDRED));
    }

    private String buildExitContext(BigDecimal entryClose,
                                    BigDecimal contextClose,
                                    BollingerBandLevels entryBands,
                                    BollingerBandLevels contextBands) {
        return "4h close=" + scaled(contextClose)
                + " < mid=" + scaled(contextBands.middle())
                + " | 1m close=" + scaled(entryClose)
                + " <= lower=" + scaled(entryBands.lower());
    }

    private String scaled(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percent(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
