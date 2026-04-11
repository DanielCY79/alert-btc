package com.mobai.alert.access.event.binance.cms.support;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Binance CMS 签名工具。
 * 负责将待签名参数按约定顺序拼接成查询串，并使用 {@code HmacSHA256} 生成签名，
 * 供 CMS WebSocket 建连时完成鉴权。
 */
@Component
public class BinanceCmsSigner {

    /**
     * 按传入顺序将参数拼接为 URL 查询串。
     *
     * @param params 待拼接参数
     * @return 形如 {@code key1=value1&key2=value2} 的查询串
     */
    public String buildPayload(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * 对参数集合直接签名。
     *
     * @param params 待签名参数
     * @param secret API Secret
     * @return 十六进制签名串
     */
    public String sign(Map<String, String> params, String secret) {
        return sign(buildPayload(params), secret);
    }

    /**
     * 对已拼接完成的载荷字符串进行签名。
     *
     * @param payload 已拼接的原始 payload
     * @param secret API Secret
     * @return 十六进制签名串
     */
    public String sign(String payload, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] signed = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(signed.length * 2);
            for (byte value : signed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign Binance CMS payload", e);
        }
    }
}
