# Research: Resilience4j per-endpoint circuit-breaker config surface

Grounds issue #4. Sources: [Resilience4j CircuitBreaker docs](https://resilience4j.readme.io/docs/circuitbreaker), [Getting started — Spring Boot 3](https://resilience4j.readme.io/docs/getting-started-3), [CircuitBreakerRegistry.java source](https://github.com/resilience4j/resilience4j/blob/master/resilience4j-circuitbreaker/src/main/java/io/github/resilience4j/circuitbreaker/CircuitBreakerRegistry.java).

## 1. Dynamic per-instance creation via `CircuitBreakerRegistry`

The registry is a thread-safe factory/cache keyed by `name` (String). Relevant overloads on `CircuitBreakerRegistry`:

```java
CircuitBreaker circuitBreaker(String name)
CircuitBreaker circuitBreaker(String name, Map<String, String> tags)
CircuitBreaker circuitBreaker(String name, CircuitBreakerConfig config)
CircuitBreaker circuitBreaker(String name, CircuitBreakerConfig config, Map<String, String> tags)
CircuitBreaker circuitBreaker(String name, String configName)                    // shared/named config
CircuitBreaker circuitBreaker(String name, Supplier<CircuitBreakerConfig> configSupplier)
Set<CircuitBreaker> getAllCircuitBreakers()
```

All are "get-or-create" — first call for a given `name` creates and caches the instance; subsequent calls with the same name return the cached one (config args on a repeat call are ignored, the first registration wins). This is exactly the mechanism for "endpoint set not known at startup": call `registry.circuitBreaker(endpointId, config)` the first time a webhook endpoint is registered/delivered-to, and every later delivery attempt just calls `registry.circuitBreaker(endpointId)` to fetch the same instance.

Registry-level events for observability: `circuitBreakerRegistry.getEventPublisher().onEntryAdded(...)`, `.onEntryRemoved(...)` — fire when a new per-endpoint breaker is created/evicted.

## 2. Sliding window: COUNT_BASED vs TIME_BASED

`CircuitBreakerConfig.Builder`:

| Property | Default |
|---|---|
| `slidingWindowType()` | `COUNT_BASED` |
| `slidingWindowSize()` | 100 |
| `minimumNumberOfCalls()` | 100 |
| `failureRateThreshold()` | 50% |
| `slowCallRateThreshold()` | 100% |
| `slowCallDurationThreshold()` | 60000ms |
| `waitDurationInOpenState()` | 60000ms |
| `permittedNumberOfCallsInHalfOpenState()` | 10 |

- **COUNT_BASED**: circular array of the last N *calls*, regardless of how long they took to accumulate. Rate is evaluated only once `minimumNumberOfCalls` calls have landed in the window.
- **TIME_BASED**: aggregates calls in the last N *seconds*, via a ring buffer of per-second buckets. Rate calc still gated by `minimumNumberOfCalls` — with sparse traffic, a stale/expired bucket can leave the window under the minimum indefinitely, meaning the breaker never opens even against a dead endpoint.

**Recommendation for this project**: `COUNT_BASED`, with a small window (e.g. `slidingWindowSize = 10`, `minimumNumberOfCalls = 5`). Webhook deliveries per endpoint are sporadic/bursty, not steady QPS — a time window can sit empty for long stretches waiting for calls to arrive, so it doesn't reliably represent "how has this endpoint behaved lately." A count-based window evaluates strictly on the last N actual delivery attempts, which matches "did the last few deliveries to this endpoint fail" — the actual signal wanted for per-endpoint health.

Suggested demo defaults per endpoint:
```java
CircuitBreakerConfig.custom()
  .slidingWindowType(SlidingWindowType.COUNT_BASED)
  .slidingWindowSize(10)
  .minimumNumberOfCalls(5)
  .failureRateThreshold(50)
  .waitDurationInOpenState(Duration.ofSeconds(30))
  .permittedNumberOfCallsInHalfOpenState(3)
  .build();
```

## 3. Per-instance config vs shared default

Two mechanisms, both usable together:

- **Programmatic, fully independent config per key** — `registry.circuitBreaker(endpointId, customConfig)` where `customConfig` is a fresh `CircuitBreakerConfig` built per endpoint (e.g. if a future feature lets users tune thresholds per endpoint). No YAML entry needed; the registry just stores whatever `CircuitBreakerConfig` object is passed on first creation.
- **Named "shared config" registered once, referenced by name** — `registry.addConfiguration("endpointDefault", config)` then `registry.circuitBreaker(endpointId, "endpointDefault")` for every dynamically-discovered endpoint. This is the programmatic analogue of the Spring Boot YAML pattern:
  ```yaml
  resilience4j.circuitbreaker:
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
    instances:
      # static instances only — NOT usable for endpoints unknown at startup
      exampleStaticEndpoint:
        baseConfig: default
  ```
  The YAML `instances.*` block only supports circuit breakers whose names are known at build/deploy time — it cannot register one the first time a user adds a webhook endpoint at runtime. For this project's dynamic-per-endpoint requirement, the YAML `configs.default` block is still useful (defines the shared `CircuitBreakerConfig` template consumed by Spring's autoconfigured registry), but the *instance* registration itself must happen in application code via `registry.circuitBreaker(endpointId, "default")` at the moment an endpoint is first used.

## 4. Spring Boot beans/config

- `resilience4j-spring-boot3` autoconfigures a `CircuitBreakerRegistry` bean (backed by the `resilience4j.circuitbreaker.configs.default` YAML block if present, else library defaults) plus the AOP aspect that powers the `@CircuitBreaker` annotation.
- **No custom `CircuitBreakerRegistry` `@Bean` is required** — `@Autowired CircuitBreakerRegistry registry` in a service and call `registry.circuitBreaker(...)` programmatically. Defining a custom bean is only needed to override global defaults or add a `RegistryEventConsumer`.
- **`@CircuitBreaker(name = "...")` annotation does NOT fit this use case.** The annotation's `name` is a compile-time string (or SpEL against method args, but still declared statically per call site) applied via AOP around one method — it can't dynamically fan out to N independent breaker instances keyed by a runtime-discovered endpoint ID. Confirmed: use **programmatic decoration** instead —
  ```java
  CircuitBreaker cb = registry.circuitBreaker(endpointId, "default");
  Supplier<HttpResponse> decorated = CircuitBreaker.decorateSupplier(cb, () -> httpClient.send(endpointId, payload));
  Try.ofSupplier(decorated)...
  ```
  or `cb.executeSupplier(...)` / `cb.executeCallable(...)`.
- Actuator: `management.endpoints.web.exposure.include: circuitbreakers,circuitbreakerevents,health` exposes:
  - `GET /actuator/circuitbreakers` — names of all registered instances (i.e., all currently known endpoints).
  - `GET /actuator/circuitbreakerevents` and `/actuator/circuitbreakerevents/{name}` — last 100 state-transition/call events, filterable per endpoint.
  - Health indicator per instance requires `management.health.circuitbreakers.enabled: true`; each instance surfaces as a health group member (state CLOSED/OPEN/HALF_OPEN).
- **For the frontend's per-endpoint health view**, two viable approaches, usable together:
  1. Build a custom `@RestController` endpoint that iterates `registry.getAllCircuitBreakers()` and maps each `CircuitBreaker` to `{name, state, metrics}` via `cb.getName()`, `cb.getState()`, `cb.getMetrics()` (`getFailureRate()`, `getNumberOfBufferedCalls()`, etc.) — gives full control over the JSON shape the UI needs, keyed by endpoint ID.
  2. Consume `/actuator/circuitbreakerevents/{endpointId}` for a live event/audit trail per endpoint (state transitions over time), and/or attach `cb.getEventPublisher().onStateTransition(event -> ...)` per instance at creation time to push updates over a websocket/SSE channel for real-time UI updates instead of polling actuator.

## 5. Summary recommendation for this project

1. Autowire the Spring-Boot-autoconfigured `CircuitBreakerRegistry` — no custom bean needed.
2. Define one shared `CircuitBreakerConfig` template via `resilience4j.circuitbreaker.configs.default` in `application.yml` (COUNT_BASED, size 10, min calls 5, 50% failure threshold, 30s open wait, 3 half-open probes).
3. On first delivery attempt to a newly-registered endpoint, call `registry.circuitBreaker(endpointId, "default")` — creates and caches the per-endpoint breaker on demand; every later call for that ID retrieves the same instance.
4. Wrap actual HTTP delivery calls with `CircuitBreaker.decorateSupplier(cb, ...)` / `cb.executeSupplier(...)` — do not use the `@CircuitBreaker` annotation, since endpoint IDs aren't known at compile time.
5. Expose per-endpoint state to the frontend via a custom controller iterating `registry.getAllCircuitBreakers()`, optionally backed by actuator's `/actuator/circuitbreakers` and `/actuator/circuitbreakerevents/{name}` for richer event history.
