# Research: contract-test tooling choice

Ticket: [#16](https://github.com/luizwalber/reliable-webhooks/issues/16) — "what's the best-fit tool for validating the Spring Boot implementation against `openapi.yaml`?"

## Framing

This is **not** consumer-driven contract testing (CDC). CDC tools generate/verify contracts between a consumer and provider, typically producing (or driven by) their own contract format. What this ticket actually needs is **spec conformance testing**: take real HTTP request/response pairs produced by the running Spring Boot controllers and assert they match the schemas already defined in `openapi.yaml`. That's a narrower, cheaper problem than CDC, and it changes which tools are even in scope.

## Options evaluated

### 1. Spring Cloud Contract — poor fit, ruled out

- Spring Cloud Contract (SCC) is built around Groovy/YAML **contract DSL files that it owns and generates stubs/tests from** — the contract is the source of truth, and SCC generates a WireMock stub + a provider-side test from it. The natural direction is DSL → generated spec/stubs, not "validate an existing `openapi.yaml`."
- SCC does have an OpenAPI-contract-converter incubating feature (writing contracts *as* OpenAPI paths), but this means restructuring `openapi.yaml` to be SCC's contract format rather than using the already-finalized spec as-is. It's designed for multi-service CDC workflows (producer/consumer, contract broker), which is enterprise-scale plumbing this portfolio project doesn't need. ([DZone: Contracts for Microservices With OpenAPI and Spring Cloud Contract](https://dzone.com/articles/contracts-for-microservices-with-openapi-and-sprin), [Spring Framework Guru: Defining Spring Cloud Contracts in Open API](https://springframework.guru/defining-spring-cloud-contracts-in-open-api/))
- Verdict: wrong direction for "validate implementation against an existing spec." Would add Groovy DSL authoring overhead with no real benefit here. Not recommended.

### 2. atlassian/swagger-request-validator (now rebranding to `openapi-request-validator`) — recommended

- Purpose-built for exactly this: validates real HTTP request/response pairs against an OpenAPI/Swagger spec. Works standalone or wired into Spring MVC, **Spring MockMvc**, REST Assured, Spring WebClient, WireMock, or Pact.
- Actively maintained. The project underwent a rename from `swagger-request-validator` → `openapi-request-validator` (artifact IDs moving from `com.atlassian.oai:swagger-request-validator-*` to the new naming) as of v3.0.0, with recent modules explicitly stating **Spring 6 / Spring Boot 3 / Jakarta namespace / JDK17+ compatibility**, and newer branches already tracking Spring 7 / Boot 4. This confirms active upkeep and a straight-line compatibility story for this project's Java 21 + Spring Boot 3 stack. ([GitHub: atlassian/openapi-request-validator](https://github.com/atlassian/openapi-request-validator), [Maven Central: swagger-request-validator-spring-webmvc](https://central.sonatype.com/artifact/com.atlassian.oai/swagger-request-validator-spring-webmvc/2.35.0))
- Relevant module for this project: **`swagger-request-validator-mockmvc`** — ships a `ResultMatcher` that plugs directly into existing `MockMvc` `.andExpect(...)` chains, so it rides on infrastructure the team is presumably already using for controller tests (no new test-execution model, no extra containers). ([Maven Repository: swagger-request-validator-mockmvc](https://mvnrepository.com/artifact/com.atlassian.oai/swagger-request-validator-mockmvc), [DEV Community: Spring — Adding OpenAPI validation to MockMvc tests](https://dev.to/janux_de/spring-adding-openapi-validation-to-mockmvc-tests-2p21))
- There's also a WireMock-based module (`swagger-request-validator-wiremock`) for validating third-party/webhook-delivery traffic — worth flagging given this project's name (webhooks) and its likely need to assert outbound webhook payloads conform to a schema too, not just inbound API responses.
- Setup cost: low — one test-scope Maven dependency, point it at the repo's `openapi.yaml` (classpath resource or file path), wrap `MockMvc` with the validating `ResultMatcher`. No extra CI infrastructure beyond what's already there (JUnit 5, Testcontainers for Postgres/Kafka are untouched).
- CI-friendliness: runs entirely inside `mvn test`, deterministic, no network calls, no extra services — plays well alongside Testcontainers-based integration tests already in the suite.

### 3. openapi4j — ruled out (abandoned)

- Repository was **archived by the owner on 2021-07-09** ("read-only," owner cited insufficient time to maintain it). No updates since. Not viable for a project that wants to avoid dead dependencies. ([GitHub: openapi4j/openapi4j](https://github.com/openapi4j/openapi4j))

### 4. Confluent/Optic, Dredd, Schemathesis — not JVM-native, higher friction here

- **Schemathesis** and **Dredd** are property-based / fuzzing-style spec-conformance testers that run as external CLI processes against a live server (they send generated requests derived from the spec and check responses). They're strong for API-only repos with a language-agnostic CI step, but they don't integrate into `mvn test` or JUnit assertions — they'd need a running app instance plus a separate CI stage/process orchestration, which is more infra than this project wants layered on top of Testcontainers. Better suited to catching a wider or fuzz-driven edge-case surface than to the "check my controller's JSON matches the schema" need described in the ticket.
- **Optic** (formerly Optic CLI, now more focused on API diffing/change-review workflows) is oriented at diffing traffic against a spec over time / PR-based API change review, not at being a JUnit-embeddable assertion library. Wrong shape for a portfolio-scale test suite that wants inline pass/fail per test.
- None of these beat swagger-request-validator's fit for "embed directly in existing JVM/MockMvc tests with minimal setup."

## Recommendation

**Use `atlassian/openapi-request-validator` (artifact family formerly `swagger-request-validator`), specifically the `swagger-request-validator-mockmvc` module**, as the contract-test layer sitting alongside the existing JUnit 5 + Mockito + Testcontainers stack. It is the only evaluated option that is (a) actively maintained and Spring Boot 3 / Java 21-compatible, (b) purpose-built to validate real request/response bodies against an *existing* `openapi.yaml` rather than generating its own contract format, and (c) embeddable directly into `MockMvc`-based tests with no extra CI infrastructure.

### How you'd wire it in

```xml
<!-- pom.xml, test scope -->
<dependency>
    <groupId>com.atlassian.oai</groupId>
    <artifactId>swagger-request-validator-mockmvc</artifactId>
    <version>2.44.1</version>
    <scope>test</scope>
</dependency>
```

```java
// src/test/java/.../contract/OpenApiValidationSupport.java
class OpenApiValidationSupport {
    static final OpenApiValidationResultMatcher OPENAPI =
        OpenApiValidationResultMatcher.wrap(
            OpenApiValidator.createFor(new ClassPathResource("openapi.yaml").getURL().toString())
        );
}
```

```java
@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerContractTest {

    @Autowired MockMvc mockMvc;

    @Test
    void createWebhookMatchesOpenApiContract() throws Exception {
        mockMvc.perform(post("/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateWebhookPayload()))
            .andExpect(status().isCreated())
            .andExpect(OpenApiValidationSupport.OPENAPI);   // fails the test if body/headers violate openapi.yaml
    }
}
```

- Point the validator at the committed `openapi.yaml` (e.g. copied to `src/test/resources` or loaded from the `contract/openapi-v1-draft` branch's file once merged) as the single source of truth.
- Because it's a `ResultMatcher`, it composes with existing `.andExpect(status()...)`/`.andExpect(jsonPath()...)` assertions — no separate test runner, no new build phase, and it fails fast inside the normal `mvn test` / CI pipeline the project already has.
- For outbound webhook delivery payloads (this project's actual product), the same library's `swagger-request-validator-wiremock` module can validate the JSON this service sends to subscriber endpoints, reusing the same `openapi.yaml` if outbound payloads are also modeled there.
