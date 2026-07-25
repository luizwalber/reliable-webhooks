package com.reliablewebhooks.delivery.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 over "{timestamp}.{raw body}", formatted as the Stripe-style
 * X-Webhook-Signature header value (docs/adr/0007-hmac-signing). A plain
 * static utility, not a port — single call site (HttpEndpointDeliveryClient),
 * no second implementation ever needed.
 */
final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {
    }

    static String header(String secret, String rawBody, Instant timestamp) {
        long epochSeconds = timestamp.getEpochSecond();
        String canonical = epochSeconds + "." + rawBody;
        return "t=%d,v1=sha256=%s".formatted(epochSeconds, hex(sign(secret, canonical)));
    }

    private static byte[] sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
