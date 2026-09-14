-- 已有库增量：系统备份回滚
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_backup` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `backup_no`     VARCHAR(64)  NOT NULL               COMMENT '备份编号 BK+时间戳',
  `trigger_type`  VARCHAR(32)  NOT NULL               COMMENT 'MANUAL/SCHEDULED/PRE_RESTORE/PRE_RISK',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
  `remark`        VARCHAR(255) DEFAULT NULL,
  `file_path`     VARCHAR(512) DEFAULT NULL           COMMENT '本地 gzip 路径',
  `object_key`    VARCHAR(512) DEFAULT NULL           COMMENT 'MinIO 副本 objectKey',
  `file_size`     BIGINT       DEFAULT NULL,
  `table_count`   INT          DEFAULT NULL,
  `row_count`     INT          DEFAULT NULL,
  `object_count`  INT          DEFAULT NULL           COMMENT '附件对象清单条数',
  `checksum`      VARCHAR(64)  DEFAULT NULL,
  `error_msg`     VARCHAR(1000) DEFAULT NULL,
  `created_by`    VARCHAR(64)  DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at`   DATETIME     DEFAULT NULL,
  `restored_at`   DATETIME     DEFAULT NULL,
  `restored_by`   VARCHAR(64)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_backup_no` (`backup_no`),
  KEY `idx_created` (`created_at`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统备份快照';
