-- 人员花名册权威姓名同步（工号 100001-100016）
-- 密码：与工号相同（如 100012 / 100012）；插入时即使用各自 BCrypt，迁移脚本用于修正存量账号
-- 导入：mysql --default-character-set=utf8mb4 ... < personnel_roster_v1.sql

SET NAMES utf8mb4;

UPDATE sys_user SET real_name='系统管理员' WHERE employee_no='100001' OR username='admin';
UPDATE sys_user SET real_name='周明远' WHERE employee_no='100002';
UPDATE sys_user SET real_name='王建国' WHERE employee_no='100003';
UPDATE sys_user SET real_name='何雨桐' WHERE employee_no='100004';
UPDATE sys_user SET real_name='方致远' WHERE employee_no='100005';
UPDATE sys_user SET real_name='田念慈' WHERE employee_no='100006';
UPDATE sys_user SET real_name='陈铁军' WHERE employee_no='100007';
UPDATE sys_user SET real_name='蔡文渊' WHERE employee_no='100008';
UPDATE sys_user SET real_name='赵美玲' WHERE employee_no='100009';
UPDATE sys_user SET real_name='毕仲文' WHERE employee_no='100010';
UPDATE sys_user SET real_name='龚雪君' WHERE employee_no='100011';
UPDATE sys_user SET real_name='林晚晴' WHERE employee_no='100012';
UPDATE sys_user SET real_name='顾思远' WHERE employee_no='100013';
UPDATE sys_user SET real_name='沈知行' WHERE employee_no='100014';
UPDATE sys_user SET real_name='陆嘉言' WHERE employee_no='100015';
UPDATE sys_user SET real_name='韩承泽' WHERE employee_no='100016';

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100002', '$2b$10$yZpoP.pLLIrbQDWhACtBuORTD9aOMETTJ9s4tOty8tfQ9QnUpg9em', '周明远', '100002', 1, '中国商飞总部', '科技管理部（总部办公室）', '100002@comac.cc',
  '公司领导', 'leader', '暂无项目角色', '专家', 'COMPANY', 1, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100002' OR u.username='100002');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100003', '$2b$10$VEJSBhoZA5ybDoLFExgXtuEhXIkiWjCgm0eQ7K6CpEPBDL2Orh6eq', '王建国', '100003', 1, '中国商飞总部', '科技管理部（总部办公室）', '100003@comac.cc',
  '总部责任处室处长', 'hqHead', '总部处室处长', '研究员', 'COMPANY', 1, 'hq', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100003' OR u.username='100003');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100004', '$2b$10$f1dib1U/uKCACzx8E94DZeOBB793V1fH42pyOLSwwLJbicxXds2PS', '何雨桐', '100004', 1, '中国商飞总部', '科研项目处', '100004@comac.cc',
  '总部科研项目主管', 'hqStaff', '总部处室主管', '高级工程师', 'COMPANY', 0, 'hq', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100004' OR u.username='100004');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100005', '$2b$10$uT17Vc1CLNLVZXS/sD4DreYe/ldX8q6.5BBHpN06Jwj5N3zR7a.Ri', '方致远', '100005', 10, '上飞院', '科技管理部', '100005@comac.cc',
  '单位科研管理部门负责人', 'unitHead', '单位科技部长', '高级工程师', 'UNIT', 1, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100005' OR u.username='100005');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100006', '$2b$10$CPlO/BDDUhmReA/KkbaLBuOW.sKneTJCuWPSLrdzOymQqjrZm.Kfi', '田念慈', '100006', 10, '上飞院', '科技管理部', '100006@comac.cc',
  '单位项目主管', 'unitStaff', '单位科技主管', '工程师', 'UNIT', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100006' OR u.username='100006');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100007', '$2b$10$rnb1TEumgBPz0NNm9/rty.60g2yAcmlvpWsUeiU6cSoA4EBNOqkai', '陈铁军', '100007', 1, '中国商飞总部', '科技管理部（总部办公室）', '100007@comac.cc',
  '一级总师（公司级）', 'chief1', '一级总师', '研究员', 'SELF', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100007' OR u.username='100007');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100008', '$2b$10$4A0HW7AmNyIV1ueKcKWHJOl4ByurfHUdAXJq0fYBjBGaLz0Jt10FW', '蔡文渊', '100008', 10, '上飞院', '科技管理部', '100008@comac.cc',
  '二级总师（单位级）', 'chief2', '二级总师', '研究员', 'SELF', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100008' OR u.username='100008');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100009', '$2b$10$NlKYpYqQ43EmW12E0h5fQ.9e7rGQDXDKZey2rZkSm..bNUlOvUzPy', '赵美玲', '100009', 1, '中国商飞总部', '财务部', '100009@comac.cc',
  '总部财务主管', 'finHq', '总部财务主管', '财务', 'COMPANY', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100009' OR u.username='100009');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100010', '$2b$10$t1ehpIFYPrBEXpIvogLZPuM/HcDBbi3lFHqVKE2OWe2/DJos.ND0G', '毕仲文', '100010', 10, '上飞院', '财务部', '100010@comac.cc',
  '单位财务部长', 'finHead', '单位财务部长', '财务', 'UNIT', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100010' OR u.username='100010');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100011', '$2b$10$C6SnwBMYfoq.wfg2qLfT2OO6PLSpIAlAGJM79IoD3jTBiDzJRMexy', '龚雪君', '100011', 10, '上飞院', '财务部', '100011@comac.cc',
  '单位财务主管', 'finStaff', '单位财务主管', '财务', 'UNIT', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100011' OR u.username='100011');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100012', '$2b$10$5UkkpPX1pYDPpG1pmH5usOQolOd1OJrLSu8yli5nxokm16PwUv16m', '林晚晴', '100012', 10, '上飞院', '科研项目处', '100012@comac.cc',
  '项目负责人', 'owner', '项目负责人', '工程师', 'SELF', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100012' OR u.username='100012');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100013', '$2b$10$dwkAE6eiXQa1ERy9VZyHfe2TvffEnXGNS0wy0p/6lJJV3llBOguai', '顾思远', '100013', 10, '上飞院', '科研项目处', '100013@comac.cc',
  '项目联系人', 'contactLogin', '项目联系人', '工程师', 'SELF', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100013' OR u.username='100013');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100014', '$2b$10$0bhQJW5wi8DnHed4Ot4pt.ANxSUdr0SCCaf9X7j0tkpLpgs1Lpgdu', '沈知行', '100014', 10, '上飞院', '科研项目处', '100014@comac.cc',
  '技术负责人', 'techLead', '技术负责人', '高级工程师', 'SELF', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100014' OR u.username='100014');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100015', '$2b$10$3qg/TMOxX4UtBGOLcK8eb.7w08pVQxyHcxf1wVm/jEUdVZRVgr0da', '陆嘉言', '100015', 10, '上飞院', '科研项目处', '100015@comac.cc',
  '项目主管', 'projectPm', '项目主管', '工程师', 'SELF', 0, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100015' OR u.username='100015');

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100016', '$2b$10$14n6xhFMcJYDwCsmt6axQe9H3/h2OVv3LymxUzqARh.MsRtrjRJH.', '韩承泽', '100016', 10, '上飞院', '总体气动部', '100016@comac.cc',
  '项目承担部门负责人', 'deptHead', '项目承担部门负责人', '高级工程师', 'DEPT', 1, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100016' OR u.username='100016');

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code='ADMIN' WHERE u.employee_no='100001' OR u.username='admin';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code='MANAGEMENT' WHERE u.employee_no IN ('100002','100003','100004','100005','100006','100016');
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code='CHIEF_ENGINEER' WHERE u.employee_no IN ('100007','100008');
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code='FINANCE' WHERE u.employee_no IN ('100009','100010','100011');
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code='PROJECT_TEAM' WHERE u.employee_no IN ('100012','100013','100014','100015');

SELECT employee_no, real_name, identity FROM sys_user WHERE employee_no LIKE '1000%' OR username='admin' ORDER BY employee_no;
