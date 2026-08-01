# L2 Integration Tests

Use this package when a test verifies multiple real application components
together. Typical tools are Spring Boot Test, MockMvc, Spring Security Test, and
an isolated test database.

- `api`: HTTP endpoints through the Spring web and validation layers.
- `repository`: entity mapping, queries, constraints, and transactions.
- `scheduler`: real job/service execution followed by persisted-state checks.
- `security`: authentication, authorization, CORS, and filter-chain behavior.
- `support`: L2-only configuration, database seeding, and test helpers.

External mail, payment, storage, and other network services must remain stubbed.
Use the `IT` class suffix so L2 tests run only when explicitly selected.

Integration tests must use the `integration` profile and the isolated database
properties in `src/test/resources/integration`. Never point L2 tests at the
development, staging, or production database.

Annotate every L2 class with `@ComiverseIntegrationTest`; it activates the safe
profile and mandatory test-database properties consistently for the whole team.
