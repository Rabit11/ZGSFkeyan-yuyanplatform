package com.comac.rpm.modules.change.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.SeqUtil;
import com.comac.rpm.modules.change.entity.ProjChange;
import com.comac.rpm.modules.change.mapper.ProjChangeMapper;
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

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 项目变更 / 数据变更：实施阶段唯一调整渠道
 * <p>
 * 项目变更：二级单位主管部门初审 → 总部对应管理部门逐级终审；
 * 重大变更（外协单位更换、总经费调整、整体周期变更）强制联动法务部门审核。
 * 数据变更：二级单位内部审批 → 总部科技主管确认。
 */
@RestController
@RequestMapping("/api/changes")
public class ChangeController {

    /** 需要强制法务审核的重大变更类别 */
    private static final String LEGAL_CATEGORIES = ",OUTSOURCE,PERIOD,FUND,";

    @Autowired
    private ProjChangeMapper changeMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping
    public R<PageVO<ProjChange>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "changeType", required = false) String changeType,
            @RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<ProjChange> w = new LambdaQueryWrapper<>();
        if (projectId != null) {
            w.eq(ProjChange::getProjectId, projectId);
        }
        if (changeType != null && !changeType.isEmpty()) {
            w.eq(ProjChange::getChangeType, changeType);
        }
        if (status != null && !status.isEmpty()) {
            w.eq(ProjChange::getStatus, status);
        }
        w.orderByDesc(ProjChange::getCreatedAt);
        return R.ok(PageVO.of(changeMapper.selectPage(new Page<>(page, size), w)));
    }

    @PostMapping
    public R<Long> create(@RequestBody ProjChange body) {
        flowAuditGuard.requireActors(body.getProjectId(), "变更填写", "owner", "projectPm", "techLead", "contactLogin");
        body.setId(null);
        body.setChangeNo(SeqUtil.changeNo(changeMapper.selectCount(null) + 1));
        body.setStatus("DRAFT");
        body.setLegalReview(needLegal(body.getCategory()) ? 1 : 0);
        body.setApplicant(UserContext.getUsername());
        body.setCreatedAt(LocalDateTime.now());
        changeMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjChange body) {
        body.setId(id);
        changeMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        changeMapper.deleteById(id);
        return R.ok(true);
    }

    @PostMapping("/{id}/submit")
    public R<Boolean> submit(@PathVariable("id") Long id) {
        ProjChange c = changeMapper.selectById(id);
        if (c == null) {
            return R.fail("变更记录不存在");
        }
        flowAuditGuard.requireActors(c.getProjectId(), "变更提交", "owner", "projectPm", "contactLogin");
        c.setStatus("APPROVING");
        if ("DATA".equals(c.getChangeType())) {
            c.setFlowNode("二级单位内部审批");
        } else if (Integer.valueOf(1).equals(c.getLegalReview())) {
            c.setFlowNode("法务部门审核");
        } else {
            c.setFlowNode("二级单位主管部门初审");
        }
        changeMapper.updateById(c);
        return R.ok(true);
    }

    @PostMapping("/{id}/audit")
    public R<Boolean> audit(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjChange exist = changeMapper.selectById(id);
        if (exist == null) {
            return R.fail("变更记录不存在");
        }
        flowAuditGuard.requireFlowNode(exist.getFlowNode(), exist.getProjectId(),
                exist.getFlowNode() == null ? "变更审核" : exist.getFlowNode());
        boolean pass = body != null && Boolean.TRUE.equals(body.get("pass"));
        ProjChange c = new ProjChange();
        c.setId(id);
        c.setStatus(pass ? "APPROVED" : "REJECTED");
        changeMapper.updateById(c);
        return R.ok(true);
    }

    private boolean needLegal(String category) {
        return category != null && LEGAL_CATEGORIES.contains("," + category + ",");
    }
}
