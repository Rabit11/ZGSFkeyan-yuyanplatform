package com.comac.rpm.modules.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.common.util.SeqUtil;
import com.comac.rpm.modules.acceptance.entity.ProjAcceptance;
import com.comac.rpm.modules.acceptance.mapper.ProjAcceptanceMapper;
import com.comac.rpm.modules.change.entity.ProjChange;
import com.comac.rpm.modules.change.mapper.ProjChangeMapper;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.declaration.entity.ProjMaterial;
import com.comac.rpm.modules.declaration.mapper.ProjMaterialMapper;
import com.comac.rpm.modules.evaluation.entity.ProjEvaluation;
import com.comac.rpm.modules.evaluation.mapper.ProjEvaluationMapper;
import com.comac.rpm.modules.fund.entity.FundBudget;
import com.comac.rpm.modules.fund.entity.FundPayment;
import com.comac.rpm.modules.fund.mapper.FundBudgetMapper;
import com.comac.rpm.modules.fund.mapper.FundPaymentMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.partner.entity.PartnerBlacklist;
import com.comac.rpm.modules.partner.entity.PartnerEval;
import com.comac.rpm.modules.partner.mapper.PartnerBlacklistMapper;
import com.comac.rpm.modules.partner.mapper.PartnerEvalMapper;
import com.comac.rpm.modules.plan.entity.ProjPlan;
import com.comac.rpm.modules.plan.mapper.ProjPlanMapper;
import com.comac.rpm.modules.posteval.entity.ProjPostEval;
import com.comac.rpm.modules.posteval.mapper.ProjPostEvalMapper;
import com.comac.rpm.modules.project.entity.ProjAnnualPlan;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjParticipant;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjAnnualPlanMapper;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjParticipantMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.comac.rpm.modules.transform.entity.AchvTransform;
import com.comac.rpm.modules.transform.mapper.AchvTransformMapper;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 科研项目一本账
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private static final DateTimeFormatter PROJECT_NO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DATA_SOURCE_FORM_MAINT = "FORM_MAINT";
    private static final String MAINT_BIZ_TYPE = "FORM_MAINT_MAINTENANCE";
    private static final String MAINT_STATUS_DRAFT = "MAINT_DRAFT";
    private static final String MAINT_STATUS_UNIT_REVIEW = "MAINT_UNIT_REVIEW";
    private static final String MAINT_STATUS_HQ_REVIEW = "MAINT_HQ_REVIEW";
    private static final String MAINT_STATUS_DONE = "MAINT_DONE";
    private static final String MAINT_STATUS_REJECTED = "MAINT_REJECTED";

    @Autowired
    private ProjInfoMapper projInfoMapper;
    @Autowired
    private ProjParticipantMapper participantMapper;
    @Autowired
    private ProjTeamMemberMapper teamMemberMapper;
    @Autowired
    private ProjAnnualPlanMapper annualPlanMapper;
    @Autowired
    private ProjMilestoneMapper milestoneMapper;
    @Autowired
    private ProjMaterialMapper materialMapper;
    @Autowired
    private ProjPlanMapper planMapper;
    @Autowired
    private FundBudgetMapper fundBudgetMapper;
    @Autowired
    private FundPaymentMapper fundPaymentMapper;
    @Autowired
    private ProjEvaluationMapper evaluationMapper;
    @Autowired
    private ProjChangeMapper changeMapper;
    @Autowired
    private ProjAcceptanceMapper acceptanceMapper;
    @Autowired
    private ProjDeliverableMapper deliverableMapper;
    @Autowired
    private PartnerEvalMapper partnerEvalMapper;
    @Autowired
    private PartnerBlacklistMapper blacklistMapper;
    @Autowired
    private AchvTransformMapper transformMapper;
    @Autowired
    private ProjPostEvalMapper postEvalMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;
    @Autowired
    private com.comac.rpm.modules.milestone.controller.MilestoneController milestoneController;
    @Autowired
    private SysAuditLogMapper auditLogMapper;
    @Autowired
    private SysUserMapper userMapper;

    /**
     * 分页查询项目台账
     */
    @GetMapping
    public R<PageVO<ProjInfo>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "levelCode", required = false) String levelCode,
            @RequestParam(value = "channelId", required = false) Long channelId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warnColor", required = false) String warnColor,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "dataSource", required = false) String dataSource) {

        LambdaQueryWrapper<ProjInfo> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            w.and(x -> x.like(ProjInfo::getName, keyword).or().like(ProjInfo::getProjectNo, keyword));
        }
        if (levelCode != null && !levelCode.isEmpty()) {
            w.eq(ProjInfo::getLevelCode, levelCode);
        }
        if (channelId != null) {
            w.eq(ProjInfo::getChannelId, channelId);
        }
        if (status != null && !status.isEmpty()) {
            w.eq(ProjInfo::getStatus, status);
        }
        if (warnColor != null && !warnColor.isEmpty()) {
            w.eq(ProjInfo::getWarnColor, warnColor);
        }
        if (dataSource != null && !dataSource.isEmpty() && !"ALL".equalsIgnoreCase(dataSource)) {
            w.eq(ProjInfo::getDataSource, dataSource);
        }
        SysUser viewer = currentUserOrNull();
        applyViewerScope(w, viewer);
        if (orgId != null) {
            // 用户传入的单位过滤只能在自身范围内缩小，不能扩大
            w.eq(ProjInfo::getOrgId, orgId);
        }
        w.orderByDesc(ProjInfo::getId);
        Page<ProjInfo> result = projInfoMapper.selectPage(new Page<>(page, size), w);
        enrichLedgerSummaries(result.getRecords());
        return R.ok(PageVO.of(result));
    }

    /** 台账列表一次附带里程碑/交付/协作/转化摘要，避免前端 N+1 */
    private void enrichLedgerSummaries(List<ProjInfo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(ProjInfo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<ProjMilestone>> msMap = milestoneMapper.selectList(
                        new LambdaQueryWrapper<ProjMilestone>().in(ProjMilestone::getProjectId, ids))
                .stream().collect(Collectors.groupingBy(ProjMilestone::getProjectId));
        Map<Long, List<ProjDeliverable>> dvMap = deliverableMapper.selectList(
                        new LambdaQueryWrapper<ProjDeliverable>().in(ProjDeliverable::getProjectId, ids))
                .stream().collect(Collectors.groupingBy(ProjDeliverable::getProjectId));
        Map<Long, List<PartnerEval>> peMap = partnerEvalMapper.selectList(
                        new LambdaQueryWrapper<PartnerEval>().in(PartnerEval::getProjectId, ids))
                .stream().collect(Collectors.groupingBy(PartnerEval::getProjectId));
        Map<Long, List<AchvTransform>> tfMap = transformMapper.selectList(
                        new LambdaQueryWrapper<AchvTransform>().in(AchvTransform::getProjectId, ids))
                .stream().collect(Collectors.groupingBy(AchvTransform::getProjectId));
        Map<Long, List<ProjParticipant>> ptMap = participantMapper.selectList(
                        new LambdaQueryWrapper<ProjParticipant>().in(ProjParticipant::getProjectId, ids))
                .stream().collect(Collectors.groupingBy(ProjParticipant::getProjectId));
        Map<Long, List<ProjTeamMember>> tmMap = teamMemberMapper.selectList(
                        new LambdaQueryWrapper<ProjTeamMember>().in(ProjTeamMember::getProjectId, ids))
                .stream().collect(Collectors.groupingBy(ProjTeamMember::getProjectId));
        Set<String> black = blacklistMapper.selectList(null).stream()
                .map(PartnerBlacklist::getPartnerName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        boolean ledgerEdit = flowAuditGuard.canLedgerEdit();
        boolean ledgerDel = flowAuditGuard.canLedgerDelete();
        SysUser currentUser = currentUserOrNull();

        for (ProjInfo p : records) {
            Long id = p.getId();
            List<ProjMilestone> milestones = msMap.getOrDefault(id, List.of());
            for (ProjMilestone m : milestones) {
                m.setColorStatus(ColorUtil.calcCode(m.getPlanDate(), "DONE".equals(m.getStatus())));
            }
            List<ProjDeliverable> deliverables = dvMap.getOrDefault(id, List.of());
            List<PartnerEval> partners = peMap.getOrDefault(id, List.of());
            List<AchvTransform> transforms = tfMap.getOrDefault(id, List.of());
            List<ProjParticipant> participants = ptMap.getOrDefault(id, List.of());
            List<ProjTeamMember> teamMembers = tmMap.getOrDefault(id, List.of());

            int msDone = (int) milestones.stream()
                    .filter(m -> "DONE".equals(m.getStatus()) || m.getActualDate() != null).count();
            int msTotal = milestones.size();
            boolean accepted = p.getStatus() != null && (
                    p.getStatus().contains("ACCEPTED") || "FINISHED".equals(p.getStatus()));
            p.setMilestoneDone(msDone);
            p.setMilestoneTotal(msTotal);
            p.setMilestonePercent(msTotal > 0 ? Math.round(msDone * 100f / msTotal)
                    : (accepted ? 100 : 0));
            p.setLeaderName(p.getOwnerName() == null ? "" : p.getOwnerName());
            p.setDeliverableDone((int) deliverables.stream().filter(d -> "DELIVERED".equals(d.getStatus())).count());
            p.setDeliverableTotal(deliverables.size());
            p.setPartnerDone((int) partners.stream()
                    .filter(e -> "DONE".equals(e.getStatus()) || e.getEvalDate() != null).count());
            p.setPartnerTotal(Math.max(partners.size(), participants.size()));
            boolean hasBl = partners.stream().anyMatch(e -> black.contains(e.getPartnerName()))
                    || participants.stream().anyMatch(e -> black.contains(e.getOrgName()))
                    || partners.stream().anyMatch(e -> "FAIL".equals(e.getGrade()));
            p.setHasBlacklist(hasBl);
            p.setTeamMembers(teamMembers);
            p.setTransformDone((int) transforms.stream()
                    .filter(t -> "DONE".equals(t.getStatus()) || t.getActualDate() != null).count());
            p.setTransformTotal(transforms.size());
            ProjMilestone next = milestones.stream()
                    .filter(m -> !"DONE".equals(m.getStatus()) && m.getActualDate() == null)
                    .sorted(Comparator.comparing(ProjMilestone::getPlanDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .findFirst().orElse(null);
            p.setNextMilestone(next);
            if (next == null && p.getStatus() != null
                    && ("IMPLEMENTING".equals(p.getStatus()) || "DELAYED".equals(p.getStatus()))) {
                p.setNextFallback("计划结束");
            }
            // 已立项（平台流程产生）的项目台账由各业务流程自动归集，不提供直接编辑；仅表单维护导入项目可维护
            p.setCanEdit((ledgerEdit && "FORM_MAINT".equals(p.getDataSource()))
                    || canFormMaintOwnerMaintain(p, teamMembers, currentUser));
            p.setCanDelete(ledgerDel && !"FORM_MAINT".equals(p.getDataSource()));
        }
    }

    private void applyOrgScope(LambdaQueryWrapper<ProjInfo> w, Long orgId) {
        if (orgId == null) {
            return;
        }
        List<String> ownerKeys = userMapper.selectList(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrgId, orgId))
                .stream()
                .flatMap(u -> Stream.of(u.getRealName(), u.getEmployeeNo(), u.getUsername()))
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .collect(Collectors.toList());
        List<Long> ownerProjectIds = ownerKeys.isEmpty()
                ? List.of()
                : teamMemberMapper.selectList(
                                new LambdaQueryWrapper<ProjTeamMember>()
                                        .and(q -> q.eq(ProjTeamMember::getRoleCode, "PROJECT_LEADER")
                                                .or()
                                                .eq(ProjTeamMember::getRoleName, "项目负责人"))
                                        .and(q -> q.in(ProjTeamMember::getUserName, ownerKeys)
                                                .or()
                                                .in(ProjTeamMember::getEmployeeNo, ownerKeys)))
                        .stream()
                        .map(ProjTeamMember::getProjectId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
        w.and(scope -> {
            scope.eq(ProjInfo::getOrgId, orgId);
            if (!ownerKeys.isEmpty()) {
                scope.or(form -> {
                    form.eq(ProjInfo::getDataSource, "FORM_MAINT");
                    appendOwnerKeyMatches(form, ownerKeys);
                });
            }
            if (!ownerProjectIds.isEmpty()) {
                scope.or(form -> form.eq(ProjInfo::getDataSource, "FORM_MAINT")
                        .in(ProjInfo::getId, ownerProjectIds));
            }
        });
    }

    /** 统一数据范围：总部/管理员全量；项目团队与总师仅本人关联项目；其余按本单位 */
    private void applyViewerScope(LambdaQueryWrapper<ProjInfo> w, SysUser viewer) {
        if (viewer == null) {
            if (!UserContext.isAdmin()) {
                applyOrgScope(w, UserContext.getOrgId());
            }
            return;
        }
        String code = FlowAuditGuard.identityOf(viewer);
        if (FlowAuditGuard.isHqIdentity(code)) {
            return;
        }
        if (FlowAuditGuard.isTeamScopeIdentity(code)) {
            applyProjectOwnerScope(w, viewer);
            return;
        }
        applyOrgScope(w, UserContext.getOrgId());
    }

    private void applyProjectOwnerScope(LambdaQueryWrapper<ProjInfo> w, SysUser user) {
        if (user == null) {
            applyOrgScope(w, UserContext.getOrgId());
            return;
        }
        String realName = user.getRealName() == null ? "" : user.getRealName().trim();
        String emp = digits(user.getEmployeeNo());
        // 任一团队岗位（负责人、联系人、技术负责人、主管、总师、管理/财务岗位）均视为本人关联
        List<Long> memberProjectIds = new ArrayList<>(flowAuditGuard.assignedProjectIds(user));
        w.and(scope -> {
            boolean seeded = false;
            if (user.getId() != null) {
                scope.eq(ProjInfo::getCreateBy, user.getId());
                seeded = true;
            }
            if (!realName.isEmpty()) {
                if (seeded) scope.or();
                scope.like(ProjInfo::getOwnerName, realName);
                seeded = true;
            }
            if (!emp.isEmpty()) {
                if (seeded) scope.or();
                scope.like(ProjInfo::getOwnerName, emp);
                seeded = true;
            }
            if (!memberProjectIds.isEmpty()) {
                if (seeded) scope.or();
                scope.in(ProjInfo::getId, memberProjectIds);
            }
        });
    }

    private void appendOwnerKeyMatches(LambdaQueryWrapper<ProjInfo> wrapper, List<String> ownerKeys) {
        wrapper.and(owner -> {
            boolean first = true;
            for (String key : ownerKeys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (first) {
                    owner.and(match -> match.like(ProjInfo::getOwnerName, key)
                            .or()
                            .like(ProjInfo::getCreateByName, key));
                    first = false;
                } else {
                    owner.or(match -> match.like(ProjInfo::getOwnerName, key)
                            .or()
                            .like(ProjInfo::getCreateByName, key));
                }
            }
        });
    }

    private boolean hasCompanyLedgerScope(SysUser user) {
        if (user == null) {
            return UserContext.isAdmin();
        }
        String code = user.getIdentityCode() == null ? "" : user.getIdentityCode();
        return "admin".equals(code) || "leader".equals(code) || "hqHead".equals(code) || "hqStaff".equals(code);
    }

    private boolean isProjectTeamLedgerScope(SysUser user) {
        if (user == null) {
            return false;
        }
        String code = user.getIdentityCode() == null ? "" : user.getIdentityCode();
        return "owner".equals(code) || "techLead".equals(code) || "projectPm".equals(code) || "contactLogin".equals(code);
    }

    private SysUser currentUserOrNull() {
        try {
            return flowAuditGuard.currentUser();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean canFormMaintOwnerMaintain(ProjInfo project, List<ProjTeamMember> members, SysUser user) {
        if (project == null || user == null || !"FORM_MAINT".equals(project.getDataSource())) {
            return false;
        }
        if (!"owner".equals(user.getIdentityCode())) {
            return false;
        }
        String userName = user.getRealName() == null ? "" : user.getRealName().trim();
        String userEmp = digits(user.getEmployeeNo());
        if (!userName.isEmpty() && project.getOwnerName() != null && project.getOwnerName().contains(userName)) {
            return true;
        }
        if (!userEmp.isEmpty() && project.getOwnerName() != null && project.getOwnerName().contains(userEmp)) {
            return true;
        }
        for (ProjTeamMember member : members) {
            if (sameOwner(userName, userEmp, member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 项目详情（含参研单位、团队、年度目标）
     */
    @GetMapping("/{id}")
    public R<ProjInfo> detail(@PathVariable("id") Long id) {
        ProjInfo info = projInfoMapper.selectById(id);
        if (info == null) {
            return R.fail("项目不存在");
        }
        flowAuditGuard.requireProjectAccess(id);
        info.setParticipants(participantMapper.selectList(
                new LambdaQueryWrapper<ProjParticipant>().eq(ProjParticipant::getProjectId, id)
                        .orderByAsc(ProjParticipant::getSort)));
        info.setTeamMembers(teamMemberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, id)
                        .orderByAsc(ProjTeamMember::getSort)));
        List<ProjAnnualPlan> plans = annualPlanMapper.selectList(
                new LambdaQueryWrapper<ProjAnnualPlan>().eq(ProjAnnualPlan::getProjectId, id));
        for (ProjAnnualPlan p : plans) {
            p.setColorStatus(ColorUtil.calcCode(p.getDueDate(), "DONE".equals(p.getFinishStatus())));
        }
        info.setAnnualPlans(plans);
        return R.ok(info);
    }

    /**
     * 全周期详情聚合：一次性返回项目各阶段数据
     */
    @GetMapping("/{id}/overview")
    public R<Map<String, Object>> overview(@PathVariable("id") Long id) {
        flowAuditGuard.requireProjectAccess(id);
        Map<String, Object> map = new HashMap<>();
        ProjInfo project = detail(id).getData();
        map.put("project", project);
        List<ProjMilestone> milestones = milestoneController.decoratedMilestonesOf(id);
        map.put("milestones", milestones);
        map.put("plans", planMapper.selectList(
                new LambdaQueryWrapper<ProjPlan>().eq(ProjPlan::getProjectId, id)
                        .orderByAsc(ProjPlan::getDueDate)));
        map.put("budgets", fundBudgetMapper.selectList(
                new LambdaQueryWrapper<FundBudget>().eq(FundBudget::getProjectId, id)));
        map.put("payments", fundPaymentMapper.selectList(
                new LambdaQueryWrapper<FundPayment>().eq(FundPayment::getProjectId, id)));
        map.put("evaluations", evaluationMapper.selectList(
                new LambdaQueryWrapper<ProjEvaluation>().eq(ProjEvaluation::getProjectId, id)));
        map.put("changes", changeMapper.selectList(
                new LambdaQueryWrapper<ProjChange>().eq(ProjChange::getProjectId, id)
                        .orderByDesc(ProjChange::getCreatedAt)));
        map.put("acceptance", acceptanceMapper.selectOne(
                new LambdaQueryWrapper<ProjAcceptance>().eq(ProjAcceptance::getProjectId, id).last("LIMIT 1")));
        map.put("deliverables", deliverableMapper.selectList(
                new LambdaQueryWrapper<ProjDeliverable>().eq(ProjDeliverable::getProjectId, id)));
        map.put("partnerEvals", partnerEvalMapper.selectList(
                new LambdaQueryWrapper<PartnerEval>().eq(PartnerEval::getProjectId, id)));
        map.put("transforms", transformMapper.selectList(
                new LambdaQueryWrapper<AchvTransform>().eq(AchvTransform::getProjectId, id)));
        map.put("postEval", postEvalMapper.selectOne(
                new LambdaQueryWrapper<ProjPostEval>().eq(ProjPostEval::getProjectId, id).last("LIMIT 1")));
        if (project != null && DATA_SOURCE_FORM_MAINT.equals(project.getDataSource())) {
            List<ProjMaterial> maintenanceMaterials = listMaintenanceMaterials(id);
            map.put("maintenanceMaterials", maintenanceMaterials);
            map.put("maintenanceFlow", maintenanceFlow(project, maintenanceMaterials));
        }
        return R.ok(map);
    }

    /** 待维护材料：当前登录人的待审核项目。 */
    @GetMapping("/maintenance/pending")
    public R<List<Map<String, Object>>> pendingMaintenanceReviews() {
        SysUser user = currentUserOrNull();
        String identity = user == null ? "" : (user.getIdentityCode() == null ? "" : user.getIdentityCode());
        if (user == null && !UserContext.isAdmin()) {
            return R.ok(List.of());
        }
        LambdaQueryWrapper<ProjInfo> w = new LambdaQueryWrapper<>();
        w.eq(ProjInfo::getDataSource, DATA_SOURCE_FORM_MAINT);
        if ("unitHead".equals(identity)) {
            w.eq(ProjInfo::getAcceptStatus, MAINT_STATUS_UNIT_REVIEW);
        } else if ("hqHead".equals(identity) || "hqStaff".equals(identity)) {
            w.eq(ProjInfo::getAcceptStatus, MAINT_STATUS_HQ_REVIEW);
        } else if ("admin".equals(identity) || UserContext.isAdmin()) {
            w.in(ProjInfo::getAcceptStatus, java.util.Arrays.asList(MAINT_STATUS_UNIT_REVIEW, MAINT_STATUS_HQ_REVIEW));
        } else {
            return R.ok(List.of());
        }
        w.orderByDesc(ProjInfo::getId);
        List<ProjInfo> projects = projInfoMapper.selectList(w).stream()
                .filter(p -> (MAINT_STATUS_UNIT_REVIEW.equals(p.getAcceptStatus()) && canUnitMaintainAudit(p))
                        || (MAINT_STATUS_HQ_REVIEW.equals(p.getAcceptStatus()) && canHqMaintainAudit())
                        || "admin".equals(identity)
                        || UserContext.isAdmin())
                .collect(Collectors.toList());
        enrichLedgerSummaries(projects);
        return R.ok(projects.stream().map(this::maintenancePendingRow).collect(Collectors.toList()));
    }

    private Map<String, Object> maintenancePendingRow(ProjInfo project) {
        List<ProjMaterial> materials = listMaintenanceMaterials(project.getId());
        Map<String, Object> row = new HashMap<>();
        row.put("id", project.getId());
        row.put("projectNo", project.getProjectNo());
        row.put("name", project.getName());
        row.put("levelCode", project.getLevelCode());
        row.put("channelName", project.getChannelName());
        row.put("leadOrgName", project.getLeadOrgName());
        row.put("orgName", project.getOrgName());
        row.put("ownerName", project.getOwnerName());
        row.put("status", project.getStatus());
        row.put("acceptStatus", project.getAcceptStatus());
        row.put("reviewKind", MAINT_STATUS_UNIT_REVIEW.equals(project.getAcceptStatus()) ? "unit" : "hq");
        row.put("flowNode", MAINT_STATUS_UNIT_REVIEW.equals(project.getAcceptStatus())
                ? "本单位科技管理部负责人审核" : "总部主管审核");
        row.put("maintenanceFlow", maintenanceFlow(project, materials));
        row.put("materialCount", materials.size());
        return row;
    }

    /** 表单维护导入项目：项目负责人上传待维护材料。 */
    @PostMapping("/{id}/maintenance/materials")
    public R<Long> saveMaintenanceMaterial(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = body == null ? new HashMap<>() : body;
        ProjInfo project = projInfoMapper.selectById(id);
        if (!isFormMaintProject(project)) {
            return R.fail(404, "仅表单维护导入的待维护项目可上传维护材料");
        }
        String status = normalizeMaintenanceStatus(project, listMaintenanceMaterials(id));
        if (MAINT_STATUS_UNIT_REVIEW.equals(status) || MAINT_STATUS_HQ_REVIEW.equals(status)
                || MAINT_STATUS_DONE.equals(status)) {
            return R.fail(403, "当前项目已进入审核流程，不能继续上传维护材料");
        }
        flowAuditGuard.requireActors(id, "上传待维护项目维护材料", "owner");
        ProjMaterial m = new ProjMaterial();
        m.setBizType(MAINT_BIZ_TYPE);
        m.setBizId(id);
        m.setFieldCode(String.valueOf(payload.getOrDefault("fieldCode", "MAINTAIN_MATERIAL")));
        m.setFieldName(String.valueOf(payload.getOrDefault("fieldName", "维护材料")));
        m.setFileName(String.valueOf(payload.getOrDefault("fileName", "")));
        m.setFileUrl(String.valueOf(payload.getOrDefault("fileUrl", "")));
        Object fileSize = payload.get("fileSize");
        if (fileSize instanceof Number) {
            m.setFileSize(((Number) fileSize).longValue());
        }
        m.setVersion(1);
        m.setRequired(0);
        m.setLocked(0);
        m.setUploadedBy(UserContext.getUsername());
        m.setUploadedAt(LocalDateTime.now());
        materialMapper.insert(m);
        if (project.getAcceptStatus() == null || project.getAcceptStatus().isBlank()
                || MAINT_STATUS_REJECTED.equals(project.getAcceptStatus())) {
            updateMaintenanceStatus(id, MAINT_STATUS_DRAFT);
        }
        auditLogMapper.write("PROJECT", "UPLOAD", "PROJECT", id,
                "上传待维护项目维护材料：" + m.getFileName());
        return R.ok(m.getId());
    }

    /** 表单维护导入项目：项目负责人提交本单位科技管理部负责人审核。 */
    @PostMapping("/{id}/maintenance/submit")
    public R<Boolean> submitMaintenance(@PathVariable("id") Long id) {
        ProjInfo project = projInfoMapper.selectById(id);
        if (!isFormMaintProject(project)) {
            return R.fail(404, "仅表单维护导入的待维护项目可提交维护审核");
        }
        List<ProjMaterial> materials = listMaintenanceMaterials(id);
        if (materials.isEmpty()) {
            return R.fail(400, "请先上传维护相关材料");
        }
        String status = normalizeMaintenanceStatus(project, materials);
        if (!MAINT_STATUS_DRAFT.equals(status) && !MAINT_STATUS_REJECTED.equals(status)) {
            return R.fail(403, "当前维护流程状态不可提交");
        }
        flowAuditGuard.requireActors(id, "提交待维护项目维护材料", "owner");
        updateMaintenanceStatus(id, MAINT_STATUS_UNIT_REVIEW);
        auditLogMapper.write("PROJECT", "SUBMIT", "PROJECT", id,
                "待维护项目提交本单位科技管理部负责人审核：" + project.getName());
        return R.ok(true);
    }

    /** 表单维护导入项目：本单位科技管理部负责人审核。 */
    @PostMapping("/{id}/maintenance/unit-audit")
    public R<Boolean> auditMaintenanceByUnit(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjInfo project = projInfoMapper.selectById(id);
        if (!isFormMaintProject(project)) {
            return R.fail(404, "仅表单维护导入的待维护项目可审核");
        }
        if (!MAINT_STATUS_UNIT_REVIEW.equals(normalizeMaintenanceStatus(project, listMaintenanceMaterials(id)))) {
            return R.fail(403, "当前项目不在本单位科技管理部审核节点");
        }
        if (!canUnitMaintainAudit(project)) {
            return R.fail(403, "仅本单位科技管理部负责人可审核");
        }
        boolean pass = body == null || !Boolean.FALSE.equals(body.get("pass"));
        saveMaintenanceAuditRecord(id, pass ? "UNIT_AUDIT_PASS" : "UNIT_AUDIT_REJECT",
                pass ? "单位审核通过" : "单位审核退回",
                body == null ? "" : String.valueOf(body.getOrDefault("opinion", "")));
        updateMaintenanceStatus(id, pass ? MAINT_STATUS_HQ_REVIEW : MAINT_STATUS_REJECTED);
        auditLogMapper.write("PROJECT", pass ? "APPROVE" : "REJECT", "PROJECT", id,
                (pass ? "本单位科技管理部负责人审核通过：" : "本单位科技管理部负责人退回：") + project.getName());
        return R.ok(true);
    }

    /** 表单维护导入项目：总部主管终审。 */
    @PostMapping("/{id}/maintenance/hq-audit")
    public R<Boolean> auditMaintenanceByHq(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjInfo project = projInfoMapper.selectById(id);
        if (!isFormMaintProject(project)) {
            return R.fail(404, "仅表单维护导入的待维护项目可审核");
        }
        if (!MAINT_STATUS_HQ_REVIEW.equals(normalizeMaintenanceStatus(project, listMaintenanceMaterials(id)))) {
            return R.fail(403, "当前项目不在总部主管审核节点");
        }
        if (!canHqMaintainAudit()) {
            return R.fail(403, "仅总部主管可审核");
        }
        boolean pass = body == null || !Boolean.FALSE.equals(body.get("pass"));
        saveMaintenanceAuditRecord(id, pass ? "HQ_AUDIT_PASS" : "HQ_AUDIT_REJECT",
                pass ? "总部审核通过" : "总部审核退回",
                body == null ? "" : String.valueOf(body.getOrDefault("opinion", "")));
        updateMaintenanceStatus(id, pass ? MAINT_STATUS_DONE : MAINT_STATUS_REJECTED);
        auditLogMapper.write("PROJECT", pass ? "APPROVE" : "REJECT", "PROJECT", id,
                (pass ? "总部主管审核通过：" : "总部主管退回：") + project.getName());
        return R.ok(true);
    }

    private boolean isFormMaintProject(ProjInfo project) {
        return project != null && DATA_SOURCE_FORM_MAINT.equals(project.getDataSource());
    }

    private List<ProjMaterial> listMaintenanceMaterials(Long projectId) {
        return materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, MAINT_BIZ_TYPE)
                .eq(ProjMaterial::getBizId, projectId)
                .likeRight(ProjMaterial::getFieldCode, "MAINTAIN_MATERIAL")
                .orderByDesc(ProjMaterial::getUploadedAt));
    }

    private Map<String, Object> maintenanceFlow(ProjInfo project, List<ProjMaterial> materials) {
        String status = normalizeMaintenanceStatus(project, materials);
        Map<String, Object> flow = new HashMap<>();
        flow.put("status", status);
        flow.put("statusText", maintenanceStatusText(status));
        flow.put("canUpload", (MAINT_STATUS_DRAFT.equals(status) || MAINT_STATUS_REJECTED.equals(status))
                && canProjectOwner(project.getId()));
        flow.put("canSubmit", (MAINT_STATUS_DRAFT.equals(status) || MAINT_STATUS_REJECTED.equals(status))
                && materials != null && !materials.isEmpty() && canProjectOwner(project.getId()));
        flow.put("canUnitAudit", MAINT_STATUS_UNIT_REVIEW.equals(status) && canUnitMaintainAudit(project));
        flow.put("canHqAudit", MAINT_STATUS_HQ_REVIEW.equals(status) && canHqMaintainAudit());
        flow.put("handlers", maintenanceHandlers(project));
        flow.put("tracks", maintenanceAuditRecords(project.getId()));
        return flow;
    }

    private Map<String, Object> maintenanceHandlers(ProjInfo project) {
        Map<String, Object> handlers = new HashMap<>();
        SysUser owner = findUserByOwnerLabel(project.getOwnerName());
        if (owner == null) {
            owner = findUserByOwnerLabel(project.getCreateByName());
        }
        handlers.put("owner", owner == null ? safeLabel(project.getOwnerName(), "项目负责人（待指定）") : userLabel(owner));
        Long orgId = project.getOrgId();
        if (orgId == null && owner != null) {
            orgId = owner.getOrgId();
        }
        handlers.put("unitReviewer", userLabels(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(orgId != null, SysUser::getOrgId, orgId)
                .eq(SysUser::getIdentityCode, "unitHead")
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId)), "本单位科技管理部负责人（待指定）"));
        handlers.put("hqReviewer", userLabels(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getIdentityCode, java.util.Arrays.asList("hqStaff", "hqHead"))
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId)), "总部主管（待指定）"));
        return handlers;
    }

    private String normalizeMaintenanceStatus(ProjInfo project, List<ProjMaterial> materials) {
        String status = project == null ? "" : project.getAcceptStatus();
        if (MAINT_STATUS_DRAFT.equals(status) || MAINT_STATUS_UNIT_REVIEW.equals(status)
                || MAINT_STATUS_HQ_REVIEW.equals(status) || MAINT_STATUS_DONE.equals(status)
                || MAINT_STATUS_REJECTED.equals(status)) {
            return status;
        }
        return MAINT_STATUS_DRAFT;
    }

    private String maintenanceStatusText(String status) {
        switch (status) {
            case MAINT_STATUS_UNIT_REVIEW:
                return "待单位科技管理部审核";
            case MAINT_STATUS_HQ_REVIEW:
                return "待总部主管审核";
            case MAINT_STATUS_DONE:
                return "维护完成";
            case MAINT_STATUS_REJECTED:
                return "退回待维护";
            default:
                return "待维护";
        }
    }

    private void updateMaintenanceStatus(Long id, String status) {
        ProjInfo update = new ProjInfo();
        update.setId(id);
        update.setAcceptStatus(status);
        projInfoMapper.updateById(update);
    }

    private boolean canProjectOwner(Long projectId) {
        try {
            flowAuditGuard.requireActors(projectId, "待维护项目负责人操作", "owner");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean canUnitMaintainAudit(ProjInfo project) {
        SysUser user = currentUserOrNull();
        if (user == null) {
            return UserContext.isAdmin();
        }
        String identity = user.getIdentityCode() == null ? "" : user.getIdentityCode();
        if ("admin".equals(identity)) {
            return true;
        }
        if (!"unitHead".equals(identity)) {
            return false;
        }
        Long userOrgId = user.getOrgId();
        if (userOrgId == null) {
            return false;
        }
        if (project.getOrgId() != null && userOrgId.equals(project.getOrgId())) {
            return true;
        }
        SysUser owner = findUserByOwnerLabel(project.getOwnerName());
        if (owner == null) {
            owner = findUserByOwnerLabel(project.getCreateByName());
        }
        return owner != null && userOrgId.equals(owner.getOrgId());
    }

    private boolean canHqMaintainAudit() {
        SysUser user = currentUserOrNull();
        if (user == null) {
            return UserContext.isAdmin();
        }
        String identity = user.getIdentityCode() == null ? "" : user.getIdentityCode();
        return "admin".equals(identity) || "hqHead".equals(identity) || "hqStaff".equals(identity);
    }

    private String userLabels(List<SysUser> users, String fallback) {
        if (users == null || users.isEmpty()) {
            return fallback;
        }
        String labels = users.stream()
                .map(this::userLabel)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("、"));
        return labels == null || labels.isBlank() ? fallback : labels;
    }

    private String userLabel(SysUser user) {
        if (user == null) {
            return "";
        }
        String name = safeLabel(user.getRealName(), safeLabel(user.getUsername(), ""));
        String emp = user.getEmployeeNo() == null ? "" : user.getEmployeeNo().trim();
        return emp.isEmpty() ? name : name + "（" + emp + "）";
    }

    private String safeLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void saveMaintenanceAuditRecord(Long projectId, String fieldCode, String fieldName, String opinion) {
        ProjMaterial record = new ProjMaterial();
        record.setBizType(MAINT_BIZ_TYPE);
        record.setBizId(projectId);
        record.setFieldCode(fieldCode);
        record.setFieldName(fieldName);
        record.setFileName(opinion == null ? "" : opinion);
        record.setFileUrl("");
        record.setVersion(1);
        record.setRequired(0);
        record.setLocked(1);
        record.setUploadedBy(UserContext.getUsername());
        record.setUploadedAt(LocalDateTime.now());
        materialMapper.insert(record);
    }

    private List<Map<String, Object>> maintenanceAuditRecords(Long projectId) {
        return materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                        .eq(ProjMaterial::getBizType, MAINT_BIZ_TYPE)
                        .eq(ProjMaterial::getBizId, projectId)
                        .orderByAsc(ProjMaterial::getUploadedAt))
                .stream()
                .filter(record -> record.getFieldCode() == null || !record.getFieldCode().startsWith("MAINTAIN_MATERIAL"))
                .map(record -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("action", record.getFieldName());
                    item.put("actor", record.getUploadedBy());
                    item.put("time", record.getUploadedAt());
                    item.put("opinion", record.getFileName());
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 新增项目（系统自动生成项目编号）
     */
    @PostMapping
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public R<Long> create(@RequestBody ProjInfo body) {
        flowAuditGuard.requireLedgerEditor("新增台账项目");
        body.setId(null);
        body.setDeleted(null);
        body.setProjectNo(nextProjectNo());
        if (body.getWarnColor() == null) {
            body.setWarnColor("BLUE");
        }
        if (body.getStatus() == null) {
            body.setStatus("DRAFT");
        }
        if (body.getDataSource() == null || body.getDataSource().isEmpty()) {
            body.setDataSource("PLATFORM");
        }
        if ((body.getCreateByName() == null || body.getCreateByName().isEmpty())
                && body.getOwnerName() != null && !body.getOwnerName().isEmpty()) {
            body.setCreateByName(body.getOwnerName());
        }
        applyOwnerOrgForFormMaint(body);
        body.setCreateBy(UserContext.getUserId());
        projInfoMapper.insert(body);
        syncOwnerMember(body.getId(), body.getOwnerName());
        return R.ok(body.getId());
    }

    private String nextProjectNo() {
        String prefix = "XM" + LocalDate.now().format(PROJECT_NO_DATE_FMT);
        String latest = projInfoMapper.selectMaxProjectNoByPrefix(prefix);
        long seq = 1;
        if (latest != null && latest.length() > prefix.length()) {
            try {
                seq = Long.parseLong(latest.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return SeqUtil.projectNo(seq);
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjInfo body) {
        ProjInfo existing = projInfoMapper.selectById(id);
        if (existing == null) return R.fail(404, "项目不存在或已删除");
        if ("FILING".equals(existing.getStatus()) && body.getStatus() != null
                && !"FILING".equals(body.getStatus())) {
            return R.fail(403, "立项备案状态只能通过总部备案审核流转，不能直接编辑");
        }
        // 项目团队不得直接改台账：必须通过「项目基本信息」草稿 → 四级审批（BasicDraftController）
        flowAuditGuard.requireLedgerEditor("台账编辑");
        flowAuditGuard.requireProjectAccess(id);
        if (!"FORM_MAINT".equals(existing.getDataSource())) {
            return R.fail(403, "已立项项目的台账由流程自动归集，不能直接编辑：补录请走「项目基本信息」审批，纠错请走「数据变更」");
        }
        // 系统字段不允许通过接口覆写
        body.setDeleted(null);
        body.setCreateBy(existing.getCreateBy());
        body.setCreateByName(existing.getCreateByName());
        body.setCreatedAt(null);
        body.setWarnColor(existing.getWarnColor());
        body.setOrgId(existing.getOrgId());
        body.setOrgName(existing.getOrgName());
        body.setId(id);
        if (body.getDataSource() == null || body.getDataSource().isBlank()) {
            body.setDataSource(existing.getDataSource());
        }
        if (body.getProjectNo() == null || body.getProjectNo().isBlank()) {
            body.setProjectNo(existing.getProjectNo());
        }
        applyOwnerOrgForFormMaint(body);
        projInfoMapper.updateById(body);
        if (body.getParticipants() != null) {
            participantMapper.delete(new LambdaQueryWrapper<ProjParticipant>()
                    .eq(ProjParticipant::getProjectId, id));
            int sort = 1;
            for (ProjParticipant participant : body.getParticipants()) {
                if ((participant.getOrgName() == null || participant.getOrgName().isBlank())
                        && (participant.getWorkContent() == null || participant.getWorkContent().isBlank())) continue;
                participant.setId(null);
                participant.setProjectId(id);
                participant.setSort(sort++);
                participantMapper.insert(participant);
            }
        }
        if (body.getTeamMembers() != null) {
            teamMemberMapper.delete(new LambdaQueryWrapper<ProjTeamMember>()
                    .eq(ProjTeamMember::getProjectId, id));
            int sort = 1;
            for (ProjTeamMember member : body.getTeamMembers()) {
                if ((member.getUserName() == null || member.getUserName().isBlank())
                        && (member.getEmployeeNo() == null || member.getEmployeeNo().isBlank())) continue;
                member.setId(null);
                member.setProjectId(id);
                member.setSort(sort++);
                teamMemberMapper.insert(member);
            }
        } else if (body.getOwnerName() != null && !body.getOwnerName().isEmpty()) {
            syncOwnerMember(id, body.getOwnerName());
        }
        return R.ok(true);
    }

    /** 表单维护页内编辑：不跳转台账详情，保存后仍写审计日志。 */
    @PutMapping("/form-maint/{id}")
    public R<Boolean> updateFromFormMaint(@PathVariable("id") Long id, @RequestBody ProjInfo body) {
        flowAuditGuard.requireAdmin("表单维护编辑项目");
        ProjInfo existing = projInfoMapper.selectById(id);
        if (existing == null) return R.fail(404, "项目不存在或已删除");
        body.setId(id);
        body.setProjectNo(existing.getProjectNo());
        body.setDataSource(existing.getDataSource() == null ? "FORM_MAINT" : existing.getDataSource());
        if (body.getOrgName() == null || body.getOrgName().isBlank()) {
            body.setOrgName(body.getLeadOrgName());
        }
        if (body.getCreateBy() == null) {
            body.setCreateBy(existing.getCreateBy());
        }
        applyOwnerOrgForFormMaint(body);
        projInfoMapper.updateById(body);
        syncOwnerMember(id, body.getOwnerName());
        auditLogMapper.write("PROJECT", "UPDATE", "PROJECT", id,
                "表单维护编辑项目：" + (body.getName() == null ? existing.getName() : body.getName()));
        return R.ok(true);
    }

    /** 年初编制/更新本年度目标，里程碑清单由里程碑接口按项目与年度实时汇总。 */
    @PutMapping("/{id}/annual-plan")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public R<Long> saveAnnualPlan(@PathVariable("id") Long id, @RequestBody ProjAnnualPlan body) {
        if (projInfoMapper.selectById(id) == null) {
            return R.fail("项目不存在");
        }
        flowAuditGuard.requireActors(id, "编制年度目标", "techLead", "owner", "contactLogin", "projectPm");
        body.setProjectId(id);
        if (body.getYear() == null) {
            body.setYear(java.time.LocalDate.now().getYear());
        }
        ProjAnnualPlan current = annualPlanMapper.selectOne(
                new LambdaQueryWrapper<ProjAnnualPlan>()
                        .eq(ProjAnnualPlan::getProjectId, id)
                        .eq(ProjAnnualPlan::getYear, body.getYear())
                        .last("LIMIT 1"));
        if (current != null && "PENDING_AUDIT".equals(current.getFinishStatus())) {
            return R.fail(403, "年度清单正在审核中，审核结束前不能修改年度目标");
        }
        if (current != null && "DONE".equals(current.getFinishStatus())) {
            return R.fail(403, "年度清单已审核存档，年度目标修改请通过「数据变更」办理");
        }
        // 审核状态只能由提交/审核接口流转，不接受前端直写
        if (current == null) {
            body.setId(null);
            body.setFinishStatus("DOING");
            body.setColorStatus("BLUE");
            annualPlanMapper.insert(body);
        } else {
            body.setId(current.getId());
            body.setFinishStatus(current.getFinishStatus());
            body.setColorStatus(current.getColorStatus());
            annualPlanMapper.updateById(body);
        }
        auditLogMapper.write("MILESTONE", "UPDATE", "ANNUAL_PLAN", body.getId(), "编制年度目标（" + body.getYear() + "）");
        return R.ok(body.getId());
    }

    /** 年度清单提交审核：至少一个里程碑；进入 PENDING_AUDIT，由单位科技部门审核存档。 */
    @PostMapping("/{id}/annual-plan/submit")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public R<Boolean> submitAnnualPlan(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjInfo project = projInfoMapper.selectById(id);
        if (project == null) {
            return R.fail("项目不存在");
        }
        flowAuditGuard.requireActors(id, "提交年度里程碑清单审核", "techLead", "owner", "contactLogin", "projectPm");
        int year = body == null || body.get("year") == null ? java.time.LocalDate.now().getYear()
                : Integer.parseInt(String.valueOf(body.get("year")));
        long count = milestoneMapper.selectCount(new LambdaQueryWrapper<ProjMilestone>()
                .eq(ProjMilestone::getProjectId, id).eq(ProjMilestone::getYear, year));
        if (count == 0) {
            return R.fail("请先编制至少一个里程碑节点再提交审核");
        }
        ProjAnnualPlan current = annualPlanMapper.selectOne(new LambdaQueryWrapper<ProjAnnualPlan>()
                .eq(ProjAnnualPlan::getProjectId, id).eq(ProjAnnualPlan::getYear, year).last("LIMIT 1"));
        if (current == null || current.getAnnualGoal() == null || current.getAnnualGoal().isBlank()) {
            return R.fail("请先填写并保存本年度目标，再提交清单审核");
        }
        {
            if ("PENDING_AUDIT".equals(current.getFinishStatus())) {
                return R.fail("年度清单已在审核中");
            }
            ProjAnnualPlan patch = new ProjAnnualPlan();
            patch.setId(current.getId());
            patch.setFinishStatus("PENDING_AUDIT");
            annualPlanMapper.updateById(patch);
        }
        auditLogMapper.write("MILESTONE", "SUBMIT", "ANNUAL_PLAN", current.getId(),
                "提交年度里程碑清单审核：" + project.getName() + "（" + year + "，" + count + " 个节点）");
        return R.ok(true);
    }

    /** 表单维护/台账同步：负责人写入项目团队 */
    private void syncOwnerMember(Long projectId, String ownerName) {
        if (projectId == null || ownerName == null || ownerName.isBlank()) {
            return;
        }
        teamMemberMapper.delete(new LambdaQueryWrapper<ProjTeamMember>()
                .eq(ProjTeamMember::getProjectId, projectId)
                .and(w -> w.eq(ProjTeamMember::getRoleCode, "PROJECT_LEADER")
                        .or()
                        .eq(ProjTeamMember::getRoleName, "项目负责人")));
        ProjTeamMember m = new ProjTeamMember();
        m.setProjectId(projectId);
        m.setGroupCode("TECH");
        m.setRoleCode("PROJECT_LEADER");
        m.setRoleName("项目负责人");
        SysUser owner = findUserByOwnerLabel(ownerName);
        if (owner != null) {
            m.setUserName(owner.getRealName() == null || owner.getRealName().isBlank() ? ownerName.trim() : owner.getRealName());
            m.setEmployeeNo(owner.getEmployeeNo());
        } else {
            m.setUserName(ownerName.trim());
        }
        m.setSort(1);
        teamMemberMapper.insert(m);
    }

    private void applyOwnerOrgForFormMaint(ProjInfo body) {
        if (body == null || !"FORM_MAINT".equals(body.getDataSource())) {
            return;
        }
        SysUser owner = findUserByOwnerLabel(body.getOwnerName());
        if (owner == null) {
            owner = findUserByOwnerLabel(body.getCreateByName());
        }
        if (owner == null) {
            return;
        }
        if (body.getOrgId() == null) {
            body.setOrgId(owner.getOrgId());
        }
        if ((body.getOrgName() == null || body.getOrgName().isBlank()) && owner.getOrgName() != null) {
            body.setOrgName(owner.getOrgName());
        }
    }

    private SysUser findUserByOwnerLabel(String label) {
        String text = label == null ? "" : label.trim();
        if (text.isEmpty()) {
            return null;
        }
        String emp = digits(text);
        String name = text.replaceAll("[（(]\\s*\\d+\\s*[）)]", "").trim();
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        w.and(q -> q.eq(SysUser::getRealName, text)
                .or().eq(SysUser::getUsername, text)
                .or().eq(SysUser::getRealName, name)
                .or().eq(SysUser::getUsername, name));
        if (!emp.isEmpty()) {
            w.or(q -> q.eq(SysUser::getEmployeeNo, emp).or().eq(SysUser::getUsername, emp));
        }
        w.last("LIMIT 1");
        return userMapper.selectOne(w);
    }

    private static boolean sameOwner(String userName, String userEmp, ProjTeamMember member) {
        if (member == null) {
            return false;
        }
        String memberName = member.getUserName() == null ? "" : member.getUserName().trim();
        String memberEmp = digits(member.getEmployeeNo());
        if (!userEmp.isEmpty() && userEmp.equals(memberEmp)) {
            return true;
        }
        return !userName.isEmpty() && userName.equals(memberName);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /**
     * 逻辑删除
     */
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        ProjInfo project = projInfoMapper.selectById(id);
        if (project == null) return R.fail(404, "项目不存在或已删除");
        if ("FORM_MAINT".equals(project.getDataSource())) {
            return R.fail(403, "表单维护导入的项目请在表单维护页面删除");
        }
        if (!flowAuditGuard.canLedgerDelete()) return R.fail(403, "无项目台账删除权限");
        return R.ok(projInfoMapper.deleteById(id) > 0);
    }

    /** 表单维护和台账共用项目记录，删除后两处同步移除。 */
    @DeleteMapping("/form-maint/{id}")
    public R<Boolean> deleteFromFormMaint(@PathVariable("id") Long id) {
        flowAuditGuard.requireAdmin("表单维护删除项目");
        if (projInfoMapper.selectById(id) == null) return R.fail(404, "项目不存在或已删除");
        return R.ok(projInfoMapper.deleteById(id) > 0);
    }

    /**
     * 提交备案
     */
    @PostMapping("/{id}/submit")
    public R<Boolean> submit(@PathVariable("id") Long id) {
        flowAuditGuard.requireActors(id, "提交备案", "owner", "contactLogin", "techLead", "projectPm", "unitHead", "unitStaff");
        ProjInfo info = new ProjInfo();
        info.setId(id);
        info.setStatus("DECLARING");
        projInfoMapper.updateById(info);
        return R.ok(true);
    }

    /**
     * 重算项目四色状态与项目状态：
     * 取里程碑、交付物、待办计划中最严重的颜色作为项目预警色
     */
    @PostMapping("/{id}/refresh-status")
    public R<ProjInfo> refreshStatus(@PathVariable("id") Long id) {
        ProjInfo info = projInfoMapper.selectById(id);
        if (info == null) {
            return R.fail("项目不存在");
        }
        List<String> colors = new ArrayList<>();
        for (ProjMilestone m : milestoneMapper.selectList(
                new LambdaQueryWrapper<ProjMilestone>().eq(ProjMilestone::getProjectId, id))) {
            colors.add(ColorUtil.calcCode(m.getPlanDate(), "DONE".equals(m.getStatus())));
        }
        for (ProjDeliverable d : deliverableMapper.selectList(
                new LambdaQueryWrapper<ProjDeliverable>().eq(ProjDeliverable::getProjectId, id))) {
            colors.add(ColorUtil.calcCode(d.getDueDate(), "DELIVERED".equals(d.getStatus())));
        }
        for (ProjPlan p : planMapper.selectList(
                new LambdaQueryWrapper<ProjPlan>().eq(ProjPlan::getProjectId, id))) {
            colors.add(ColorUtil.calcCode(p.getDueDate(), "DONE".equals(p.getPlanType())));
        }
        flowAuditGuard.requireProjectAccess(id);
        info.setWarnColor(ColorUtil.worstCode(colors));
        if ("RED".equals(info.getWarnColor()) && "IMPLEMENTING".equals(info.getStatus())) {
            info.setStatus("DELAYED");
        } else if (!"RED".equals(info.getWarnColor()) && "DELAYED".equals(info.getStatus())) {
            info.setStatus("IMPLEMENTING");
        }
        projInfoMapper.updateById(info);
        return R.ok(info);
    }

    /**
     * 导出（返回符合条件的全部记录，前端生成 Excel/CSV）
     */
    @GetMapping("/export")
    public R<List<ProjInfo>> export(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "levelCode", required = false) String levelCode,
            @RequestParam(value = "channelId", required = false) Long channelId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warnColor", required = false) String warnColor) {
        LambdaQueryWrapper<ProjInfo> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            w.and(x -> x.like(ProjInfo::getName, keyword).or().like(ProjInfo::getProjectNo, keyword));
        }
        if (levelCode != null && !levelCode.isEmpty()) {
            w.eq(ProjInfo::getLevelCode, levelCode);
        }
        if (channelId != null) {
            w.eq(ProjInfo::getChannelId, channelId);
        }
        if (status != null && !status.isEmpty()) {
            w.eq(ProjInfo::getStatus, status);
        }
        if (warnColor != null && !warnColor.isEmpty()) {
            w.eq(ProjInfo::getWarnColor, warnColor);
        }
        SysUser viewer = currentUserOrNull();
        applyViewerScope(w, viewer);
        return R.ok(projInfoMapper.selectList(w));
    }

    /** 计划填报模板用于提前查看要求，不能被当作节点完成佐证。 */
    private static boolean hasCompletionEvidence(List<ProjMaterial> materials) {
        return materials != null && materials.stream().anyMatch(material ->
                material.getFileUrl() != null
                        && !material.getFileUrl().isBlank()
                        && !"PLAN_TEMPLATE".equalsIgnoreCase(material.getFieldCode()));
    }
}
