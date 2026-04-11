package com.mobai.alert.feature.extractor;

import com.mobai.alert.access.capitalflow.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.feature.model.DerivativeFeatures;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 衍生品特征提取器。
 * 把原始衍生品快照转换为更适合策略使用的标准化指标。
 */
@Component
public class DerivativeFeatureExtractor {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    /**
     * 从原始衍生品数据中提取标准化特征。
     */
    public DerivativeFeatures extract(BinanceDerivativeFeaturesDTO rawFeatures) {
        DerivativeFeatures features = new DerivativeFeatures();
        if (rawFeatures == null) {
            return features;
        }

        features.setAsOfTime(rawFeatures.getAsOfTime());
        features.setOiDelta5m(rawFeatures.getOiDelta5m());
        features.setFundingZscore(rawFeatures.getFundingZscore());
        features.setTakerBuySellImbalance(rawFeatures.getTakerBuySellImbalance());
        features.setTopTraderAccountRatioChange(rawFeatures.getTopTraderAccountRatioChange());
        features.setTopTraderPositionRatioChange(rawFeatures.getTopTraderPositionRatioChange());
        features.setLiquidationClusterIntensity(rawFeatures.getLiquidationClusterIntensity());
        features.setTakerFlowScore(clampSigned(rawFeatures.getTakerBuySellImbalance()));
        features.setLongShortCrowdingScore(average(
                normalizeSigned(rawFeatures.getFundingZscore(), new BigDecimal("3.0")),
                normalizeSigned(rawFeatures.getTopTraderAccountRatioChange(), new BigDecimal("0.20")),
                normalizeSigned(rawFeatures.getTopTraderPositionRatioChange(), new BigDecimal("0.20"))
        ));
        features.setLiquidationStressScore(normalizePositiveByLog(rawFeatures.getLiquidationClusterIntensity()));
        return features;
    }

    /**
     * 把有方向的原始值按尺度压缩到 -1 到 1。
     */
    private BigDecimal normalizeSigned(BigDecimal value, BigDecimal scale) {
        if (value == null || scale == null || scale.compareTo(ZERO) == 0) {
            return null;
        }
        return clampSigned(value.divide(scale, 8, RoundingMode.HALF_UP));
    }

    /**
     * 对只增不减的压力指标做对数压缩，避免极值主导结果。
     */
    private BigDecimal normalizePositiveByLog(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        double normalized = Math.log10(value.doubleValue() + 1.0d) / 6.0d;
        return BigDecimal.valueOf(Math.min(1.0d, Math.max(0.0d, normalized)));
    }

    /**
     * 对一组可空分数求平均。
     */
    private BigDecimal average(BigDecimal... values) {
        List<BigDecimal> usable = new ArrayList<>();
        for (BigDecimal value : values) {
            if (value != null) {
                usable.add(value);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }

        BigDecimal total = ZERO;
        for (BigDecimal value : usable) {
            total = total.add(value);
        }
        return total.divide(BigDecimal.valueOf(usable.size()), 8, RoundingMode.HALF_UP);
    }

    /**
     * 把数值限制在 -1 到 1。
     */
    private BigDecimal clampSigned(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(ONE.negate()) < 0) {
            return ONE.negate();
        }
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        return value;
    }
}
