package com.comac.rpm.modules.deliverable.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交付物：与任务书考核指标一一对应，支撑成果分类统计与成果转化联动
 */
@RestController
@RequestMapping("/api")
public class DeliverableController {

    @Autowired
    private ProjDeliverableMapper deliverableMapper;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping("/deliverables")
    public R<PageVO<ProjDeliverable>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "deliverType", required = false) String deliverType,
            @RequestParam(value = "ownerOrg", required = false) String ownerOrg) {
        LambdaQueryWrapper<ProjDeliverable> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(ProjDeliverable::getName, keyword)
                    .or().like(ProjDeliverable::getAchievementNo, keyword));
        }
        if (status != null && !status.isEmpty()) {
            w.eq(ProjDeliverable::getStatus, status);
        }
        if (deliverType != null && !deliverType.isEmpty()) {
            w.eq(ProjDeliverable::getDeliverType, deliverType);
        }
        if (ownerOrg != null && !ownerOrg.isEmpty()) {
            w.like(ProjDeliverable::getOwnerOrgs, ownerOrg);
        }
        w.orderByDesc(ProjDeliverable::getId);
        Page<ProjDeliverable> p = deliverableMapper.selectPage(new Page<>(page, size), w);
        Map<Long, ProjInfo> projects = new HashMap<>();
        for (ProjDeliverable d : p.getRecords()) {
            decorate(d, projects);
        }
        return R.ok(PageVO.of(p));
    }

    private void decorate(ProjDeliverable d, Map<Long, ProjInfo> cache) {
        d.setColorStatus(ColorUtil.calcCode(d.getDueDate(), "DELIVERED".equals(d.getStatus())));
        if ("RED".equals(d.getColorStatus()) && !"DELIVERED".equals(d.getStatus())) {
            d.setStatus("OVERDUE");
        }
        if (d.getProjectId() == null) {
            return;
        }
        ProjInfo p = cache.computeIfAbsent(d.getProjectId(), projectMapper::selectById);
        if (p != null) {
            d.setProjectName(p.getName());
            d.setProjectNo(p.getProjectNo());
        }
    }

    @GetMapping("/projects/{projectId}/deliverables")
    public R<List<ProjDeliverable>> list(@PathVariable("projectId") Long projectId) {
        List<ProjDeliverable> list = deliverableMapper.selectList(
                new LambdaQueryWrapper<ProjDeliverable>().eq(ProjDeliverable::getProjectId, projectId)
                        .orderByAsc(ProjDeliverable::getDueDate));
        Map<Long, ProjInfo> projects = new HashMap<>();
        for (ProjDeliverable d : list) {
            decorate(d, projects);
        }
        return R.ok(list);
    }

    @PostMapping("/deliverables")
    public R<Long> create(@RequestBody ProjDeliverable body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增交付物", "techLead", "owner");
        body.setId(null);
        if (body.getStatus() == null) {
            body.setStatus("PENDING");
        }
        body.setColorStatus(ColorUtil.calcCode(body.getDueDate(), "DELIVERED".equals(body.getStatus())));
        deliverableMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/deliverables/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjDeliverable body) {
        ProjDeliverable existing = writable(id, "修改交付物");
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        if ("DELIVERED".equals(body.getStatus()) && body.getDeliverDate() == null) {
            body.setDeliverDate(LocalDate.now());
        }
        body.setColorStatus(ColorUtil.calcCode(body.getDueDate(), "DELIVERED".equals(body.getStatus())));
        deliverableMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/deliverables/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        writable(id, "删除交付物");
        deliverableMapper.deleteById(id);
        return R.ok(true);
    }

    /** 绑定成果编号：多项交付物可绑定同一成果编号实现打包转化 */
    @PostMapping("/deliverables/{id}/bind")
    public R<Boolean> bind(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        writable(id, "绑定交付物成果编号");
        ProjDeliverable d = new ProjDeliverable();
        d.setId(id);
        d.setAchievementNo(body.get("achievementNo") == null ? null : String.valueOf(body.get("achievementNo")));
        deliverableMapper.updateById(d);
        return R.ok(true);
    }

    private ProjDeliverable writable(Long id, String action) {
        flowAuditGuard.requireIdentities(action, "techLead", "owner");
        ProjDeliverable existing = deliverableMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("交付物不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), action, "techLead", "owner");
        return existing;
    }
}
