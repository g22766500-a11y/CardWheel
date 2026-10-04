-- Run with a schema administration account; no API/Docker container is required.
CREATE DATABASE IF NOT EXISTS cardwheel CHARACTER SET utf8mb4;
USE cardwheel;
CREATE TABLE IF NOT EXISTS cardwheel_backup (
    backup_id INT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL,
    saved_at BIGINT NULL,
    payload LONGTEXT NOT NULL,
    CONSTRAINT cardwheel_valid_payload CHECK (JSON_VALID(payload))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- An empty server has no row. The first backup creates row 1 atomically.
-- Existing backups created by the earlier API version are compatible.
