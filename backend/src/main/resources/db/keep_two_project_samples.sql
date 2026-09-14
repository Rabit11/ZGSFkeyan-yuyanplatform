-- 保留两个互补项目样本，并为历史/未来里程碑补齐可查看的填报材料。
-- 执行前请先完成 rpm 数据库全量备份。
SET NAMES utf8mb4;
START TRANSACTION;

SET @keep_project_1 := (SELECT id FROM proj_info WHERE project_no = 'XM2026S001' LIMIT 1);
SET @keep_project_2 := (SELECT id FROM proj_info WHERE project_no = 'XM2026S002' LIMIT 1);

-- 先清理依赖项目的子表，再删除其余项目主记录。
DELETE FROM achv_transform_item
WHERE transform_id IN (
  SELECT id FROM achv_transform WHERE project_id NOT IN (@keep_project_1, @keep_project_2)
);
DELETE FROM achv_transform WHERE project_id NOT IN (@keep_project_1, @keep_project_2);

DELETE FROM fund_payment WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM hq_fund_transfer
WHERE budget_id IN (
  SELECT id FROM fund_budget WHERE project_id NOT IN (@keep_project_1, @keep_project_2)
);
DELETE FROM hq_fund_quota
WHERE budget_id IN (
  SELECT id FROM fund_budget WHERE project_id NOT IN (@keep_project_1, @keep_project_2)
);
DELETE FROM fund_budget WHERE project_id NOT IN (@keep_project_1, @keep_project_2);

DELETE FROM proj_material
WHERE biz_type = 'MILESTONE'
  AND biz_id IN (
    SELECT id FROM proj_milestone WHERE project_id NOT IN (@keep_project_1, @keep_project_2)
  );
DELETE FROM proj_acceptance WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_annual_plan WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_change WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_deliverable WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_evaluation WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_filing WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_milestone WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_participant WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_plan WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_post_eval WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_team_member WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM partner_eval WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM sys_warning WHERE project_id NOT IN (@keep_project_1, @keep_project_2);
DELETE FROM proj_info WHERE id NOT IN (@keep_project_1, @keep_project_2);

-- 重建保留项目的里程碑材料，历史节点为实际填报，未来节点为计划填报模板。
DELETE pm
FROM proj_material pm
JOIN proj_milestone m ON m.id = pm.biz_id
WHERE pm.biz_type = 'MILESTONE'
  AND m.project_id IN (@keep_project_1, @keep_project_2);

INSERT INTO proj_material
  (biz_type, biz_id, field_code, field_name, file_name, file_url, file_size,
   version, required, locked, uploaded_by, uploaded_at)
SELECT 'MILESTONE', m.id, 'HISTORY_REPORT', '历史节点实际填报材料',
       'XM2026S001-总体方案设计评审-历史填报.md',
       '/api/files/download?objectKey=evidence/2026-09-10/c5234aec4bde402198db686e8dde8e61_XM2026S001-总体方案设计评审-历史填报.md',
       312, 1, 1, 0, '林晚晴', TIMESTAMP(m.actual_date, '16:00:00')
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S001' AND m.name='总体方案设计评审'
UNION ALL
SELECT 'MILESTONE', m.id, 'HISTORY_REPORT', '历史节点实际填报材料',
       'XM2026S001-详细设计冻结-历史填报.md',
       '/api/files/download?objectKey=evidence/2026-09-10/749e27ade7d8442eaecce125db556d48_XM2026S001-详细设计冻结-历史填报.md',
       285, 1, 1, 0, '林晚晴', TIMESTAMP(m.actual_date, '16:00:00')
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S001' AND m.name='详细设计冻结'
UNION ALL
SELECT 'MILESTONE', m.id, 'PLAN_TEMPLATE', '未来节点计划填报模板（非完成佐证）',
       'XM2026S001-典型件试验验证-未来填报模板.md',
       '/api/files/download?objectKey=evidence/2026-09-10/8f2741c663f642f0a7a61496c1ba918f_XM2026S001-典型件试验验证-未来填报模板.md',
       318, 1, 1, 0, '林晚晴', NOW()
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S001' AND m.name='典型件试验验证'
UNION ALL
SELECT 'MILESTONE', m.id, 'PLAN_TEMPLATE', '未来节点计划填报模板（非完成佐证）',
       'XM2026S001-全尺寸件验证与规范固化-未来填报模板.md',
       '/api/files/download?objectKey=evidence/2026-09-10/fb5176ec2269413ea7167b9f5a3062b4_XM2026S001-全尺寸件验证与规范固化-未来填报模板.md',
       312, 1, 1, 0, '林晚晴', NOW()
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S001' AND m.name='全尺寸件验证与规范固化'
UNION ALL
SELECT 'MILESTONE', m.id, 'HISTORY_REPORT', '历史节点实际填报材料',
       'XM2026S002-余度架构方案评审-历史填报.md',
       '/api/files/download?objectKey=evidence/2026-09-10/81d258e409a04e2aaf96d5192f2932bf_XM2026S002-余度架构方案评审-历史填报.md',
       300, 1, 1, 0, '林晚晴', TIMESTAMP(m.actual_date, '16:00:00')
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S002' AND m.name='余度架构方案评审'
UNION ALL
SELECT 'MILESTONE', m.id, 'HISTORY_REPORT', '历史逾期节点填报说明',
       'XM2026S002-重构算法验证-历史逾期填报.md',
       '/api/files/download?objectKey=evidence/2026-09-10/9fa3e6b1c9a24b6d8fb12e6f8dc1acac_XM2026S002-重构算法验证-历史逾期填报.md',
       297, 1, 1, 0, '林晚晴', NOW()
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S002' AND m.name='重构算法验证'
UNION ALL
SELECT 'MILESTONE', m.id, 'PLAN_TEMPLATE', '未来节点计划填报模板（非完成佐证）',
       'XM2026S002-半物理仿真试验-未来填报模板.md',
       '/api/files/download?objectKey=evidence/2026-09-10/b877d79be9c84a30a911c9c9f758e171_XM2026S002-半物理仿真试验-未来填报模板.md',
       300, 1, 1, 0, '林晚晴', NOW()
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S002' AND m.name='半物理仿真试验'
UNION ALL
SELECT 'MILESTONE', m.id, 'PLAN_TEMPLATE', '未来节点计划填报模板（非完成佐证）',
       'XM2026S002-适航符合性数据包编制-未来填报模板.md',
       '/api/files/download?objectKey=evidence/2026-09-10/34570c51447241a5848cb727970d97af_XM2026S002-适航符合性数据包编制-未来填报模板.md',
       318, 1, 1, 0, '林晚晴', NOW()
FROM proj_milestone m JOIN proj_info p ON p.id=m.project_id
WHERE p.project_no='XM2026S002' AND m.name='适航符合性数据包编制';

-- 历史实际填报才算完成佐证；未来模板不改变节点完成状态。
UPDATE proj_milestone m
JOIN proj_info p ON p.id = m.project_id
SET m.evidence = CASE WHEN m.actual_date IS NOT NULL THEN 1 ELSE 0 END
WHERE p.project_no IN ('XM2026S001', 'XM2026S002');

INSERT INTO sys_audit_log (user_name, module, action, content)
VALUES ('系统管理员', '项目台账', 'UPDATE',
        '演示环境仅保留 XM2026S001、XM2026S002，并补齐历史/未来节点填报材料');

COMMIT;

