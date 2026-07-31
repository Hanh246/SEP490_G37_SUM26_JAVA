# ComiVerse Backend Test Structure

This structure follows the L1, L2, and L3 definitions in the workspace document
`../docs/SEP490_G37_Report_5.0_TestPlan.md` and mirrors the level-first layout
used by the frontend test suite.

## Directory layout

```text
src/test/
|-- java/com/sep/comiverse/
|   |-- unit/                       # L1: isolated tests
|   |   |-- controller/
|   |   |-- fixture/
|   |   |-- plugin/
|   |   |   |-- crud/
|   |   |   `-- mapper/
|   |   |-- service/
|   |   |   `-- scheduler/
|   |   `-- util/
|   |-- integration/                # L2: components working together
|   |   |-- api/
|   |   |-- repository/
|   |   |-- scheduler/
|   |   |-- security/
|   |   `-- support/
|   `-- system/                     # L3: deployed-application flows
|       |-- api/
|       |-- security/
|       |-- support/
|       `-- workflow/
`-- resources/
    |-- unit/
    |-- integration/
    `-- system/
```

All existing automated tests are classified in the level-first packages above.
New tests must use the matching package and class suffix so Maven selects the
correct execution phase.

## Level rules

### L1 - Unit

- Test one class or small unit in isolation.
- Mock repositories, gateways, mail, storage, clocks, and other collaborators.
- Do not start Spring, connect to a real database, call HTTP endpoints, or use
  external services.
- Put reusable object builders in `unit.fixture`.
- Class suffix: `Test`, for example `NotificationServiceTest`.

### L2 - Integration

- Verify two or more real application components together.
- Use Spring Boot Test, MockMvc, repositories, security filters, or an isolated
  test database when the scenario requires them.
- Scheduler tests belong to L2 when they invoke the real job/service and verify
  persisted state, as required by the Test Plan.
- Stub external payment, mail, cloud storage, and other network providers.
- Class suffix: `IT`, for example `ForumNotificationIT`.

### L3 - System

- Exercise a complete business flow through the running application from the
  outside, using HTTP or a browser/mobile client.
- Do not import or invoke production controllers, services, or repositories
  directly.
- Read base URLs and test credentials from environment variables. Never commit
  credentials or tokens.
- Keep environment setup and API clients in `system.support`.
- Class suffix: `ST`, for example `ReaderRegistrationST`.

## Naming and traceability

- Add the requirement or functional-test ID to `@DisplayName`, for example
  `@DisplayName("FT-01 - valid credentials return an access token")`.
- Use a Java-safe method name in the Test Plan format:
  `ft01_validCredentials_returnsAccessToken`.
- Keep one scenario per test method and follow Arrange, Act, Assert.
- Keep test data deterministic. Tests must not depend on execution order.

## Running tests

From `SEP490_G37_SUM26_JAVA`:

```powershell
# L1 only
.\mvnw.cmd clean test

# L1 followed by L2
.\mvnw.cmd clean verify

# L1, L2, and L3, with the required system environment running
.\mvnw.cmd clean verify -Psystem-tests
```

Surefire runs L1 classes ending in `Test`. Failsafe runs L2 classes ending in
`IT` during `verify`; the `system-tests` profile adds L3 classes ending in `ST`.
These boundaries keep environment-dependent tests out of the default unit-test
run. Each test level should store its own fixtures and configuration under the
matching `src/test/resources` directory.

Before running L2, provide `TEST_DB_URL`, `TEST_DB_USERNAME`, and
`TEST_DB_PASSWORD` for an isolated PostgreSQL database. The integration profile
uses `ddl-auto=validate` and must never target a deployed ComiVerse database.
