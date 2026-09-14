-- 实施阶段增量 v2（2026-09-14）：里程碑基线/滞后原因/逻辑删除、变更回写字段、基本信息审批草稿、预警按工号投递
-- 已部署库执行一次；全新环境 schema.sql 已包含同等结构。
SET NAMES utf8mb4;

ALTER TABLE `proj_milestone`
  ADD COLUMN `baseline_plan_date` DATE DEFAULT NULL COMMENT '基线计划日期（年度清单审核存档时固化）' AFTER `plan_date`,
  ADD COLUMN `delay_count` INT NOT NULL DEFAULT 0 COMMENT '延期变更次数' AFTER `baseline_plan_date`,
  ADD COLUMN `lag_measure` VARCHAR(1000) DEFAULT NULL COMMENT '滞后处理措施' AFTER `lag_reason`,
  ADD COLUMN `audit_by` VARCHAR(64) DEFAULT NULL COMMENT '最近审核人' AFTER `lag_measure`,
  ADD COLUMN `audit_at` DATETIME DEFAULT NULL COMMENT '最近审核时间' AFTER `audit_by`,
  ADD COLUMN `audit_opinion` VARCHAR(1000) DEFAULT NULL COMMENT '最近审核意见' AFTER `audit_at`,
  ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`,
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER `updated_at`;

ALTER TABLE `proj_change`
  ADD COLUMN `milestone_id` BIGINT DEFAULT NULL COMMENT '延期变更对应里程碑' AFTER `after_value`,
  ADD COLUMN `new_plan_date` DATE DEFAULT NULL COMMENT '延期变更新计划日期' AFTER `milestone_id`,
  ADD COLUMN `audit_by` VARCHAR(64) DEFAULT NULL AFTER `applicant`,
  ADD COLUMN `audit_at` DATETIME DEFAULT NULL AFTER `audit_by`,
  ADD COLUMN `audit_opinion` VARCHAR(1000) DEFAULT NULL AFTER `audit_at`,
  ADD COLUMN `audit_trail` MEDIUMTEXT DEFAULT NULL COMMENT '审批记录 JSON' AFTER `audit_opinion`,
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 AFTER `updated_at`,
  ADD KEY `idx_milestone` (`milestone_id`);

CREATE TABLE IF NOT EXISTS `proj_basic_draft` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`     BIGINT      NOT NULL,
  `payload`        MEDIUMTEXT  DEFAULT NULL COMMENT '待审批的基本信息快照 JSON（含参研单位、团队）',
  `status`         VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  `flow_node`      VARCHAR(64) DEFAULT NULL COMMENT '当前节点编码 PROJECT_LEADER/UNIT_TECH/UNIT_LEADER/HQ',
  `flow_node_name` VARCHAR(128) DEFAULT NULL,
  `submitted_by`   VARCHAR(64) DEFAULT NULL,
  `submitted_no`   VARCHAR(32) DEFAULT NULL,
  `submitted_at`   DATETIME    DEFAULT NULL,
  `audit_trail`    MEDIUMTEXT  DEFAULT NULL COMMENT '审批记录 JSON',
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目基本信息审批草稿';

ALTER TABLE `sys_warning`
  ADD COLUMN `receiver_nos` VARCHAR(500) DEFAULT NULL COMMENT '接收人工号，逗号分隔' AFTER `receiver`,
  ADD COLUMN `read_nos` VARCHAR(1000) DEFAULT NULL COMMENT '已读人工号，逗号分隔' AFTER `is_read`;

-- 清理 schema.sql 遗留的非花名册演示账号
DELETE FROM `sys_user_role` WHERE `user_id` IN (SELECT id FROM (SELECT id FROM `sys_user` WHERE `employee_no` LIKE 'E1%') t);
DELETE FROM `sys_user` WHERE `employee_no` LIKE 'E1%';
