package com.comac.rpm.modules.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.project.entity.ProjAnnualPlan;
import com.comac.rpm.modules.project.entity.ProjBasicDraft;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjParticipant;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjAnnualPlanMapper;
import com.comac.rpm.modules.project.mapper.ProjBasicDraftMapper;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjParticipantMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目基本信息审批：项目团队补充字段 → 草稿 → 四级审批 → 通过后写入台账。
 * <p>
 * 节点：PROJECT_LEADER 项目负责人审核 → UNIT_TECH 单位科技管理部审核 →
 * UNIT_LEADER 单位分管领导复核（团队未配置该岗位时自动跳过）→ HQ 总部科研项目处确认。
 */
@RestController
@RequestMapping("/api/projects")
public class BasicDraftController {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final Set<String> FILLER_IDENTITIES = Set.of("owner", "contactLogin", "techLead", "projectPm");

    /** 节点定义：code, name, 办理身份（团队岗位优先，其次任职身份） */
    private static final List<String[]> NODES = Arrays.asList(
            new String[]{"PROJECT_LEADER", "项目负责人审核", "owner"},
            new String[]{"UNIT_TECH", "单位科技管理部审核", "unitHead"},
            new String[]{"UNIT_LEADER", "单位分管领导复核", "unitLeader"},
            new String[]{"HQ", "总部科研项目处确认", "hqStaff", "hqHead"});

    /** 审批通过后允许回写台账的字段（白名单，防止 mass assignment） */
    private static final Set<String> WRITABLE_FIELDS = Set.of(
            "name", "goal", "startDate", "endDate", "levelCode", "filingDept", "channelId", "channelName",
            "leadOrgName", "mainWork", "totalFund", "nationalFund", "selfFund", "manageOrgName", "bureauOffice",
            "projectType", "major1", "major2", "ownerName");

    @Autowired
    private ProjBasicDraftMapper draftMapper;
    @Autowired
    private ProjInfoMapper projInfoMapper;
    @Autowired
    private ProjParticipantMapper participantMapper;
    @Autowired
    private ProjTeamMemberMapper teamMemberMapper;
    @Autowired
    private ProjAnnualPlanMapper annualPlanMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;
    @Autowired
    private SysAuditLogMapper auditLogMapper;

    /** 需求链含「单位分管领导」；花名册暂无该岗位时默认跳过并留痕，置 true 则缺岗禁止提交 */
    @org.springframework.beans.factory.annotation.Value("${rpm.basic-audit.require-unit-leader:false}")
    private boolean requireUnitLeader;

    @GetMapping("/{id}/basic-draft")
    public R<Map<String, Object>> current(@PathVariable("id") Long id) {
        flowAuditGuard.requireProjectAccess(id);
        ProjInfo project = requireProject(id);
        ProjBasicDraft draft = latestDraft(id);
        return R.ok(view(project, draft));
    }

    /** 保存草稿（项目团队）。审批中的草稿不可修改；已通过/已驳回则新建一份。 */
    @PutMapping("/{id}/basic-draft")
    @Transactional(rollbackFor = Exception.class)
    public R<Long> save(@PathVariable("id") Long id, @RequestBody Map<String, Object> payload) {
        ProjInfo project = requireProject(id);
        requireProjectTeam(id, "填写项目基本信息");
        ProjBasicDraft draft = latestDraft(id);
        if (draft != null && ProjBasicDraft.STATUS_APPROVING.equals(draft.getStatus())) {
            throw new BusinessException(403, "基本信息正在审批中（" + draft.getFlowNodeName() + "），审批结束前不能修改");
        }
        validatePayload(payload);
        String json = toJson(sanitize(payload));
        SysUser u = flowAuditGuard.currentUser();
        if (draft == null || ProjBasicDraft.STATUS_APPROVED.equals(draft.getStatus())) {
            draft = new ProjBasicDraft();
            draft.setProjectId(id);
            draft.setStatus(ProjBasicDraft.STATUS_DRAFT);
            draft.setPayload(json);
            draft.setSubmittedBy(u.getRealName());
            draft.setSubmittedNo(u.getEmployeeNo());
            draft.setCreatedAt(LocalDateTime.now());
            draftMapper.insert(draft);
        } else {
            ProjBasicDraft patch = new ProjBasicDraft();
            patch.setId(draft.getId());
            patch.setPayload(json);
            patch.setStatus(ProjBasicDraft.STATUS_DRAFT);
            patch.setFlowNode(null);
            patch.setFlowNodeName(null);
            draftMapper.updateById(patch);
        }
        auditLogMapper.write("PROJECT", "UPDATE", "BASIC_DRAFT", draft.getId(), "保存项目基本信息草稿：" + project.getName());
        return R.ok(draft.getId());
    }

    @PostMapping("/{id}/basic-draft/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> submit(@PathVariable("id") Long id) {
        ProjInfo project = requireProject(id);
        requireProjectTeam(id, "提交项目基本信息审批");
        ProjBasicDraft draft = latestDraft(id);
        if (draft == null || ProjBasicDraft.STATUS_APPROVED.equals(draft.getStatus())) {
            throw new BusinessException("请先保存基本信息草稿");
        }
        if (ProjBasicDraft.STATUS_APPROVING.equals(draft.getStatus())) {
            throw new BusinessException("已在审批中");
        }
        Map<String, Object> payload = fromJson(draft.getPayload());
        validatePayload(payload);
        // 审核路由只认台账里已批准的团队，草稿里拟议的岗位变动不能授予审批权
        List<ProjTeamMember> members = currentMembers(id);
        boolean leaderMissing = FlowAuditGuard.findMember(members, "unitLeader") == null;
        if (leaderMissing && requireUnitLeader) {
            throw new BusinessException("项目团队未配置「单位分管领导」，请先在项目团队中指定后再提交审批");
        }
        List<String[]> nodes = effectiveNodes(members);
        SysUser u = flowAuditGuard.currentUser();
        ProjBasicDraft patch = new ProjBasicDraft();
        patch.setId(draft.getId());
        patch.setStatus(ProjBasicDraft.STATUS_APPROVING);
        String trail = appendTrail(draft.getAuditTrail(), "SUBMIT", "提交", u, true, "提交审批");
        // 需求链“项目团队填写 → 项目负责人审核”：提交人本人就是项目负责人时，首节点自动通过并留痕，任务直接流转到单位科技管理部
        int startIdx = 0;
        ProjTeamMember ownerMember = FlowAuditGuard.findMember(members, "owner");
        boolean submitterIsOwner = ownerMember != null ? FlowAuditGuard.samePerson(u, ownerMember)
                : FlowAuditGuard.matchesOwnerLabel(project.getOwnerName(), u);
        if (submitterIsOwner && "PROJECT_LEADER".equals(nodes.get(0)[0]) && nodes.size() > 1) {
            trail = appendTrail(trail, "PROJECT_LEADER", "项目负责人审核", u, true, "提交人即项目负责人，本节点自动通过");
            startIdx = 1;
        }
        patch.setFlowNode(nodes.get(startIdx)[0]);
        patch.setFlowNodeName(nodes.get(startIdx)[1]);
        patch.setSubmittedBy(u.getRealName());
        patch.setSubmittedNo(u.getEmployeeNo());
        patch.setSubmittedAt(LocalDateTime.now());
        if (leaderMissing) {
            trail = appendTrail(trail, "UNIT_LEADER", "单位分管领导复核", null, true,
                    "系统：团队未配置该岗位，按当前口径跳过；如需强制可开启 rpm.basic-audit.require-unit-leader");
        }
        patch.setAuditTrail(trail);
        draftMapper.updateById(patch);
        auditLogMapper.write("PROJECT", "SUBMIT", "BASIC_DRAFT", draft.getId(),
                "提交项目基本信息审批：" + project.getName() + "，当前节点 " + nodes.get(startIdx)[1]
                        + (startIdx > 0 ? "（提交人即项目负责人，首节点自动通过）" : ""));
        return R.ok(true);
    }

    @PostMapping("/{id}/basic-draft/audit")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> audit(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjInfo project = requireProject(id);
        ProjBasicDraft draft = latestDraft(id);
        if (draft == null || !ProjBasicDraft.STATUS_APPROVING.equals(draft.getStatus())) {
            throw new BusinessException(403, "当前没有待审批的基本信息");
        }
        Map<String, Object> payload = fromJson(draft.getPayload());
        List<ProjTeamMember> members = currentMembers(id);
        List<String[]> nodes = effectiveNodes(members);
        int idx = indexOf(nodes, draft.getFlowNode());
        if (idx < 0) {
            throw new BusinessException("审批节点异常：" + draft.getFlowNode());
        }
        String[] node = nodes.get(idx);
        SysUser auditor = flowAuditGuard.currentUser();
        if (!canAudit(id, node, members, auditor)) {
            throw new BusinessException(403, "仅当前节点办理人（" + node[1] + "）可审批");
        }
        boolean pass = body == null || body.get("pass") == null || Boolean.parseBoolean(String.valueOf(body.get("pass")));
        String opinion = body == null || body.get("opinion") == null ? "" : String.valueOf(body.get("opinion"));
        ProjBasicDraft patch = new ProjBasicDraft();
        patch.setId(draft.getId());
        patch.setAuditTrail(appendTrail(draft.getAuditTrail(), node[0], node[1], auditor, pass, opinion));
        if (!pass) {
            patch.setStatus(ProjBasicDraft.STATUS_REJECTED);
            patch.setFlowNode(null);
            patch.setFlowNodeName("已退回：" + node[1]);
            draftMapper.updateById(patch);
            auditLogMapper.write("PROJECT", "REJECT", "BASIC_DRAFT", draft.getId(),
                    node[1] + "退回项目基本信息：" + project.getName() + (opinion.isBlank() ? "" : "，意见：" + opinion));
            return R.ok(true);
        }
        if (idx < nodes.size() - 1) {
            patch.setFlowNode(nodes.get(idx + 1)[0]);
            patch.setFlowNodeName(nodes.get(idx + 1)[1]);
            draftMapper.updateById(patch);
            auditLogMapper.write("PROJECT", "APPROVE", "BASIC_DRAFT", draft.getId(),
                    node[1] + "通过，流转 " + nodes.get(idx + 1)[1] + "：" + project.getName());
            return R.ok(true);
        }
        patch.setStatus(ProjBasicDraft.STATUS_APPROVED);
        patch.setFlowNode("DONE");
        patch.setFlowNodeName("已办结");
        draftMapper.updateById(patch);
        applyToLedger(project, payload, membersFromPayload(payload));
        auditLogMapper.write("PROJECT", "APPROVE", "PROJECT", id,
                "项目基本信息审批通过并同步台账：" + project.getName());
        return R.ok(true);
    }

    /** 当前登录人待审的基本信息 */
    @GetMapping("/basic-drafts/pending")
    public R<List<Map<String, Object>>> pending() {
        SysUser u = flowAuditGuard.currentUser();
        List<ProjBasicDraft> drafts = draftMapper.selectList(new LambdaQueryWrapper<ProjBasicDraft>()
                .eq(ProjBasicDraft::getStatus, ProjBasicDraft.STATUS_APPROVING)
                .orderByDesc(ProjBasicDraft::getSubmittedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProjBasicDraft d : drafts) {
            ProjInfo p = projInfoMapper.selectById(d.getProjectId());
            if (p == null) {
                continue;
            }
            List<ProjTeamMember> members = currentMembers(p.getId());
            List<String[]> nodes = effectiveNodes(members);
            int idx = indexOf(nodes, d.getFlowNode());
            if (idx < 0 || !canAudit(p.getId(), nodes.get(idx), members, u)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("draftId", d.getId());
            row.put("projectId", p.getId());
            row.put("projectNo", p.getProjectNo());
            row.put("projectName", p.getName());
            row.put("ownerName", p.getOwnerName());
            row.put("flowNode", d.getFlowNode());
            row.put("flowNodeName", d.getFlowNodeName());
            row.put("submittedBy", d.getSubmittedBy());
            row.put("submittedAt", d.getSubmittedAt());
            out.add(row);
        }
        return R.ok(out);
    }

    // ------------------------------------------------------------------ 内部

    /** 填报仅限项目团队；超级管理员按需求“禁止直接改业务数据”，同样不能代填 */
    private void requireProjectTeam(Long projectId, String actionLabel) {
        SysUser u = flowAuditGuard.currentUser();
        if ("admin".equals(FlowAuditGuard.identityOf(u))) {
            throw new BusinessException(403, "超级管理员不能直接填报业务数据，「" + actionLabel + "」请由项目团队办理");
        }
        flowAuditGuard.requireActors(projectId, actionLabel, "owner", "contactLogin", "techLead", "projectPm");
    }

    private Map<String, Object> view(ProjInfo project, ProjBasicDraft draft) {
        SysUser u = flowAuditGuard.currentUserOrNull();
        Map<String, Object> v = new LinkedHashMap<>();
        // 管理员/管理团队走台账直接编辑（PUT /projects/{id}），草稿填写只对项目团队开放
        boolean filler = u != null && !flowAuditGuard.isLedgerEditor(u)
                && flowAuditGuard.canActAs(project.getId(), u, "owner", "contactLogin", "techLead", "projectPm");
        if (draft == null) {
            v.put("id", null);
            v.put("status", "NONE");
            v.put("flowNode", null);
            v.put("flowNodeName", null);
            v.put("flowNodes", nodeViews(effectiveNodes(currentMembers(project.getId())), null));
            v.put("payload", null);
            v.put("auditTrail", List.of());
            v.put("canEdit", filler);
            v.put("canSubmit", false);
            v.put("canAudit", false);
            return v;
        }
        Map<String, Object> payload = fromJson(draft.getPayload());
        List<ProjTeamMember> members = currentMembers(project.getId());
        List<String[]> nodes = effectiveNodes(members);
        boolean approving = ProjBasicDraft.STATUS_APPROVING.equals(draft.getStatus());
        int idx = indexOf(nodes, draft.getFlowNode());
        v.put("id", draft.getId());
        v.put("status", draft.getStatus());
        v.put("flowNode", draft.getFlowNode());
        v.put("flowNodeName", draft.getFlowNodeName());
        v.put("flowNodes", nodeViews(nodes, approving ? draft.getFlowNode() : null));
        v.put("payload", ProjBasicDraft.STATUS_APPROVED.equals(draft.getStatus()) ? null : payload);
        v.put("auditTrail", parseTrail(draft.getAuditTrail()));
        v.put("submittedBy", draft.getSubmittedBy());
        v.put("submittedAt", draft.getSubmittedAt());
        v.put("canEdit", filler && !approving);
        v.put("canSubmit", filler && !approving && !ProjBasicDraft.STATUS_APPROVED.equals(draft.getStatus()));
        v.put("canAudit", approving && idx >= 0 && canAudit(project.getId(), nodes.get(idx), members, u));
        return v;
    }

    private List<Map<String, Object>> nodeViews(List<String[]> nodes, String currentCode) {
        List<Map<String, Object>> list = new ArrayList<>();
        boolean reached = currentCode == null;
        for (String[] n : NODES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", n[0]);
            m.put("name", n[1]);
            boolean skipped = nodes.stream().noneMatch(x -> x[0].equals(n[0]));
            m.put("skipped", skipped);
            m.put("current", n[0].equals(currentCode));
            list.add(m);
        }
        return list;
    }

    /** 团队未配置「单位分管领导」时跳过该节点 */
    private List<String[]> effectiveNodes(List<ProjTeamMember> members) {
        List<String[]> nodes = new ArrayList<>();
        for (String[] n : NODES) {
            if ("UNIT_LEADER".equals(n[0]) && FlowAuditGuard.findMember(members, "unitLeader") == null) {
                continue;
            }
            nodes.add(n);
        }
        return nodes;
    }

    private static int indexOf(List<String[]> nodes, String code) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i)[0].equals(code)) {
                return i;
            }
        }
        return -1;
    }

    private boolean canAudit(Long projectId, String[] node, List<ProjTeamMember> members, SysUser u) {
        if (u == null || "admin".equals(FlowAuditGuard.identityOf(u))) {
            return false;
        }
        String[] identities = Arrays.copyOfRange(node, 2, node.length);
        for (String ident : identities) {
            ProjTeamMember named = FlowAuditGuard.findMember(members, ident);
            if (named != null) {
                if (FlowAuditGuard.samePerson(u, named)) {
                    return true;
                }
                continue;
            }
            if (ident.equals(FlowAuditGuard.identityOf(u))) {
                ProjInfo p = projInfoMapper.selectById(projectId);
                if ("hqStaff".equals(ident) || "hqHead".equals(ident)) {
                    return true;
                }
                return p != null && p.getOrgId() != null && p.getOrgId().equals(u.getOrgId());
            }
        }
        return false;
    }

    private void applyToLedger(ProjInfo project, Map<String, Object> payload, List<ProjTeamMember> members) {
        Map<String, Object> patchMap = new LinkedHashMap<>();
        for (String f : WRITABLE_FIELDS) {
            if (payload.containsKey(f)) {
                patchMap.put(f, payload.get(f));
            }
        }
        ProjInfo patch = JSON.convertValue(patchMap, ProjInfo.class);
        patch.setId(project.getId());
        patch.setUpdatedAt(LocalDateTime.now());
        if (patch.getName() == null || patch.getName().isBlank()) {
            patch.setName(project.getName());
        }
        projInfoMapper.updateById(patch);

        participantMapper.delete(new LambdaQueryWrapper<ProjParticipant>().eq(ProjParticipant::getProjectId, project.getId()));
        int sort = 1;
        for (Map<String, Object> pt : listOfMaps(payload.get("participants"))) {
            String org = str(pt.get("orgName"));
            String work = str(pt.get("workContent"));
            if (org == null && work == null) {
                continue;
            }
            ProjParticipant p = new ProjParticipant();
            p.setProjectId(project.getId());
            p.setOrgName(org);
            p.setWorkContent(work);
            p.setSort(sort++);
            participantMapper.insert(p);
        }
        teamMemberMapper.delete(new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, project.getId()));
        int s = 1;
        for (ProjTeamMember m : members) {
            m.setId(null);
            m.setProjectId(project.getId());
            m.setSort(s++);
            teamMemberMapper.insert(m);
        }
        // 年度目标（只更新目标与计划内容，不改审核状态）
        for (Map<String, Object> ap : listOfMaps(payload.get("annualPlans"))) {
            Integer year = ap.get("year") == null ? null : Integer.valueOf(String.valueOf(ap.get("year")));
            if (year == null) {
                continue;
            }
            ProjAnnualPlan cur = annualPlanMapper.selectOne(new LambdaQueryWrapper<ProjAnnualPlan>()
                    .eq(ProjAnnualPlan::getProjectId, project.getId()).eq(ProjAnnualPlan::getYear, year).last("LIMIT 1"));
            if (cur == null) {
                continue;
            }
            ProjAnnualPlan ap2 = new ProjAnnualPlan();
            ap2.setId(cur.getId());
            ap2.setAnnualGoal(str(ap.get("annualGoal")));
            ap2.setPlanContent(str(ap.get("planContent")));
            annualPlanMapper.updateById(ap2);
        }
    }

    private List<ProjTeamMember> membersFromPayload(Map<String, Object> payload) {
        List<ProjTeamMember> list = new ArrayList<>();
        for (Map<String, Object> m : listOfMaps(payload.get("teamMembers"))) {
            String name = str(m.get("userName"));
            String no = str(m.get("employeeNo"));
            if (name == null && no == null) {
                continue;
            }
            ProjTeamMember t = new ProjTeamMember();
            String group = str(m.get("groupCode"));
            if (group == null) {
                group = defaultGroupCode(str(m.get("roleName")));
            }
            t.setGroupCode(group);
            t.setRoleCode(str(m.get("roleCode")) == null ? str(m.get("roleName")) : str(m.get("roleCode")));
            t.setRoleName(str(m.get("roleName")));
            t.setUserName(name);
            t.setEmployeeNo(no);
            list.add(t);
        }
        return list;
    }

    /** 团队分组缺省：按岗位名推断（技术团队 / 责任专家 / 管理团队 / 财务团队），proj_team_member.group_code 非空 */
    private static String defaultGroupCode(String roleName) {
        String r = roleName == null ? "" : roleName;
        if (r.contains("总师")) {
            return "EXPERT";
        }
        if (r.contains("财务")) {
            return "FIN";
        }
        if (r.contains("处长") || r.contains("主管") && !r.contains("项目主管") || r.contains("科技部长") || r.contains("负责人") && !r.contains("项目负责人") && !r.contains("技术负责人") || r.contains("分管领导")) {
            return "MGMT";
        }
        return "TECH";
    }

    private List<ProjTeamMember> currentMembers(Long projectId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, projectId));
    }

    private void validatePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException("基本信息不能为空");
        }
        if (str(payload.get("name")) == null) {
            throw new BusinessException("请填写项目名称");
        }
        LocalDate start = date(payload.get("startDate"));
        LocalDate end = date(payload.get("endDate"));
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException("结束时间不能早于开始时间");
        }
        Object fund = payload.get("totalFund");
        if (fund != null && !String.valueOf(fund).isBlank()) {
            try {
                if (new BigDecimal(String.valueOf(fund)).signum() < 0) {
                    throw new BusinessException("总经费不能为负数");
                }
            } catch (NumberFormatException e) {
                throw new BusinessException("总经费格式不正确");
            }
        }
        boolean hasOwner = false;
        for (Map<String, Object> m : listOfMaps(payload.get("teamMembers"))) {
            String role = str(m.get("roleName"));
            if (role != null && role.contains("项目负责人") && (str(m.get("employeeNo")) != null || str(m.get("userName")) != null)) {
                hasOwner = true;
            }
        }
        if (!hasOwner && str(payload.get("ownerName")) == null) {
            throw new BusinessException("请指定项目负责人（姓名+工号）");
        }
    }

    /** 只保留业务字段，剔除台账系统字段（状态、预警色、组织、创建人等） */
    private Map<String, Object> sanitize(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String f : WRITABLE_FIELDS) {
            if (payload.containsKey(f)) {
                out.put(f, payload.get(f));
            }
        }
        out.put("participants", listOfMaps(payload.get("participants")));
        out.put("teamMembers", listOfMaps(payload.get("teamMembers")));
        out.put("annualPlans", listOfMaps(payload.get("annualPlans")));
        return out;
    }

    private ProjBasicDraft latestDraft(Long projectId) {
        return draftMapper.selectOne(new LambdaQueryWrapper<ProjBasicDraft>()
                .eq(ProjBasicDraft::getProjectId, projectId)
                .orderByDesc(ProjBasicDraft::getId).last("LIMIT 1"));
    }

    private ProjInfo requireProject(Long id) {
        ProjInfo p = id == null ? null : projInfoMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("项目不存在");
        }
        return p;
    }

    private String appendTrail(String json, String nodeCode, String nodeName, SysUser u, boolean pass, String opinion) {
        List<Map<String, Object>> trail = parseTrail(json);
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("node", nodeCode);
        rec.put("nodeName", nodeName);
        rec.put("actor", u == null ? "系统" : u.getRealName());
        rec.put("actorNo", u == null ? "" : u.getEmployeeNo());
        rec.put("pass", pass);
        rec.put("opinion", opinion);
        rec.put("time", LocalDateTime.now().toString());
        trail.add(rec);
        return toJson(trail);
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

    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("草稿数据损坏，请重新填写");
        }
    }

    private static String toJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException("草稿序列化失败");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?>) {
            for (Object item : (List<?>) o) {
                if (item instanceof Map<?, ?>) {
                    out.add((Map<String, Object>) item);
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static LocalDate date(Object o) {
        String s = str(o);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            throw new BusinessException("日期格式不正确：" + s);
        }
    }
}
