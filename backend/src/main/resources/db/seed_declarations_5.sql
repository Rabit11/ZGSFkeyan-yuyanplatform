-- 仅重建项目申报样本（5 条），不清空项目台账
SET NAMES utf8mb4;

DELETE FROM proj_material WHERE biz_type='DECLARATION';
DELETE FROM proj_filing WHERE declaration_id IS NOT NULL;
DELETE FROM proj_declaration;

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

SELECT apply_no, status, lead_org_name, major1, applicant, name FROM proj_declaration ORDER BY apply_no;
