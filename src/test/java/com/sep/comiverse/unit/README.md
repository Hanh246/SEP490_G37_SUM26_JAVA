# L1 Unit Tests

Use this package for isolated JUnit 5 and Mockito tests. Tests in this level must
not start the Spring context or access real databases, files, HTTP services, or
third-party providers.

Place each test beside its production responsibility:

- `controller`: direct controller behavior with mocked dependencies.
- `fixture`: deterministic test-data builders shared by L1 tests.
- `plugin.crud`: CRUD plugin behavior.
- `plugin.mapper`: entity and DTO mapping behavior.
- `service`: service business rules.
- `service.scheduler`: isolated scheduler delegation or calculation logic.
- `util`: pure utility behavior.

Use the `Test` class suffix.
