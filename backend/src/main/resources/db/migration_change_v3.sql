-- Additive change-module storage. Existing controlled single-field applications use a read fallback.
CREATE TABLE IF NOT EXISTS proj_change_item (
  change_id BIGINT NOT NULL,
  ordinal INT NOT NULL,
  target_key VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  category VARCHAR(64) NOT NULL,
  before_value TEXT NOT NULL,
  after_value TEXT NOT NULL,
  PRIMARY KEY(change_id, ordinal),
  UNIQUE KEY uk_change_target(change_id, target_key, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS proj_change_round (
  change_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  actor_name VARCHAR(128) NOT NULL,
  snapshot_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(change_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
