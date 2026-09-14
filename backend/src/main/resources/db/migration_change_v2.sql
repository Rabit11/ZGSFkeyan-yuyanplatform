-- 项目变更专属扩展；不改变原表结构或其他模块，不改写历史变更。
CREATE TABLE IF NOT EXISTS proj_change_control (
  change_id BIGINT PRIMARY KEY,
  applicant_id BIGINT NOT NULL,
  request_key VARCHAR(64) NOT NULL,
  target_key VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  legal_reviewer_id BIGINT DEFAULT NULL,
  route_json TEXT NOT NULL,
  step_index INT NOT NULL DEFAULT 0,
  revision INT NOT NULL DEFAULT 0,
  applied_at DATETIME DEFAULT NULL,
  archive_ref VARCHAR(255) DEFAULT NULL,
  UNIQUE KEY uk_request (applicant_id,request_key),
  KEY idx_applicant (applicant_id), KEY idx_legal (legal_reviewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS proj_change_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  change_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_name VARCHAR(128) NOT NULL,
  opinion VARCHAR(2000) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_change_history (change_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS proj_change_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  change_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_size BIGINT NOT NULL,
  uploaded_by BIGINT NOT NULL,
  removed TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_change_attachment (change_id,removed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
