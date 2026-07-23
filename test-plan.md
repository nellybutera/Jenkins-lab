# API Test Plan

## 1. Overview

**Project:** Automation Test Execution with Jenkins CI/CD
**API Under Test:** FakeStoreAPI (`https://fakestoreapi.com`)
**Test Framework:** REST Assured + JUnit 5
**Reporting:** Allure Reports (published in Jenkins via HTML Publisher)
**CI/CD:** Jenkins (containerized, `jenkins/jenkins:lts` + Blue Ocean), Docker
**Date:** 2026-07-23

---

## 2. Scope

### In Scope
- GET, POST, PUT, DELETE operations on the `/products` resource
- GET on `/products/categories`
- Status code validation
- Response body validation (field presence, values, types)
- Response header validation (Content-Type)
- JSON schema validation for a single product
- Query parameter filtering (`limit`)

### Out of Scope
- `/carts`, `/users`, `/auth/login` resources
- Authentication/authorization flows
- Data persistence across requests (FakeStoreAPI simulates writes; nothing is actually persisted)
- Performance/load testing
- `HEAD` requests — see Section 8, FakeStoreAPI's WAF returns 403 on `HEAD /products`; content-type is instead
  asserted via the `GET` responses themselves

---

## 3. Test Approach

Tests are implemented using:
- **REST Assured** for HTTP request construction and response assertion
- **JUnit 5** as the test runner
- **Allure** annotations (`@Epic`, `@Feature`, `@Severity`, `@Description`) for structured reporting
- **JSON Schema Validator** (bundled with REST Assured) for schema-level validation

All test classes extend `BaseTest`, which configures the base URI (`https://fakestoreapi.com`) and attaches the
Allure REST Assured filter plus request/response logging filters for CI diagnostics.

---

## 4. Test Cases

### 4.1 GET Requests

| ID | Test Case | Expected Result |
|----|-----------|----------------|
| GET-01 | GET `/products` — fetch default page | 200, `application/json`, exactly 20 items (verified default page size) |
| GET-02 | GET `/products?limit=5` — filter by limit | 200, exactly 5 items returned |
| GET-03 | GET `/products/1` — fetch single product | 200, `id=1`, passes `product-schema.json` |
| GET-04 | GET `/products/99999` — non-existent id | **200 with empty body** (documented quirk, see Section 8 — not a 404) |
| GET-05 | GET `/products/categories` | 200, array includes `electronics`, `jewelery`, `men's clothing`, `women's clothing` |

### 4.2 POST Requests

| ID | Test Case | Expected Result |
|----|-----------|----------------|
| POST-01 | POST `/products` with full payload | 201, echoes submitted `title`/`price`/`category`, `id` present (not pinned to a specific value) |
| POST-02 | POST `/products` with partial payload | 201, `id` present in response |

### 4.3 PUT Requests

| ID | Test Case | Expected Result |
|----|-----------|----------------|
| PUT-01 | PUT `/products/1` with full payload | 200, echoes back updated `title`/`category` |
| PUT-02 | PUT `/products/5` with partial payload | 200, echoes updated `title` |

### 4.4 DELETE Requests

| ID | Test Case | Expected Result |
|----|-----------|----------------|
| DEL-01 | DELETE `/products/1` | **200 with the full deleted product body** (documented quirk, see Section 8 — not an empty `{}`) |
| DEL-02 | DELETE `/products/10` | 200, body `id=10` |

---

## 5. Test Data

FakeStoreAPI is a public mock API seeded with ~20 static products (ids 1–20):
- `GET /products` default page size is 20 (verified via live probe, not assumed)
- `POST`/`PUT`/`DELETE` are simulated echoes — nothing is persisted between requests
- `id` returned by `POST` is not deterministic across runs, so tests assert presence (`notNullValue()`), never a
  hardcoded value

---

## 6. Test Environment

| Component | Details |
|-----------|---------|
| Language | Java 17 |
| Build tool | Maven 3.9.x |
| Test framework | JUnit 5.12.2 |
| HTTP library | REST Assured 5.5.2 |
| Report tool | Allure 2.29.1 |
| CI/CD | Jenkins (Docker, `jenkins/jenkins:lts` + Blue Ocean) |
| Container | Docker (`maven:3.9-eclipse-temurin-17`) |
| API | https://fakestoreapi.com |

---

## 7. How to Run

**Locally:**
```bash
mvn test
mvn allure:serve    # opens Allure report in browser
```

**Via Docker:**
```bash
docker build -t jenkins-lab-tests .
docker run --rm jenkins-lab-tests
```

**Via Jenkins:**
Pipeline defined in `Jenkinsfile` — see `README.md` for full setup (Docker Compose, plugin list, webhook, Slack
notification).

---

## 8. Risks and Assumptions

| Risk | Mitigation |
|------|-----------|
| FakeStoreAPI does not follow standard REST semantics — `GET` on a missing id returns 200 (empty body) instead of 404, and `DELETE` echoes the full original object instead of an empty body | These are asserted as *observed, verified behavior* (live-probed before writing assertions), not defects in the test suite. Documented explicitly here so a reviewer reads them as intentional, not a misunderstanding of REST. |
| `HEAD /products` returns 403 (Cloudflare WAF blocks `HEAD`) | Suite uses `GET` exclusively; header assertions are made against `GET` responses. |
| FakeStoreAPI is a public third-party service with known intermittent downtime | Tests have no control over uptime. Confirm the API is reachable (`curl -I https://fakestoreapi.com/products`) immediately before any live demo or pipeline run. If the API is down, the pipeline will legitimately go red — this is an external dependency risk, not a suite defect. |
| Write operations are not persisted | Assertions target simulated echo responses only, never persisted state across requests. |

---

## 9. Deliverables

- [x] Test plan (this document)
- [x] Maven project with REST Assured + JUnit 5
- [x] Test cases for GET, POST, PUT, DELETE
- [x] JSON schema validation
- [x] Allure reporting integration
- [x] Dockerfile for containerization
- [ ] Jenkins pipeline (Jenkinsfile, Docker Compose, Blue Ocean, webhook, Slack notification) — see `README.md`
