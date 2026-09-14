-- 增量：项目承担部门负责人 韩承泽 / 100016
-- 密码由 migration_demo_password_employee_no_v1.sql 统一设置为各自工号
SET NAMES utf8mb4;

INSERT INTO sys_user (username, password, real_name, employee_no, org_id, org_name, dept_name, email,
  identity, identity_code, project_post, rank_title, data_scope, finish_auth, form_maint_scope, declare_result_access, status)
SELECT '100016', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '韩承泽', '100016', 10, '上飞院', '总体气动部', '100016@comac.cc',
  '项目承担部门负责人', 'deptHead', '项目承担部门负责人', '高级工程师', 'DEPT', 1, '', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.employee_no='100016' OR u.username='100016');

UPDATE sys_user SET
  real_name='韩承泽',
  org_id=10,
  org_name='上飞院',
  dept_name='总体气动部',
  identity='项目承担部门负责人',
  identity_code='deptHead',
  project_post='项目承担部门负责人',
  rank_title='高级工程师',
  data_scope='DEPT',
  finish_auth=1,
  email='100016@comac.cc',
  password='$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi'
WHERE employee_no='100016' OR username='100016';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.role_code='MANAGEMENT'
WHERE u.employee_no='100016' OR u.username='100016';

INSERT IGNORE INTO sys_post_permission (post_code, perm_code, enabled) VALUES
 ('deptHead','initiate_approval',1),
 ('deptHead','assess_archive',1),
 ('deptHead','members_edit',1);

INSERT IGNORE INTO proj_team_member (project_id, group_code, role_code, role_name, user_name, employee_no, sort)
SELECT p.id, 'MGMT', 'DEPT_HEAD', '项目承担部门负责人', '韩承泽', '100016', 14
FROM proj_info p
WHERE p.project_no LIKE 'XM2026S%'
  AND NOT EXISTS (
    SELECT 1 FROM proj_team_member m
    WHERE m.project_id = p.id AND m.role_code = 'DEPT_HEAD'
  );

SELECT employee_no, real_name, identity, identity_code FROM sys_user
WHERE employee_no='100016' OR username='100016';
