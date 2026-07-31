# L2 Test Resources

Store isolated integration configuration, SQL seed data, JSON fixtures, and
stub-provider responses here. Do not store real credentials or production data.

`application-integration.properties` requires these environment variables:

- `TEST_DB_URL`: JDBC URL for an isolated PostgreSQL test database.
- `TEST_DB_USERNAME`: test database username.
- `TEST_DB_PASSWORD`: test database password.

Optional Redis overrides are `TEST_REDIS_HOST` and `TEST_REDIS_PORT`. Hibernate
uses `validate`, so L2 tests cannot create, update, or drop the schema.
