-- 交付物绑定里程碑节点，并支持编制期清单附件
ALTER TABLE `proj_deliverable`
  ADD COLUMN `milestone_id` BIGINT DEFAULT NULL COMMENT '对应里程碑节点' AFTER `project_id`,
  ADD COLUMN `file_name` VARCHAR(255) DEFAULT NULL COMMENT '清单附件或证明文件名' AFTER `achievement_no`,
  ADD COLUMN `file_url` VARCHAR(512) DEFAULT NULL COMMENT '清单附件或证明地址' AFTER `file_name`;

ALTER TABLE `proj_deliverable` ADD KEY `idx_ms` (`milestone_id`);
