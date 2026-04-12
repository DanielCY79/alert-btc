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

    @Value("${monitoring.strategy.boll.context-trend-lookback-bars:3}")
    private int contextTrendLookbackBars;

    @Value("${monitoring.strategy.boll.context-min-middle-rise-pct:0.0020}")
    private BigDecimal contextMinMiddleRisePct;

    @Value("${monitoring.strategy.boll.context-min-close-buffer-pct:0.0015}")
    private BigDecimal contextMinCloseBufferPct;

    @Value("${monitoring.strategy.boll.context-bandwidth-lookback-bars:3}")
    private int contextBandwidthLookbackBars;

    @Value("${monitoring.strategy.boll.context-min-bandwidth-expansion-pct:0.0150}")
    private BigDecimal contextMinBandwidthExpansionPct;

    @Value("${monitoring.strategy.boll.fast-failure-max-bars:360}")
    private int fastFailureMaxBars;

    @Value("${monitoring.strategy.boll.fast-failure-loss-pct:0.0035}")
    private BigDecimal fastFailureLossPct;

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
        if (!passesContextTrendFilter(closedContextKlines, contextBands, contextClose)) {
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
                buildEntryContext(entryClose, contextClose, entryBands, contextBands, closedContextKlines),
                BollingerSupport.scaleOrNull(entryClose),
                BollingerSupport.scaleOrNull(stopPrice)
        ));
    }

    public Optional<AlertSignal> evaluateLongExit(List<BinanceKlineDTO> closedEntryKlines,
                                                  List<BinanceKlineDTO> closedContextKlines,
                                                  BigDecimal entryPrice,
                                                  BigDecimal stopPrice,
                                                  int heldBars) {
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
        if (shouldFastFail(entryBands, entryClose, entryPrice, heldBars)) {
            return Optional.of(buildFastFailureExitSignal(entryBar, closedEntryKlines, entryClose, entryPrice, stopPrice, entryBands));
        }
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

    private boolean passesContextTrendFilter(List<BinanceKlineDTO> closedContextKlines,
                                             BollingerBandLevels currentBands,
                                             BigDecimal contextClose) {
        BigDecimal closeBufferPct = BollingerSupport.ratio(
                contextClose.subtract(currentBands.middle()),
                currentBands.middle()
        );
        if (closeBufferPct.compareTo(nonNegative(contextMinCloseBufferPct)) < 0) {
            return false;
        }
        if (contextTrendLookbackBars <= 0) {
            return true;
        }

        BollingerBandLevels previousBands = BollingerSupport.calculateBandsAtOffset(
                closedContextKlines,
                bollPeriod,
                stddevMultiplier,
                contextTrendLookbackBars
        );
        if (previousBands == null) {
            return false;
        }
        BigDecimal middleRisePct = BollingerSupport.ratio(
                currentBands.middle().subtract(previousBands.middle()),
                previousBands.middle()
        );
        if (middleRisePct.compareTo(nonNegative(contextMinMiddleRisePct)) < 0) {
            return false;
        }
        return passesBandwidthExpansionFilter(closedContextKlines, currentBands);
    }

    private boolean passesBandwidthExpansionFilter(List<BinanceKlineDTO> closedContextKlines,
                                                   BollingerBandLevels currentBands) {
        if (contextBandwidthLookbackBars <= 0) {
            return true;
        }
        BollingerBandLevels previousBands = BollingerSupport.calculateBandsAtOffset(
                closedContextKlines,
                bollPeriod,
                stddevMultiplier,
                contextBandwidthLookbackBars
        );
        if (previousBands == null) {
            return false;
        }
        BigDecimal currentBandwidthPct = bandWidthPct(currentBands);
        BigDecimal previousBandwidthPct = bandWidthPct(previousBands);
        if (previousBandwidthPct.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal expansionPct = BollingerSupport.ratio(
                currentBandwidthPct.subtract(previousBandwidthPct),
                previousBandwidthPct
        );
        return expansionPct.compareTo(nonNegative(contextMinBandwidthExpansionPct)) >= 0;
    }

    private boolean shouldFastFail(BollingerBandLevels entryBands,
                                   BigDecimal entryClose,
                                   BigDecimal entryPrice,
                                   int heldBars) {
        if (fastFailureMaxBars <= 0 || heldBars <= 0 || heldBars > fastFailureMaxBars) {
            return false;
        }
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (entryClose.compareTo(entryBands.middle()) >= 0) {
            return false;
        }
        BigDecimal lossPct = BollingerSupport.ratio(entryPrice.subtract(entryClose), entryPrice);
        return lossPct.compareTo(nonNegative(fastFailureLossPct)) >= 0;
    }

    private AlertSignal buildFastFailureExitSignal(BinanceKlineDTO entryBar,
                                                   List<BinanceKlineDTO> closedEntryKlines,
                                                   BigDecimal entryClose,
                                                   BigDecimal entryPrice,
                                                   BigDecimal stopPrice,
                                                   BollingerBandLevels entryBands) {
        String summary = "The 1m follow-through failed quickly after entry and price slipped back under the Bollinger middle band. "
                + "That usually marks a weak breakout rather than a trend worth holding.";
        return new AlertSignal(
                TradeDirection.LONG,
                "4h/1m Bollinger Fast Failure Exit",
                entryBar,
                "EXIT_BOLLINGER_FAST_FAILURE_LONG",
                summary,
                BollingerSupport.scaleOrNull(entryClose),
                BollingerSupport.scaleOrNull(stopPrice),
                null,
                BollingerSupport.scaleOrNull(BollingerSupport.volumeRatio(closedEntryKlines, entryVolumeLookback)),
                null,
                "entryPrice=" + scaled(entryPrice)
                        + " | 1m close=" + scaled(entryClose)
                        + " < mid=" + scaled(entryBands.middle())
                        + " | quickLoss=" + percent(BollingerSupport.ratio(entryPrice.subtract(entryClose), entryPrice).multiply(ONE_HUNDRED)),
                BollingerSupport.scaleOrNull(entryPrice),
                BollingerSupport.scaleOrNull(stopPrice)
        );
    }

    private boolean hasEnoughBars(List<BinanceKlineDTO> closedKlines) {
        return !CollectionUtils.isEmpty(closedKlines) && closedKlines.size() >= bollPeriod;
    }

    private String buildEntryContext(BigDecimal entryClose,
                                     BigDecimal contextClose,
                                     BollingerBandLevels entryBands,
                                     BollingerBandLevels contextBands,
                                     List<BinanceKlineDTO> closedContextKlines) {
        BigDecimal closeBufferPct = BollingerSupport.ratio(
                contextClose.subtract(contextBands.middle()),
                contextBands.middle()
        ).multiply(ONE_HUNDRED);
        BigDecimal middleRisePct = null;
        BigDecimal bandwidthExpansionPct = null;
        if (contextTrendLookbackBars > 0) {
            BollingerBandLevels previousBands = BollingerSupport.calculateBandsAtOffset(
                    closedContextKlines,
                    bollPeriod,
                    stddevMultiplier,
                    contextTrendLookbackBars
            );
            if (previousBands != null) {
                middleRisePct = BollingerSupport.ratio(
                        contextBands.middle().subtract(previousBands.middle()),
                        previousBands.middle()
                ).multiply(ONE_HUNDRED);
            }
        }
        if (contextBandwidthLookbackBars > 0) {
            BollingerBandLevels previousBandwidthBands = BollingerSupport.calculateBandsAtOffset(
                    closedContextKlines,
                    bollPeriod,
                    stddevMultiplier,
                    contextBandwidthLookbackBars
            );
            if (previousBandwidthBands != null) {
                BigDecimal currentBandwidthPct = bandWidthPct(contextBands);
                BigDecimal previousBandwidthPct = bandWidthPct(previousBandwidthBands);
                if (previousBandwidthPct.compareTo(BigDecimal.ZERO) > 0) {
                    bandwidthExpansionPct = BollingerSupport.ratio(
                            currentBandwidthPct.subtract(previousBandwidthPct),
                            previousBandwidthPct
                    ).multiply(ONE_HUNDRED);
                }
            }
        }
        return "4h close=" + scaled(contextClose)
                + " > mid=" + scaled(contextBands.middle())
                + " | 1m close=" + scaled(entryClose)
                + " within [" + scaled(entryBands.middle()) + ", " + scaled(entryBands.upper()) + "]"
                + " | 4h closeBuffer=" + percent(closeBufferPct)
                + " | 4h middleRise=" + percent(middleRisePct)
                + " | 4h bandwidthExpansion=" + percent(bandwidthExpansionPct)
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

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    private BigDecimal bandWidthPct(BollingerBandLevels bands) {
        if (bands == null) {
            return BigDecimal.ZERO;
        }
        return BollingerSupport.ratio(
                bands.upper().subtract(bands.lower()),
                bands.middle()
        );
    }
}
