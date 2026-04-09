package com.mobai.alert.strategy.shared;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 策略公共计算工具类。
 *
 * 这个类承载的是“所有策略都会反复用到的基础计算能力”，例如：
 * - 只取已收盘K线
 * - 识别区间是否成立
 * - 计算均线、影线、实体、重叠度、量能均值
 * - 提供支撑/阻力/中轴等基础结构
 *
 * 设计目标是：
 * 让具体策略类只关注“交易逻辑”，
 * 而把通用的数学与结构判断沉到这里统一维护。
 */
public final class StrategySupport {

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal TWO = new BigDecimal("2");

    private StrategySupport() {
    }

    /**
     * 计算当前策略至少需要多少根 K 线样本。
     *
     * 原因很简单：
     * - 区间识别本身需要足够长的回看窗口；
     * - 均线漂移判断也需要足够长的均线周期；
     * - 再额外留一点缓冲，避免边界访问问题。
     */
    public static int minimumBarsRequired(StrategySettings settings) {
        return Math.max(settings.slowPeriod() + 6, settings.rangeLookback() + settings.fastPeriod() + 6);
    }

    /**
     * 判断样本数量是否足够。
     *
     * 这是所有策略在真正开始前的第一层防线，
     * 避免因为样本太短导致任何形态判断失真。
     */
    public static boolean hasEnoughBars(List<BinanceKlineDTO> closedKlines, int minimumSize) {
        return !CollectionUtils.isEmpty(closedKlines) && closedKlines.size() >= minimumSize;
    }

    /**
     * 统一只保留已收盘 K 线。
     *
     * 最后一根未收盘 K 线在盘中会不断变化，
     * 如果直接拿它做策略判断，很容易在回测和实盘之间产生偏差。
     */
    public static List<BinanceKlineDTO> closedKlines(List<BinanceKlineDTO> klines) {
        if (CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return List.of();
        }
        return klines.subList(0, klines.size() - 1);
    }

    /**
     * 构建“区间上下文”。
     *
     * 这是整套策略最核心的公共方法之一。
     * 只有当市场同时满足：
     * - 区间宽度合理
     * - 上下边界都被多次触达
     * - K线之间足够重叠
     * - 均线漂移不大
     *
     * 才会被认定为一个可交易区间。
     * 如果任何一条不满足，就返回 null，表示“当前不把它当区间处理”。
     */
    public static RangeContext buildRangeContext(List<BinanceKlineDTO> closedKlines, StrategySettings settings) {
        if (!hasEnoughBars(closedKlines, minimumBarsRequired(settings))) {
            return null;
        }

        // 定义区间时排除掉最后一根信号K线，避免当前K线既定义区间又触发信号。
        List<BinanceKlineDTO> window = trailingWindow(closedKlines, settings.rangeLookback(), 1);
        if (CollectionUtils.isEmpty(window) || window.size() < settings.rangeLookback()) {
            return null;
        }

        BigDecimal resistance = highestHigh(window);
        BigDecimal support = lowestLow(window);
        BigDecimal width = percentageDistance(resistance, support);
        if (width.compareTo(settings.rangeMinWidth()) < 0 || width.compareTo(settings.rangeMaxWidth()) > 0) {
            return null;
        }

        int topTouches = countTopTouches(window, resistance, settings);
        int bottomTouches = countBottomTouches(window, support, settings);
        if (topTouches < settings.requiredEdgeTouches() || bottomTouches < settings.requiredEdgeTouches()) {
            return null;
        }

        int overlappingBars = countOverlappingBars(window, settings);
        if (overlappingBars < settings.minOverlapBars()) {
            return null;
        }

        BigDecimal fastMa = movingAverage(closedKlines, settings.fastPeriod(), 0);
        BigDecimal laggedFastMa = movingAverage(closedKlines, settings.fastPeriod(), Math.min(5, closedKlines.size() - settings.fastPeriod()));
        BigDecimal maDrift = ratio(fastMa.subtract(laggedFastMa).abs(), laggedFastMa);
        if (maDrift.compareTo(settings.maFlatThreshold()) > 0) {
            return null;
        }

        BigDecimal midpoint = resistance.add(support).divide(TWO, 8, RoundingMode.HALF_UP);
        return new RangeContext(window, support, resistance, midpoint, width);
    }

    /**
     * 从尾部截取一个窗口。
     *
     * 这是策略里最常见的取样方式：
     * 从最近的历史中拿固定数量的K线来做判断，
     * 同时允许排除最后几根，避免把信号K线混进参考窗口。
     */
    public static List<BinanceKlineDTO> trailingWindow(List<BinanceKlineDTO> klines, int size, int excludeLastBars) {
        int endExclusive = klines.size() - excludeLastBars;
        int startInclusive = Math.max(0, endExclusive - size);
        return klines.subList(startInclusive, endExclusive);
    }

    /**
     * 取最后一根已收盘 K 线。
     */
    public static BinanceKlineDTO last(List<BinanceKlineDTO> klines) {
        return klines.get(klines.size() - 1);
    }

    /**
     * 简单移动平均值。
     *
     * 这里主要用于判断“价格重心有没有明显漂移”，
     * 不是用来做传统均线金叉死叉。
     */
    public static BigDecimal movingAverage(List<BinanceKlineDTO> klines, int period, int offset) {
        int safeOffset = Math.max(0, offset);
        int endExclusive = klines.size() - safeOffset;
        int startInclusive = endExclusive - period;
        BigDecimal total = ZERO;
        for (int i = startInclusive; i < endExclusive; i++) {
            total = total.add(valueOf(klines.get(i).getClose()));
        }
        return total.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    /**
     * 求窗口内最高点。
     * 在区间逻辑里，它对应候选阻力位。
     */
    public static BigDecimal highestHigh(List<BinanceKlineDTO> klines) {
        BigDecimal highest = ZERO;
        for (BinanceKlineDTO kline : klines) {
            BigDecimal candidate = valueOf(kline.getHigh());
            if (candidate.compareTo(highest) > 0) {
                highest = candidate;
            }
        }
        return highest;
    }

    /**
     * 求窗口内最低点。
     * 在区间逻辑里，它对应候选支撑位。
     */
    public static BigDecimal lowestLow(List<BinanceKlineDTO> klines) {
        BigDecimal lowest = valueOf(klines.get(0).getLow());
        for (BinanceKlineDTO kline : klines) {
            BigDecimal candidate = valueOf(kline.getLow());
            if (candidate.compareTo(lowest) < 0) {
                lowest = candidate;
            }
        }
        return lowest;
    }

    /**
     * 计算平均成交量。
     *
     * 用途主要有两个：
     * - 确认突破时要求放量；
     * - 回踩确认时更偏好缩量。
     */
    public static BigDecimal averageVolume(List<BinanceKlineDTO> klines) {
        BigDecimal total = ZERO;
        for (BinanceKlineDTO kline : klines) {
            total = total.add(volumeOf(kline));
        }
        return total.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
    }

    /**
     * 统计窗口内有多少根 K 线“足够接近”区间上沿。
     *
     * 这是判断阻力是否被市场反复确认的重要依据。
     */
    public static int countTopTouches(List<BinanceKlineDTO> window, BigDecimal resistance, StrategySettings settings) {
        int count = 0;
        BigDecimal floor = resistance.multiply(ONE.subtract(settings.rangeEdgeTolerance()));
        for (BinanceKlineDTO kline : window) {
            if (valueOf(kline.getHigh()).compareTo(floor) >= 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计窗口内有多少根 K 线“足够接近”区间下沿。
     *
     * 这是判断支撑是否被市场反复确认的重要依据。
     */
    public static int countBottomTouches(List<BinanceKlineDTO> window, BigDecimal support, StrategySettings settings) {
        int count = 0;
        BigDecimal ceiling = support.multiply(ONE.add(settings.rangeEdgeTolerance()));
        for (BinanceKlineDTO kline : window) {
            if (valueOf(kline.getLow()).compareTo(ceiling) <= 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计相邻 K 线中，有多少组达到足够的重叠比例。
     *
     * 区间市场的典型特征之一，就是价格在一个范围里来回交换主导权，
     * 因此相邻K线之间通常有明显重叠。
     */
    public static int countOverlappingBars(List<BinanceKlineDTO> window, StrategySettings settings) {
        int count = 0;
        for (int i = 1; i < window.size(); i++) {
            if (overlapRatio(window.get(i - 1), window.get(i)).compareTo(settings.overlapThreshold()) >= 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 计算两根 K 线的重叠比例。
     *
     * 比例越大，说明这两根K线越像是在同一个价格带里纠缠，
     * 而不是朝某个方向持续推进。
     */
    public static BigDecimal overlapRatio(BinanceKlineDTO left, BinanceKlineDTO right) {
        BigDecimal leftHigh = valueOf(left.getHigh());
        BigDecimal leftLow = valueOf(left.getLow());
        BigDecimal rightHigh = valueOf(right.getHigh());
        BigDecimal rightLow = valueOf(right.getLow());

        BigDecimal overlapHigh = leftHigh.min(rightHigh);
        BigDecimal overlapLow = leftLow.max(rightLow);
        if (overlapHigh.compareTo(overlapLow) <= 0) {
            return ZERO;
        }

        BigDecimal overlap = overlapHigh.subtract(overlapLow);
        BigDecimal leftRange = leftHigh.subtract(leftLow);
        BigDecimal rightRange = rightHigh.subtract(rightLow);
        BigDecimal smallerRange = leftRange.min(rightRange);
        if (smallerRange.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return overlap.divide(smallerRange, 8, RoundingMode.HALF_UP);
    }

    /**
     * 判断是否为阳线。
     */
    public static boolean isBullish(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).compareTo(valueOf(kline.getOpen())) > 0;
    }

    /**
     * 判断是否为阴线。
     */
    public static boolean isBearish(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).compareTo(valueOf(kline.getOpen())) < 0;
    }

    /**
     * 计算高低点之间的相对距离。
     *
     * 这个值常被用来描述：
     * - 区间有多宽
     * - 当前结构是不是还在可接受的震荡范围内
     */
    public static BigDecimal percentageDistance(BigDecimal high, BigDecimal low) {
        return high.subtract(low).divide(low, 8, RoundingMode.HALF_UP);
    }

    /**
     * 通用比例计算。
     *
     * 例如：
     * - 当前成交量 / 平均成交量
     * - 均线偏移 / 参考均线
     */
    public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    /**
     * 取成交量数值。
     */
    public static BigDecimal volumeOf(BinanceKlineDTO kline) {
        return valueOf(kline.getVolume());
    }

    /**
     * K 线实体大小。
     *
     * 实体越大，说明方向性越明确；
     * 实体越小，说明这根K线更偏犹豫或拉锯。
     */
    public static BigDecimal bodySize(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).subtract(valueOf(kline.getOpen())).abs();
    }

    /**
     * K 线总波动范围。
     */
    public static BigDecimal barRange(BinanceKlineDTO kline) {
        return valueOf(kline.getHigh()).subtract(valueOf(kline.getLow()));
    }

    /**
     * 实体占整根 K 线波动范围的比例。
     *
     * 这个值越高，说明这根K线越像“有效推进”；
     * 越低则越像“长影线、低效率波动”。
     */
    public static BigDecimal bodyRatio(BinanceKlineDTO kline) {
        BigDecimal range = barRange(kline);
        if (range.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return bodySize(kline).divide(range, 8, RoundingMode.HALF_UP);
    }

    /**
     * 收盘位置在整根 K 线中的相对位置。
     *
     * 返回值接近 1：
     * 表示收盘靠近最高点；
     * 返回值接近 0：
     * 表示收盘靠近最低点。
     */
    public static BigDecimal closeLocation(BinanceKlineDTO kline) {
        BigDecimal range = barRange(kline);
        if (range.compareTo(ZERO) == 0) {
            return new BigDecimal("0.50");
        }
        return valueOf(kline.getClose()).subtract(valueOf(kline.getLow()))
                .divide(range, 8, RoundingMode.HALF_UP);
    }

    /**
     * 下影线长度。
     *
     * 常用于判断“下探后被强力拉回”的拒绝感是否足够明显。
     */
    public static BigDecimal lowerWick(BinanceKlineDTO kline) {
        BigDecimal open = valueOf(kline.getOpen());
        BigDecimal close = valueOf(kline.getClose());
        BigDecimal low = valueOf(kline.getLow());
        return open.min(close).subtract(low).max(ZERO);
    }

    /**
     * 上影线长度。
     *
     * 常用于判断“上冲后被强力打回”的拒绝感是否足够明显。
     */
    public static BigDecimal upperWick(BinanceKlineDTO kline) {
        BigDecimal open = valueOf(kline.getOpen());
        BigDecimal close = valueOf(kline.getClose());
        BigDecimal high = valueOf(kline.getHigh());
        return high.subtract(open.max(close)).max(ZERO);
    }

    /**
     * 字符串价格转 BigDecimal。
     */
    public static BigDecimal valueOf(String value) {
        return new BigDecimal(value);
    }

    /**
     * 对可为空的价格进行统一保留两位小数。
     *
     * 主要用于构造信号对象时，避免每个策略类重复写相同格式化逻辑。
     */
    public static BigDecimal scaleOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
