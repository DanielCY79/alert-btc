package com.mobai.alert.strategy.shared;

import com.mobai.alert.access.binance.kline.dto.BinanceKlineDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 鍖洪棿涓婁笅鏂囧璞°€?
 *
 * 杩欎釜绫昏〃绀猴細
 * 鈥滃湪鏈€杩戜竴娈靛巻鍙睰绾块噷锛岀郴缁熷凡缁忕‘璁ゅ嚭浜嗕竴涓彲浜ゆ槗鍖洪棿鈥濄€?
 *
 * 瀹冩妸鍚庣画绛栫暐鍒ゆ柇鏈€鍏冲績鐨勫嚑涓环鏍煎尯鍩熺粺涓€鎵撳寘锛?
 * - window锛氱敤浜庡畾涔夎繖涓尯闂寸殑鍘嗗彶鏍锋湰绐楀彛
 * - support锛氬尯闂翠笅娌?
 * - resistance锛氬尯闂翠笂娌?
 * - midpoint锛氬尯闂翠腑杞?
 * - width锛氬尯闂村搴?
 *
 * 鍙鏌愪釜绛栫暐闇€瑕佸熀浜庘€滄垚鐔熷尯闂粹€濆仛鍒ゆ柇锛?
 * 瀹冮€氬父閮戒細鍏堜緷璧栬繖涓璞°€?
 */
public record RangeContext(List<BinanceKlineDTO> window,
                           BigDecimal support,
                           BigDecimal resistance,
                           BigDecimal midpoint,
                           BigDecimal width) {
    /*
     * 浠庝氦鏄撶悊瑙ｄ笂锛?
     * - support / resistance 鍐冲畾杈圭紭浜ゆ槗涓庣獊鐮翠綅缃紱
     * - midpoint 鍐冲畾鍖洪棿鍐呭弽鍚戜氦鏄撶殑绗竴鐩爣锛?
     * - width 鏃㈣兘鐢ㄦ潵杩囨护鍖洪棿锛屼篃鑳借鐢?measured move 鐩爣銆?
     */
}

