CREATE TABLE IF NOT EXISTS achv_transform_file (
    id VARCHAR(36) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transform_file_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
