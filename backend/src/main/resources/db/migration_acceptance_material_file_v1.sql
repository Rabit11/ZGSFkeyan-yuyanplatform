ALTER TABLE `proj_acceptance_item`
  ADD COLUMN `file_name` VARCHAR(255) DEFAULT NULL COMMENT '文件名' AFTER `file_url`,
  ADD COLUMN `file_size` BIGINT DEFAULT NULL COMMENT '文件大小' AFTER `file_name`,
  ADD COLUMN `uploaded_by` VARCHAR(64) DEFAULT NULL COMMENT '上传人' AFTER `file_size`,
  ADD COLUMN `uploaded_at` DATETIME DEFAULT NULL COMMENT '上传时间' AFTER `uploaded_by`;

ALTER TABLE `proj_acceptance`
  ADD COLUMN `current_node` VARCHAR(64) DEFAULT NULL COMMENT '当前审批节点' AFTER `status`,
  ADD COLUMN `latest_opinion` VARCHAR(1000) DEFAULT NULL COMMENT '最近流程意见' AFTER `current_node`,
  ADD COLUMN `latest_process_at` DATETIME DEFAULT NULL COMMENT '最近流程处理时间' AFTER `latest_opinion`;
