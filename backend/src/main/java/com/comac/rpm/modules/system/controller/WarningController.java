package com.comac.rpm.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.entity.SysWarning;
import com.comac.rpm.modules.system.mapper.SysWarningMapper;
import com.comac.rpm.modules.system.service.WarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预警中心：到期前 30 天黄色预警，超期红色告警；按工号投递给项目团队与对应管理团队。
 * 扫描由 WarningScheduler 每日执行，管理员可手动触发。
 */
@RestController
@RequestMapping("/api/warnings")
public class WarningController {

    @Autowired
    private SysWarningMapper warningMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;
    @Autowired
    private WarningService warningService;

    /** 当前用户相关的预警：总部/管理员全量；其他人只看投递给本人工号的（旧数据无工号时按本单位项目回落） */
    @GetMapping
    public R<PageVO<SysWarning>> page(@RequestParam(value = "page", defaultValue = "1") long page,
                                      @RequestParam(value = "size", defaultValue = "10") long size,
                                      @RequestParam(value = "isRead", required = false) Integer isRead,
                                      @RequestParam(value = "warnLevel", required = false) String warnLevel) {
        SysUser u = flowAuditGuard.currentUser();
        String no = u.getEmployeeNo() == null ? "" : u.getEmployeeNo().trim();
        LambdaQueryWrapper<SysWarning> w = new LambdaQueryWrapper<>();
        if (!FlowAuditGuard.isHqIdentity(FlowAuditGuard.identityOf(u))) {
            if (no.isEmpty()) {
                return R.ok(PageVO.of(new Page<>(page, size)));
            }
            w.apply("FIND_IN_SET({0}, receiver_nos) > 0", no);
        }
        if (isRead != null && !no.isEmpty()) {
            if (isRead == 1) {
                w.apply("FIND_IN_SET({0}, read_nos) > 0", no);
            } else {
                w.and(q -> q.isNull(SysWarning::getReadNos).or().apply("FIND_IN_SET({0}, read_nos) = 0", no));
            }
        }
        if (warnLevel != null && !warnLevel.isEmpty()) {
            w.eq(SysWarning::getWarnLevel, warnLevel);
        }
        w.orderByDesc(SysWarning::getCreatedAt);
        Page<SysWarning> result = warningMapper.selectPage(new Page<>(page, size), w);
        for (SysWarning item : result.getRecords()) {
            item.setIsRead(WarningService.containsNo(item.getReadNos(), no) ? 1 : 0);
        }
        return R.ok(PageVO.of(result));
    }

    /** 按人标记已读 */
    @PostMapping("/{id}/read")
    public R<Boolean> read(@PathVariable("id") Long id) {
        SysUser u = flowAuditGuard.currentUser();
        SysWarning existing = warningMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("预警不存在");
        }
        String no = u.getEmployeeNo() == null ? "" : u.getEmployeeNo().trim();
        boolean hq = FlowAuditGuard.isHqIdentity(FlowAuditGuard.identityOf(u));
        if (!hq && !WarningService.containsNo(existing.getReceiverNos(), no)) {
            throw new BusinessException(403, "该预警未投递给您");
        }
        SysWarning w = new SysWarning();
        w.setId(id);
        w.setReadNos(WarningService.appendNo(existing.getReadNos(), no));
        w.setIsRead(1);
        warningMapper.updateById(w);
        return R.ok(true);
    }

    /** 手动触发全量预警扫描（管理员） */
    @PostMapping("/scan")
    public R<Integer> scan() {
        flowAuditGuard.requireAdmin("执行全量预警扫描");
        return R.ok(warningService.scan());
    }
}
