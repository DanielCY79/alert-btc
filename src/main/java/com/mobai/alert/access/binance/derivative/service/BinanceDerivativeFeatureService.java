package com.mobai.alert.access.binance.derivative.service;

import com.mobai.alert.access.binance.derivative.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceFundingRateDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceOpenInterestDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTakerBuySellVolumeDTO;
import com.mobai.alert.access.binance.derivative.dto.BinanceTopTraderRatioDTO;
import com.mobai.alert.access.binance.derivative.rest.BinanceDerivativeRestClient;
import com.mobai.alert.access.binance.derivative.stream.BinanceForceOrderWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binance 衍生品特征聚合服务。
 * 从持仓量、资金费率、主动买卖量、多空比、强平流中构造策略特征。
 */
@Service
public class BinanceDerivativeFeatureService {

    private static final Logger log = LoggerFactory.getLogger(BinanceDerivativeFeatureService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    private final BinanceDerivativeRestClient derivativeRestClient;
    private final BinanceForceOrderWebSocketService forceOrderWebSocketService;
    private final Map<String, Deque<OpenInterestSnapshot>> openInterestSnapshots = new ConcurrentHashMap<>();

    @Value("${backtest.enabled:false}")
    private boolean backtestEnabled;

    @Value("${monitoring.market-data.derivatives.enabled:true}")
    private boolean derivativesEnabled;

    @Value("${monitoring.target-symbol:BTCUSDT}")
    private String targetSymbol;

    @Value("${monitoring.market-data.derivatives.oi-window-ms:300000}")
    private long oiWindowMs;

    @Value("${monitoring.market-data.derivatives.funding-lookback:30}")
    private int fundingLookback;

    @Value("${monitoring.market-data.derivatives.taker-period:5m}")
    private String takerPeriod;

    @Value("${monitoring.market-data.derivatives.taker-limit:1}")
    private int takerLimit;

    @Value("${monitoring.market-data.derivatives.ratio-period:5m}")
    private String ratioPeriod;

    @Value("${monitoring.market-data.derivatives.ratio-limit:2}")
    private int ratioLimit;

    @Value("${monitoring.market-data.derivatives.liquidation-window-ms:300000}")
    private long liquidationWindowMs;

    public BinanceDerivativeFeatureService(BinanceDerivativeRestClient derivativeRestClient,
                                           BinanceForceOrderWebSocketService forceOrderWebSocketService) {
        this.derivativeRestClient = derivativeRestClient;
        this.forceOrderWebSocketService = forceOrderWebSocketService;
    }

    @Scheduled(
            fixedDelayString = "${monitoring.market-data.derivatives.snapshot-interval-ms:60000}",
            initialDelayString = "${monitoring.market-data.derivatives.snapshot-initial-delay-ms:10000}"
    )
    public void refreshTargetSymbolOpenInterest() {
        if (!isFeatureActive(targetSymbol)) {
            return;
        }
        cacheOpenInterest(targetSymbol, derivativeRestClient.getOpenInterest(targetSymbol));
        forceOrderWebSocketService.ensureConnected("feature_snapshot");
    }

    public BinanceDerivativeFeaturesDTO buildFeatures(String symbol) {
        if (!isFeatureActive(symbol)) {
            return emptyFeatures(symbol);
        }

        cacheOpenInterest(symbol, derivativeRestClient.getOpenInterest(symbol));
        forceOrderWebSocketService.ensureConnected("feature_request");

        BinanceDerivativeFeaturesDTO dto = new BinanceDerivativeFeaturesDTO();
        dto.setSymbol(symbol);
        dto.setAsOfTime(System.currentTimeMillis());
        dto.setOiDelta5m(calculateOpenInterestDelta(symbol));
        dto.setFundingZscore(calculateFundingZscore(symbol));
        dto.setTakerBuySellImbalance(calculateTakerBuySellImbalance(symbol));
        dto.setTopTraderAccountRatioChange(calculateRatioChange(
                derivativeRestClient.listTopTraderAccountRatios(symbol, ratioPeriod, ratioLimit, null, null)));
        dto.setTopTraderPositionRatioChange(calculateRatioChange(
                derivativeRestClient.listTopTraderPositionRatios(symbol, ratioPeriod, ratioLimit, null, null)));
        dto.setLiquidationClusterIntensity(forceOrderWebSocketService.calculateClusterIntensity(symbol, liquidationWindowMs));
        return dto;
    }

    private BinanceDerivativeFeaturesDTO emptyFeatures(String symbol) {
        BinanceDerivativeFeaturesDTO dto = new BinanceDerivativeFeaturesDTO();
        dto.setSymbol(symbol);
        dto.setAsOfTime(System.currentTimeMillis());
        return dto;
    }

    private boolean isFeatureActive(String symbol) {
        return derivativesEnabled && !backtestEnabled && StringUtils.hasText(symbol);
    }

    private void cacheOpenInterest(String symbol, BinanceOpenInterestDTO openInterest) {
        if (!StringUtils.hasText(symbol) || openInterest == null || !StringUtils.hasText(openInterest.getOpenInterest())) {
            return;
        }

        long now = System.currentTimeMillis();
        Deque<OpenInterestSnapshot> snapshots = openInterestSnapshots.computeIfAbsent(symbol, key -> new ArrayDeque<>());
        synchronized (snapshots) {
            snapshots.addLast(new OpenInterestSnapshot(now, decimal(openInterest.getOpenInterest())));
            while (!snapshots.isEmpty() && now - snapshots.peekFirst().timestamp() > oiWindowMs * 2) {
                snapshots.removeFirst();
            }
        }
        log.debug("已缓存 Binance 持仓量快照，symbol={}，openInterest={}", symbol, openInterest.getOpenInterest());
    }

    private BigDecimal calculateOpenInterestDelta(String symbol) {
        Deque<OpenInterestSnapshot> snapshots = openInterestSnapshots.get(symbol);
        if (snapshots == null) {
            return null;
        }

        long baselineThreshold = System.currentTimeMillis() - oiWindowMs;
        synchronized (snapshots) {
            if (snapshots.size() < 2) {
                return null;
            }
            OpenInterestSnapshot latest = snapshots.peekLast();
            OpenInterestSnapshot baseline = null;
            for (OpenInterestSnapshot snapshot : snapshots) {
                if (snapshot.timestamp() <= baselineThreshold) {
                    baseline = snapshot;
                }
            }
            if (latest == null || baseline == null) {
                return null;
            }
            return latest.value().subtract(baseline.value(), MATH_CONTEXT);
        }
    }

    private BigDecimal calculateFundingZscore(String symbol) {
        List<BinanceFundingRateDTO> fundingRates = derivativeRestClient.listFundingRates(symbol, fundingLookback, null, null);
        if (fundingRates.isEmpty()) {
            return null;
        }

        BigDecimal sum = ZERO;
        int count = 0;
        for (BinanceFundingRateDTO fundingRate : fundingRates) {
            if (!StringUtils.hasText(fundingRate.getFundingRate())) {
                continue;
            }
            sum = sum.add(decimal(fundingRate.getFundingRate()), MATH_CONTEXT);
            count++;
        }
        if (count < 2) {
            return null;
        }

        BigDecimal mean = sum.divide(BigDecimal.valueOf(count), MATH_CONTEXT);
        BigDecimal varianceSum = ZERO;
        BigDecimal latest = null;
        for (BinanceFundingRateDTO fundingRate : fundingRates) {
            if (!StringUtils.hasText(fundingRate.getFundingRate())) {
                continue;
            }
            BigDecimal value = decimal(fundingRate.getFundingRate());
            latest = value;
            BigDecimal diff = value.subtract(mean, MATH_CONTEXT);
            varianceSum = varianceSum.add(diff.multiply(diff, MATH_CONTEXT), MATH_CONTEXT);
        }
        if (latest == null) {
            return null;
        }

        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(
                varianceSum.divide(BigDecimal.valueOf(count), MATH_CONTEXT).doubleValue()));
        if (stdDev.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return latest.subtract(mean, MATH_CONTEXT).divide(stdDev, MATH_CONTEXT);
    }

    private BigDecimal calculateTakerBuySellImbalance(String symbol) {
        List<BinanceTakerBuySellVolumeDTO> takerVolumes =
                derivativeRestClient.listTakerBuySellVolumes(symbol, takerPeriod, takerLimit, null, null);
        if (takerVolumes.isEmpty()) {
            return null;
        }

        BinanceTakerBuySellVolumeDTO latest = takerVolumes.get(takerVolumes.size() - 1);
        BigDecimal buyVol = decimal(latest.getBuyVol());
        BigDecimal sellVol = decimal(latest.getSellVol());
        BigDecimal total = buyVol.add(sellVol, MATH_CONTEXT);
        if (total.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return buyVol.subtract(sellVol, MATH_CONTEXT).divide(total, MATH_CONTEXT);
    }

    private BigDecimal calculateRatioChange(List<BinanceTopTraderRatioDTO> ratios) {
        if (ratios.size() < 2) {
            return null;
        }
        BinanceTopTraderRatioDTO previous = ratios.get(ratios.size() - 2);
        BinanceTopTraderRatioDTO latest = ratios.get(ratios.size() - 1);
        return decimal(latest.getLongShortRatio()).subtract(decimal(previous.getLongShortRatio()), MATH_CONTEXT);
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return ZERO;
        }
        try {
            return new BigDecimal(value, MATH_CONTEXT);
        } catch (NumberFormatException e) {
            return ZERO;
        }
    }

    private record OpenInterestSnapshot(long timestamp, BigDecimal value) {
    }
}
