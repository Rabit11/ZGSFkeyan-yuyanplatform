-- 评估检查模块升级（任务④）：仅涉及 proj_evaluation 表
-- 新增：渠道编码、评审结论、整改说明/完成标志、提交/审核人及时间
-- 状态机扩展：DRAFT/SUBMITTED/UNIT_OK/DONE/RECTIFYING/REJECTED
-- 佐证材料复用通用表 proj_material（biz_type='EVALUATION'），无需改结构
-- MySQL 8+：逐句执行；若列/注释已存在可忽略报错后继续
SET NAMES utf8mb4;

ALTER TABLE `proj_evaluation` ADD COLUMN `channel_code` VARCHAR(64) DEFAULT NULL COMMENT '项目渠道编码（审批链差异）' AFTER `report_file`;
ALTER TABLE `proj_evaluation` ADD COLUMN `conclusion` VARCHAR(2000) DEFAULT NULL COMMENT '评审结论' AFTER `channel_code`;
ALTER TABLE `proj_evaluation` ADD COLUMN `rectify_note` VARCHAR(2000) DEFAULT NULL COMMENT '整改说明' AFTER `conclusion`;
ALTER TABLE `proj_evaluation` ADD COLUMN `rectify_done` TINYINT NOT NULL DEFAULT 0 COMMENT '整改是否完成 0否/1是' AFTER `rectify_note`;
ALTER TABLE `proj_evaluation` ADD COLUMN `submit_by` VARCHAR(64) DEFAULT NULL AFTER `rectify_done`;
ALTER TABLE `proj_evaluation` ADD COLUMN `audit_by` VARCHAR(64) DEFAULT NULL AFTER `submit_by`;
ALTER TABLE `proj_evaluation` ADD COLUMN `audit_at` DATETIME DEFAULT NULL AFTER `audit_by`;

-- 状态机扩展说明（列本身无需改类型）
ALTER TABLE `proj_evaluation`
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
  COMMENT 'DRAFT/SUBMITTED/UNIT_OK/DONE/RECTIFYING/REJECTED';

-- 历史数据状态平滑迁移：旧 PENDING → DRAFT
UPDATE `proj_evaluation` SET `status` = 'DRAFT' WHERE `status` = 'PENDING';
