package com.mobai.alert.strategy.backtest;

import com.mobai.alert.state.backtest.BacktestConfig;
import com.mobai.alert.state.backtest.BacktestReport;
import com.mobai.alert.state.backtest.BatchBacktestResult;
import com.mobai.alert.state.backtest.SensitivityResult;
import com.mobai.alert.strategy.policy.CompositeFactorPolicyProfile;
import com.mobai.alert.strategy.policy.MarketStateMachine;
import com.mobai.alert.strategy.policy.PolicyWeights;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回测结果格式化测试，确保原始基线、策略基线与敏感性分析都能正确输出。
 */
class StrategyBacktestServiceFormattingTests {

    /**
     * 批量回测文本应同时包含原始结果、策略结果及对比信息。
     */
    @Test
    void shouldIncludeRawVsPolicyComparisonInBatchResult() {
        StrategyBacktestService service = new StrategyBacktestService(null, null, null, new MarketStateMachine());
        BacktestConfig config = config();

        BacktestReport rawReport = new BacktestReport(
                100,
                List.of(),
                Map.of("CONFIRMED_BREAKOUT_LONG", 5),
                Map.of(),
                5,
                0,
                false,
                4,
                new BigDecimal("0.50"),
                new BigDecimal("0.20"),
                new BigDecimal("0.80"),
                new BigDecimal("1.40"),
                new BigDecimal("0.60"),
                config
        );
        BacktestReport policyReport = new BacktestReport(
                100,
                List.of(),
                Map.of("CONFIRMED_BREAKOUT_LONG", 3),
                Map.of("CONFIRMED_BREAKOUT_LONG", 2),
                5,
                2,
                true,
                3,
                new BigDecimal("0.66666667"),
                new BigDecimal("0.30"),
                new BigDecimal("0.90"),
                new BigDecimal("1.80"),
                new BigDecimal("0.40"),
                config
        );
        BatchBacktestResult result = new BatchBacktestResult(
                100,
                rawReport,
                policyReport,
                List.of(new SensitivityResult("policy.breakoutMinScore=0.60", policyReport, policyReport.totalR()))
        );

        String formatted = service.formatBatchResult(result);

        assertThat(formatted).contains("Raw Baseline");
        assertThat(formatted).contains("Policy Baseline");
        assertThat(formatted).contains("Comparison |");
        assertThat(formatted).contains("blockedSignals=2");
        assertThat(formatted).contains("blockedMix={CONFIRMED_BREAKOUT_LONG=2}");
        assertThat(formatted).contains("Policy Sensitivity");
    }

    /**
     * 构造统一的回测配置样本。
     */
    private BacktestConfig config() {
        return new BacktestConfig(
                "BTCUSDT",
                "4h",
                0L,
                1L,
                20,
                60,
                36,
                new BigDecimal("0.03"),
                new BigDecimal("0.18"),
                new BigDecimal("0.015"),
                2,
                new BigDecimal("0.45"),
                12,
                new BigDecimal("0.012"),
                new BigDecimal("0.003"),
                new BigDecimal("1.50"),
                new BigDecimal("0.45"),
                new BigDecimal("0.05"),
                new BigDecimal("0.008"),
                new BigDecimal("0.003"),
                new BigDecimal("0.001"),
                new BigDecimal("1.20"),
                new BigDecimal("0.008"),
                new BigDecimal("0.006"),
                new BigDecimal("1.10"),
                new BigDecimal("0.001"),
                new BigDecimal("0.25"),
                new BigDecimal("0.55"),
                new BigDecimal("0.80"),
                43200000L,
                12,
                18,
                18,
                new BigDecimal("1.50"),
                new BigDecimal("1.00"),
                new BigDecimal("0.50"),
                new BigDecimal("1.20"),
                new BigDecimal("1.00"),
                1,
                new BigDecimal("1.60"),
                new BigDecimal("0.35"),
                profile()
        );
    }

    /**
     * 构造策略权重档位，用于格式化测试。
     */
    private CompositeFactorPolicyProfile profile() {
        PolicyWeights rangeWeights = new PolicyWeights(
                new BigDecimal("0.18"),
                new BigDecimal("0.10"),
                new BigDecimal("0.12"),
                new BigDecimal("0.12"),
                new BigDecimal("0.18")
        );
        PolicyWeights breakoutWeights = new PolicyWeights(
                new BigDecimal("0.18"),
                new BigDecimal("0.26"),
                new BigDecimal("0.10"),
                new BigDecimal("0.08"),
                new BigDecimal("0.22")
        );
        PolicyWeights pullbackWeights = new PolicyWeights(
                new BigDecimal("0.24"),
                new BigDecimal("0.18"),
                new BigDecimal("0.10"),
                new BigDecimal("0.06"),
                new BigDecimal("0.18")
        );
        return new CompositeFactorPolicyProfile(
                true,
                new BigDecimal("0.50"),
                new BigDecimal("0.50"),
                new BigDecimal("0.55"),
                new BigDecimal("0.53"),
                new BigDecimal("0.88"),
                BigDecimal.ZERO,
                new BigDecimal("0.80"),
                new BigDecimal("-0.20"),
                new BigDecimal("-0.35"),
                new BigDecimal("-0.60"),
                new BigDecimal("0.45"),
                new BigDecimal("0.70"),
                rangeWeights,
                breakoutWeights,
                pullbackWeights
        );
    }
}
