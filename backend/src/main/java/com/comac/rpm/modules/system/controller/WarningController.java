package com.comac.rpm.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.plan.entity.ProjPlan;
import com.comac.rpm.modules.plan.mapper.ProjPlanMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.system.entity.SysWarning;
import com.comac.rpm.modules.system.mapper.SysWarningMapper;
import com.comac.rpm.modules.transform.entity.AchvTransform;
import com.comac.rpm.modules.transform.mapper.AchvTransformMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警中心：到期前 30 天黄色预警，超期红色告警；推送方式为站内消息 + 企业邮箱 + 蓝信
 */
@RestController
@RequestMapping("/api/warnings")
public class WarningController {

    @Autowired
    private SysWarningMapper warningMapper;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private ProjMilestoneMapper milestoneMapper;
    @Autowired
    private ProjPlanMapper planMapper;
    @Autowired
    private ProjDeliverableMapper deliverableMapper;
    @Autowired
    private AchvTransformMapper transformMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping
    public R<PageVO<SysWarning>> page(@RequestParam(value = "page", defaultValue = "1") long page,
                                      @RequestParam(value = "size", defaultValue = "10") long size,
                                      @RequestParam(value = "isRead", required = false) Integer isRead,
                                      @RequestParam(value = "warnLevel", required = false) String warnLevel) {
        LambdaQueryWrapper<SysWarning> w = new LambdaQueryWrapper<>();
        if (isRead != null) {
            w.eq(SysWarning::getIsRead, isRead);
        }
        if (warnLevel != null && !warnLevel.isEmpty()) {
            w.eq(SysWarning::getWarnLevel, warnLevel);
        }
        w.orderByDesc(SysWarning::getCreatedAt);
        return R.ok(PageVO.of(warningMapper.selectPage(new Page<>(page, size), w)));
    }

    @PostMapping("/{id}/read")
    public R<Boolean> read(@PathVariable("id") Long id) {
        flowAuditGuard.requireIdentities("标记预警已读", "contactLogin", "owner", "techLead", "projectPm",
                "unitHead", "unitStaff", "deptHead", "hqHead", "hqStaff");
        SysWarning existing = warningMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("预警不存在");
        }
        flowAuditGuard.requireProjectRelated(existing.getProjectId(), "contactLogin", "owner", "techLead", "projectPm",
                "unitHead", "unitStaff", "deptHead", "hqHead", "hqStaff");
        SysWarning w = new SysWarning();
        w.setId(id);
        w.setIsRead(1);
        warningMapper.updateById(w);
        return R.ok(true);
    }

    /**
     * 手动触发全量预警扫描：同一业务、同一天、同一级别只生成一条
     */
    @PostMapping("/scan")
    public R<Integer> scan() {
        flowAuditGuard.requireAdmin("执行全量预警扫描");
        List<ProjInfo> projects = projectMapper.selectList(null);
        Map<Long, String> names = new LinkedHashMap<>();
        for (ProjInfo p : projects) {
            names.put(p.getId(), p.getName());
        }
        List<SysWarning> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (ProjMilestone m : milestoneMapper.selectList(null)) {
            String c = ColorUtil.calcCode(m.getPlanDate(), "DONE".equals(m.getStatus()));
            if (isWarn(c)) {
                result.add(build("MILESTONE", m.getId(), m.getProjectId(), names.get(m.getProjectId()), c,
                        ("RED".equals(c) ? "里程碑逾期：" : "里程碑临期：") + m.getName(),
                        "计划完成时间 " + m.getPlanDate(), today));
            }
        }
        for (ProjPlan p : planMapper.selectList(null)) {
            if (!"TODO".equals(p.getPlanType())) {
                continue;
            }
            String c = ColorUtil.calcCode(p.getDueDate(), false);
            if (isWarn(c)) {
                result.add(build("PLAN", p.getId(), p.getProjectId(), names.get(p.getProjectId()), c,
                        ("RED".equals(c) ? "计划超期：" : "计划临期：") + p.getTitle(),
                        "待办计划到期日 " + p.getDueDate(), today));
            }
        }
        for (ProjDeliverable d : deliverableMapper.selectList(null)) {
            String c = ColorUtil.calcCode(d.getDueDate(), "DELIVERED".equals(d.getStatus()));
            if (isWarn(c)) {
                result.add(build("DELIVERABLE", d.getId(), d.getProjectId(), names.get(d.getProjectId()), c,
                        ("RED".equals(c) ? "交付物逾期：" : "交付物临期：") + d.getName(),
                        "应交付时间 " + d.getDueDate(), today));
            }
        }
        for (AchvTransform t : transformMapper.selectList(null)) {
            String c = ColorUtil.calcCode(t.getPlanDate(), "DONE".equals(t.getStatus()));
            if (isWarn(c)) {
                result.add(build("TRANSFORM", t.getId(), t.getProjectId(), t.getName(), c,
                        ("RED".equals(c) ? "成果转化逾期：" : "成果转化临期：") + t.getName(),
                        "计划转化时间 " + t.getPlanDate(), today));
            }
        }

        int count = 0;
        for (SysWarning w : result) {
            long exists = warningMapper.selectCount(new LambdaQueryWrapper<SysWarning>()
                    .eq(SysWarning::getBizType, w.getBizType())
                    .eq(SysWarning::getBizId, w.getBizId())
                    .eq(SysWarning::getWarnLevel, w.getWarnLevel())
                    .ge(SysWarning::getCreatedAt, today.atStartOfDay()));
            if (exists == 0) {
                warningMapper.insert(w);
                count++;
            }
        }
        return R.ok(count);
    }

    private boolean isWarn(String color) {
        return "RED".equals(color) || "YELLOW".equals(color);
    }

    private SysWarning build(String bizType, Long bizId, Long projectId, String projectName,
                             String level, String title, String content, LocalDate today) {
        SysWarning w = new SysWarning();
        w.setBizType(bizType);
        w.setBizId(bizId);
        w.setProjectId(projectId);
        w.setProjectName(projectName);
        w.setWarnLevel(level);
        w.setTitle(title);
        w.setContent(content);
        w.setReceiver("PROJECT_TEAM,MANAGEMENT");
        w.setIsRead(0);
        w.setCreatedAt(today.atStartOfDay());
        return w;
    }
}
