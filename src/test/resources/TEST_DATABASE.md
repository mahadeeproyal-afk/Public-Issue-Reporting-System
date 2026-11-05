# Isolated integration database

Integration tests run with `publicissues.testDatabase=true`. Maven Surefire
sets this property, and `DBConnection` then loads `test-db.properties`, requires
the exact `civic_issue_test_db` URL, reads credentials only from
`CIVIC_ISSUE_TEST_DB_USERNAME` and `CIVIC_ISSUE_TEST_DB_PASSWORD`, and verifies
the connected catalog before returning a connection. It does not fall back to
the production configuration in test mode.

Provision the schema and synthetic fixtures with
`sql/setup_civic_issue_test_db.sql`. The script creates tables only in
`civic_issue_test_db` and seeds only clearly marked test users/departments. Do
not run it with another database selected. Tests create their own issues and
remove only those exact issue IDs and their history rows in `@AfterEach`.
The seeded password field is a non-BCrypt sentinel; these fixtures cannot be
used for password authentication. Current integration tests authorize by the
database role and do not perform password verification.

Set the two test credential environment variables to a MySQL account that can
read, insert, update, and delete rows in `civic_issue_test_db`. Do not use or
copy production credentials into this test configuration. The test account
does not need privileges on `civic_issue_db` or schema-management privileges.

Run with `mvn test`. If test credentials or the test database are missing, the
tests fail instead of falling back to production.
