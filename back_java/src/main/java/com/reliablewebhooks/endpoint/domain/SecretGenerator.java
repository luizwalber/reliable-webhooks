package com.reliablewebhooks.endpoint.domain;

/**
 * Domain port for issuing an Endpoint's HMAC signing secret (docs/adr/0007-hmac-signing).
 * How the randomness is sourced is an infrastructure concern; the domain only
 * needs "give me a secret" — implemented by endpoint.infrastructure.SecureRandomSecretGenerator.
 */
public interface SecretGenerator {

    String generate();
}
