-- 表单维护 / 台账双向同步：项目来源与扩展字段（MySQL 8）
-- 若列已存在会报错，部署脚本按列探测后执行

ALTER TABLE `proj_info`
  ADD COLUMN `data_source` VARCHAR(32) NOT NULL DEFAULT 'PLATFORM'
    COMMENT 'PLATFORM平台同步/FORM_MAINT表单维护导入' AFTER `warn_color`;

ALTER TABLE `proj_info`
  ADD COLUMN `national_fund` DECIMAL(18,2) DEFAULT 0 COMMENT '国拨经费（万元）' AFTER `total_fund`,
  ADD COLUMN `self_fund` DECIMAL(18,2) DEFAULT 0 COMMENT '自筹经费（万元）' AFTER `national_fund`,
  ADD COLUMN `manage_org_name` VARCHAR(128) DEFAULT NULL COMMENT '管理/需求单位' AFTER `lead_org_name`,
  ADD COLUMN `bureau_office` VARCHAR(128) DEFAULT NULL COMMENT '司局/处室' AFTER `manage_org_name`,
  ADD COLUMN `project_type` VARCHAR(128) DEFAULT NULL COMMENT '项目类型' AFTER `bureau_office`,
  ADD COLUMN `major1` VARCHAR(64) DEFAULT NULL COMMENT '一级专业' AFTER `project_type`,
  ADD COLUMN `major2` VARCHAR(64) DEFAULT NULL COMMENT '二级专业' AFTER `major1`,
  ADD COLUMN `owner_name` VARCHAR(64) DEFAULT NULL COMMENT '项目负责人' AFTER `major2`,
  ADD COLUMN `accept_status` VARCHAR(64) DEFAULT NULL COMMENT '验收状态' AFTER `owner_name`;

UPDATE `proj_info` SET `data_source` = 'PLATFORM' WHERE `data_source` IS NULL OR `data_source` = '';
