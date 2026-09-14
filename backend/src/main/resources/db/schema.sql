-- ============================================================================
-- 科研项目信息化管理平台 —— 数据库结构（PolarDB MySQL 8.0 兼容）
-- 字符集 utf8mb4 / 排序规则 utf8mb4_general_ci
-- 命名规范：业务表前缀 proj_（项目域）、fund_（经费域）、hq_（总部域）
--           achv_（成果域）、biz_（流程域）、sys_（系统域）
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、系统域 sys_
-- ---------------------------------------------------------------------------

-- 组织机构（总部 / 二级单位 / 部门处室）
DROP TABLE IF EXISTS `sys_org`;
CREATE TABLE `sys_org` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '上级组织ID，0为根',
  `org_code`    VARCHAR(64)  NOT NULL               COMMENT '组织编码',
  `org_name`    VARCHAR(128) NOT NULL               COMMENT '组织名称',
  `org_type`    VARCHAR(32)  NOT NULL               COMMENT '类型：HQ总部/UNIT二级单位/DEPT部门/OFFICE处室',
  `sort`        INT          NOT NULL DEFAULT 0    COMMENT '排序',
  `status`      TINYINT      NOT NULL DEFAULT 1    COMMENT '状态：1启用 0停用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`org_code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '组织机构';

-- 用户
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `username`     VARCHAR(64)  NOT NULL               COMMENT '登录账号',
  `password`     VARCHAR(128) NOT NULL               COMMENT 'BCrypt 密码',
  `real_name`    VARCHAR(64)  NOT NULL               COMMENT '姓名',
  `employee_no`  VARCHAR(64)  DEFAULT NULL           COMMENT '工号',
  `org_id`       BIGINT       DEFAULT NULL           COMMENT '所属组织',
  `org_name`     VARCHAR(128) DEFAULT NULL,
  `dept_name`    VARCHAR(128) DEFAULT NULL           COMMENT '主部门',
  `email`        VARCHAR(128) DEFAULT NULL,
  `mobile`       VARCHAR(32)  DEFAULT NULL,
  `identity`     VARCHAR(64)  DEFAULT NULL           COMMENT '登录任职身份标签（15类）',
  `identity_code` VARCHAR(32) DEFAULT NULL          COMMENT '身份编码：admin/leader/hqHead/...',
  `project_post` VARCHAR(64)  DEFAULT NULL           COMMENT '常用项目岗位展示名',
  `rank_title`   VARCHAR(64)  DEFAULT NULL           COMMENT '职称',
  `data_scope`   VARCHAR(32)  DEFAULT 'SELF'         COMMENT 'COMPANY/UNIT/DEPT/PROJECT/SELF/SELECTED',
  `finish_auth`  TINYINT      NOT NULL DEFAULT 0     COMMENT '项目内办结权限',
  `form_maint_scope` VARCHAR(16) DEFAULT NULL        COMMENT '表单维护：hq/unit/channel/type/self',
  `form_maint_channels` VARCHAR(500) DEFAULT NULL    COMMENT '指定渠道编码，逗号分隔',
  `form_maint_types` VARCHAR(500) DEFAULT NULL       COMMENT '指定项目类型，逗号分隔',
  `declare_result_access` TINYINT NOT NULL DEFAULT 0 COMMENT '立项审批结果专项权限',
  `status`       TINYINT      NOT NULL DEFAULT 1     COMMENT '1在岗 0已离岗',
  `last_login_at` DATETIME    DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_identity` (`identity_code`),
  KEY `idx_employee` (`employee_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户/成员';

-- 项目岗位办理权限矩阵（14岗位 × 18权限，管理员可配置）
DROP TABLE IF EXISTS `sys_post_permission`;
CREATE TABLE `sys_post_permission` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `post_code`  VARCHAR(64) NOT NULL COMMENT '项目岗位编码',
  `perm_code`  VARCHAR(64) NOT NULL COMMENT '办理权限编码',
  `enabled`    TINYINT     NOT NULL DEFAULT 1,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_perm` (`post_code`, `perm_code`),
  KEY `idx_post` (`post_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目岗位办理权限矩阵';

-- 角色：PROJECT_TEAM 项目团队 / CHIEF_ENGINEER 责任总师 / MANAGEMENT 管理团队
--       FINANCE 财务团队 / ADMIN 超级管理员
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`      BIGINT      NOT NULL AUTO_INCREMENT,
  `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
  `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
  `remark`  VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关系';

-- 数据字典（全局枚举：项目层级、交付物类型、转化方式、评价等级等）
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `dict_type`   VARCHAR(64) NOT NULL               COMMENT '字典类型',
  `dict_code`   VARCHAR(64) NOT NULL               COMMENT '字典编码',
  `dict_name`   VARCHAR(128) NOT NULL              COMMENT '字典名称',
  `parent_code` VARCHAR(64) DEFAULT NULL           COMMENT '父级编码（级联字典）',
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      TINYINT     NOT NULL DEFAULT 1,
  `remark`      VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_code` (`dict_type`, `dict_code`),
  KEY `idx_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据字典';

-- 审计日志
DROP TABLE IF EXISTS `sys_audit_log`;
CREATE TABLE `sys_audit_log` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`   BIGINT      DEFAULT NULL,
  `user_name` VARCHAR(64) DEFAULT NULL,
  `module`    VARCHAR(64) DEFAULT NULL             COMMENT '业务模块',
  `action`    VARCHAR(64) DEFAULT NULL             COMMENT '动作：CREATE/UPDATE/DELETE/SUBMIT/APPROVE/EXPORT',
  `biz_type`  VARCHAR(64) DEFAULT NULL,
  `biz_id`    BIGINT      DEFAULT NULL,
  `content`   VARCHAR(1000) DEFAULT NULL,
  `ip`        VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计日志';

-- 预警消息
DROP TABLE IF EXISTS `sys_warning`;
CREATE TABLE `sys_warning` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `biz_type`     VARCHAR(64) DEFAULT NULL          COMMENT 'MILESTONE/PLAN/ACCEPTANCE/FUND/TRANSFORM/POST_EVAL',
  `biz_id`       BIGINT      DEFAULT NULL,
  `project_id`   BIGINT      DEFAULT NULL,
  `project_name` VARCHAR(255) DEFAULT NULL,
  `warn_level`   VARCHAR(16) DEFAULT NULL          COMMENT 'YELLOW临期 / RED逾期',
  `title`        VARCHAR(255) DEFAULT NULL,
  `content`      VARCHAR(1000) DEFAULT NULL,
  `receiver`     VARCHAR(255) DEFAULT NULL         COMMENT '接收角色，逗号分隔',
  `is_read`      TINYINT     NOT NULL DEFAULT 0,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_level` (`warn_level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预警消息';

-- ---------------------------------------------------------------------------
-- 二、项目渠道分类体系
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `proj_channel`;
CREATE TABLE `proj_channel` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `channel_code` VARCHAR(64) NOT NULL              COMMENT '渠道编码，全局唯一',
  `channel_name` VARCHAR(128) NOT NULL             COMMENT '渠道名称（专项名称）',
  `level_code`   VARCHAR(32) NOT NULL              COMMENT 'NATIONAL国家级/LOCAL地方级/COMPANY公司级',
  `channel_dept` VARCHAR(128) DEFAULT NULL         COMMENT '渠道部委/委局',
  `channel_office` VARCHAR(128) DEFAULT NULL       COMMENT '渠道司局/处室',
  `inner_dept`   VARCHAR(128) DEFAULT NULL         COMMENT '内部管理部门',
  `inner_office` VARCHAR(128) DEFAULT NULL         COMMENT '内部管理处室',
  `flow_nodes`   VARCHAR(1000) DEFAULT NULL        COMMENT '全周期流程节点，→分隔',
  `declare_material` VARCHAR(1000) DEFAULT NULL    COMMENT '项目申报需提交材料',
  `filing_material` VARCHAR(1000) DEFAULT NULL     COMMENT '项目立项需提交材料',
  `status`       TINYINT     NOT NULL DEFAULT 1,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_code` (`channel_code`),
  KEY `idx_level` (`level_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目渠道分类字典';

-- ---------------------------------------------------------------------------
-- 三、项目一本账（主表 + 关联子表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `proj_info`;
CREATE TABLE `proj_info` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `project_no`      VARCHAR(64) NOT NULL              COMMENT '项目编号，系统自动生成',
  `name`            VARCHAR(255) NOT NULL             COMMENT '项目名称（立项文件全称）',
  `goal`            VARCHAR(2000) DEFAULT NULL        COMMENT '项目整体目标',
  `start_date`      DATE        DEFAULT NULL          COMMENT '开始时间',
  `end_date`        DATE        DEFAULT NULL          COMMENT '结束时间（延期后按变更后日期）',
  `level_code`      VARCHAR(32) DEFAULT NULL          COMMENT 'NATIONAL/LOCAL/COMPANY',
  `filing_dept`     VARCHAR(128) DEFAULT NULL         COMMENT '立项部门',
  `channel_id`      BIGINT      DEFAULT NULL,
  `channel_name`    VARCHAR(128) DEFAULT NULL         COMMENT '渠道类别（专项名称）',
  `lead_org_id`     BIGINT      DEFAULT NULL,
  `lead_org_name`   VARCHAR(128) DEFAULT NULL         COMMENT '牵头单位',
  `main_work`       VARCHAR(2000) DEFAULT NULL        COMMENT '牵头单位主要工作内容',
  `status`          VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
      COMMENT 'DRAFT草稿/DECLARING申报中/IMPLEMENTING进行中/DELAYED已延期/ACCEPTING验收中/COMPANY_ACCEPTED已通过公司级验收/GOV_ACCEPTED已通过机关验收/FINISHED已完成',
  `transform_status` VARCHAR(32) DEFAULT NULL
      COMMENT '成果转化状态：APPLIED已转化应用/CONTINUE接续研发立项/RESERVE技术储备待应用',
  `warn_color`      VARCHAR(16) NOT NULL DEFAULT 'BLUE' COMMENT '四色状态 GREEN/BLUE/YELLOW/RED',
  `data_source`     VARCHAR(32) NOT NULL DEFAULT 'PLATFORM' COMMENT 'PLATFORM平台同步/FORM_MAINT表单维护导入',
  `total_fund`      DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '总经费（万元）',
  `national_fund`   DECIMAL(18,2) DEFAULT 0 COMMENT '国拨经费（万元）',
  `self_fund`       DECIMAL(18,2) DEFAULT 0 COMMENT '自筹经费（万元）',
  `expense_total`   DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '历年支出（万元）',
  `year_budget`     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '年度预算（万元）',
  `year_expense`    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '年度支出（万元）',
  `outsource_amount` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '外协金额（万元）',
  `manage_org_name` VARCHAR(128) DEFAULT NULL COMMENT '管理/需求单位',
  `bureau_office`   VARCHAR(128) DEFAULT NULL COMMENT '司局/处室',
  `project_type`    VARCHAR(128) DEFAULT NULL COMMENT '项目类型',
  `major1`          VARCHAR(64) DEFAULT NULL COMMENT '一级专业',
  `major2`          VARCHAR(64) DEFAULT NULL COMMENT '二级专业',
  `owner_name`      VARCHAR(64) DEFAULT NULL COMMENT '项目负责人',
  `accept_status`   VARCHAR(64) DEFAULT NULL COMMENT '验收状态',
  `org_id`          BIGINT      DEFAULT NULL          COMMENT '承担单位（数据权限）',
  `org_name`        VARCHAR(128) DEFAULT NULL,
  `create_by`       BIGINT      DEFAULT NULL,
  `create_by_name`  VARCHAR(64) DEFAULT NULL,
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_no` (`project_no`),
  KEY `idx_channel` (`channel_id`),
  KEY `idx_level` (`level_code`),
  KEY `idx_status` (`status`),
  KEY `idx_org` (`org_id`),
  KEY `idx_warn` (`warn_color`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '科研项目主表（一本账）';

-- 参研单位（预留多个）
DROP TABLE IF EXISTS `proj_participant`;
CREATE TABLE `proj_participant` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `org_name`    VARCHAR(128) DEFAULT NULL           COMMENT '参研单位名称',
  `work_content` VARCHAR(2000) DEFAULT NULL         COMMENT '主要工作内容',
  `sort`        INT         NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '参研单位';

-- 项目团队/责任专家/管理团队/财务团队
DROP TABLE IF EXISTS `proj_team_member`;
CREATE TABLE `proj_team_member` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `group_code`  VARCHAR(32) NOT NULL                COMMENT 'TECH技术团队/EXPERT责任专家/MGMT管理团队/FIN财务团队',
  `role_code`   VARCHAR(64) NOT NULL                COMMENT 'PROJECT_LEADER/TECH_LEADER/PROJECT_SUPERVISOR/L1_CHIEF/L2_CHIEF/HQ_DIRECTOR/HQ_SUPERVISOR/UNIT_MINISTER/UNIT_SUPERVISOR/HQ_FINANCE/UNIT_FIN_MINISTER/UNIT_FIN_SUPERVISOR',
  `role_name`   VARCHAR(64) DEFAULT NULL,
  `user_name`   VARCHAR(64) DEFAULT NULL,
  `employee_no` VARCHAR(64) DEFAULT NULL,
  `sort`        INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目团队与管理人员';

-- 科研年度目标及计划
DROP TABLE IF EXISTS `proj_annual_plan`;
CREATE TABLE `proj_annual_plan` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`    BIGINT      NOT NULL,
  `year`          INT         DEFAULT NULL,
  `annual_goal`   VARCHAR(2000) DEFAULT NULL        COMMENT '年度目标',
  `plan_content`  VARCHAR(2000) DEFAULT NULL        COMMENT '计划内容',
  `due_date`      DATE        DEFAULT NULL          COMMENT '完成时间',
  `finish_status` VARCHAR(32) DEFAULT NULL          COMMENT 'DONE已完成/DOING进行中/OVERDUE已超期',
  `color_status`  VARCHAR(16) NOT NULL DEFAULT 'BLUE',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '科研年度目标及计划';

-- ---------------------------------------------------------------------------
-- 四、立项阶段
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `proj_declaration`;
CREATE TABLE `proj_declaration` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `apply_no`    VARCHAR(64) DEFAULT NULL            COMMENT '申报单号',
  `name`        VARCHAR(255) NOT NULL               COMMENT '项目名称',
  `channel_id`  BIGINT      DEFAULT NULL,
  `channel_name` VARCHAR(128) DEFAULT NULL,
  `level_code`  VARCHAR(32) DEFAULT NULL,
  `need_approval` TINYINT   NOT NULL DEFAULT 1     COMMENT '1需审批/0直接报备',
  `goal`        VARCHAR(2000) DEFAULT NULL,
  `apply_fund`  DECIMAL(14,2) DEFAULT NULL         COMMENT '申报经费(万元)',
  `start_date`  DATE        DEFAULT NULL,
  `end_date`    DATE        DEFAULT NULL,
  `partner_orgs` VARCHAR(500) DEFAULT NULL,
  `major1`      VARCHAR(64) DEFAULT NULL            COMMENT '一级专业（附件1）',
  `major2`      VARCHAR(64) DEFAULT NULL            COMMENT '二级专业（附件1）',
  `demand_org`  VARCHAR(128) DEFAULT NULL,
  `lead_org_name` VARCHAR(128) DEFAULT NULL         COMMENT '责任单位/牵头单位',
  `lead_work_content` VARCHAR(1000) DEFAULT NULL,
  `org_id`      BIGINT      DEFAULT NULL,
  `org_name`    VARCHAR(128) DEFAULT NULL,
  `applicant_id` BIGINT     DEFAULT NULL,
  `applicant`   VARCHAR(64) DEFAULT NULL,
  `apply_at`    DATETIME    DEFAULT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/REVOKED/REPORTED',
  `flow_node`   VARCHAR(128) DEFAULT NULL           COMMENT '当前审批节点',
  `opinion`     VARCHAR(1000) DEFAULT NULL,
  `remark`      VARCHAR(1000) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_org` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目申报';

-- 申报岗位人员：申报审签及立项后全周期流程共用同一人员来源
DROP TABLE IF EXISTS `proj_declaration_post`;
CREATE TABLE `proj_declaration_post` (
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

DROP TABLE IF EXISTS `proj_filing`;
CREATE TABLE `proj_filing` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `declaration_id` BIGINT     DEFAULT NULL,
  `project_id`    BIGINT      DEFAULT NULL,
  `filing_no`     VARCHAR(64) DEFAULT NULL          COMMENT '备案编号',
  `filing_date`   DATE        DEFAULT NULL,
  `filing_dept`   VARCHAR(128) DEFAULT NULL,
  `status`        VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/FILED/ARCHIVED',
  `remark`        VARCHAR(1000) DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目立项备案';

-- 通用材料/附件表（支撑申报、立项、验收、评估、变更、后评价等）
DROP TABLE IF EXISTS `proj_material`;
CREATE TABLE `proj_material` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `biz_type`    VARCHAR(64) NOT NULL                COMMENT 'DECLARATION/FILING/ACCEPTANCE/EVALUATION/CHANGE/POST_EVAL/TRANSFORM/PARTNER',
  `biz_id`      BIGINT      NOT NULL,
  `field_code`  VARCHAR(64) DEFAULT NULL            COMMENT '附件栏编码（按渠道自适应）',
  `field_name`  VARCHAR(128) DEFAULT NULL           COMMENT '附件栏名称',
  `file_name`   VARCHAR(255) DEFAULT NULL,
  `file_url`    VARCHAR(512) DEFAULT NULL,
  `file_size`   BIGINT      DEFAULT NULL,
  `version`     INT         NOT NULL DEFAULT 1      COMMENT '版本号，附件替换自增',
  `required`    TINYINT     NOT NULL DEFAULT 0,
  `locked`      TINYINT     NOT NULL DEFAULT 0      COMMENT '1：当前渠道不适用，锁定不可编辑',
  `uploaded_by` VARCHAR(64) DEFAULT NULL,
  `uploaded_at` DATETIME    DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通用材料附件';

-- ---------------------------------------------------------------------------
-- 五、实施阶段
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `proj_milestone`;
CREATE TABLE `proj_milestone` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `year`        INT         DEFAULT NULL,
  `name`        VARCHAR(255) NOT NULL               COMMENT '里程碑名称',
  `plan_date`   DATE        DEFAULT NULL,
  `actual_date` DATE        DEFAULT NULL,
  `budget`      DECIMAL(18,2) NOT NULL DEFAULT 0    COMMENT '节点预算（万元）',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'DOING' COMMENT 'DOING/DONE/OVERDUE',
  `color_status` VARCHAR(16) NOT NULL DEFAULT 'BLUE',
  `evidence`    TINYINT     NOT NULL DEFAULT 0      COMMENT '是否已上传佐证材料',
  `lag_reason`  VARCHAR(1000) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_plan_date` (`plan_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '年度里程碑';

DROP TABLE IF EXISTS `proj_plan`;
CREATE TABLE `proj_plan` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `source`      VARCHAR(32) NOT NULL DEFAULT 'CMOS' COMMENT '数据来源',
  `title`       VARCHAR(255) DEFAULT NULL,
  `plan_type`   VARCHAR(16) NOT NULL DEFAULT 'TODO' COMMENT 'TODO待办/DONE已完成',
  `due_date`    DATE        DEFAULT NULL,
  `finish_date` DATE        DEFAULT NULL,
  `owner`       VARCHAR(64) DEFAULT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'DOING',
  `color_status` VARCHAR(16) NOT NULL DEFAULT 'BLUE',
  `apply_status` VARCHAR(32) DEFAULT NULL           COMMENT '办结申请状态 NONE/PENDING/APPROVED/REJECTED',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_type` (`plan_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '计划管理（CMOS 同步）';

DROP TABLE IF EXISTS `proj_evaluation`;
CREATE TABLE `proj_evaluation` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `eval_type`   VARCHAR(32) DEFAULT NULL            COMMENT 'MID中期/QUARTER季度/YEAR年度/STAGE阶段/SUPERVISE督导',
  `name`        VARCHAR(255) DEFAULT NULL,
  `due_date`    DATE        DEFAULT NULL,
  `result`      VARCHAR(16) DEFAULT NULL            COMMENT 'PASS合格/FAIL不合格',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `report_file` VARCHAR(512) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评估检查';

DROP TABLE IF EXISTS `proj_change`;
CREATE TABLE `proj_change` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `change_no`   VARCHAR(64) DEFAULT NULL,
  `project_id`  BIGINT      NOT NULL,
  `project_name` VARCHAR(255) DEFAULT NULL,
  `change_type` VARCHAR(16) NOT NULL                COMMENT 'PROJECT项目变更/DATA数据变更',
  `category`    VARCHAR(32) DEFAULT NULL            COMMENT 'MILESTONE_DELAY/FUND/PERIOD/OUTSOURCE/PAYMENT/INDICATOR/BASIC/LEVEL',
  `title`       VARCHAR(255) DEFAULT NULL,
  `reason`      VARCHAR(2000) DEFAULT NULL,
  `before_value` VARCHAR(2000) DEFAULT NULL,
  `after_value`  VARCHAR(2000) DEFAULT NULL,
  `legal_review` TINYINT    NOT NULL DEFAULT 0      COMMENT '重大变更是否需法务审核',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED',
  `flow_node`   VARCHAR(128) DEFAULT NULL,
  `applicant`   VARCHAR(64) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目变更 / 数据变更';

-- ---------------------------------------------------------------------------
-- 六、经费域
-- ---------------------------------------------------------------------------
-- 6.1 项目级经费执行账
DROP TABLE IF EXISTS `fund_budget`;
CREATE TABLE `fund_budget` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `year`        INT         DEFAULT NULL,
  `milestone_id` BIGINT     DEFAULT NULL            COMMENT '绑定里程碑',
  `milestone_name` VARCHAR(255) DEFAULT NULL,
  `amount`      DECIMAL(18,2) NOT NULL DEFAULT 0,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目节点预算';

DROP TABLE IF EXISTS `fund_payment`;
CREATE TABLE `fund_payment` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `budget_id`   BIGINT      DEFAULT NULL,
  `flow_type`   VARCHAR(16) NOT NULL                COMMENT 'PAY付款/EXPENSE支出/WRITEOFF核销',
  `amount`      DECIMAL(18,2) NOT NULL DEFAULT 0,
  `voucher_no`  VARCHAR(128) DEFAULT NULL           COMMENT '凭证号',
  `occur_date`  DATE        DEFAULT NULL,
  `writeoff_status` VARCHAR(16) DEFAULT 'PENDING'   COMMENT 'PENDING/WRITTEN',
  `operator`    VARCHAR(64) DEFAULT NULL,
  `remark`      VARCHAR(500) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目经费付款/支出/核销';

-- 6.2 总部预算管控（与项目端经费独立存储、互不联动）
DROP TABLE IF EXISTS `hq_fund_budget`;
CREATE TABLE `hq_fund_budget` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `year`        INT         NOT NULL,
  `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '年度总盘子（万元）',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/LOCKED',
  `approve_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED',
  `remark`      VARCHAR(1000) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_year` (`year`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '总部年度科研经费预算';

DROP TABLE IF EXISTS `hq_fund_quota`;
CREATE TABLE `hq_fund_quota` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `budget_id`   BIGINT      NOT NULL,
  `org_id`      BIGINT      DEFAULT NULL,
  `org_name`    VARCHAR(128) DEFAULT NULL,
  `quota_amount` DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '拨付额度上限',
  `used_amount` DECIMAL(18,2) NOT NULL DEFAULT 0    COMMENT '累计已拨付',
  PRIMARY KEY (`id`),
  KEY `idx_budget` (`budget_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '二级单位经费额度';

DROP TABLE IF EXISTS `hq_fund_transfer`;
CREATE TABLE `hq_fund_transfer` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `budget_id`   BIGINT      NOT NULL,
  `quota_id`    BIGINT      DEFAULT NULL,
  `org_id`      BIGINT      DEFAULT NULL,
  `org_name`    VARCHAR(128) DEFAULT NULL,
  `amount`      DECIMAL(18,2) NOT NULL DEFAULT 0,
  `apply_no`    VARCHAR(64) DEFAULT NULL,
  `reason`      VARCHAR(2000) DEFAULT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/UNIT_AUDIT/HQ_AUDIT/PAID/REJECTED',
  `apply_at`    DATETIME    DEFAULT NULL,
  `approved_at` DATETIME    DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_budget` (`budget_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '总部经费拨付执行';

-- ---------------------------------------------------------------------------
-- 七、验收阶段
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `proj_acceptance`;
CREATE TABLE `proj_acceptance` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `accept_level` VARCHAR(32) DEFAULT NULL           COMMENT 'UNIT/COMPANY/NATIONAL/LOCAL',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/CHECKING/APPLYING/ACCEPTING/DONE',
  `apply_at`    DATETIME    DEFAULT NULL,
  `finish_at`   DATETIME    DEFAULT NULL,
  `conclusion`  VARCHAR(1000) DEFAULT NULL,
  `expert_review` TINYINT   NOT NULL DEFAULT 0      COMMENT '是否需要责任总师技术复核',
  `partner_due_date` DATE   DEFAULT NULL            COMMENT '协作单位评价到期日（验收后30日）',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目验收';

DROP TABLE IF EXISTS `proj_acceptance_item`;
CREATE TABLE `proj_acceptance_item` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `acceptance_id` BIGINT    NOT NULL,
  `level_code`  VARCHAR(32) DEFAULT NULL            COMMENT '所属验收层级',
  `level_name`  VARCHAR(128) DEFAULT NULL,
  `field_code`  VARCHAR(64) DEFAULT NULL,
  `material_name` VARCHAR(255) DEFAULT NULL,
  `required`    TINYINT     NOT NULL DEFAULT 0,
  `locked`      TINYINT     NOT NULL DEFAULT 0,
  `file_url`    VARCHAR(512) DEFAULT NULL,
  `status`      VARCHAR(32) DEFAULT 'EMPTY'         COMMENT 'EMPTY/UPLOADED/APPROVED',
  `sort`        INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_acceptance` (`acceptance_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '验收分级材料栏';

DROP TABLE IF EXISTS `proj_deliverable`;
CREATE TABLE `proj_deliverable` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `milestone_id` BIGINT     DEFAULT NULL            COMMENT '对应里程碑节点',
  `name`        VARCHAR(255) NOT NULL               COMMENT '交付物名称（与任务书考核指标一致）',
  `deliver_type` VARCHAR(64) DEFAULT NULL           COMMENT '专利/论文/软著/技术标准/原理样机/设备/成套技术成果',
  `due_date`    DATE        DEFAULT NULL,
  `deliver_date` DATE       DEFAULT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待交付/DELIVERED已交付/OVERDUE已逾期',
  `color_status` VARCHAR(16) NOT NULL DEFAULT 'BLUE',
  `owner_orgs`  VARCHAR(255) DEFAULT NULL           COMMENT '权属：公司/各单位/参研单位/外协单位（多选）',
  `achievement_no` VARCHAR(64) DEFAULT NULL         COMMENT '关联成果编号（联动主键）',
  `file_name`   VARCHAR(255) DEFAULT NULL           COMMENT '清单附件或证明文件名',
  `file_url`    VARCHAR(512) DEFAULT NULL           COMMENT '清单附件或证明地址',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_ms` (`milestone_id`),
  KEY `idx_achv` (`achievement_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '交付物';

DROP TABLE IF EXISTS `partner_eval`;
CREATE TABLE `partner_eval` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`  BIGINT      NOT NULL,
  `partner_name` VARCHAR(255) NOT NULL              COMMENT '协作单位名称（与合同一致）',
  `partner_type` VARCHAR(32) DEFAULT NULL           COMMENT 'LEAD牵头/PARTNER参研/OUTSOURCE科研外协',
  `tech_score`  INT         DEFAULT 0               COMMENT '技术能力',
  `quality_score` INT       DEFAULT 0               COMMENT '交付质量',
  `progress_score` INT      DEFAULT 0               COMMENT '进度履约',
  `service_score` INT       DEFAULT 0               COMMENT '服务配合',
  `compliance_score` INT    DEFAULT 0               COMMENT '合规性',
  `score`       INT         DEFAULT 0               COMMENT '总分',
  `grade`       VARCHAR(16) DEFAULT NULL            COMMENT 'EXCELLENT优秀/GOOD良好/PASS合格/FAIL不合格',
  `eval_date`   DATE        DEFAULT NULL,
  `evaluator`   VARCHAR(64) DEFAULT NULL,
  `due_date`    DATE        DEFAULT NULL            COMMENT '评价到期日',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DOING/DONE',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '协作单位评价';

DROP TABLE IF EXISTS `partner_blacklist`;
CREATE TABLE `partner_blacklist` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `partner_name` VARCHAR(255) NOT NULL,
  `reason`      VARCHAR(2000) DEFAULT NULL,
  `evidence`    VARCHAR(512) DEFAULT NULL,
  `in_date`     DATE        DEFAULT NULL,
  `create_by`   VARCHAR(64) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_partner` (`partner_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '协作单位黑名单';

-- ---------------------------------------------------------------------------
-- 八、成果转化
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `achv_transform`;
CREATE TABLE `achv_transform` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `achievement_no` VARCHAR(64) NOT NULL             COMMENT '成果编号，全局唯一',
  `name`          VARCHAR(255) NOT NULL             COMMENT '成果名称',
  `project_id`    BIGINT      DEFAULT NULL,
  `project_no`    VARCHAR(64) DEFAULT NULL,
  `intro`         VARCHAR(500) DEFAULT NULL         COMMENT '成果简介（100字内）',
  `transform_way` VARCHAR(32) DEFAULT NULL          COMMENT 'MODEL向型号转化/MARKET向市场转化',
  `transform_form` VARCHAR(64) DEFAULT NULL         COMMENT '装机/未装机 或 转让/许可/联合实施/作价投资/其他',
  `plan_date`     DATE        DEFAULT NULL,
  `actual_date`   DATE        DEFAULT NULL,
  `status`        VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED未启动/NEGOTIATING洽谈中/SIGNED已签协议/DONE已完成',
  `color_status`  VARCHAR(16) NOT NULL DEFAULT 'BLUE',
  `intro_detail`  VARCHAR(2000) DEFAULT NULL,
  `duty_org`      VARCHAR(128) DEFAULT NULL,
  `item_count`    INT         NOT NULL DEFAULT 0    COMMENT '关联交付物数量',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_achv_no` (`achievement_no`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成果转化台账（成果包）';

DROP TABLE IF EXISTS `achv_transform_item`;
CREATE TABLE `achv_transform_item` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `transform_id`  BIGINT NOT NULL,
  `deliverable_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_t_d` (`transform_id`, `deliverable_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成果包与交付物绑定';

-- ---------------------------------------------------------------------------
-- 九、后评价
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `proj_post_eval`;
CREATE TABLE `proj_post_eval` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`    BIGINT      NOT NULL,
  `project_name`  VARCHAR(255) DEFAULT NULL,
  `due_date`      DATE        DEFAULT NULL          COMMENT '后评价到期（终审完成后3年内）',
  `goal_achieve`  VARCHAR(2000) DEFAULT NULL        COMMENT '整体目标达成',
  `progress_ctrl` VARCHAR(2000) DEFAULT NULL        COMMENT '进度管控',
  `fund_exec`     VARCHAR(2000) DEFAULT NULL        COMMENT '经费执行',
  `achievement_output` VARCHAR(2000) DEFAULT NULL   COMMENT '科研成果',
  `partner_perform` VARCHAR(2000) DEFAULT NULL     COMMENT '协作履约',
  `risk_ctrl`     VARCHAR(2000) DEFAULT NULL        COMMENT '风险管控',
  `score`         INT         DEFAULT NULL,
  `conclusion`    VARCHAR(1000) DEFAULT NULL,
  `status`        VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DOING/DONE',
  `color_status`  VARCHAR(16) NOT NULL DEFAULT 'BLUE',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目后评价';

-- ---------------------------------------------------------------------------
-- 十、通用审批流
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_flow_instance`;
CREATE TABLE `biz_flow_instance` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `biz_type`    VARCHAR(64) NOT NULL                COMMENT 'DECLARATION/CHANGE/ACCEPTANCE/PLAN/TRANSFORM/POST_EVAL/PARTNER',
  `biz_id`      BIGINT      NOT NULL,
  `title`       VARCHAR(255) DEFAULT NULL,
  `applicant_id` BIGINT     DEFAULT NULL,
  `applicant`   VARCHAR(64) DEFAULT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/APPROVED/REJECTED/REVOKED',
  `current_node` VARCHAR(128) DEFAULT NULL,
  `node_index`  INT         NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` DATETIME    DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批流实例';

DROP TABLE IF EXISTS `biz_flow_task`;
CREATE TABLE `biz_flow_task` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT      NOT NULL,
  `node_name`   VARCHAR(128) DEFAULT NULL,
  `node_order`  INT         NOT NULL DEFAULT 0,
  `assignee_id` BIGINT      DEFAULT NULL,
  `assignee`    VARCHAR(64) DEFAULT NULL,
  `action`      VARCHAR(32) DEFAULT NULL            COMMENT 'APPROVE/REJECT/TRANSFER',
  `opinion`     VARCHAR(1000) DEFAULT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/SKIPPED',
  `handled_at`  DATETIME    DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_instance` (`instance_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批任务节点';

-- ============================================================================
-- 初始化数据
-- ============================================================================

-- 组织
INSERT INTO `sys_org` (`id`, `parent_id`, `org_code`, `org_name`, `org_type`, `sort`) VALUES
 (1, 0, 'HQ', '总部科技部', 'HQ', 1),
 (2, 1, 'HQ-KY', '总部科技部科研项目处', 'OFFICE', 1),
 (3, 1, 'HQ-FZ', '总部科技部科技发展处', 'OFFICE', 2),
 (4, 1, 'HQ-JS', '总部科技部技术基础处', 'OFFICE', 3),
 (10, 0, 'SFY', '上海飞机设计研究院', 'UNIT', 10),
 (20, 0, 'SFG', '上海飞机制造有限公司', 'UNIT', 11),
 (30, 0, 'BZX', '北京民用飞机技术研究中心', 'UNIT', 12);

-- 角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `remark`) VALUES
 (1, 'PROJECT_TEAM', '项目团队', '项目责任人、技术责任人、项目主管'),
 (2, 'CHIEF_ENGINEER', '责任总师', '一级/二级总师，仅查看与审核，无修改权限'),
 (3, 'MANAGEMENT', '管理团队', '总部/各单位科研项目管理岗'),
 (4, 'FINANCE', '财务团队', '各二级单位财务负责人及经办'),
 (5, 'ADMIN', '超级管理员', '权限配置、流程模板维护、数据运维');

-- 初始用户示意值；演示环境执行 migration_demo_password_employee_no_v1.sql 后账号、密码均为工号
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `employee_no`, `org_id`, `org_name`, `dept_name`, `email`,
  `identity`, `identity_code`, `project_post`, `rank_title`, `data_scope`, `finish_auth`, `form_maint_scope`, `declare_result_access`, `status`) VALUES
 (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '100001', 2, '中国商飞总部', '科研项目处', 'admin@comac.cc',
  '系统管理员', 'admin', '暂无项目角色', '工程师', 'COMPANY', 1, 'hq', 1, 1),
 (2, 'pm01',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张明',   'E1001', 10, '上海飞机设计研究院', '科研项目处', 'pm01@comac.cc',
  '项目负责人', 'owner', '项目负责人', '工程师', 'SELF', 0, NULL, 0, 1),
 (3, 'chief01','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李国栋', 'E1002', 10, '上海飞机设计研究院', '科技管理部', 'chief01@comac.cc',
  '一级总师（公司级）', 'chief1', '一级总师', '研究员', 'SELF', 0, NULL, 0, 1),
 (4, 'mgr01',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王芳',   'E1003', 2,  '中国商飞总部', '科研项目处', 'mgr01@comac.cc',
  '总部科研项目主管', 'hqStaff', '总部处室主管', '高级工程师', 'COMPANY', 0, 'hq', 0, 1),
 (5, 'fin01',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵静',   'E1004', 20, '上海飞机制造有限公司', '财务部', 'fin01@comac.cc',
  '单位财务部长', 'finHead', '单位财务部长', '财务', 'UNIT', 0, NULL, 0, 1);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
 (1,5), (2,1), (3,2), (4,3), (5,4);

-- 默认项目岗位办理权限矩阵（对齐推荐默认）
INSERT INTO `sys_post_permission` (`post_code`, `perm_code`, `enabled`) VALUES
 ('contact','baseinfo_edit',1),('contact','milestone_plan',1),('contact','milestone_close',1),('contact','funds_submit',1),
 ('contact','transform_update',1),('contact','declare_submit',1),('contact','filing_upload',1),('contact','initiate_approval',1),
 ('contact','assess_submit',1),('contact','change_submit',1),('contact','members_edit',1),
 ('owner','baseinfo_edit',1),('owner','milestone_plan',1),('owner','milestone_close',1),('owner','funds_submit',1),
 ('owner','funds_voucher',1),('owner','deliverable_manage',1),('owner','eval_collaborator',1),('owner','transform_update',1),
 ('owner','filing_upload',1),('owner','initiate_approval',1),('owner','assess_submit',1),('owner','change_submit',1),
 ('owner','accept_apply',1),('owner','members_edit',1),
 ('tech','baseinfo_edit',1),('tech','milestone_plan',1),('tech','milestone_close',1),('tech','funds_submit',1),
 ('tech','deliverable_manage',1),('tech','initiate_approval',1),('tech','change_submit',1),
 ('pm','baseinfo_edit',1),('pm','plan_manage',1),('pm','funds_submit',1),('pm','initiate_approval',1),
 ('pm','assess_submit',1),('pm','change_submit',1),('pm','contract_register',1),
 ('chief1','funds_submit',1),('chief2','funds_submit',1),
 ('hqHead','assess_archive',1),('hqStaff','assess_archive',1),
 ('unitDeptHead','milestone_close',1),('unitDeptHead','initiate_approval',1),('unitDeptHead','assess_archive',1),('unitDeptHead','members_edit',1),
 ('unitStaff','milestone_close',1),('unitStaff','assess_archive',1),('unitStaff','members_edit',1),
 ('deptHead','initiate_approval',1),('deptHead','assess_archive',1),('deptHead','members_edit',1),
 ('finHq','funds_submit',1),('finHead','funds_submit',1);

-- 数据字典
INSERT INTO `sys_dict` (`dict_type`, `dict_code`, `dict_name`, `sort`) VALUES
 ('PROJECT_LEVEL', 'NATIONAL', '国家级', 1),
 ('PROJECT_LEVEL', 'LOCAL', '地方级', 2),
 ('PROJECT_LEVEL', 'COMPANY', '公司级', 3),
 ('DELIVERABLE_TYPE', 'PATENT', '专利', 1),
 ('DELIVERABLE_TYPE', 'PAPER', '论文', 2),
 ('DELIVERABLE_TYPE', 'SOFTWARE', '软著', 3),
 ('DELIVERABLE_TYPE', 'STANDARD', '技术标准', 4),
 ('DELIVERABLE_TYPE', 'PROTOTYPE', '原理样机', 5),
 ('DELIVERABLE_TYPE', 'EQUIPMENT', '设备', 6),
 ('DELIVERABLE_TYPE', 'TECH_PACKAGE', '成套技术成果', 7),
 ('TRANSFORM_WAY', 'MODEL', '向型号转化', 1),
 ('TRANSFORM_WAY', 'MARKET', '向市场转化', 2),
 ('TRANSFORM_FORM', 'INSTALLED', '装机', 1),
 ('TRANSFORM_FORM', 'UNINSTALLED', '未装机', 2),
 ('TRANSFORM_FORM', 'TRANSFER', '转让', 3),
 ('TRANSFORM_FORM', 'LICENSE', '许可', 4),
 ('TRANSFORM_FORM', 'JOINT', '联合实施', 5),
 ('TRANSFORM_FORM', 'INVEST', '作价投资', 6),
 ('TRANSFORM_FORM', 'OTHER', '其他', 7),
 ('PARTNER_TYPE', 'LEAD', '牵头', 1),
 ('PARTNER_TYPE', 'PARTNER', '参研', 2),
 ('PARTNER_TYPE', 'OUTSOURCE', '科研外协', 3),
 ('EVAL_TYPE', 'MID', '中期评估', 1),
 ('EVAL_TYPE', 'QUARTER', '季度评估', 2),
 ('EVAL_TYPE', 'YEAR', '年度评估', 3),
 ('EVAL_TYPE', 'STAGE', '阶段性检查', 4),
 ('EVAL_TYPE', 'SUPERVISE', '现场督导', 5),
 ('CHANGE_CATEGORY', 'MILESTONE_DELAY', '里程碑延期', 1),
 ('CHANGE_CATEGORY', 'FUND', '经费调整', 2),
 ('CHANGE_CATEGORY', 'PERIOD', '周期变更', 3),
 ('CHANGE_CATEGORY', 'OUTSOURCE', '外协单位更换', 4),
 ('CHANGE_CATEGORY', 'PAYMENT', '付款节点调整', 5),
 ('CHANGE_CATEGORY', 'INDICATOR', '核心指标变动', 6),
 ('CHANGE_CATEGORY', 'BASIC', '基础信息纠错', 7),
 ('CHANGE_CATEGORY', 'LEVEL', '项目层级修正', 8),
 ('ACCEPT_LEVEL', 'UNIT', '单位级验收', 1),
 ('ACCEPT_LEVEL', 'COMPANY', '公司级验收', 2),
 ('ACCEPT_LEVEL', 'NATIONAL', '国家级验收', 3),
 ('ACCEPT_LEVEL', 'LOCAL', '属地主管部门验收', 4);

-- 项目渠道
INSERT INTO `proj_channel` (`channel_code`, `channel_name`, `level_code`, `channel_dept`, `channel_office`, `inner_dept`, `inner_office`, `flow_nodes`, `declare_material`, `filing_material`) VALUES
 ('MJKY', 'MJKY', 'NATIONAL', 'GXB', '装备二司', '科技部', '科研项目处', '建议书申报→立项批复→任务书/可研报告申报→任务书/可研报告申报批复→中期评估→单位、公司两级验收评审→国家级验收', '建议书、建议书意见', '立项批复'),
 ('04ZXJX', '04专项接续', 'NATIONAL', 'GXB', '装备一司', '科技部', '科研项目处', '建议书申报→立项批复→合同书签署→中期评估→单位、公司两级验收评审→国家级验收', '建议书、建议书意见', '立项批复'),
 ('ZDYFJH', '重点研发计划', 'NATIONAL', 'GXB', '高新技术司', '科技部', '科研项目处', '申请书提交→申请书评审→任务书签署→启动会→中期评估→单位、公司两级验收评审→综合绩效评价', '申请书、申请书评审', '立项批复'),
 ('XX25', 'XX25专项', 'NATIONAL', '国资委', '科技创新局', '科技部', '科研项目处', '任务清单报送→任务清单评估并下达→签署任务书→季度会/双月报/年度评估→国资委现场督导→单位、公司两级验收评审→验收评估', '申报通知、任务清单、任务清单评估', '立项批复'),
 ('ZRJJ', '国家自然科学基金', 'NATIONAL', '科学技术部', '国自然', '科技部', '科研项目处', '申请书提交→申请书评审→批准通知→年度实施报告→中期评估→单位、公司两级验收评审→国家级验收', '申请书、申请书评审', '批准通知'),
 ('FGGGXJC', 'FGW GXJC项目', 'NATIONAL', 'FGW', '高技术司', '科技部', '科研项目处', '建议书申报→立项批复→任务书签署→中期评估→单位、公司两级验收评审→国家级验收', '建议书、建议书意见', '立项批复'),
 ('SHJBGS', '上海市科技攻关揭榜挂帅', 'LOCAL', '上海市科委', '空天海洋处', '科技部', '科研项目处', '榜单梳理→榜单发布→榜单答疑→申请书评审并批复立项→合同签订→中期评审→单位验收评审→科委验收', '榜单答疑', '申请书评审'),
 ('SHKJCX', '上海市科技创新行动计划', 'LOCAL', '上海市科委', '空天海洋处', '科技部', '科研项目处', '建议书申报→建议书评审→项目立项→合同签订→阶段性检查→单位验收评审→综合绩效评价', '建议书、建议书评审', '立项通知'),
 ('YYGD', '预研三年滚动计划', 'COMPANY', '科技部', '科研项目处', '科技部', '科研项目处', '建议书申报→建议书评审→项目立项→任务书提交→任务书确认并签订合同→阶段性检查→单位级验收评审→公司级验收评审', '建议书、建议书评审', '立项通知'),
 ('ZDZX', '重大科技创新专项', 'COMPANY', '科技部', '科研项目处', '科技部', '科研项目处', '建议书申报→建议书评审→项目立项→任务书签署→阶段性检查→单位级验收评审→公司级验收评审', '建议书、建议书评审', '立项通知'),
 ('XJQX', '新疆大飞机气象创新中心', 'COMPANY', '科技部', '科研项目处', '科技部', '科研项目处', '申请书提交→申请书评审→技术委员会/主任委员会/理事会审议→项目立项→任务书提交→任务书确认和合同签订→阶段性检查→单位验收评审', '申请书、申请书评审、委员会审议', '立项通知'),
 ('KJZ', '科技周', 'COMPANY', '科技部', '科技发展处', '科技部', '科技发展处', '发布拟立项项目清单→各单位立项→实施→验收', '合作需求、需求对接总结、技术发展战略委员会审议', '拟立项通知、立项文件'),
 ('DFJYJY', '大飞机研究院', 'COMPANY', '科技部', '科技发展处', '科技部', '科技发展处', '项目建议书编制→项目建议书评审→形成拟立项清单→理事会审议→立项→项目实施→项目验收', '项目申请书、学术委员会审议', '立项通知'),
 ('CLM', '大飞机先进材料创新联盟', 'COMPANY', '科技部', '技术基础处', '科技部', '技术基础处', '项目申报→申请书评审→联盟专委会审议→联盟理事会审议→报批→发布立项通知→合同书签署→项目实施→承担单位验收评审', '项目申请书', '立项建议清单及联盟专委会、理事会审议意见'),
 ('BOKH', '“中国商飞-波音”可持续航空技术研究中心项目', 'COMPANY', '科技部', '科研项目处', '科技部', '科研项目处', '项目波音指导委员会立项→项目合同签订→向公司报备→项目实施→项目承担单位验收→与总部签订拨款合同→拨款', '波音指导委员会会议纪要', '三方合同');
