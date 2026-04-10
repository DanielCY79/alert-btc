package com.mobai.alert.access.binance.cms.support;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Binance CMS 签名器。
 * 负责把查询参数按顺序拼接，并生成 HmacSHA256 签名。
 */
@Component
public class BinanceCmsSigner {

    public String buildPayload(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public String sign(Map<String, String> params, String secret) {
        return sign(buildPayload(params), secret);
    }

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
