package com.reliablewebhooks.endpoint.infrastructure;

import com.reliablewebhooks.endpoint.domain.SecretGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class SecureRandomSecretGenerator implements SecretGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Override
    public String generate() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }
}
