package com.comac.rpm.modules.plan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.plan.entity.ProjPlan;
import com.comac.rpm.modules.plan.mapper.ProjPlanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 计划管理：自动同步 CMOS 计划数据，区分待办/已完成，四色展示
 */
@RestController
@RequestMapping("/api")
public class PlanController {

    @Autowired
    private ProjPlanMapper planMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping("/projects/{projectId}/plans")
    public R<List<ProjPlan>> list(@PathVariable("projectId") Long projectId,
                                  @RequestParam(value = "planType", required = false) String planType) {
        LambdaQueryWrapper<ProjPlan> w = new LambdaQueryWrapper<ProjPlan>()
                .eq(ProjPlan::getProjectId, projectId);
        if (planType != null && !planType.isEmpty()) {
            w.eq(ProjPlan::getPlanType, planType);
        }
        w.orderByAsc(ProjPlan::getDueDate);
        List<ProjPlan> list = planMapper.selectList(w);
        for (ProjPlan p : list) {
            p.setColorStatus(ColorUtil.calcCode(p.getDueDate(), "DONE".equals(p.getPlanType())));
            if ("RED".equals(p.getColorStatus()) && !"DONE".equals(p.getPlanType())) {
                p.setStatus("OVERDUE");
            }
        }
        return R.ok(list);
    }

    @PostMapping("/plans")
    public R<Long> create(@RequestBody ProjPlan body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增计划", "projectPm", "owner");
        body.setId(null);
        body.setColorStatus(ColorUtil.calcCode(body.getDueDate(), false));
        if (body.getPlanType() == null) {
            body.setPlanType("TODO");
        }
        if (body.getSource() == null) {
            body.setSource("CMOS");
        }
        planMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/plans/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjPlan body) {
        flowAuditGuard.requireIdentities("修改计划", "projectPm", "owner");
        ProjPlan existing = planMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("计划不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), "修改计划", "projectPm", "owner");
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        body.setColorStatus(ColorUtil.calcCode(body.getDueDate(), "DONE".equals(body.getPlanType())));
        planMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/plans/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        flowAuditGuard.requireIdentities("删除计划", "projectPm", "owner");
        ProjPlan existing = planMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("计划不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), "删除计划", "projectPm", "owner");
        planMapper.deleteById(id);
        return R.ok(true);
    }

    /** 办结申请 */
    @PostMapping("/plans/{id}/finish-apply")
    public R<Boolean> finishApply(@PathVariable("id") Long id) {
        flowAuditGuard.requireIdentities("提交计划办结申请", "projectPm", "owner");
        ProjPlan existing = planMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("计划不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), "提交计划办结申请", "projectPm", "owner");
        ProjPlan p = new ProjPlan();
        p.setId(id);
        p.setApplyStatus("PENDING");
        planMapper.updateById(p);
        return R.ok(true);
    }

    /**
     * 办结终审：二级单位管理团队终审，通过后自动转为「已完成计划」，全程无总部审批
     */
    @PostMapping("/plans/{id}/finish-audit")
    public R<Boolean> finishAudit(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjPlan exist = planMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("计划不存在");
        }
        flowAuditGuard.requireIdentities("二级单位管理团队终审", "unitHead");
        flowAuditGuard.requireProjectRelated(exist.getProjectId(), "unitHead");
        boolean pass = body != null && Boolean.TRUE.equals(body.get("pass"));
        ProjPlan p = new ProjPlan();
        p.setId(id);
        if (pass) {
            p.setPlanType("DONE");
            p.setStatus("DONE");
            p.setColorStatus("GREEN");
            p.setApplyStatus("APPROVED");
            p.setFinishDate(LocalDate.now());
        } else {
            p.setApplyStatus("REJECTED");
        }
        planMapper.updateById(p);
        return R.ok(true);
    }

    /**
     * 模拟从 CMOS 系统同步计划数据
     */
    @PostMapping("/plans/sync")
    public R<Integer> sync() {
        flowAuditGuard.requireAdmin("同步CMOS计划");
        return R.ok(planMapper.selectCount(null).intValue());
    }
}
