-- =============================================================================
-- 演示业务样本：清空后重建 5 条丰富详实项目（含里程碑/经费/团队/变更/转化等）
-- 人员口径：仅花名册 100001–100015；项目负责人林晚晴/100012
-- 导入：mysql --default-character-set=utf8mb4 ... < seed_demo_projects.sql
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 0. 清空历史业务样本（保留组织/用户/渠道/字典）
-- ---------------------------------------------------------------------------
DELETE FROM fund_payment;
DELETE FROM fund_budget;
DELETE FROM proj_milestone;
DELETE FROM proj_plan;
DELETE FROM proj_deliverable;
DELETE FROM proj_evaluation;
DELETE FROM proj_change;
DELETE FROM proj_participant;
DELETE FROM proj_team_member;
DELETE FROM proj_annual_plan;
DELETE FROM proj_acceptance_item;
DELETE FROM proj_acceptance;
DELETE FROM partner_eval;
DELETE FROM achv_transform_item;
DELETE FROM achv_transform;
DELETE FROM proj_post_eval;
DELETE FROM proj_material;
DELETE FROM proj_filing;
DELETE FROM proj_declaration;
DELETE FROM sys_warning;
DELETE FROM partner_blacklist;
DELETE FROM hq_fund_transfer WHERE apply_no LIKE 'BF2026%';
DELETE FROM proj_info;

-- ---------------------------------------------------------------------------
-- 1. 五条项目主表（覆盖实施中/延期/申报中/机关验收 + 多专业/渠道/预警色）
-- ---------------------------------------------------------------------------
INSERT INTO `proj_info` (
  `project_no`, `name`, `goal`, `start_date`, `end_date`, `level_code`, `filing_dept`,
  `channel_id`, `channel_name`, `lead_org_id`, `lead_org_name`, `main_work`, `status`,
  `transform_status`, `warn_color`, `data_source`, `total_fund`, `national_fund`, `self_fund`,
  `expense_total`, `year_budget`, `year_expense`, `outsource_amount`, `manage_org_name`,
  `bureau_office`, `project_type`, `major1`, `major2`, `owner_name`, `accept_status`,
  `org_id`, `org_name`, `create_by_name`
) VALUES
('XM2026S001', '大型客机复合材料主承力结构关键技术研究',
 '面向宽体客机复合材料主承力结构应用需求，突破壁板/梁/框一体化设计、损伤容限评定与典型件验证关键技术，形成设计—制造—试验闭环能力；输出设计规范、试验数据集与发明专利，支撑复合材料结构用量提升至 25% 的工程目标。',
 DATE_SUB(CURDATE(), INTERVAL 420 DAY), DATE_ADD(CURDATE(), INTERVAL 300 DAY),
 'NATIONAL', 'GXB', 1, 'MJKY', 10, '上海飞机设计研究院',
 '牵头总体技术方案、典型件设计与试验验证统筹，组织参研高校开展规范编制与方法验证，按节点完成阶段评审与成果固化。',
 'IMPLEMENTING', 'APPLIED', 'YELLOW', 'PLATFORM',
 12800.00, 7680.00, 5120.00, 4860.00, 3200.00, 2450.00, 1920.00,
 '中国商飞科技部', '装备二司', '预先研究', '50-复合材料', '5001-复合材料设计', '林晚晴', '未验收',
 10, '上海飞机设计研究院', '林晚晴'),

('XM2026S002', '民机飞控余度架构可靠性与重构技术研究',
 '针对民机飞控余度架构故障诊断与重构需求，突破健康监控、故障隔离与余度重构算法，完成半物理仿真与关键科目验证；形成可支撑适航符合性论证的技术规范与验证数据包，并闭环处理关键节点延期风险。',
 DATE_SUB(CURDATE(), INTERVAL 380 DAY), DATE_ADD(CURDATE(), INTERVAL 100 DAY),
 'NATIONAL', 'GXB', 2, '04专项接续', 10, '上海飞机设计研究院',
 '牵头余度架构方案、重构算法与半物理仿真验证，协调外协测控集成与变更审批，推动逾期里程碑闭环。',
 'DELAYED', 'RESERVE', 'RED', 'PLATFORM',
 7800.00, 4680.00, 3120.00, 4120.00, 1950.00, 1880.00, 1560.00,
 '中国商飞科技部', '装备一司', '预先研究', '30-系统', '3002-飞控', '林晚晴', '未验收',
 10, '上海飞机设计研究院', '林晚晴'),

('XM2026S003', '增材制造钛合金结构件疲劳性能研究',
 '面向增材制造钛合金装机件工程应用，建立工艺参数—显微组织—疲劳性能关联数据库与评定方法；完成典型装机件试样制备、疲劳试验与工艺规范固化，形成可推广的评定流程与数据包。',
 DATE_SUB(CURDATE(), INTERVAL 240 DAY), DATE_ADD(CURDATE(), INTERVAL 280 DAY),
 'NATIONAL', 'FGW', 6, 'FGW GXJC项目', 20, '上海飞机制造有限公司',
 '牵头增材工艺参数优化、试样制备与疲劳性能评定，组织西工大开展方法研究与数据联合分析。',
 'IMPLEMENTING', 'CONTINUE', 'BLUE', 'PLATFORM',
 5800.00, 3480.00, 2320.00, 1880.00, 1450.00, 920.00, 870.00,
 '中国商飞科技部', '高技术司', '试验验证', '40-制造', '4005-增材制造', '林晚晴', '未验收',
 20, '上海飞机制造有限公司', '林晚晴'),

('XM2026S004', '预研三年滚动计划——民机机翼气动优化设计',
 '围绕民机机翼巡航效率提升目标，开展气动外形多学科优化、基准构型标定与风洞验证；形成优化设计方法、构型数据集与阶段建议书，支撑滚动计划立项与后续型号应用论证。',
 DATE_SUB(CURDATE(), INTERVAL 45 DAY), DATE_ADD(CURDATE(), INTERVAL 520 DAY),
 'COMPANY', '科技部', 9, '预研三年滚动计划', 10, '上海飞机设计研究院',
 '牵头建议书编制、气动优化设计与风洞验证统筹，组织内部评审并按滚动计划节点推进。',
 'DECLARING', 'RESERVE', 'BLUE', 'PLATFORM',
 6200.00, 0.00, 6200.00, 280.00, 1550.00, 120.00, 0.00,
 '中国商飞总部', '科研项目处', '预先研究', '10-总体气动', '1001-总体与气动', '林晚晴', '未验收',
 10, '上海飞机设计研究院', '林晚晴'),

('XM2026S005', '民机环控系统适航符合性验证方法研究',
 '建立民机环控系统适航符合性验证方法体系，完成典型科目试验验证与成套数据包固化；已通过机关验收，成果进入型号应用转化阶段，并完成后评价基础资料归档。',
 DATE_SUB(CURDATE(), INTERVAL 820 DAY), DATE_SUB(CURDATE(), INTERVAL 45 DAY),
 'NATIONAL', 'GXB', 1, 'MJKY', 10, '上海飞机设计研究院',
 '牵头验证方法研究、试验验证与机关验收材料编制，组织航研院开展符合性支撑与数据包固化。',
 'GOV_ACCEPTED', 'APPLIED', 'GREEN', 'PLATFORM',
 6800.00, 4080.00, 2720.00, 6680.00, 0.00, 0.00, 1020.00,
 '中国商飞科技部', '装备二司', '试验验证', '30-系统', '3006-环控与氧气', '林晚晴', '已验收',
 10, '上海飞机设计研究院', '林晚晴');

-- ---------------------------------------------------------------------------
-- 2. 参研单位
-- ---------------------------------------------------------------------------
INSERT INTO proj_participant (project_id, org_name, work_content, sort)
SELECT p.id, v.org_name, v.work_content, v.sort
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, '南京航空航天大学' AS org_name, '承担典型件试验验证、损伤容限方法校核与试验报告编制' AS work_content, 1 AS sort
  UNION ALL SELECT 'XM2026S001', '中国航空研究院', '承担设计规范编制支撑与行业对标分析', 2
  UNION ALL SELECT 'XM2026S002', '北京航空航天大学', '承担半物理仿真平台搭建与重构算法联合验证', 1
  UNION ALL SELECT 'XM2026S002', '某外协测控公司', '承担测控设备集成与试验数据采集（示意不合格协作单位）', 2
  UNION ALL SELECT 'XM2026S003', '西北工业大学', '承担疲劳性能评定方法研究与数据库联合建模', 1
  UNION ALL SELECT 'XM2026S004', '北京航空航天大学', '承担气动优化算法与多学科约束建模支撑', 1
  UNION ALL SELECT 'XM2026S005', '中国航空研究院', '承担适航符合性验证支撑与试验科目联合实施', 1
) v ON v.project_no = p.project_no;

-- ---------------------------------------------------------------------------
-- 3. 项目团队（花名册岗位，含项目承担部门负责人）
-- ---------------------------------------------------------------------------
INSERT INTO proj_team_member (project_id, group_code, role_code, role_name, user_name, employee_no, sort)
SELECT p.id, m.group_code, m.role_code, m.role_name, m.user_name, m.employee_no, m.sort
FROM proj_info p
CROSS JOIN (
  SELECT 'TECH' AS group_code, 'PROJECT_LEADER' AS role_code, '项目负责人' AS role_name, '林晚晴' AS user_name, '100012' AS employee_no, 1 AS sort
  UNION ALL SELECT 'TECH', 'TECH_LEADER', '技术负责人', '沈知行', '100014', 2
  UNION ALL SELECT 'TECH', 'PROJECT_SUPERVISOR', '项目主管', '陆嘉言', '100015', 3
  UNION ALL SELECT 'TECH', 'PROJECT_CONTACT', '项目联系人', '顾思远', '100013', 4
  UNION ALL SELECT 'EXPERT', 'L1_CHIEF', '一级总师', '陈铁军', '100007', 5
  UNION ALL SELECT 'EXPERT', 'L2_CHIEF', '二级总师', '蔡文渊', '100008', 6
  UNION ALL SELECT 'MGMT', 'HQ_DIRECTOR', '总部处室处长', '王建国', '100003', 7
  UNION ALL SELECT 'MGMT', 'HQ_SUPERVISOR', '总部处室主管', '何雨桐', '100004', 8
  UNION ALL SELECT 'MGMT', 'UNIT_MINISTER', '单位科技部长', '方致远', '100005', 9
  UNION ALL SELECT 'MGMT', 'UNIT_SUPERVISOR', '单位科技主管', '田念慈', '100006', 10
  UNION ALL SELECT 'MGMT', 'DEPT_HEAD', '项目承担部门负责人', '韩承泽', '100016', 14
  UNION ALL SELECT 'FIN', 'HQ_FINANCE', '总部财务主管', '赵美玲', '100009', 11
  UNION ALL SELECT 'FIN', 'UNIT_FIN_MINISTER', '单位财务部长', '毕仲文', '100010', 12
  UNION ALL SELECT 'FIN', 'UNIT_FIN_SUPERVISOR', '单位财务主管', '龚雪君', '100011', 13
) m
WHERE p.project_no LIKE 'XM2026S%';

-- ---------------------------------------------------------------------------
-- 4. 里程碑 / 年度计划 / CMOS 计划 / 交付物
-- ---------------------------------------------------------------------------
INSERT INTO proj_milestone (project_id, year, name, plan_date, actual_date, budget, status, color_status, evidence, lag_reason)
SELECT p.id, YEAR(v.plan_date), v.name, v.plan_date, v.actual_date, v.budget, v.status, v.color_status, v.evidence, v.lag_reason
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, '总体方案设计评审' AS name, DATE_SUB(CURDATE(), INTERVAL 280 DAY) AS plan_date, DATE_SUB(CURDATE(), INTERVAL 275 DAY) AS actual_date, 2100.00 AS budget, 'DONE' AS status, 'GREEN' AS color_status, 1 AS evidence, NULL AS lag_reason
  UNION ALL SELECT 'XM2026S001', '详细设计冻结', DATE_SUB(CURDATE(), INTERVAL 40 DAY), DATE_SUB(CURDATE(), INTERVAL 32 DAY), 2400.00, 'DONE', 'GREEN', 1, NULL
  UNION ALL SELECT 'XM2026S001', '典型件试验验证', DATE_ADD(CURDATE(), INTERVAL 55 DAY), NULL, 3200.00, 'DOING', 'YELLOW', 0, '试验台排期紧张，已协调顺延窗口'
  UNION ALL SELECT 'XM2026S001', '全尺寸件验证与规范固化', DATE_ADD(CURDATE(), INTERVAL 260 DAY), NULL, 2800.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S002', '余度架构方案评审', DATE_SUB(CURDATE(), INTERVAL 200 DAY), DATE_SUB(CURDATE(), INTERVAL 195 DAY), 1800.00, 'DONE', 'GREEN', 1, NULL
  UNION ALL SELECT 'XM2026S002', '重构算法验证', DATE_SUB(CURDATE(), INTERVAL 18 DAY), NULL, 2100.00, 'OVERDUE', 'RED', 0, '关键试验设备排期冲突，已启动延期变更 BG2026S001'
  UNION ALL SELECT 'XM2026S002', '半物理仿真试验', DATE_ADD(CURDATE(), INTERVAL 70 DAY), NULL, 2000.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S002', '适航符合性数据包编制', DATE_ADD(CURDATE(), INTERVAL 95 DAY), NULL, 900.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S003', '工艺参数优化与冻结', DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY), 1200.00, 'DONE', 'GREEN', 1, NULL
  UNION ALL SELECT 'XM2026S003', '疲劳试样制备', DATE_ADD(CURDATE(), INTERVAL 35 DAY), NULL, 1500.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S003', '疲劳性能评定与数据库建库', DATE_ADD(CURDATE(), INTERVAL 160 DAY), NULL, 1800.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S003', '工艺规范与评定方法固化', DATE_ADD(CURDATE(), INTERVAL 250 DAY), NULL, 900.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S004', '建议书编制与内部评审', DATE_ADD(CURDATE(), INTERVAL 35 DAY), NULL, 800.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S004', '基准构型标定', DATE_ADD(CURDATE(), INTERVAL 120 DAY), NULL, 1400.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S004', '气动多学科优化设计', DATE_ADD(CURDATE(), INTERVAL 260 DAY), NULL, 2000.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S004', '风洞验证与构型数据集固化', DATE_ADD(CURDATE(), INTERVAL 450 DAY), NULL, 1600.00, 'DOING', 'BLUE', 0, NULL
  UNION ALL SELECT 'XM2026S005', '验证方法体系研究', DATE_SUB(CURDATE(), INTERVAL 700 DAY), DATE_SUB(CURDATE(), INTERVAL 690 DAY), 1600.00, 'DONE', 'GREEN', 1, NULL
  UNION ALL SELECT 'XM2026S005', '典型科目试验验证', DATE_SUB(CURDATE(), INTERVAL 320 DAY), DATE_SUB(CURDATE(), INTERVAL 300 DAY), 2200.00, 'DONE', 'GREEN', 1, NULL
  UNION ALL SELECT 'XM2026S005', '成套数据包与规范编制', DATE_SUB(CURDATE(), INTERVAL 120 DAY), DATE_SUB(CURDATE(), INTERVAL 110 DAY), 1500.00, 'DONE', 'GREEN', 1, NULL
  UNION ALL SELECT 'XM2026S005', '机关验收与归档', DATE_SUB(CURDATE(), INTERVAL 50 DAY), DATE_SUB(CURDATE(), INTERVAL 45 DAY), 800.00, 'DONE', 'GREEN', 1, NULL
) v ON v.project_no = p.project_no;

INSERT INTO proj_annual_plan (project_id, year, annual_goal, plan_content, due_date, finish_status, color_status)
SELECT p.id, v.year, v.annual_goal, v.plan_content, v.due_date, v.finish_status, v.color_status
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, YEAR(CURDATE()) AS year, '完成典型件试验验证并形成阶段报告' AS annual_goal, '典型件试验、数据分析与阶段评审材料编制' AS plan_content, DATE_ADD(CURDATE(), INTERVAL 180 DAY) AS due_date, 'DOING' AS finish_status, 'YELLOW' AS color_status
  UNION ALL SELECT 'XM2026S001', YEAR(CURDATE())-1, '完成详细设计冻结', '详细设计评审与技术状态冻结', DATE_SUB(CURDATE(), INTERVAL 60 DAY), 'DONE', 'GREEN'
  UNION ALL SELECT 'XM2026S002', YEAR(CURDATE()), '完成重构算法验证与延期闭环', '算法验证、变更审批与半物理仿真准备', DATE_ADD(CURDATE(), INTERVAL 100 DAY), 'DOING', 'RED'
  UNION ALL SELECT 'XM2026S003', YEAR(CURDATE()), '完成疲劳试样制备与首轮试验', '试样制备、首轮疲劳试验与数据入库', DATE_ADD(CURDATE(), INTERVAL 200 DAY), 'DOING', 'BLUE'
  UNION ALL SELECT 'XM2026S004', YEAR(CURDATE()), '完成建议书编制与滚动计划评审', '建议书编制、内部评审与申报材料完善', DATE_ADD(CURDATE(), INTERVAL 90 DAY), 'DOING', 'BLUE'
  UNION ALL SELECT 'XM2026S005', YEAR(CURDATE())-1, '完成机关验收与成果归档', '验收材料、数据包归档与转化启动', DATE_SUB(CURDATE(), INTERVAL 40 DAY), 'DONE', 'GREEN'
) v ON v.project_no = p.project_no;

INSERT INTO proj_plan (project_id, source, title, plan_type, due_date, finish_date, owner, status, color_status, apply_status)
SELECT p.id, v.source, v.title, v.plan_type, v.due_date, v.finish_date, v.owner, v.status, v.color_status, v.apply_status
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, 'CMOS' AS source, '【MJKY】季度进展报送' AS title, 'TODO' AS plan_type, DATE_ADD(CURDATE(), INTERVAL 20 DAY) AS due_date, NULL AS finish_date, '陆嘉言' AS owner, 'DOING' AS status, 'BLUE' AS color_status, 'NONE' AS apply_status
  UNION ALL SELECT 'XM2026S001', 'CMOS', '【MJKY】阶段成果提交', 'TODO', DATE_ADD(CURDATE(), INTERVAL 80 DAY), NULL, '沈知行', 'DOING', 'BLUE', 'NONE'
  UNION ALL SELECT 'XM2026S002', 'CMOS', '【04专项接续】延期节点专项报告', 'TODO', DATE_ADD(CURDATE(), INTERVAL 10 DAY), NULL, '林晚晴', 'OVERDUE', 'RED', 'NONE'
  UNION ALL SELECT 'XM2026S003', 'CMOS', '【FGW GXJC】年度实施报告', 'TODO', DATE_ADD(CURDATE(), INTERVAL 60 DAY), NULL, '顾思远', 'DOING', 'BLUE', 'NONE'
  UNION ALL SELECT 'XM2026S004', 'CMOS', '【预研三年滚动】建议书内部评审材料', 'TODO', DATE_ADD(CURDATE(), INTERVAL 30 DAY), NULL, '顾思远', 'DOING', 'BLUE', 'NONE'
  UNION ALL SELECT 'XM2026S005', 'CMOS', '【MJKY】验收归档检查', 'DONE', DATE_SUB(CURDATE(), INTERVAL 40 DAY), DATE_SUB(CURDATE(), INTERVAL 38 DAY), '陆嘉言', 'DONE', 'GREEN', 'APPROVED'
) v ON v.project_no = p.project_no;

INSERT INTO proj_deliverable (project_id, name, deliver_type, due_date, deliver_date, status, color_status, owner_orgs, achievement_no)
SELECT p.id, v.name, v.deliver_type, v.due_date, v.deliver_date, v.status, v.color_status, v.owner_orgs, v.achievement_no
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, '复合材料主承力结构设计规范（草案）' AS name, 'STANDARD' AS deliver_type, DATE_ADD(CURDATE(), INTERVAL 120 DAY) AS due_date, NULL AS deliver_date, 'PENDING' AS status, 'BLUE' AS color_status, '公司,各单位' AS owner_orgs, NULL AS achievement_no
  UNION ALL SELECT 'XM2026S001', '典型件试验验证报告', 'TECH_PACKAGE', DATE_ADD(CURDATE(), INTERVAL 70 DAY), NULL, 'PENDING', 'YELLOW', '公司,各单位', NULL
  UNION ALL SELECT 'XM2026S001', '发明专利：一种复合材料主承力结构连接方法', 'PATENT', DATE_SUB(CURDATE(), INTERVAL 8 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'DELIVERED', 'GREEN', '公司', 'CG2026S001'
  UNION ALL SELECT 'XM2026S001', '原理样机 1 套', 'PROTOTYPE', DATE_ADD(CURDATE(), INTERVAL 220 DAY), NULL, 'PENDING', 'BLUE', '公司', NULL
  UNION ALL SELECT 'XM2026S002', '飞控余度重构技术规范', 'STANDARD', DATE_ADD(CURDATE(), INTERVAL 90 DAY), NULL, 'PENDING', 'BLUE', '公司,各单位', NULL
  UNION ALL SELECT 'XM2026S002', '半物理仿真验证报告', 'TECH_PACKAGE', DATE_ADD(CURDATE(), INTERVAL 110 DAY), NULL, 'PENDING', 'BLUE', '公司', NULL
  UNION ALL SELECT 'XM2026S002', '发明专利：飞控余度故障重构方法', 'PATENT', DATE_ADD(CURDATE(), INTERVAL 140 DAY), NULL, 'PENDING', 'BLUE', '公司', NULL
  UNION ALL SELECT 'XM2026S003', '增材制造钛合金疲劳性能数据库', 'TECH_PACKAGE', DATE_ADD(CURDATE(), INTERVAL 200 DAY), NULL, 'PENDING', 'BLUE', '公司,各单位', NULL
  UNION ALL SELECT 'XM2026S003', '增材制造工艺规范', 'STANDARD', DATE_ADD(CURDATE(), INTERVAL 230 DAY), NULL, 'PENDING', 'BLUE', '公司', NULL
  UNION ALL SELECT 'XM2026S003', '学术论文 2 篇', 'PAPER', DATE_ADD(CURDATE(), INTERVAL 180 DAY), NULL, 'PENDING', 'BLUE', '公司', NULL
  UNION ALL SELECT 'XM2026S004', '机翼气动优化设计方法', 'PAPER', DATE_ADD(CURDATE(), INTERVAL 300 DAY), NULL, 'PENDING', 'BLUE', '公司', NULL
  UNION ALL SELECT 'XM2026S004', '优化构型数据集', 'TECH_PACKAGE', DATE_ADD(CURDATE(), INTERVAL 460 DAY), NULL, 'PENDING', 'BLUE', '公司,各单位', NULL
  UNION ALL SELECT 'XM2026S005', '环控适航符合性验证方法', 'STANDARD', DATE_SUB(CURDATE(), INTERVAL 80 DAY), DATE_SUB(CURDATE(), INTERVAL 75 DAY), 'DELIVERED', 'GREEN', '公司', 'CG2026S002'
  UNION ALL SELECT 'XM2026S005', '成套验证数据包', 'TECH_PACKAGE', DATE_SUB(CURDATE(), INTERVAL 55 DAY), DATE_SUB(CURDATE(), INTERVAL 50 DAY), 'DELIVERED', 'GREEN', '公司,各单位', 'CG2026S002'
  UNION ALL SELECT 'XM2026S005', '学术论文 3 篇', 'PAPER', DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_SUB(CURDATE(), INTERVAL 85 DAY), 'DELIVERED', 'GREEN', '公司', NULL
) v ON v.project_no = p.project_no;

-- ---------------------------------------------------------------------------
-- 5. 经费 / 评估 / 变更 / 验收 / 协作评价 / 黑名单
-- ---------------------------------------------------------------------------
INSERT INTO fund_budget (project_id, year, milestone_id, milestone_name, amount, status)
SELECT m.project_id, YEAR(CURDATE()), m.id, m.name, m.budget, IF(m.status='DONE','APPROVED','PENDING')
FROM proj_milestone m
JOIN proj_info p ON p.id=m.project_id AND p.project_no LIKE 'XM2026S%';

INSERT INTO fund_payment (project_id, budget_id, flow_type, amount, voucher_no, occur_date, writeoff_status, operator)
SELECT b.project_id, b.id, 'PAY', ROUND(b.amount*0.85,2),
       CONCAT('PZ', YEAR(CURDATE()), LPAD(b.id,6,'0')),
       COALESCE(m.actual_date, CURDATE()), 'WRITTEN', '龚雪君'
FROM fund_budget b
JOIN proj_milestone m ON m.id=b.milestone_id AND m.status='DONE'
JOIN proj_info p ON p.id=b.project_id AND p.project_no LIKE 'XM2026S%';

INSERT INTO proj_evaluation (project_id, eval_type, name, due_date, result, status)
SELECT p.id, v.eval_type, v.name, v.due_date, v.result, v.status
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, 'MID' AS eval_type, '中期评估' AS name, DATE_ADD(CURDATE(), INTERVAL 45 DAY) AS due_date, NULL AS result, 'PENDING' AS status
  UNION ALL SELECT 'XM2026S002', 'STAGE', '阶段性检查（延期专项）', DATE_ADD(CURDATE(), INTERVAL 15 DAY), NULL, 'PENDING'
  UNION ALL SELECT 'XM2026S003', 'YEAR', '年度评估', DATE_ADD(CURDATE(), INTERVAL 120 DAY), NULL, 'PENDING'
  UNION ALL SELECT 'XM2026S005', 'YEAR', '年度评估', DATE_SUB(CURDATE(), INTERVAL 120 DAY), 'PASS', 'DONE'
  UNION ALL SELECT 'XM2026S005', 'FINAL', '机关验收评估', DATE_SUB(CURDATE(), INTERVAL 50 DAY), 'PASS', 'DONE'
) v ON v.project_no=p.project_no;

INSERT INTO proj_change (change_no, project_id, project_name, change_type, category, title, reason, before_value, after_value, legal_review, status, flow_node, applicant)
SELECT v.change_no, p.id, p.name, v.change_type, v.category, v.title, v.reason, v.before_value, v.after_value, v.legal_review, v.status, v.flow_node, v.applicant
FROM proj_info p
JOIN (
  SELECT 'BG2026S001' AS change_no, 'XM2026S002' AS project_no, 'PROJECT' AS change_type, 'MILESTONE_DELAY' AS category,
         '重构算法验证节点延期 60 天' AS title, '关键试验设备排期冲突，需顺延节点并同步半物理仿真窗口' AS reason,
         DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 18 DAY), '%Y-%m-%d') AS before_value,
         DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 42 DAY), '%Y-%m-%d') AS after_value,
         0 AS legal_review, 'APPROVING' AS status, '二级单位主管部门初审' AS flow_node, '林晚晴' AS applicant
  UNION ALL SELECT 'BG2026S002', 'XM2026S001', 'PROJECT', 'FUND',
         '年度预算额度调增 300 万元', '典型件外协试验费用上涨，需追加年度预算',
         '3200 万元', '3500 万元', 0, 'APPROVED', '总部终审', '何雨桐'
  UNION ALL SELECT 'BG2026S003', 'XM2026S002', 'PROJECT', 'OUTSOURCE',
         '外协单位更换申请', '原外协单位交付质量不达标且评价不合格',
         '某外协测控公司', '拟更换为示意外协单位 B', 1, 'APPROVING', '法务审核', '陆嘉言'
  UNION ALL SELECT 'BG2026S004', 'XM2026S003', 'PROJECT', 'PERIOD',
         '项目周期延长 3 个月', '疲劳试验机台共用导致试样批次顺延',
         DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 280 DAY), '%Y-%m-%d'),
         DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 370 DAY), '%Y-%m-%d'),
         0, 'DRAFT', NULL, '林晚晴'
) v ON v.project_no=p.project_no;

INSERT INTO proj_acceptance (project_id, accept_level, status, expert_review, partner_due_date)
SELECT p.id, v.accept_level, v.status, v.expert_review, v.partner_due_date
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, 'NATIONAL' AS accept_level, 'NOT_STARTED' AS status, 1 AS expert_review, DATE_ADD(CURDATE(), INTERVAL 40 DAY) AS partner_due_date
  UNION ALL SELECT 'XM2026S002', 'NATIONAL', 'NOT_STARTED', 1, DATE_ADD(CURDATE(), INTERVAL 40 DAY)
  UNION ALL SELECT 'XM2026S003', 'NATIONAL', 'NOT_STARTED', 1, DATE_ADD(CURDATE(), INTERVAL 40 DAY)
  UNION ALL SELECT 'XM2026S004', 'COMPANY', 'NOT_STARTED', 0, DATE_ADD(CURDATE(), INTERVAL 60 DAY)
  UNION ALL SELECT 'XM2026S005', 'NATIONAL', 'DONE', 1, DATE_SUB(CURDATE(), INTERVAL 30 DAY)
) v ON v.project_no=p.project_no;

INSERT INTO partner_eval (project_id, partner_name, partner_type, tech_score, quality_score, progress_score, service_score, compliance_score, score, grade, eval_date, evaluator, due_date, status)
SELECT p.id, v.partner_name, v.partner_type, v.tech_score, v.quality_score, v.progress_score, v.service_score, v.compliance_score, v.score, v.grade, v.eval_date, v.evaluator, v.due_date, v.status
FROM proj_info p
JOIN (
  SELECT 'XM2026S001' AS project_no, '南京航空航天大学' AS partner_name, 'PARTNER' AS partner_type,
         18 AS tech_score, 18 AS quality_score, 17 AS progress_score, 19 AS service_score, 18 AS compliance_score,
         90 AS score, 'EXCELLENT' AS grade, DATE_SUB(CURDATE(), INTERVAL 12 DAY) AS eval_date, '林晚晴' AS evaluator,
         DATE_ADD(CURDATE(), INTERVAL 25 DAY) AS due_date, 'DONE' AS status
  UNION ALL SELECT 'XM2026S001', '中国航空研究院', 'PARTNER',
         17, 18, 16, 18, 17, 86, 'GOOD', DATE_SUB(CURDATE(), INTERVAL 20 DAY), '沈知行',
         DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'DONE'
  UNION ALL SELECT 'XM2026S002', '某外协测控公司', 'OUTSOURCE',
         8, 7, 6, 9, 8, 38, 'FAIL', DATE_SUB(CURDATE(), INTERVAL 14 DAY), '林晚晴',
         DATE_ADD(CURDATE(), INTERVAL 60 DAY), 'DONE'
  UNION ALL SELECT 'XM2026S002', '北京航空航天大学', 'PARTNER',
         18, 17, 16, 18, 17, 86, 'GOOD', DATE_SUB(CURDATE(), INTERVAL 30 DAY), '沈知行',
         DATE_ADD(CURDATE(), INTERVAL 40 DAY), 'DONE'
  UNION ALL SELECT 'XM2026S003', '西北工业大学', 'PARTNER',
         0, 0, 0, 0, 0, 0, NULL, NULL, NULL, DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'PENDING'
  UNION ALL SELECT 'XM2026S005', '中国航空研究院', 'PARTNER',
         19, 18, 18, 19, 18, 92, 'EXCELLENT', DATE_SUB(CURDATE(), INTERVAL 60 DAY), '林晚晴',
         DATE_SUB(CURDATE(), INTERVAL 40 DAY), 'DONE'
) v ON v.project_no=p.project_no;

INSERT INTO partner_blacklist (partner_name, reason, in_date, create_by) VALUES
('某外协测控公司', '评价得分 38 分（不合格），交付质量与进度履约不达标，纳入黑名单', DATE_SUB(CURDATE(), INTERVAL 14 DAY), '林晚晴'),
('示意——华东某复合材料加工厂', '历史项目多次延期且关键节点佐证材料虚假，经单位科技管理部确认列入黑名单', DATE_SUB(CURDATE(), INTERVAL 120 DAY), '方致远');

-- ---------------------------------------------------------------------------
-- 6. 成果转化 / 后评价 / 申报 / 预警
-- ---------------------------------------------------------------------------
INSERT INTO achv_transform (achievement_no, name, project_id, project_no, intro, transform_way, transform_form, plan_date, actual_date, status, color_status, duty_org, item_count)
SELECT v.achievement_no, v.name, p.id, p.project_no, v.intro, v.transform_way, v.transform_form, v.plan_date, v.actual_date, v.status, v.color_status, p.lead_org_name, v.item_count
FROM proj_info p
JOIN (
  SELECT 'CG2026S001' AS achievement_no, 'XM2026S001' AS project_no, '复合材料主承力结构成果包' AS name,
         '面向型号应用的关键技术成果，含专利与试验数据包，正在推进装机应用对接。' AS intro,
         'MODEL' AS transform_way, 'UNINSTALLED' AS transform_form,
         DATE_ADD(CURDATE(), INTERVAL 90 DAY) AS plan_date, NULL AS actual_date, 'NEGOTIATING' AS status, 'BLUE' AS color_status, 1 AS item_count
  UNION ALL SELECT 'CG2026S002', 'XM2026S005', '环控适航验证方法成果包',
         '已通过机关验收的成套验证方法与数据包，已完成型号应用转化。',
         'MODEL', 'INSTALLED',
         DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 'DONE', 'GREEN', 2
) v ON v.project_no=p.project_no;

INSERT INTO proj_post_eval (project_id, project_name, due_date, goal_achieve, progress_ctrl, fund_exec, achievement_output, partner_perform, risk_ctrl, score, conclusion, status, color_status)
SELECT p.id, p.name, DATE_ADD(p.end_date, INTERVAL 365 DAY),
       '整体目标达成良好，验证方法体系完整并通过机关验收。',
       '进度按计划推进，关键节点均按期闭环。',
       '经费执行规范，决算与任务书匹配。',
       '形成验证方法、成套数据包与论文等成果，转化应用已落地。',
       '协作单位履约评价优秀。',
       '风险可控，无重大质量问题。',
       92, '目标达成良好，验证方法可推广，建议持续跟踪型号应用成效。', 'DONE', 'GREEN'
FROM proj_info p WHERE p.project_no='XM2026S005';

INSERT INTO proj_declaration (
  apply_no, name, channel_id, channel_name, level_code,
  need_approval, goal, apply_fund, start_date, end_date, partner_orgs,
  major1, major2, demand_org, lead_org_name, lead_work_content,
  org_id, org_name, applicant, apply_at, status, flow_node, remark
) VALUES
('SB2026S001', 'MJKY — 民机复合材料壁板自动铺丝与固化工艺研究', 1, 'MJKY', 'NATIONAL',
 1, '突破自动铺丝与固化关键工艺，形成可工程化的复合材料壁板制造能力。', 1860.00, '2026-09-01', '2029-08-31', '南京航空航天大学',
 '50-复合材料', '5002-复合材料与工艺', '科技部科研项目处', '上飞公司', '牵头铺丝工艺试验、固化参数优化与典型件验证。',
 20, '上海飞机制造有限公司', '林晚晴', DATE_SUB(NOW(), INTERVAL 18 DAY), 'APPROVING', '二级总师', '演示申报样本'),
('SB2026S002', '04专项接续 — 飞控余度健康监控与重构算法研究', 2, '04专项接续', 'NATIONAL',
 1, '形成飞控余度健康监控与故障重构算法验证闭环，支撑适航符合性论证。', 1520.00, '2026-10-01', '2029-09-30', '北京航空航天大学',
 '30-系统', '3002-飞控', '科技部科研项目处', '上飞院', '牵头余度架构方案与半物理仿真验证。',
 10, '上海飞机设计研究院', '林晚晴', DATE_SUB(NOW(), INTERVAL 12 DAY), 'SUBMITTED', '项目承担部门负责人', '演示申报样本'),
('SB2026S003', '预研三年滚动计划 — 民机机翼气动多学科优化设计', 9, '预研三年滚动计划', 'COMPANY',
 1, '开展机翼气动外形多学科优化，实现巡航效率提升目标论证。', 980.00, '2026-09-01', '2028-12-31', NULL,
 '10-总体气动', '1001-总体与气动', '科技部科研项目处', '上飞院', '牵头气动优化设计、风洞验证与构型数据集固化。',
 10, '上海飞机设计研究院', '林晚晴', DATE_SUB(NOW(), INTERVAL 5 DAY), 'DRAFT', NULL, '演示草稿'),
('SB2026S004', '重大科技创新专项 — 增材制造钛合金装机件疲劳评定', 10, '重大科技创新专项', 'COMPANY',
 1, '建立增材制造钛合金装机件疲劳性能评定方法与数据库。', 1240.00, '2026-11-01', '2029-10-31', '西北工业大学',
 '40-制造', '4005-增材制造', '科技部科研项目处', '上飞公司', '牵头工艺参数优化、试样制备与疲劳性能评定。',
 20, '上海飞机制造有限公司', '林晚晴', DATE_SUB(NOW(), INTERVAL 9 DAY), 'APPROVING', '一级总师', '演示申报样本'),
('SB2026S005', '重点研发计划 — 民机环控系统适航符合性验证方法', 3, '重点研发计划', 'NATIONAL',
 1, '形成环控系统适航符合性验证方法体系与成套验证数据包。', 1680.00, '2026-09-15', '2029-06-30', '中国航空研究院',
 '30-系统', '3006-环控与氧气', '科技部科研项目处', '上飞院', '牵头验证方法研究、试验验证与机关验收材料编制。',
 10, '上海飞机设计研究院', '林晚晴', DATE_SUB(NOW(), INTERVAL 2 DAY), 'SUBMITTED', '项目承担部门负责人', '演示申报样本');

INSERT INTO sys_warning (biz_type, project_id, project_name, warn_level, title, content, is_read)
SELECT v.biz_type, p.id, p.name, v.warn_level, v.title, v.content, 0
FROM proj_info p
JOIN (
  SELECT 'XM2026S002' AS project_no, 'MILESTONE' AS biz_type, 'RED' AS warn_level,
         '里程碑逾期：重构算法验证' AS title,
         '计划完成时间已超期未完成，请尽快处理或推动延期变更 BG2026S001 闭环。' AS content
  UNION ALL SELECT 'XM2026S001', 'MILESTONE', 'YELLOW',
         '里程碑临期：典型件试验验证',
         '距计划完成时间不足 60 天，试验台排期紧张，请及时协调并准备佐证材料。'
  UNION ALL SELECT 'XM2026S002', 'PLAN', 'RED',
         'CMOS 待办逾期：延期节点专项报告',
         '专项报告已临期/逾期，请项目负责人尽快报送。'
) v ON v.project_no=p.project_no;

INSERT INTO sys_audit_log (user_name, module, action, content) VALUES
('林晚晴', '项目台账', 'CREATE', '重建丰富演示项目 XM2026S001–S005'),
('何雨桐', '项目变更', 'APPROVE', '审批通过演示变更 BG2026S002'),
('龚雪君', '经费管理', 'UPDATE', '核销演示付款凭证');
