package com.mobai.alert.feature.extractor;

import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.DerivativeFeatures;
import com.mobai.alert.feature.model.EventFeatures;
import com.mobai.alert.feature.model.PriceFeatures;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 复合因子计算器。
 * 把价格、衍生品和事件特征汇总成统一的上下文评分输入。
 */
@Component
public class CompositeFactorCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final BigDecimal TWO = new BigDecimal("2");

    /**
     * 聚合三类特征，生成复合因子结果。
     */
    public CompositeFactors calculate(PriceFeatures priceFeatures,
                                      DerivativeFeatures derivativeFeatures,
                                      EventFeatures eventFeatures) {
        CompositeFactors factors = new CompositeFactors();

        BigDecimal trendBiasScore = averageSigned(
                normalizeSigned(priceFeatures == null ? null : priceFeatures.getReturn3Bar(), new BigDecimal("0.015")),
                normalizeSigned(priceFeatures == null ? null : priceFeatures.getReturn12Bar(), new BigDecimal("0.040")),
                normalizeSigned(priceFeatures == null ? null : priceFeatures.getMaSpreadPct(), new BigDecimal("0.020")),
                priceFeatures == null ? null : rangeBias(priceFeatures.getRangePosition())
        );
        factors.setTrendBiasScore(trendBiasScore);

        BigDecimal directionalCloseScore = priceFeatures == null ? null : rangeBias(priceFeatures.getCloseLocation());
        BigDecimal breakoutDirectionScore = null;
        if (directionalCloseScore != null && priceFeatures != null && priceFeatures.getBreakoutStrengthScore() != null) {
            breakoutDirectionScore = clampSigned(priceFeatures.getBreakoutStrengthScore().multiply(directionalCloseScore));
        }

        factors.setBreakoutConfirmationScore(averageSigned(
                normalizeSigned(priceFeatures == null ? null : priceFeatures.getReturn1Bar(), new BigDecimal("0.008")),
                directionalCloseScore,
                normalizeSigned(expansion(priceFeatures == null ? null : priceFeatures.getVolumeRatio()), new BigDecimal("0.50")),
                breakoutDirectionScore,
                derivativeFeatures == null ? null : derivativeFeatures.getTakerFlowScore(),
                trendBiasScore
        ));

        BigDecimal crowdingScore = averageSigned(
                derivativeFeatures == null ? null : derivativeFeatures.getLongShortCrowdingScore(),
                derivativeFeatures == null ? null : derivativeFeatures.getTakerFlowScore()
        );
        factors.setCrowdingScore(crowdingScore);
        factors.setEventBiasScore(normalizeSigned(eventFeatures == null ? null : eventFeatures.getEventBiasScore(), ONE));
        factors.setRegimeRiskScore(averagePositive(
                absolute(crowdingScore),
                derivativeFeatures == null ? null : derivativeFeatures.getLiquidationStressScore(),
                normalizePositive(eventFeatures == null ? null : eventFeatures.getEventShockScore())
        ));
        return factors;
    }

    /**
     * 把 0 到 1 的区间位置映射成 -1 到 1 的方向偏置。
     */
    private BigDecimal rangeBias(BigDecimal normalizedPosition) {
        if (normalizedPosition == null) {
            return null;
        }
        return clampSigned(normalizedPosition.subtract(HALF).multiply(TWO));
    }

    /**
     * 把量比转换为“相对扩张幅度”。
     */
    private BigDecimal expansion(BigDecimal volumeRatio) {
        if (volumeRatio == null) {
            return null;
        }
        return volumeRatio.subtract(ONE);
    }

    /**
     * 把正负向指标按给定尺度归一化到 -1 到 1。
     */
    private BigDecimal normalizeSigned(BigDecimal value, BigDecimal scale) {
        if (value == null || scale == null || scale.compareTo(ZERO) == 0) {
            return null;
        }
        return clampSigned(value.divide(scale, 8, RoundingMode.HALF_UP));
    }

    /**
     * 把只允许为正的指标压缩到 0 到 1。
     */
    private BigDecimal normalizePositive(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        return value;
    }

    /**
     * 取绝对值，空值保持为空。
     */
    private BigDecimal absolute(BigDecimal value) {
        return value == null ? null : value.abs();
    }

    /**
     * 计算一组有方向分数的平均值。
     */
    private BigDecimal averageSigned(BigDecimal... values) {
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
        return clampSigned(total.divide(BigDecimal.valueOf(usable.size()), 8, RoundingMode.HALF_UP));
    }

    /**
     * 计算一组正向分数的平均值。
     */
    private BigDecimal averagePositive(BigDecimal... values) {
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
        return normalizePositive(total.divide(BigDecimal.valueOf(usable.size()), 8, RoundingMode.HALF_UP));
    }

    /**
     * 把分数截断到 -1 到 1 区间内。
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
