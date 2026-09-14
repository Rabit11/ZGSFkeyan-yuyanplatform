-- 项目申报表补齐列表/表单字段（责任单位、专业等）
-- MySQL 8+：逐列添加；若列已存在可忽略报错后继续
SET NAMES utf8mb4;

ALTER TABLE `proj_declaration` ADD COLUMN `need_approval` TINYINT NOT NULL DEFAULT 1 COMMENT '1需审批/0直接报备' AFTER `level_code`;
ALTER TABLE `proj_declaration` ADD COLUMN `goal` VARCHAR(2000) DEFAULT NULL AFTER `need_approval`;
ALTER TABLE `proj_declaration` ADD COLUMN `apply_fund` DECIMAL(14,2) DEFAULT NULL COMMENT '申报经费(万元)' AFTER `goal`;
ALTER TABLE `proj_declaration` ADD COLUMN `start_date` DATE DEFAULT NULL AFTER `apply_fund`;
ALTER TABLE `proj_declaration` ADD COLUMN `end_date` DATE DEFAULT NULL AFTER `start_date`;
ALTER TABLE `proj_declaration` ADD COLUMN `partner_orgs` VARCHAR(500) DEFAULT NULL AFTER `end_date`;
ALTER TABLE `proj_declaration` ADD COLUMN `major1` VARCHAR(64) DEFAULT NULL COMMENT '一级专业（附件1）' AFTER `partner_orgs`;
ALTER TABLE `proj_declaration` ADD COLUMN `major2` VARCHAR(64) DEFAULT NULL COMMENT '二级专业（附件1）' AFTER `major1`;
ALTER TABLE `proj_declaration` ADD COLUMN `demand_org` VARCHAR(128) DEFAULT NULL AFTER `major2`;
ALTER TABLE `proj_declaration` ADD COLUMN `lead_org_name` VARCHAR(128) DEFAULT NULL COMMENT '责任单位/牵头单位' AFTER `demand_org`;
ALTER TABLE `proj_declaration` ADD COLUMN `lead_work_content` VARCHAR(1000) DEFAULT NULL AFTER `lead_org_name`;
