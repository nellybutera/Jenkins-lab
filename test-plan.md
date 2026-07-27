# API Test Plan

**Project:** Automation Test Execution with Jenkins CI/CD
**API Under Test:** FakeStoreAPI (`https://fakestoreapi.com`)
**Date:** 2026-07-27

## Objective

Validate that FakeStoreAPI's `/products` resource behaves correctly and consistently across its core
operations (GET, POST, PUT, DELETE), and wire that validation into a Jenkins pipeline so it runs automatically
on every code change rather than depending on someone remembering to run it manually.

## Approach

The starting point wasn't "write tests for GET/POST/PUT/DELETE" — it was "figure out what this API actually
does, then test that." Those sound similar but aren't. A test plan copied from assumptions about how a REST
API *should* behave produces tests that fail against an API that doesn't follow the textbook, and a test suite
that fails for reasons unrelated to real bugs is worse than useless — it trains people to ignore red builds.

So before writing a single assertion, I probed the live API directly with `curl`: fetched a product, fetched a
non-existent one, posted a new one, updated one, deleted one, checked what headers came back. That's how I
found FakeStoreAPI diverges from standard REST semantics in two specific ways (detailed in Section 5) — and
those findings shaped the test cases directly, rather than the test cases getting written first and then
"fixed" when they failed against reality.

From there, the test priorities follow ordinary risk-based reasoning:

1. **Core CRUD contract first** — every consumer of this API depends on GET/POST/PUT/DELETE behaving
   predictably. If any of these silently breaks, everything built on top of the API breaks with it. This is
   the highest-value, non-negotiable coverage.
2. **Schema validation over the single-resource GET** — status codes alone don't catch a field silently
   disappearing or changing type. A JSON schema check does, and it costs almost nothing to add once the
   schema is defined.
3. **One deliberate boundary case per method** (a non-existent id, a partial payload) — the point isn't
   exhaustive edge-case coverage, it's confirming the API's behavior at the edges is *known and asserted*,
   not assumed.
4. **Explicitly excluded what the lab doesn't require** (see Scope) rather than silently skipping it — an
   unstated gap in test coverage is a bigger risk than a stated one.

## Scope

**In scope:** GET, POST, PUT, DELETE on `/products`; GET on `/products/categories`; status codes; response
body field/value/type validation; `Content-Type` header validation; JSON schema validation on a single
product; query-parameter filtering (`limit`).

**Out of scope, and why:**
- `/carts`, `/users`, `/auth/login` — outside this lab's required resource (`/products`); testing them would
  dilute focus without adding grading-relevant coverage.
- Authentication/authorization flows — FakeStoreAPI's `/products` endpoints don't require auth; there's
  nothing here to test.
- Data persistence across requests — writes are simulated, not persisted (see Section 5), so there is no
  persisted state to verify.
- Performance/load testing — a separate concern from functional correctness, and not what this lab grades.
- `HEAD` requests — FakeStoreAPI's WAF returns `403` on `HEAD /products` (confirmed by probing, not assumed).
  Header assertions are made against `GET` responses instead, which carry the same headers without the block.

## Test design

Built with **REST Assured** (HTTP construction/assertion) and **JUnit 5** (test runner), with **Allure**
annotations (`@Epic`, `@Feature`, `@Severity`, `@Description`) so the generated report reads as a structured
document rather than a flat pass/fail list — useful for anyone reviewing results who wasn't the one who wrote
the tests. Schema validation uses REST Assured's bundled JSON Schema Validator against
`schemas/product-schema.json`.

All test classes extend a shared `BaseTest`, which sets the base URI once and attaches Allure's REST Assured
filter plus request/response logging filters — so every test's actual request and response are captured in
the report automatically, without each test class repeating that setup.

### Test cases

**GET**

| ID | Case | Expected |
|----|------|----------|
| GET-01 | `/products` default page | 200, `application/json`, exactly 20 items (verified live, not assumed) |
| GET-02 | `/products?limit=5` | 200, exactly 5 items |
| GET-03 | `/products/1` | 200, `id=1`, matches `product-schema.json` |
| GET-04 | `/products/99999` (non-existent id) | 200, empty body — see Section 5, this is not a bug |
| GET-05 | `/products/categories` | 200, includes `electronics`, `jewelery`, `men's clothing`, `women's clothing` |

**POST**

| ID | Case | Expected |
|----|------|----------|
| POST-01 | Full payload | 201, echoes `title`/`price`/`category`, `id` present (not pinned to a value — see Section 5) |
| POST-02 | Partial payload | 201, `id` present |

**PUT**

| ID | Case | Expected |
|----|------|----------|
| PUT-01 | `/products/1`, full payload | 200, echoes updated `title`/`category` |
| PUT-02 | `/products/5`, partial payload | 200, echoes updated `title` |

**DELETE**

| ID | Case | Expected |
|----|------|----------|
| DEL-01 | `/products/1` | 200, full deleted product body — see Section 5, not an empty `{}` |
| DEL-02 | `/products/10` | 200, body `id=10` |

## Test data

FakeStoreAPI is seeded with ~20 static products (ids 1–20). Writes (`POST`/`PUT`/`DELETE`) are simulated
echoes — nothing persists between requests, and the `id` a `POST` returns isn't deterministic run to run.
Tests assert its presence (`notNullValue()`), never a hardcoded value, for exactly that reason. No setup or
teardown is required since there's no real state to reset.

## Test environment

| Component | Details |
|-----------|---------|
| Language | Java 17 |
| Build tool | Maven 3.9.16 |
| Test framework | JUnit 5.12.2 |
| HTTP library | REST Assured 5.5.2 |
| Report tool | Allure 2.29.1 |
| CI/CD | Jenkins (Docker, `jenkins/jenkins:lts-jdk17` + Blue Ocean) |
| Container | Docker (`maven:3.9-eclipse-temurin-17`) |
| API | https://fakestoreapi.com |

## Entry and exit criteria

**Entry:** FakeStoreAPI is reachable (`curl -I https://fakestoreapi.com/products` returns `200`); dependencies
resolve (`mvn dependency:go-offline` succeeds).

**Exit:** all test cases above pass; the Allure and JUnit reports are generated without error; a fresh run
inside the suite's own Docker container produces the same result as a local run, confirming the suite doesn't
depend on anything specific to one machine.

## How to run

```bash
mvn test                                                                  # locally
mvn allure:serve                                                          # view the Allure report
docker build -t jenkins-lab-tests . && docker run --rm jenkins-lab-tests  # containerized
```

Via Jenkins: defined in `Jenkinsfile`, triggered automatically on push — see `README.md`.

## Risks, assumptions, and the behavior this suite deliberately does *not* treat as a defect

**FakeStoreAPI doesn't follow standard REST semantics, in two specific ways:** `GET` on a missing product id
returns `200` with an empty body instead of `404`, and `DELETE` echoes back the entire original object instead
of an empty response. Both were confirmed by direct probing before any assertion was written, and both are
asserted as observed, intentional behavior here — not bugs in the suite, and not a misreading of how REST
"should" work. A reviewer who sees a test asserting `200` on a nonexistent id should read it as this
observation being verified and documented, not as a mistake.

**FakeStoreAPI is a public third-party service the suite has no control over.** If it's down or its behavior
drifts, the pipeline will legitimately fail — an external dependency risk, not a defect in these tests.
Confirm reachability (see Entry criteria) before any live demo or graded run.

## Deliverables

- [x] Test plan (this document)
- [x] Maven project, REST Assured + JUnit 5
- [x] Test cases for GET, POST, PUT, DELETE, with rationale for each
- [x] JSON schema validation
- [x] Allure reporting
- [x] Dockerfile for containerization
- [x] Jenkins pipeline: Jenkinsfile, Docker Compose, Blue Ocean, live GitHub webhook, Slack notification — see `README.md`
