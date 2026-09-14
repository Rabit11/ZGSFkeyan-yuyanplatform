CREATE TABLE IF NOT EXISTS proj_supplement_channel_template (
 channel_code VARCHAR(64) NOT NULL,
 version BIGINT NOT NULL,
 payload LONGTEXT NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(channel_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
