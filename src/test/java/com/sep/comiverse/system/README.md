# L3 System Tests

Use this package for black-box tests against a complete running ComiVerse
environment. L3 tests must interact through public HTTP or browser/mobile
interfaces and must not call production Java classes directly.

- `api`: complete HTTP-level system scenarios.
- `security`: externally observable authentication and authorization scenarios.
- `support`: environment configuration, API clients, and cleanup helpers.
- `workflow`: cross-role business journeys such as submit, review, assign,
  translate, publish, notify, and read.

Use environment variables for URLs and test credentials. Use the `ST` class
suffix so L3 tests are excluded from the default local unit-test run.
