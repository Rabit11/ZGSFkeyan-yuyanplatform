CREATE TABLE IF NOT EXISTS proj_supplement_section (
 project_id BIGINT NOT NULL, section_key VARCHAR(40) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, batch_no INT NOT NULL DEFAULT 1,
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT', payload LONGTEXT NOT NULL,
 updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (project_id, section_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS proj_supplement_history (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_id BIGINT NOT NULL, section_key VARCHAR(40) NOT NULL,
 batch_no INT NOT NULL, version BIGINT NOT NULL, action VARCHAR(32) NOT NULL,
 payload LONGTEXT NOT NULL, actor_id BIGINT NOT NULL, actor_name VARCHAR(160), opinion TEXT,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_supplement_history(project_id, section_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS proj_supplement_file (
 id VARCHAR(36) PRIMARY KEY, project_id BIGINT NOT NULL, section_key VARCHAR(40) NOT NULL,
 material_code VARCHAR(120) NOT NULL, row_id VARCHAR(36), object_key VARCHAR(1024) NOT NULL,
 file_name VARCHAR(512) NOT NULL, file_size BIGINT NOT NULL, content_type VARCHAR(160),
 file_version INT NOT NULL, uploaded_by BIGINT NOT NULL, uploader_name VARCHAR(160),
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_supplement_file(project_id, section_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
