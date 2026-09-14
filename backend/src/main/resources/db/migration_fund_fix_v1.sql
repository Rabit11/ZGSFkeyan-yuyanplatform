-- 项目经费模块修正（任务③）：仅涉及经费表 fund_budget / fund_payment
-- 1) 预算：同一里程碑仅允许一条有效预算（应用层校验为主，DB 加索引辅助）
-- 2) 核销：凭证号必填且唯一（应用层校验，含 REVERSE 红冲豁免）；新增 REVERSE 红冲流水类型
-- 3) 已备案预算金额锁定、核销不可撤销须红冲（逻辑在应用层，无需改结构）
-- MySQL 8+：逐句执行；若索引/注释已存在可忽略报错后继续
SET NAMES utf8mb4;

-- 预算按 (项目, 里程碑) 查唯一性用索引
ALTER TABLE `fund_budget` ADD INDEX `idx_project_milestone` (`project_id`, `milestone_id`);

-- 核销凭证号查询用索引（唯一性由应用层校验，红冲记录复用原凭证号故不建唯一索引）
ALTER TABLE `fund_payment` ADD INDEX `idx_voucher_no` (`voucher_no`);

-- flow_type 增加 REVERSE 红冲说明
ALTER TABLE `fund_payment`
  MODIFY COLUMN `flow_type` VARCHAR(16) NOT NULL
  COMMENT 'PAY付款/EXPENSE支出/WRITEOFF核销/REVERSE红冲';
