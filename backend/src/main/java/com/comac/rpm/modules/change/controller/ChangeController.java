package com.comac.rpm.modules.change.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.common.util.SeqUtil;
import com.comac.rpm.modules.change.entity.ProjChange;
import com.comac.rpm.modules.change.mapper.ProjChangeMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目变更 / 数据变更：实施阶段唯一调整渠道。
 * <p>
 * 项目变更：二级单位主管部门初审 → 总部对应管理部门终审 →（重大变更）法务部门审核；
 * 数据变更：二级单位内部审批 → 总部科技主管确认。
 * 终审通过后执行变更效果：MILESTONE_DELAY 回写里程碑计划日期并计延期次数。
 */
@RestController
@RequestMapping("/api/changes")
public class ChangeController {

    /** 需要强制法务审核的重大变更类别 */
    private static final String LEGAL_CATEGORIES = ",OUTSOURCE,PERIOD,FUND,";

    private static final String NODE_UNIT_FIRST = "二级单位主管部门初审";
    private static final String NODE_HQ_FINAL = "总部对应管理部门终审";
    private static final String NODE_LEGAL = "法务部门审核";
    private static final String NODE_DATA_UNIT = "二级单位内部审批";
    private static final String NODE_DATA_HQ = "总部科技主管确认";

    private static final Set<String> EDITABLE_STATUS = Set.of("DRAFT", "REJECTED");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ProjChangeMapper changeMapper;
    @Autowired
    private ProjMilestoneMapper milestoneMapper;
    @Autowired
    private ProjInfoMapper projInfoMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;
    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @GetMapping
    public R<PageVO<ProjChange>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "changeType", required = false) String changeType,
            @RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<ProjChange> w = new LambdaQueryWrapper<>();
        if (projectId != null) {
            flowAuditGuard.requireProjectAccess(projectId);
            w.eq(ProjChange::getProjectId, projectId);
        } else {
            SysUser u = flowAuditGuard.currentUser();
            if (!FlowAuditGuard.isHqIdentity(FlowAuditGuard.identityOf(u))) {
                Set<Long> ids = flowAuditGuard.assignedProjectIds(u);
                projInfoMapper.selectList(new LambdaQueryWrapper<ProjInfo>()
                                .eq(ProjInfo::getOrgId, UserContext.getOrgId()).select(ProjInfo::getId))
                        .forEach(p -> ids.add(p.getId()));
                if (ids.isEmpty()) {
                    return R.ok(PageVO.of(new Page<>(page, size)));
                }
                w.in(ProjChange::getProjectId, ids);
            }
        }
        if (changeType != null && !changeType.isEmpty()) {
            w.eq(ProjChange::getChangeType, changeType);
        }
        if (status != null && !status.isEmpty()) {
            w.eq(ProjChange::getStatus, status);
        }
        w.orderByDesc(ProjChange::getCreatedAt);
        Page<ProjChange> result = changeMapper.selectPage(new Page<>(page, size), w);
        result.getRecords().forEach(this::attachTrail);
        return R.ok(PageVO.of(result));
    }

    @GetMapping("/{id}")
    public R<ProjChange> detail(@PathVariable("id") Long id) {
        ProjChange c = requireChange(id);
        flowAuditGuard.requireProjectAccess(c.getProjectId());
        attachTrail(c);
        return R.ok(c);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Long> create(@RequestBody ProjChange body) {
        flowAuditGuard.requireActors(body.getProjectId(), "变更填写", "owner", "projectPm", "techLead", "contactLogin");
        ProjInfo project = projInfoMapper.selectById(body.getProjectId());
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        ProjChange c = new ProjChange();
        c.setProjectId(body.getProjectId());
        c.setProjectName(project.getName());
        c.setChangeType("DATA".equals(body.getChangeType()) ? "DATA" : "PROJECT");
        c.setCategory(body.getCategory());
        c.setTitle(body.getTitle());
        c.setReason(body.getReason());
        c.setBeforeValue(body.getBeforeValue());
        c.setAfterValue(body.getAfterValue());
        if ("MILESTONE_DELAY".equals(body.getCategory())) {
            if (body.getMilestoneId() == null || body.getNewPlanDate() == null) {
                throw new BusinessException("里程碑延期变更请从里程碑页面的【延期申请】发起，需指定节点与新日期");
            }
            ProjMilestone m = milestoneMapper.selectById(body.getMilestoneId());
            if (m == null || !m.getProjectId().equals(body.getProjectId())) {
                throw new BusinessException("里程碑不存在或不属于该项目");
            }
            c.setMilestoneId(m.getId());
            c.setNewPlanDate(body.getNewPlanDate());
            c.setBeforeValue(String.valueOf(m.getPlanDate()));
            c.setAfterValue(String.valueOf(body.getNewPlanDate()));
        }
        c.setChangeNo(nextChangeNo());
        c.setStatus("DRAFT");
        c.setLegalReview(needLegal(c.getCategory()) ? 1 : 0);
        c.setApplicant(UserContext.getUsername());
        c.setCreatedAt(LocalDateTime.now());
        changeMapper.insert(c);
        auditLogMapper.write("CHANGE", "CREATE", "CHANGE", c.getId(), "创建变更单 " + c.getChangeNo() + "：" + c.getTitle());
        return R.ok(c.getId());
    }

    /** 草稿 / 已驳回的变更单，仅申请人或项目团队可修改内容字段 */
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjChange body) {
        ProjChange exist = requireChange(id);
        requireEditable(exist, "修改变更单");
        ProjChange patch = new ProjChange();
        patch.setId(id);
        patch.setTitle(body.getTitle());
        patch.setReason(body.getReason());
        patch.setBeforeValue(body.getBeforeValue());
        patch.setAfterValue(body.getAfterValue());
        if (!"MILESTONE_DELAY".equals(exist.getCategory())) {
            patch.setCategory(body.getCategory());
            patch.setChangeType("DATA".equals(body.getChangeType()) ? "DATA" : body.getChangeType() == null ? null : "PROJECT");
            patch.setLegalReview(needLegal(body.getCategory() == null ? exist.getCategory() : body.getCategory()) ? 1 : 0);
        } else if (body.getNewPlanDate() != null) {
            patch.setNewPlanDate(body.getNewPlanDate());
            patch.setAfterValue(String.valueOf(body.getNewPlanDate()));
        }
        patch.setStatus("DRAFT");
        patch.setFlowNode(null);
        changeMapper.updateById(patch);
        auditLogMapper.write("CHANGE", "UPDATE", "CHANGE", id, "修改变更单 " + exist.getChangeNo());
        return R.ok(true);
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> delete(@PathVariable("id") Long id) {
        ProjChange exist = requireChange(id);
        requireEditable(exist, "删除变更单");
        changeMapper.deleteById(id);
        auditLogMapper.write("CHANGE", "DELETE", "CHANGE", id, "删除变更单（逻辑删除）" + exist.getChangeNo());
        return R.ok(true);
    }

    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> submit(@PathVariable("id") Long id) {
        ProjChange c = requireChange(id);
        if (!EDITABLE_STATUS.contains(nvl(c.getStatus()))) {
            throw new BusinessException("变更单当前状态不可提交：" + c.getStatus());
        }
        flowAuditGuard.requireActors(c.getProjectId(), "变更提交", "owner", "projectPm", "techLead", "contactLogin");
        if (c.getReason() == null || c.getReason().isBlank()) {
            throw new BusinessException("请填写变更缘由");
        }
        ProjChange patch = new ProjChange();
        patch.setId(id);
        patch.setStatus("APPROVING");
        patch.setFlowNode(flowNodes(c).get(0));
        patch.setAuditTrailJson(appendTrail(c, "提交", true, "提交审批"));
        changeMapper.updateById(patch);
        auditLogMapper.write("CHANGE", "SUBMIT", "CHANGE", id, "提交变更审批 " + c.getChangeNo() + "，节点：" + patch.getFlowNode());
        return R.ok(true);
    }

    /**
     * 逐节点审批：通过则流转下一节点，最后一个节点通过才 APPROVED 并执行变更效果；退回即 REJECTED。
     */
    @PostMapping("/{id}/audit")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> audit(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjChange exist = requireChange(id);
        if (!"APPROVING".equals(nvl(exist.getStatus()))) {
            throw new BusinessException(403, "变更单不在审批中");
        }
        String node = exist.getFlowNode() == null ? flowNodes(exist).get(0) : exist.getFlowNode();
        flowAuditGuard.requireFlowNode(node, exist.getProjectId(), node);
        boolean pass = body != null && Boolean.TRUE.equals(body.get("pass"));
        String opinion = body == null || body.get("opinion") == null ? "" : String.valueOf(body.get("opinion"));
        SysUser auditor = flowAuditGuard.currentUser();

        ProjChange patch = new ProjChange();
        patch.setId(id);
        patch.setAuditBy(label(auditor));
        patch.setAuditAt(LocalDateTime.now());
        patch.setAuditOpinion(opinion);
        patch.setAuditTrailJson(appendTrail(exist, node, pass, opinion));
        if (!pass) {
            patch.setStatus("REJECTED");
            changeMapper.updateById(patch);
            auditLogMapper.write("CHANGE", "REJECT", "CHANGE", id, node + "退回变更单 " + exist.getChangeNo() + (opinion.isBlank() ? "" : "，意见：" + opinion));
            return R.ok(true);
        }
        List<String> nodes = flowNodes(exist);
        int idx = nodes.indexOf(node);
        if (idx >= 0 && idx < nodes.size() - 1) {
            patch.setFlowNode(nodes.get(idx + 1));
            changeMapper.updateById(patch);
            auditLogMapper.write("CHANGE", "APPROVE", "CHANGE", id, node + "通过，流转 " + patch.getFlowNode() + "：" + exist.getChangeNo());
            return R.ok(true);
        }
        patch.setStatus("APPROVED");
        patch.setFlowNode("已办结");
        changeMapper.updateById(patch);
        applyEffect(exist, auditor);
        auditLogMapper.write("CHANGE", "APPROVE", "CHANGE", id, node + "终审通过，变更生效：" + exist.getChangeNo());
        return R.ok(true);
    }

    /** 变更生效：里程碑延期回写计划日期 */
    private void applyEffect(ProjChange c, SysUser auditor) {
        if (!"MILESTONE_DELAY".equals(c.getCategory()) || c.getMilestoneId() == null) {
            return;
        }
        ProjMilestone m = milestoneMapper.selectById(c.getMilestoneId());
        if (m == null) {
            throw new BusinessException("延期对应的里程碑已不存在，无法生效");
        }
        LocalDate newDate = c.getNewPlanDate();
        if (newDate == null && c.getAfterValue() != null) {
            newDate = LocalDate.parse(c.getAfterValue().trim());
        }
        if (newDate == null) {
            throw new BusinessException("延期变更缺少新计划日期");
        }
        ProjMilestone patch = new ProjMilestone();
        patch.setId(m.getId());
        patch.setPlanDate(newDate);
        if (m.getBaselinePlanDate() == null && m.getPlanDate() != null) {
            patch.setBaselinePlanDate(m.getPlanDate());
        }
        patch.setDelayCount((m.getDelayCount() == null ? 0 : m.getDelayCount()) + 1);
        patch.setColorStatus(ColorUtil.calcCode(newDate, "DONE".equals(m.getStatus())));
        patch.setAuditBy(label(auditor));
        patch.setAuditAt(LocalDateTime.now());
        patch.setAuditOpinion("延期变更 " + c.getChangeNo() + " 生效：" + m.getPlanDate() + " → " + newDate);
        milestoneMapper.updateById(patch);
        auditLogMapper.write("MILESTONE", "UPDATE", "MILESTONE", m.getId(),
                "延期变更生效，计划日期 " + m.getPlanDate() + " → " + newDate + "（" + c.getChangeNo() + "）");
    }

    private List<String> flowNodes(ProjChange c) {
        List<String> nodes = new ArrayList<>();
        if ("DATA".equals(c.getChangeType())) {
            nodes.add(NODE_DATA_UNIT);
            nodes.add(NODE_DATA_HQ);
            return nodes;
        }
        nodes.add(NODE_UNIT_FIRST);
        nodes.add(NODE_HQ_FINAL);
        if (Integer.valueOf(1).equals(c.getLegalReview())) {
            nodes.add(NODE_LEGAL);
        }
        return nodes;
    }

    private void requireEditable(ProjChange exist, String actionLabel) {
        if (!EDITABLE_STATUS.contains(nvl(exist.getStatus()))) {
            throw new BusinessException(403, "变更单已提交或已办结，不能" + actionLabel.replace("变更单", ""));
        }
        flowAuditGuard.requireActors(exist.getProjectId(), actionLabel, "owner", "projectPm", "techLead", "contactLogin");
    }

    private ProjChange requireChange(Long id) {
        ProjChange c = id == null ? null : changeMapper.selectById(id);
        if (c == null) {
            throw new BusinessException("变更记录不存在");
        }
        return c;
    }

    private void attachTrail(ProjChange c) {
        c.setAuditTrail(parseTrail(c.getAuditTrailJson()));
    }

    private static List<Map<String, Object>> parseTrail(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String appendTrail(ProjChange c, String node, boolean pass, String opinion) {
        List<Map<String, Object>> trail = parseTrail(c.getAuditTrailJson());
        SysUser u = flowAuditGuard.currentUserOrNull();
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("node", node);
        rec.put("actor", u == null ? UserContext.getUsername() : nvl(u.getRealName()));
        rec.put("actorNo", u == null ? "" : nvl(u.getEmployeeNo()));
        rec.put("pass", pass);
        rec.put("opinion", opinion);
        rec.put("time", LocalDateTime.now().toString());
        trail.add(rec);
        try {
            return JSON.writeValueAsString(trail);
        } catch (Exception e) {
            return c.getAuditTrailJson();
        }
    }

    private String nextChangeNo() {
        String prefix = "BG" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = changeMapper.selectCount(new LambdaQueryWrapper<ProjChange>().likeRight(ProjChange::getChangeNo, prefix));
        for (int i = 0; i < 20; i++) {
            String candidate = SeqUtil.changeNo(count + 1 + i);
            if (changeMapper.selectCount(new LambdaQueryWrapper<ProjChange>().eq(ProjChange::getChangeNo, candidate)) == 0) {
                return candidate;
            }
        }
        return prefix + System.currentTimeMillis() % 100000;
    }

    private static String label(SysUser u) {
        if (u == null) {
            return null;
        }
        String no = nvl(u.getEmployeeNo());
        return no.isEmpty() ? nvl(u.getRealName()) : nvl(u.getRealName()) + "（" + no + "）";
    }

    private boolean needLegal(String category) {
        return category != null && LEGAL_CATEGORIES.contains("," + category + ",");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    @SuppressWarnings("unused")
    private static List<String> allNodes() {
        return Arrays.asList(NODE_UNIT_FIRST, NODE_HQ_FINAL, NODE_LEGAL, NODE_DATA_UNIT, NODE_DATA_HQ);
    }
}
