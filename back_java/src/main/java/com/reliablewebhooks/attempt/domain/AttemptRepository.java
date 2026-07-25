package com.reliablewebhooks.attempt.domain;

/** Domain port. Implemented by attempt.infrastructure.AttemptRepositoryAdapter. */
public interface AttemptRepository {

    Attempt save(Attempt attempt);
}
