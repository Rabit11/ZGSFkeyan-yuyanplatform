-- 成果转化增量迁移；先备份，再在部署新后端前执行。兼容 MySQL 8 / PolarDB。
-- 可重复执行，不重建业务表。历史“已完成”先保留为本轮填报值，完成审核备案后才进入全局闭环统计。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='achv_transform' AND column_name='workflow_status')=0, 'ALTER TABLE achv_transform ADD COLUMN workflow_status VARCHAR(32) NOT NULL DEFAULT ''DRAFT''', 'SELECT 1');
PREPARE transform_migration FROM @ddl; EXECUTE transform_migration; DEALLOCATE PREPARE transform_migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='achv_transform' AND column_name='reported_status')=0, 'ALTER TABLE achv_transform ADD COLUMN reported_status VARCHAR(32) NULL', 'SELECT 1');
PREPARE transform_migration FROM @ddl; EXECUTE transform_migration; DEALLOCATE PREPARE transform_migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='achv_transform' AND column_name='reported_actual_date')=0, 'ALTER TABLE achv_transform ADD COLUMN reported_actual_date DATE NULL', 'SELECT 1');
PREPARE transform_migration FROM @ddl; EXECUTE transform_migration; DEALLOCATE PREPARE transform_migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='achv_transform' AND column_name='revision')=0, 'ALTER TABLE achv_transform ADD COLUMN revision BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE transform_migration FROM @ddl; EXECUTE transform_migration; DEALLOCATE PREPARE transform_migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='achv_transform' AND column_name='evidence_json')=0, 'ALTER TABLE achv_transform ADD COLUMN evidence_json LONGTEXT NULL', 'SELECT 1');
PREPARE transform_migration FROM @ddl; EXECUTE transform_migration; DEALLOCATE PREPARE transform_migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='achv_transform' AND column_name='history_json')=0, 'ALTER TABLE achv_transform ADD COLUMN history_json LONGTEXT NULL', 'SELECT 1');
PREPARE transform_migration FROM @ddl; EXECUTE transform_migration; DEALLOCATE PREPARE transform_migration;

UPDATE achv_transform
SET reported_status=status,
    reported_actual_date=actual_date,
    status=IF(status='DONE','SIGNED',status),
    actual_date=NULL,
    history_json=COALESCE(history_json,'[]')
WHERE reported_status IS NULL;

UPDATE sys_dict SET parent_code='MODEL' WHERE dict_type='TRANSFORM_FORM' AND dict_code IN ('INSTALLED','UNINSTALLED') AND (parent_code IS NULL OR parent_code='');
UPDATE sys_dict SET parent_code='MARKET' WHERE dict_type='TRANSFORM_FORM' AND dict_code IN ('TRANSFER','LICENSE','JOINT','INVEST','OTHER') AND (parent_code IS NULL OR parent_code='');
INSERT INTO sys_dict (dict_type,dict_code,dict_name,sort,status)
SELECT 'TRANSFORM_STATUS',v.code,v.name,v.sort,1 FROM (
 SELECT 'NOT_STARTED' code,'未启动' name,1 sort UNION ALL SELECT 'NEGOTIATING','洽谈中',2 UNION ALL SELECT 'SIGNED','已签协议',3 UNION ALL SELECT 'DONE','已完成',4
) v WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.dict_type='TRANSFORM_STATUS' AND d.dict_code=v.code);
