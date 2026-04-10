package com.mobai.alert.strategy;

import com.mobai.alert.access.binance.kline.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.strategy.breakout.ConfirmedBreakoutStrategyEvaluator;
import com.mobai.alert.strategy.pullback.BreakoutPullbackStrategyEvaluator;
import com.mobai.alert.strategy.range.RangeFailureStrategyEvaluator;
import com.mobai.alert.strategy.shared.StrategySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AlertRuleEvaluator {
    /*
     * 杩欐槸绛栫暐灞傜殑缁熶竴闂ㄩ潰绫汇€?
     * 瀹冩湰韬笉鍐嶆壙杞芥墍鏈夌瓥鐣ョ粏鑺傦紝鑰屾槸璐熻矗锛?
     * 1. 鎸佹湁褰撳墠绛栫暐鍙傛暟锛?
     * 2. 鐢熸垚鍙傛暟蹇収锛?
     * 3. 鎶婅姹傚垎鍙戝埌涓変釜鐙珛绛栫暐绫伙細
     *    - 鍖洪棿澶辫触绐佺牬
     *    - 纭绐佺牬
     *    - 绐佺牬鍚庡洖韪?
     *
     * 濡傛灉鎶婃暣濂楃瓥鐣ョ敤鏈€閫氫織鐨勮瘽鏉ヨ锛屽氨鏄細
     * - 甯傚満鍦ㄥ尯闂撮噷鏃讹紝浼樺厛鐪嬭竟缂樼殑澶辫触绐佺牬锛?
     * - 甯傚満鐪熺獊鐮存椂锛屼笉鏄湅鈥滅涓€涓嬭竟鐣屸€濓紝鑰屾槸鐪嬧€滄湁娌℃湁琚帴鍙椻€濓紱
     * - 甯傚満绐佺牬鍚庯紝鏈€鑸掓湇鐨勫叆鍦虹偣閫氬父涓嶆槸绗竴鏍圭獊鐮碖绾匡紝鑰屾槸绗竴娆″洖韪╃‘璁ゃ€?
     */

    private final RangeFailureStrategyEvaluator rangeFailureStrategyEvaluator = new RangeFailureStrategyEvaluator();
    private final ConfirmedBreakoutStrategyEvaluator confirmedBreakoutStrategyEvaluator = new ConfirmedBreakoutStrategyEvaluator();
    private final BreakoutPullbackStrategyEvaluator breakoutPullbackStrategyEvaluator = new BreakoutPullbackStrategyEvaluator();

    // 蹇€熷潎绾垮懆鏈燂紝鐢ㄦ潵琛￠噺鐭湡浠锋牸閲嶅績銆?
    @Value("${monitoring.strategy.trend.fast-period:20}")
    private int fastPeriod;

    // 鎱㈤€熷潎绾垮懆鏈燂紝鐢ㄦ潵纭繚鏍锋湰闀垮害鍜岃秼鍔胯儗鏅冻澶熺ǔ瀹氥€?
    @Value("${monitoring.strategy.trend.slow-period:60}")
    private int slowPeriod;

    // 鍖洪棿璇嗗埆鍥炵湅闀垮害锛屽喅瀹氭渶杩戝灏戞牴K绾跨敤浜庡畾涔夋敮鎾戙€侀樆鍔涘拰涓酱銆?
    @Value("${monitoring.strategy.range.lookback:36}")
    private int rangeLookback;

    // 鍙帴鍙楃殑鏈€灏忓尯闂村搴︼紝杩囩獎閫氬父鍙槸鍣０鏁寸悊锛屼笉鍏峰浜ゆ槗浠峰€笺€?
    @Value("${monitoring.strategy.range.min-width:0.03}")
    private BigDecimal rangeMinWidth;

    // 鍙帴鍙楃殑鏈€澶у尯闂村搴︼紝杩囧鍒欐洿鍍忓ぇ骞呴渿鑽★紝涓嶉€傚悎浣滀负绱у噾鍖洪棿澶勭悊銆?
    @Value("${monitoring.strategy.range.max-width:0.18}")
    private BigDecimal rangeMaxWidth;

    // 杈圭紭瀹瑰繊搴︼紝鐢ㄦ潵鍒ゆ柇浠锋牸鏄惁鈥滆冻澶熸帴杩戔€濆尯闂翠笂娌挎垨涓嬫部銆?
    @Value("${monitoring.strategy.range.edge-tolerance:0.015}")
    private BigDecimal rangeEdgeTolerance;

    // 涓婁笅杈圭紭鏈€灏戣娴嬭瘯鐨勬鏁帮紝纭繚鍖洪棿杈圭晫涓嶆槸鍋剁劧鍑虹幇銆?
    @Value("${monitoring.strategy.range.required-edge-touches:2}")
    private int requiredEdgeTouches;

    // 鐩搁偦K绾挎渶灏忛噸鍙犳瘮渚嬶紝閲嶅彔瓒婂瓒婄鍚堜氦鏄撳尯闂寸殑鏉ュ洖浜ゆ崲鐗瑰緛銆?
    @Value("${monitoring.strategy.range.overlap-threshold:0.45}")
    private BigDecimal overlapThreshold;

    // 鑷冲皯闇€瑕佸灏戞牴K绾挎弧瓒抽噸鍙犳潯浠讹紝閬垮厤鎶婂崟杈规帹杩涜鍒ゆ垚鍖洪棿銆?
    @Value("${monitoring.strategy.range.min-overlap-bars:12}")
    private int minOverlapBars;

    // 蹇€熷潎绾垮厑璁哥殑鏈€澶ф紓绉绘瘮渚嬶紝瓒呰繃杩欎釜鍊艰鏄庝环鏍奸噸蹇冨湪鏄庢樉绉诲姩銆?
    @Value("${monitoring.strategy.range.ma-flat-threshold:0.012}")
    private BigDecimal maFlatThreshold;

    // 纭绐佺牬鏃讹紝鏀剁洏蹇呴』绔欎笂/璺岀牬杈圭晫鐨勬渶灏忕紦鍐叉瘮渚嬨€?
    @Value("${monitoring.strategy.breakout.close-buffer:0.003}")
    private BigDecimal breakoutCloseBuffer;

    // 纭绐佺牬鏃堕渶瑕佽揪鍒扮殑鐩稿鏀鹃噺鍊嶆暟銆?
    @Value("${monitoring.strategy.breakout.volume-multiplier:1.5}")
    private BigDecimal breakoutVolumeMultiplier;

    // 绐佺牬K绾垮疄浣撳崰鏁存牴K绾挎尝鍔ㄧ殑鏈€灏忔瘮渚嬶紝鐢ㄦ潵杩囨护闀垮奖绾垮亣绐佺牬銆?
    @Value("${monitoring.strategy.breakout.body-ratio-threshold:0.45}")
    private BigDecimal breakoutBodyRatioThreshold;

    // 绐佺牬鍚庡厑璁歌窛绂昏竟鐣岀殑鏈€澶у欢浼告瘮渚嬶紝杩囧害鎷夊紑閫氬父涓嶉€傚悎杩戒环銆?
    @Value("${monitoring.strategy.breakout.max-extension:0.05}")
    private BigDecimal breakoutMaxExtension;

    // 绐佺牬澶辨晥缂撳啿锛岀敤浜庤缃‘璁ょ獊鐮村悗鐨勫け鏁堜綅銆?
    @Value("${monitoring.strategy.breakout.failure-buffer:0.008}")
    private BigDecimal breakoutFailureBuffer;

    // 鍋囩獊鐮存椂锛屼环鏍艰嚦灏戣鍚戣竟鐣屽鎺㈠嚭鐨勭紦鍐叉瘮渚嬨€?
    @Value("${monitoring.strategy.failure.probe-buffer:0.003}")
    private BigDecimal failureProbeBuffer;

    // 鍋囩獊鐮村悗閲嶆柊鍥炲埌鍖洪棿鍐呮椂锛岄渶瑕佸洖鏀剁殑鏈€灏忕紦鍐叉瘮渚嬨€?
    @Value("${monitoring.strategy.failure.reentry-buffer:0.001}")
    private BigDecimal failureReentryBuffer;

    // 褰辩嚎涓庡疄浣撶殑鏈€灏忓€嶆暟鍏崇郴锛岀敤鏉ョ‘璁も€滄嫆缁濇劅鈥濇槸鍚﹁冻澶熸槑鏄俱€?
    @Value("${monitoring.strategy.failure.min-wick-body-ratio:1.20}")
    private BigDecimal failureMinWickBodyRatio;

    // 鍥炶俯纭鏃讹紝浠锋牸瑙︾绐佺牬浣嶉檮杩戞墍鍏佽鐨勮宸寖鍥淬€?
    @Value("${monitoring.strategy.pullback.touch-tolerance:0.008}")
    private BigDecimal pullbackTouchTolerance;

    // 鍥炶俯纭鏃讹紝鏀剁洏鍏佽璺屽洖/绔欏洖绐佺牬浣嶅彟涓€渚х殑鏈€澶у蹇嶈寖鍥淬€?
    @Value("${monitoring.strategy.pullback.hold-buffer:0.006}")
    private BigDecimal pullbackHoldBuffer;

    // 鍥炶俯闃舵鍏佽鐨勬渶澶х浉瀵归噺鑳斤紝绛栫暐鏇村亸濂界缉閲忓洖韪╄€屼笉鏄啀娆″墽鐑堝鍐层€?
    @Value("${monitoring.strategy.pullback.max-volume-ratio:1.10}")
    private BigDecimal pullbackMaxVolumeRatio;

    /**
     * 瀵瑰鏆撮湶鈥滃尯闂翠笅鐮村け璐ュ仛澶氣€濈瓥鐣ャ€?
     * 璋冪敤鏂逛笉闇€瑕佺煡閬撳唴閮ㄥ叿浣撳疄鐜板湪鍝釜瀛愮被閲岋紝鍙渶瑕佷粠杩欓噷鍙栫粨鏋滃嵆鍙€?
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakdownLong(List<BinanceKlineDTO> klines) {
        return rangeFailureStrategyEvaluator.evaluateRangeFailedBreakdownLong(klines, currentSettings());
    }

    /**
     * 瀵瑰鏆撮湶鈥滃尯闂翠笂鐮村け璐ュ仛绌衡€濈瓥鐣ャ€?
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakoutShort(List<BinanceKlineDTO> klines) {
        return rangeFailureStrategyEvaluator.evaluateRangeFailedBreakoutShort(klines, currentSettings());
    }

    /**
     * 瀵瑰鏆撮湶鈥滃尯闂寸‘璁ょ獊鐮村仛澶氣€濈瓥鐣ャ€?
     */
    public Optional<AlertSignal> evaluateTrendBreakout(List<BinanceKlineDTO> klines) {
        return confirmedBreakoutStrategyEvaluator.evaluateTrendBreakout(klines, currentSettings());
    }

    /**
     * 瀵瑰鏆撮湶鈥滃尯闂寸‘璁よ穼鐮村仛绌衡€濈瓥鐣ャ€?
     */
    public Optional<AlertSignal> evaluateTrendBreakdown(List<BinanceKlineDTO> klines) {
        return confirmedBreakoutStrategyEvaluator.evaluateTrendBreakdown(klines, currentSettings());
    }

    /**
     * 鍚戜笂绐佺牬鍦烘櫙鐨勭畝鍖栬皟鐢紝榛樿涓嶉澶栦紶鍏ョ洰鏍囦綅銆?
     */
    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel) {
        return evaluateBreakoutPullback(klines, breakoutLevel, null, true);
    }

    /**
     * 绐佺牬鍚庡洖韪╃瓥鐣ョ殑绠€鍖栬皟鐢ㄣ€?
     * bullishBreakout=true 琛ㄧず鍚戜笂绐佺牬鍚庣殑鍥炶俯鍋氬锛?
     * bullishBreakout=false 琛ㄧず鍚戜笅璺岀牬鍚庣殑鍙嶆娊鍋氱┖銆?
     */
    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          boolean bullishBreakout) {
        return evaluateBreakoutPullback(klines, breakoutLevel, null, bullishBreakout);
    }

    /**
     * 瀵瑰鏆撮湶鈥滅獊鐮村悗鍥炶俯纭鈥濈瓥鐣ョ殑瀹屾暣璋冪敤褰㈠紡銆?
     * 杩欐槸涓夌被绛栫暐閲屾渶鍋忊€滈『鍔夸簩娆″叆鍦衡€濈殑涓€绉嶃€?
     */
    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          BigDecimal targetPrice,
                                                          boolean bullishBreakout) {
        return breakoutPullbackStrategyEvaluator.evaluateBreakoutPullback(
                klines,
                breakoutLevel,
                targetPrice,
                bullishBreakout,
                currentSettings()
        );
    }

    private StrategySettings currentSettings() {
        return new StrategySettings(
                fastPeriod,
                slowPeriod,
                rangeLookback,
                rangeMinWidth,
                rangeMaxWidth,
                rangeEdgeTolerance,
                requiredEdgeTouches,
                overlapThreshold,
                minOverlapBars,
                maFlatThreshold,
                breakoutCloseBuffer,
                breakoutVolumeMultiplier,
                breakoutBodyRatioThreshold,
                breakoutMaxExtension,
                breakoutFailureBuffer,
                failureProbeBuffer,
                failureReentryBuffer,
                failureMinWickBodyRatio,
                pullbackTouchTolerance,
                pullbackHoldBuffer,
                pullbackMaxVolumeRatio
        );
    }
}

