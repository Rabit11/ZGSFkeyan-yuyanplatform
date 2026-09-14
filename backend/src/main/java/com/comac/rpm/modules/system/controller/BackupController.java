package com.comac.rpm.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.modules.system.entity.SysBackup;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.comac.rpm.modules.system.mapper.SysBackupMapper;
import com.comac.rpm.modules.system.service.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 备份回滚：仅系统管理员。手动/定时全量快照，回滚前自动打安全点。
 */
@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private final BackupService backupService;
    private final SysBackupMapper backupMapper;
    private final SysAuditLogMapper auditLogMapper;

    public BackupController(BackupService backupService,
                            SysBackupMapper backupMapper,
                            SysAuditLogMapper auditLogMapper) {
        this.backupService = backupService;
        this.backupMapper = backupMapper;
        this.auditLogMapper = auditLogMapper;
    }

    @GetMapping
    public R<PageVO<SysBackup>> page(@RequestParam(value = "page", defaultValue = "1") long page,
                                     @RequestParam(value = "size", defaultValue = "10") long size,
                                     @RequestParam(value = "triggerType", required = false) String triggerType,
                                     @RequestParam(value = "status", required = false) String status) {
        requireAdmin();
        LambdaQueryWrapper<SysBackup> w = new LambdaQueryWrapper<>();
        if (triggerType != null && !triggerType.isBlank()) {
            w.eq(SysBackup::getTriggerType, triggerType.trim());
        }
        if (status != null && !status.isBlank()) {
            w.eq(SysBackup::getStatus, status.trim());
        }
        w.orderByDesc(SysBackup::getCreatedAt);
        return R.ok(PageVO.of(backupMapper.selectPage(new Page<>(page, size), w)));
    }

    @GetMapping("/policy")
    public R<Map<String, Object>> policy() {
        requireAdmin();
        return R.ok(backupService.policy());
    }

    @PostMapping
    public R<SysBackup> create(@RequestBody(required = false) Map<String, String> body) {
        requireAdmin();
        String remark = body == null ? null : body.get("remark");
        String trigger = body == null ? "MANUAL" : body.getOrDefault("triggerType", "MANUAL");
        SysBackup rec = backupService.create(trigger, remark == null || remark.isBlank() ? "手动备份" : remark);
        return R.ok(rec);
    }

    @PostMapping("/{id}/restore")
    public R<SysBackup> restore(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        requireAdmin();
        String confirmNo = body == null ? null : body.get("confirmNo");
        SysBackup rec = backupService.restore(id, confirmNo);
        return R.ok(rec);
    }

    @GetMapping("/{id}/file")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) throws Exception {
        requireAdmin();
        SysBackup rec = backupMapper.selectById(id);
        if (rec == null) {
            throw new BusinessException("备份不存在");
        }
        String name = rec.getBackupNo() + ".json.gz";
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        response.setContentType("application/gzip");
        try (InputStream in = backupService.openStream(rec)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
        auditLogMapper.write("BACKUP", "DOWNLOAD", "SYS_BACKUP", rec.getId(), "下载备份：" + rec.getBackupNo());
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "仅系统管理员可执行备份与回滚");
        }
    }
}
