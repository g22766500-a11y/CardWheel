-- Edit this template privately before running it as a DB administrator.
-- Cellular clients change source IPs, so this example permits the account from any host.
CREATE USER 'cardwheel_app'@'%' IDENTIFIED BY 'REPLACE_WITH_A_LONG_UNIQUE_PASSWORD';
GRANT SELECT, INSERT, UPDATE ON cardwheel.cardwheel_backup TO 'cardwheel_app'@'%';
-- Optional, ONLY when the DB has TLS enabled and the app TLS switch is ON:
-- ALTER USER 'cardwheel_app'@'%' REQUIRE SSL;
