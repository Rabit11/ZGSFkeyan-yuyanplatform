package com.comac.rpm.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.system.entity.SysAuditLog;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志：全平台业务操作全程留痕，支持查询与审计溯源
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    @Autowired
    private SysAuditLogMapper auditLogMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    /**
     * 我的已办：只返回当前登录人成功完成的审批动作，供办理人长期追溯。
     * 同时兼容历史记录中 userId 尚未落库、仅保存工号或姓名的情况。
     */
    @GetMapping("/mine/done")
    public R<PageVO<SysAuditLog>> mineDone(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "module", required = false) String module) {
        SysUser user = flowAuditGuard.currentUser();
        LambdaQueryWrapper<SysAuditLog> w = new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getAction, "APPROVE")
                .and(q -> q.eq(SysAuditLog::getUserId, user.getId())
                        .or().eq(SysAuditLog::getUserName, user.getEmployeeNo())
                        .or().eq(SysAuditLog::getUserName, user.getUsername())
                        .or().eq(SysAuditLog::getUserName, user.getRealName()))
                .and(q -> q.isNull(SysAuditLog::getContent)
                        .or().notLikeRight(SysAuditLog::getContent, "失败："));
        if (module != null && !module.isBlank()) {
            w.eq(SysAuditLog::getModule, module);
        }
        w.orderByDesc(SysAuditLog::getCreatedAt);
        return R.ok(PageVO.of(auditLogMapper.selectPage(new Page<>(page, size), w)));
    }

    @GetMapping
    public R<PageVO<SysAuditLog>> page(@RequestParam(value = "page", defaultValue = "1") long page,
                                       @RequestParam(value = "size", defaultValue = "10") long size,
                                       @RequestParam(value = "module", required = false) String module,
                                       @RequestParam(value = "action", required = false) String action,
                                       @RequestParam(value = "userName", required = false) String userName) {
        LambdaQueryWrapper<SysAuditLog> w = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            w.like(SysAuditLog::getModule, module);
        }
        if (action != null && !action.isEmpty()) {
            w.eq(SysAuditLog::getAction, action);
        }
        if (userName != null && !userName.isEmpty()) {
            w.like(SysAuditLog::getUserName, userName);
        }
        w.orderByDesc(SysAuditLog::getCreatedAt);
        return R.ok(PageVO.of(auditLogMapper.selectPage(new Page<>(page, size), w)));
    }
}
