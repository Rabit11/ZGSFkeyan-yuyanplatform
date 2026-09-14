-- 申报岗位人员持久化；已部署库执行一次。
CREATE TABLE IF NOT EXISTS `proj_declaration_post` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `declaration_id` BIGINT      NOT NULL,
  `group_code`     VARCHAR(32) NOT NULL COMMENT 'TECH/EXPERT/MGMT/FIN',
  `role_key`       VARCHAR(64) NOT NULL COMMENT '前端申报岗位字段键',
  `role_code`      VARCHAR(64) NOT NULL COMMENT '项目团队标准岗位编码',
  `role_name`      VARCHAR(64) DEFAULT NULL,
  `user_name`      VARCHAR(64) DEFAULT NULL,
  `employee_no`    VARCHAR(64) DEFAULT NULL,
  `sort`           INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_declaration_role` (`declaration_id`, `role_key`),
  KEY `idx_declaration` (`declaration_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '申报岗位人员（全周期流程人员来源）';
