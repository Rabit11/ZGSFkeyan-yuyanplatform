-- 增量：人员权限字段 + 项目岗位办理权限矩阵
-- 适用于已部署库；若列已存在请跳过对应 ALTER。全新环境请直接执行 schema.sql

ALTER TABLE `sys_user` ADD COLUMN `dept_name` VARCHAR(128) DEFAULT NULL COMMENT '主部门' AFTER `org_name`;
ALTER TABLE `sys_user` ADD COLUMN `identity` VARCHAR(64) DEFAULT NULL COMMENT '登录任职身份标签' AFTER `mobile`;
ALTER TABLE `sys_user` ADD COLUMN `identity_code` VARCHAR(32) DEFAULT NULL COMMENT '身份编码' AFTER `identity`;
ALTER TABLE `sys_user` ADD COLUMN `project_post` VARCHAR(64) DEFAULT NULL COMMENT '常用项目岗位展示名' AFTER `identity_code`;
ALTER TABLE `sys_user` ADD COLUMN `rank_title` VARCHAR(64) DEFAULT NULL COMMENT '职称' AFTER `project_post`;
ALTER TABLE `sys_user` ADD COLUMN `data_scope` VARCHAR(32) DEFAULT 'SELF' COMMENT '数据范围' AFTER `rank_title`;
ALTER TABLE `sys_user` ADD COLUMN `finish_auth` TINYINT NOT NULL DEFAULT 0 COMMENT '项目内办结权限' AFTER `data_scope`;
ALTER TABLE `sys_user` ADD COLUMN `form_maint_scope` VARCHAR(16) DEFAULT NULL COMMENT '表单维护范围' AFTER `finish_auth`;
ALTER TABLE `sys_user` ADD COLUMN `form_maint_channels` VARCHAR(500) DEFAULT NULL COMMENT '指定渠道' AFTER `form_maint_scope`;
ALTER TABLE `sys_user` ADD COLUMN `form_maint_types` VARCHAR(500) DEFAULT NULL COMMENT '指定项目类型' AFTER `form_maint_channels`;
ALTER TABLE `sys_user` ADD COLUMN `declare_result_access` TINYINT NOT NULL DEFAULT 0 COMMENT '立项审批结果权限' AFTER `form_maint_types`;

CREATE TABLE IF NOT EXISTS `sys_post_permission` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `post_code`  VARCHAR(64) NOT NULL COMMENT '项目岗位编码',
  `perm_code`  VARCHAR(64) NOT NULL COMMENT '办理权限编码',
  `enabled`    TINYINT     NOT NULL DEFAULT 1,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_perm` (`post_code`, `perm_code`),
  KEY `idx_post` (`post_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目岗位办理权限矩阵';
