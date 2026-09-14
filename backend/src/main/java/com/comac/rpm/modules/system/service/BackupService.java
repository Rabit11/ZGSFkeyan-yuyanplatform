package com.comac.rpm.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.config.BackupProperties;
import com.comac.rpm.config.MinioProperties;
import com.comac.rpm.modules.system.entity.SysBackup;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.comac.rpm.modules.system.mapper.SysBackupMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 逻辑备份 / 回滚：
 * <ul>
 *   <li>备份：全库业务表导出为 gzip JSON，并记录 MinIO 附件清单</li>
 *   <li>回滚：先自动打 PRE_RESTORE 安全点，再按快照 DELETE+INSERT 恢复（可再回滚）</li>
 *   <li>不覆盖审计日志与预警（持续留痕；预警可重新扫描）</li>
 * </ul>
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> SKIP_TABLES = Set.of("sys_backup", "sys_audit_log", "sys_warning");
    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `sys_backup` (
              `id`            BIGINT       NOT NULL AUTO_INCREMENT,
              `backup_no`     VARCHAR(64)  NOT NULL,
              `trigger_type`  VARCHAR(32)  NOT NULL,
              `status`        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
              `remark`        VARCHAR(255) DEFAULT NULL,
              `file_path`     VARCHAR(512) DEFAULT NULL,
              `object_key`    VARCHAR(512) DEFAULT NULL,
              `file_size`     BIGINT       DEFAULT NULL,
              `table_count`   INT          DEFAULT NULL,
              `row_count`     INT          DEFAULT NULL,
              `object_count`  INT          DEFAULT NULL,
              `checksum`      VARCHAR(64)  DEFAULT NULL,
              `error_msg`     VARCHAR(1000) DEFAULT NULL,
              `created_by`    VARCHAR(64)  DEFAULT NULL,
              `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
              `finished_at`   DATETIME     DEFAULT NULL,
              `restored_at`   DATETIME     DEFAULT NULL,
              `restored_by`   VARCHAR(64)  DEFAULT NULL,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_backup_no` (`backup_no`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final ReentrantLock lock = new ReentrantLock();
    private final JdbcTemplate jdbc;
    private final SysBackupMapper backupMapper;
    private final SysAuditLogMapper auditLogMapper;
    private final BackupProperties props;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final TransactionTemplate txTemplate;

    public BackupService(JdbcTemplate jdbc,
                         SysBackupMapper backupMapper,
                         SysAuditLogMapper auditLogMapper,
                         BackupProperties props,
                         ObjectMapper objectMapper,
                         MinioClient minioClient,
                         MinioProperties minioProperties,
                         PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.backupMapper = backupMapper;
        this.auditLogMapper = auditLogMapper;
        this.props = props;
        this.objectMapper = objectMapper;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void ensureTable() {
        jdbc.execute(DDL);
        try {
            Files.createDirectories(Path.of(props.getDir()));
        } catch (Exception e) {
            log.warn("创建备份目录失败: {}", e.getMessage());
        }
    }

    public SysBackup create(String triggerType, String remark) {
        if (!lock.tryLock()) {
            throw new BusinessException("已有备份或回滚任务进行中，请稍后再试");
        }
        try {
            return doCreate(triggerType, remark);
        } finally {
            lock.unlock();
        }
    }

    private SysBackup doCreate(String triggerType, String remark) {
        SysBackup rec = new SysBackup();
        try {
            String type = normalizeTrigger(triggerType);
            String no = "BK" + LocalDateTime.now().format(NO_FMT);
            rec.setBackupNo(no);
            rec.setTriggerType(type);
            rec.setStatus("RUNNING");
            rec.setRemark(remark);
            rec.setCreatedBy(operator());
            rec.setCreatedAt(LocalDateTime.now());
            backupMapper.insert(rec);

            Map<String, Object> payload = dumpPayload(no, type, remark);
            Path file = Path.of(props.getDir(), no + ".json.gz");
            Files.createDirectories(file.getParent());
            try (OutputStream raw = Files.newOutputStream(file);
                 GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
                objectMapper.writeValue(gzip, payload);
            }
            long size = Files.size(file);
            String checksum = sha256(file);
            String objectKey = "system-backup/" + no + ".json.gz";
            try {
                byte[] bytes = Files.readAllBytes(file);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType("application/gzip")
                        .build());
                rec.setObjectKey(objectKey);
            } catch (Exception e) {
                log.warn("备份副本写入 MinIO 失败（本地文件仍有效）: {}", e.getMessage());
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) payload.get("tables");
            int rows = 0;
            if (tables != null) {
                for (Map<String, Object> t : tables) {
                    Object r = t.get("rows");
                    if (r instanceof List<?> list) {
                        rows += list.size();
                    }
                }
            }
            rec.setFilePath(file.toAbsolutePath().toString());
            rec.setFileSize(size);
            rec.setTableCount(tables == null ? 0 : tables.size());
            rec.setRowCount(rows);
            rec.setObjectCount(payload.get("objects") instanceof List<?> o ? o.size() : 0);
            rec.setChecksum(checksum);
            rec.setStatus("SUCCESS");
            rec.setFinishedAt(LocalDateTime.now());
            backupMapper.updateById(rec);
            auditLogMapper.write("BACKUP", "BACKUP", "SYS_BACKUP", rec.getId(),
                    "创建备份：" + rec.getBackupNo() + " / " + rec.getTriggerType());
            purgeOld();
            log.info("备份完成 {} tables={} rows={} size={}", no, rec.getTableCount(), rows, size);
            return rec;
        } catch (BusinessException e) {
            fail(rec, e.getMessage());
            throw e;
        } catch (Exception e) {
            fail(rec, e.getMessage());
            throw new BusinessException("备份失败：" + e.getMessage());
        }
    }

    public SysBackup restore(Long id, String confirmNo) {
        SysBackup target = backupMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("备份不存在");
        }
        if (!"SUCCESS".equals(target.getStatus())) {
            throw new BusinessException("仅成功的备份可用于回滚");
        }
        if (confirmNo == null || !confirmNo.trim().equals(target.getBackupNo())) {
            throw new BusinessException("请输入完整备份编号以确认回滚：" + target.getBackupNo());
        }
        if (!lock.tryLock()) {
            throw new BusinessException("已有备份或回滚任务进行中，请稍后再试");
        }
        try {
            SysBackup safety = doCreate("PRE_RESTORE", "回滚前安全点 ← " + target.getBackupNo());
            Map<String, Object> payload = readPayload(target);
            txTemplate.executeWithoutResult(status -> applyPayload(payload));
            target.setRestoredAt(LocalDateTime.now());
            target.setRestoredBy(operator());
            backupMapper.updateById(target);
            auditLogMapper.write("BACKUP", "RESTORE", "SYS_BACKUP", target.getId(),
                    "回滚到备份：" + target.getBackupNo());
            log.info("回滚完成 {}，安全点 {}", target.getBackupNo(), safety.getBackupNo());
            return target;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("回滚失败：" + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public Path resolveFile(SysBackup rec) {
        if (rec.getFilePath() != null) {
            Path p = Path.of(rec.getFilePath());
            if (Files.exists(p)) {
                return p;
            }
        }
        return Path.of(props.getDir(), rec.getBackupNo() + ".json.gz");
    }

    public InputStream openStream(SysBackup rec) throws Exception {
        Path local = resolveFile(rec);
        if (Files.exists(local)) {
            return Files.newInputStream(local);
        }
        if (rec.getObjectKey() != null && !rec.getObjectKey().isBlank()) {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(rec.getObjectKey())
                    .build());
        }
        throw new BusinessException("备份文件不存在，无法下载或回滚");
    }

    public Map<String, Object> policy() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dir", Path.of(props.getDir()).toAbsolutePath().toString());
        m.put("keepDays", props.getKeepDays());
        m.put("maxCount", props.getMaxCount());
        m.put("cron", props.getCron());
        m.put("includeFiles", props.isIncludeFiles());
        m.put("cronText", "每天 02:00 自动全量备份");
        return m;
    }

    private Map<String, Object> dumpPayload(String no, String type, String remark) {
        List<String> tableNames = listBusinessTables();
        List<Map<String, Object>> tables = new ArrayList<>();
        for (String name : tableNames) {
            tables.add(dumpTable(name));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("backupNo", no);
        payload.put("triggerType", type);
        payload.put("remark", remark);
        payload.put("createdAt", LocalDateTime.now().toString());
        payload.put("tables", tables);
        payload.put("objects", listAttachmentKeys());
        return payload;
    }

    private Map<String, Object> dumpTable(String table) {
        String sql = "SELECT * FROM `" + table + "`";
        return jdbc.query(sql, (ResultSet rs) -> {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= cols; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>(cols);
                for (int i = 1; i <= cols; i++) {
                    row.add(normalizeCell(rs.getObject(i)));
                }
                rows.add(row);
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", table);
            t.put("columns", columns);
            t.put("rows", rows);
            return t;
        });
    }

    private void applyPayload(Map<String, Object> payload) {
        Object tablesObj = payload.get("tables");
        if (!(tablesObj instanceof List<?> tables) || tables.isEmpty()) {
            throw new BusinessException("备份包中没有可恢复的表数据");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            for (Object item : tables) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                String name = String.valueOf(raw.get("name"));
                if (!isSafeTable(name) || SKIP_TABLES.contains(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                jdbc.update("DELETE FROM `" + name + "`");
            }
            for (Object item : tables) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                restoreTable(raw);
            }
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreTable(Map<?, ?> raw) {
        String name = String.valueOf(raw.get("name"));
        if (!isSafeTable(name) || SKIP_TABLES.contains(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        List<String> columns = (List<String>) raw.get("columns");
        List<List<Object>> rows = (List<List<Object>>) raw.get("rows");
        if (columns == null || columns.isEmpty() || rows == null || rows.isEmpty()) {
            return;
        }
        for (String col : columns) {
            if (!isSafeIdent(col)) {
                throw new BusinessException("备份包列名非法：" + col);
            }
        }
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(name).append("` (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('`').append(columns.get(i)).append('`');
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');
        String insert = sql.toString();
        jdbc.batchUpdate(insert, rows, 200, (ps, row) -> {
            for (int i = 0; i < columns.size(); i++) {
                Object v = i < row.size() ? row.get(i) : null;
                ps.setObject(i + 1, v);
            }
        });
    }

    private Map<String, Object> readPayload(SysBackup rec) throws Exception {
        try (InputStream in = openStream(rec);
             GZIPInputStream gzip = new GZIPInputStream(in)) {
            return objectMapper.readValue(gzip, new TypeReference<Map<String, Object>>() {
            });
        }
    }

    private List<String> listBusinessTables() {
        String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);
        List<String> names = jdbc.query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE'",
                (rs, i) -> rs.getString(1),
                schema);
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (n != null && isSafeTable(n) && !SKIP_TABLES.contains(n.toLowerCase(Locale.ROOT))) {
                out.add(n);
            }
        }
        return out;
    }

    private List<String> listAttachmentKeys() {
        List<String> keys = new ArrayList<>();
        try {
            Iterable<Result<Item>> it = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .recursive(true)
                    .build());
            for (Result<Item> r : it) {
                Item item = r.get();
                if (item == null || item.isDir()) {
                    continue;
                }
                String name = item.objectName();
                if (name == null || name.startsWith("system-backup/")) {
                    continue;
                }
                keys.add(name);
            }
        } catch (Exception e) {
            log.warn("列举附件清单失败: {}", e.getMessage());
        }
        return keys;
    }

    private void purgeOld() {
        List<SysBackup> all = backupMapper.selectList(new LambdaQueryWrapper<SysBackup>()
                .eq(SysBackup::getStatus, "SUCCESS")
                .orderByAsc(SysBackup::getCreatedAt));
        if (all.size() <= 3) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, props.getKeepDays()));
        int keep = Math.max(3, props.getMaxCount());
        List<SysBackup> removable = new ArrayList<>();
        for (SysBackup b : all) {
            boolean old = b.getCreatedAt() != null && b.getCreatedAt().isBefore(cutoff);
            boolean overflow = all.size() - removable.size() > keep;
            boolean scheduled = "SCHEDULED".equals(b.getTriggerType());
            if ((old && scheduled) || overflow) {
                if ("PRE_RESTORE".equals(b.getTriggerType()) && b.getCreatedAt() != null
                        && b.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
                    continue;
                }
                removable.add(b);
            }
        }
        while (all.size() - removable.size() < 3 && !removable.isEmpty()) {
            removable.remove(removable.size() - 1);
        }
        for (SysBackup b : removable) {
            try {
                Path p = resolveFile(b);
                Files.deleteIfExists(p);
            } catch (Exception ignored) {
                // ignore
            }
            backupMapper.deleteById(b.getId());
        }
    }

    private void fail(SysBackup rec, String msg) {
        if (rec.getId() == null) {
            return;
        }
        rec.setStatus("FAILED");
        rec.setErrorMsg(msg == null ? "未知错误" : (msg.length() > 1000 ? msg.substring(0, 1000) : msg));
        rec.setFinishedAt(LocalDateTime.now());
        backupMapper.updateById(rec);
    }

    private Object normalizeCell(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString().replace('T', ' ');
        }
        if (v instanceof Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString().replace('T', ' ');
        }
        if (v instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return v;
    }

    private String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(md.digest());
    }

    private boolean isSafeTable(String name) {
        return isSafeIdent(name);
    }

    private boolean isSafeIdent(String name) {
        return name != null && name.matches("[A-Za-z0-9_]+");
    }

    private String normalizeTrigger(String triggerType) {
        if (triggerType == null || triggerType.isBlank()) {
            return "MANUAL";
        }
        String t = triggerType.trim().toUpperCase(Locale.ROOT);
        if (t.equals("MANUAL") || t.equals("SCHEDULED") || t.equals("PRE_RESTORE") || t.equals("PRE_RISK")) {
            return t;
        }
        return "MANUAL";
    }

    private String operator() {
        String name = UserContext.getUsername();
        return name == null || name.isBlank() ? "SYSTEM" : name;
    }
}
