package com.mobai.alert.access.binance.cms.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinanceCmsSignerTests {

    @Test
    void shouldBuildPayloadInInsertionOrder() {
        BinanceCmsSigner signer = new BinanceCmsSigner();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("random", "1");
        params.put("topic", "2");
        params.put("recvWindow", "3");
        params.put("timestamp", "4");
        String payload = signer.buildPayload(params);

        assertEquals("random=1&topic=2&recvWindow=3&timestamp=4", payload);
    }

    @Test
    void shouldGenerateExpectedSignature() {
        BinanceCmsSigner signer = new BinanceCmsSigner();

        String signature = signer.sign(
                "random=56724ac693184379ae23ffe5e910063c&topic=topic1&recvWindow=30000&timestamp=1753244327210",
                "Avqz4IQjoZSJOowMFSo3QZEd4ovfwLH7Kie8ZliTtP8ktDnqcX8bpCP7WluFtrfn"
        );

        assertEquals("8346d214e0da7165a0093043395f67e08c63f61b5d6e25779d513c11450e691b", signature);
    }
}

