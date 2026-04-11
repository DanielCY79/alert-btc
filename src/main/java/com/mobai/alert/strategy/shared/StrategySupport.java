package com.mobai.alert.strategy.shared;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 绛栫暐鍏叡璁＄畻宸ュ叿绫汇€?
 *
 * 杩欎釜绫绘壙杞界殑鏄€滄墍鏈夌瓥鐣ラ兘浼氬弽澶嶇敤鍒扮殑鍩虹璁＄畻鑳藉姏鈥濓紝渚嬪锛?
 * - 鍙彇宸叉敹鐩楰绾?
 * - 璇嗗埆鍖洪棿鏄惁鎴愮珛
 * - 璁＄畻鍧囩嚎銆佸奖绾裤€佸疄浣撱€侀噸鍙犲害銆侀噺鑳藉潎鍊?
 * - 鎻愪緵鏀拺/闃诲姏/涓酱绛夊熀纭€缁撴瀯
 *
 * 璁捐鐩爣鏄細
 * 璁╁叿浣撶瓥鐣ョ被鍙叧娉ㄢ€滀氦鏄撻€昏緫鈥濓紝
 * 鑰屾妸閫氱敤鐨勬暟瀛︿笌缁撴瀯鍒ゆ柇娌夊埌杩欓噷缁熶竴缁存姢銆?
 */
public final class StrategySupport {

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal TWO = new BigDecimal("2");

    private StrategySupport() {
    }

    /**
     * 璁＄畻褰撳墠绛栫暐鑷冲皯闇€瑕佸灏戞牴 K 绾挎牱鏈€?
     *
     * 鍘熷洜寰堢畝鍗曪細
     * - 鍖洪棿璇嗗埆鏈韩闇€瑕佽冻澶熼暱鐨勫洖鐪嬬獥鍙ｏ紱
     * - 鍧囩嚎婕傜Щ鍒ゆ柇涔熼渶瑕佽冻澶熼暱鐨勫潎绾垮懆鏈燂紱
     * - 鍐嶉澶栫暀涓€鐐圭紦鍐诧紝閬垮厤杈圭晫璁块棶闂銆?
     */
    public static int minimumBarsRequired(StrategySettings settings) {
        return Math.max(settings.slowPeriod() + 6, settings.rangeLookback() + settings.fastPeriod() + 6);
    }

    /**
     * 鍒ゆ柇鏍锋湰鏁伴噺鏄惁瓒冲銆?
     *
     * 杩欐槸鎵€鏈夌瓥鐣ュ湪鐪熸寮€濮嬪墠鐨勭涓€灞傞槻绾匡紝
     * 閬垮厤鍥犱负鏍锋湰澶煭瀵艰嚧浠讳綍褰㈡€佸垽鏂け鐪熴€?
     */
    public static boolean hasEnoughBars(List<BinanceKlineDTO> closedKlines, int minimumSize) {
        return !CollectionUtils.isEmpty(closedKlines) && closedKlines.size() >= minimumSize;
    }

    /**
     * 缁熶竴鍙繚鐣欏凡鏀剁洏 K 绾裤€?
     *
     * 鏈€鍚庝竴鏍规湭鏀剁洏 K 绾垮湪鐩樹腑浼氫笉鏂彉鍖栵紝
     * 濡傛灉鐩存帴鎷垮畠鍋氱瓥鐣ュ垽鏂紝寰堝鏄撳湪鍥炴祴鍜屽疄鐩樹箣闂翠骇鐢熷亸宸€?
     */
    public static List<BinanceKlineDTO> closedKlines(List<BinanceKlineDTO> klines) {
        if (CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return List.of();
        }
        return klines.subList(0, klines.size() - 1);
    }

    /**
     * 鏋勫缓鈥滃尯闂翠笂涓嬫枃鈥濄€?
     *
     * 杩欐槸鏁村绛栫暐鏈€鏍稿績鐨勫叕鍏辨柟娉曚箣涓€銆?
     * 鍙湁褰撳競鍦哄悓鏃舵弧瓒筹細
     * - 鍖洪棿瀹藉害鍚堢悊
     * - 涓婁笅杈圭晫閮借澶氭瑙﹁揪
     * - K绾夸箣闂磋冻澶熼噸鍙?
     * - 鍧囩嚎婕傜Щ涓嶅ぇ
     *
     * 鎵嶄細琚瀹氫负涓€涓彲浜ゆ槗鍖洪棿銆?
     * 濡傛灉浠讳綍涓€鏉′笉婊¤冻锛屽氨杩斿洖 null锛岃〃绀衡€滃綋鍓嶄笉鎶婂畠褰撳尯闂村鐞嗏€濄€?
     */
    public static RangeContext buildRangeContext(List<BinanceKlineDTO> closedKlines, StrategySettings settings) {
        if (!hasEnoughBars(closedKlines, minimumBarsRequired(settings))) {
            return null;
        }

        // 瀹氫箟鍖洪棿鏃舵帓闄ゆ帀鏈€鍚庝竴鏍逛俊鍙稫绾匡紝閬垮厤褰撳墠K绾挎棦瀹氫箟鍖洪棿鍙堣Е鍙戜俊鍙枫€?
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
     * 浠庡熬閮ㄦ埅鍙栦竴涓獥鍙ｃ€?
     *
     * 杩欐槸绛栫暐閲屾渶甯歌鐨勫彇鏍锋柟寮忥細
     * 浠庢渶杩戠殑鍘嗗彶涓嬁鍥哄畾鏁伴噺鐨凨绾挎潵鍋氬垽鏂紝
     * 鍚屾椂鍏佽鎺掗櫎鏈€鍚庡嚑鏍癸紝閬垮厤鎶婁俊鍙稫绾挎贩杩涘弬鑰冪獥鍙ｃ€?
     */
    public static List<BinanceKlineDTO> trailingWindow(List<BinanceKlineDTO> klines, int size, int excludeLastBars) {
        int endExclusive = klines.size() - excludeLastBars;
        int startInclusive = Math.max(0, endExclusive - size);
        return klines.subList(startInclusive, endExclusive);
    }

    /**
     * 鍙栨渶鍚庝竴鏍瑰凡鏀剁洏 K 绾裤€?
     */
    public static BinanceKlineDTO last(List<BinanceKlineDTO> klines) {
        return klines.get(klines.size() - 1);
    }

    /**
     * 绠€鍗曠Щ鍔ㄥ钩鍧囧€笺€?
     *
     * 杩欓噷涓昏鐢ㄤ簬鍒ゆ柇鈥滀环鏍奸噸蹇冩湁娌℃湁鏄庢樉婕傜Щ鈥濓紝
     * 涓嶆槸鐢ㄦ潵鍋氫紶缁熷潎绾块噾鍙夋鍙夈€?
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
     * 姹傜獥鍙ｅ唴鏈€楂樼偣銆?
     * 鍦ㄥ尯闂撮€昏緫閲岋紝瀹冨搴斿€欓€夐樆鍔涗綅銆?
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
     * 姹傜獥鍙ｅ唴鏈€浣庣偣銆?
     * 鍦ㄥ尯闂撮€昏緫閲岋紝瀹冨搴斿€欓€夋敮鎾戜綅銆?
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
     * 璁＄畻骞冲潎鎴愪氦閲忋€?
     *
     * 鐢ㄩ€斾富瑕佹湁涓や釜锛?
     * - 纭绐佺牬鏃惰姹傛斁閲忥紱
     * - 鍥炶俯纭鏃舵洿鍋忓ソ缂╅噺銆?
     */
    public static BigDecimal averageVolume(List<BinanceKlineDTO> klines) {
        BigDecimal total = ZERO;
        for (BinanceKlineDTO kline : klines) {
            total = total.add(volumeOf(kline));
        }
        return total.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
    }

    /**
     * 缁熻绐楀彛鍐呮湁澶氬皯鏍?K 绾库€滆冻澶熸帴杩戔€濆尯闂翠笂娌裤€?
     *
     * 杩欐槸鍒ゆ柇闃诲姏鏄惁琚競鍦哄弽澶嶇‘璁ょ殑閲嶈渚濇嵁銆?
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
     * 缁熻绐楀彛鍐呮湁澶氬皯鏍?K 绾库€滆冻澶熸帴杩戔€濆尯闂翠笅娌裤€?
     *
     * 杩欐槸鍒ゆ柇鏀拺鏄惁琚競鍦哄弽澶嶇‘璁ょ殑閲嶈渚濇嵁銆?
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
     * 缁熻鐩搁偦 K 绾夸腑锛屾湁澶氬皯缁勮揪鍒拌冻澶熺殑閲嶅彔姣斾緥銆?
     *
     * 鍖洪棿甯傚満鐨勫吀鍨嬬壒寰佷箣涓€锛屽氨鏄环鏍煎湪涓€涓寖鍥撮噷鏉ュ洖浜ゆ崲涓诲鏉冿紝
     * 鍥犳鐩搁偦K绾夸箣闂撮€氬父鏈夋槑鏄鹃噸鍙犮€?
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
     * 璁＄畻涓ゆ牴 K 绾跨殑閲嶅彔姣斾緥銆?
     *
     * 姣斾緥瓒婂ぇ锛岃鏄庤繖涓ゆ牴K绾胯秺鍍忔槸鍦ㄥ悓涓€涓环鏍煎甫閲岀籂缂狅紝
     * 鑰屼笉鏄湞鏌愪釜鏂瑰悜鎸佺画鎺ㄨ繘銆?
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
     * 鍒ゆ柇鏄惁涓洪槼绾裤€?
     */
    public static boolean isBullish(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).compareTo(valueOf(kline.getOpen())) > 0;
    }

    /**
     * 鍒ゆ柇鏄惁涓洪槾绾裤€?
     */
    public static boolean isBearish(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).compareTo(valueOf(kline.getOpen())) < 0;
    }

    /**
     * 璁＄畻楂樹綆鐐逛箣闂寸殑鐩稿璺濈銆?
     *
     * 杩欎釜鍊煎父琚敤鏉ユ弿杩帮細
     * - 鍖洪棿鏈夊瀹?
     * - 褰撳墠缁撴瀯鏄笉鏄繕鍦ㄥ彲鎺ュ彈鐨勯渿鑽¤寖鍥村唴
     */
    public static BigDecimal percentageDistance(BigDecimal high, BigDecimal low) {
        return high.subtract(low).divide(low, 8, RoundingMode.HALF_UP);
    }

    /**
     * 閫氱敤姣斾緥璁＄畻銆?
     *
     * 渚嬪锛?
     * - 褰撳墠鎴愪氦閲?/ 骞冲潎鎴愪氦閲?
     * - 鍧囩嚎鍋忕Щ / 鍙傝€冨潎绾?
     */
    public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    /**
     * 鍙栨垚浜ら噺鏁板€笺€?
     */
    public static BigDecimal volumeOf(BinanceKlineDTO kline) {
        return valueOf(kline.getVolume());
    }

    /**
     * K 绾垮疄浣撳ぇ灏忋€?
     *
     * 瀹炰綋瓒婂ぇ锛岃鏄庢柟鍚戞€ц秺鏄庣‘锛?
     * 瀹炰綋瓒婂皬锛岃鏄庤繖鏍筀绾挎洿鍋忕姽璞垨鎷夐敮銆?
     */
    public static BigDecimal bodySize(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).subtract(valueOf(kline.getOpen())).abs();
    }

    /**
     * K 绾挎€绘尝鍔ㄨ寖鍥淬€?
     */
    public static BigDecimal barRange(BinanceKlineDTO kline) {
        return valueOf(kline.getHigh()).subtract(valueOf(kline.getLow()));
    }

    /**
     * 瀹炰綋鍗犳暣鏍?K 绾挎尝鍔ㄨ寖鍥寸殑姣斾緥銆?
     *
     * 杩欎釜鍊艰秺楂橈紝璇存槑杩欐牴K绾胯秺鍍忊€滄湁鏁堟帹杩涒€濓紱
     * 瓒婁綆鍒欒秺鍍忊€滈暱褰辩嚎銆佷綆鏁堢巼娉㈠姩鈥濄€?
     */
    public static BigDecimal bodyRatio(BinanceKlineDTO kline) {
        BigDecimal range = barRange(kline);
        if (range.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return bodySize(kline).divide(range, 8, RoundingMode.HALF_UP);
    }

    /**
     * 鏀剁洏浣嶇疆鍦ㄦ暣鏍?K 绾夸腑鐨勭浉瀵逛綅缃€?
     *
     * 杩斿洖鍊兼帴杩?1锛?
     * 琛ㄧず鏀剁洏闈犺繎鏈€楂樼偣锛?
     * 杩斿洖鍊兼帴杩?0锛?
     * 琛ㄧず鏀剁洏闈犺繎鏈€浣庣偣銆?
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
     * 涓嬪奖绾块暱搴︺€?
     *
     * 甯哥敤浜庡垽鏂€滀笅鎺㈠悗琚己鍔涙媺鍥炩€濈殑鎷掔粷鎰熸槸鍚﹁冻澶熸槑鏄俱€?
     */
    public static BigDecimal lowerWick(BinanceKlineDTO kline) {
        BigDecimal open = valueOf(kline.getOpen());
        BigDecimal close = valueOf(kline.getClose());
        BigDecimal low = valueOf(kline.getLow());
        return open.min(close).subtract(low).max(ZERO);
    }

    /**
     * 涓婂奖绾块暱搴︺€?
     *
     * 甯哥敤浜庡垽鏂€滀笂鍐插悗琚己鍔涙墦鍥炩€濈殑鎷掔粷鎰熸槸鍚﹁冻澶熸槑鏄俱€?
     */
    public static BigDecimal upperWick(BinanceKlineDTO kline) {
        BigDecimal open = valueOf(kline.getOpen());
        BigDecimal close = valueOf(kline.getClose());
        BigDecimal high = valueOf(kline.getHigh());
        return high.subtract(open.max(close)).max(ZERO);
    }

    /**
     * 瀛楃涓蹭环鏍艰浆 BigDecimal銆?
     */
    public static BigDecimal valueOf(String value) {
        return new BigDecimal(value);
    }

    /**
     * 瀵瑰彲涓虹┖鐨勪环鏍艰繘琛岀粺涓€淇濈暀涓や綅灏忔暟銆?
     *
     * 涓昏鐢ㄤ簬鏋勯€犱俊鍙峰璞℃椂锛岄伩鍏嶆瘡涓瓥鐣ョ被閲嶅鍐欑浉鍚屾牸寮忓寲閫昏緫銆?
     */
    public static BigDecimal scaleOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

