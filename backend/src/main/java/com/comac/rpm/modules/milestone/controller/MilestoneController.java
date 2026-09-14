package com.comac.rpm.modules.milestone.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.declaration.entity.ProjMaterial;
import com.comac.rpm.modules.declaration.mapper.ProjMaterialMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.project.entity.ProjAnnualPlan;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjAnnualPlanMapper;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 年度里程碑：四色预警、闭环销项、延期引导、填报看板
 */
@RestController
@RequestMapping("/api")
public class MilestoneController {

    private static final String ANNUAL_STATUS_PENDING_AUDIT = "PENDING_AUDIT";
    private static final String ANNUAL_STATUS_DONE = "DONE";
    private static final String ANNUAL_STATUS_RETURN = "RETURN";
    private static final String MILESTONE_STATUS_CLOSE_DEPT_AUDIT = "CLOSE_DEPT_AUDIT";
    private static final String MILESTONE_STATUS_CLOSE_UNIT_AUDIT = "CLOSE_UNIT_AUDIT";

    @Autowired
    private ProjMilestoneMapper milestoneMapper;

    @Autowired
    private ProjMaterialMapper materialMapper;

    @Autowired
    private ProjInfoMapper projInfoMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @Autowired
    private ProjAnnualPlanMapper annualPlanMapper;

    @Autowired
    private SysAuditLogMapper auditLogMapper;
    @Autowired
    private ProjTeamMemberMapper teamMemberMapper;

    @GetMapping("/projects/{projectId}/milestones")
    public R<List<ProjMilestone>> list(@PathVariable("projectId") Long projectId,
                                       @RequestParam(value = "year", required = false) Integer year) {
        LambdaQueryWrapper<ProjMilestone> w = new LambdaQueryWrapper<ProjMilestone>()
                .eq(ProjMilestone::getProjectId, projectId);
        if (year != null) {
            w.eq(ProjMilestone::getYear, year);
        }
        w.orderByAsc(ProjMilestone::getPlanDate);
        List<ProjMilestone> list = milestoneMapper.selectList(w);
        ProjInfo project = projInfoMapper.selectById(projectId);
        for (ProjMilestone m : list) {
            decorate(m, project);
        }
        return R.ok(list);
    }

    /**
     * 填报看板：汇总卡片 + 我的待办 + 分项目节点清单
     */
    @GetMapping("/milestones/board")
    public R<Map<String, Object>> board(@RequestParam(value = "year", required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        SysUser currentUser = flowAuditGuard.currentUser();
        Set<Long> assignedProjectIds = assignedProjectIds(currentUser);
        LambdaQueryWrapper<ProjInfo> pw = new LambdaQueryWrapper<>();
        if (!UserContext.isHeadquarter()) {
            pw.and(scope -> {
                scope.eq(ProjInfo::getOrgId, UserContext.getOrgId());
                if (!assignedProjectIds.isEmpty()) {
                    scope.or().in(ProjInfo::getId, assignedProjectIds);
                }
            });
        }
        pw.orderByDesc(ProjInfo::getId);
        List<ProjInfo> projects = projInfoMapper.selectList(pw);
        if (projects.size() > 80) {
            projects = projects.subList(0, 80);
        }

        Map<Long, List<ProjAnnualPlan>> planByProject = annualPlanMapper.selectList(
                        new LambdaQueryWrapper<ProjAnnualPlan>().eq(ProjAnnualPlan::getYear, y))
                .stream().collect(Collectors.groupingBy(ProjAnnualPlan::getProjectId));

        List<Map<String, Object>> todos = new ArrayList<>();
        List<Map<String, Object>> boards = new ArrayList<>();
        int total = 0;
        int done = 0;
        int yellow = 0;
        int red = 0;

        for (ProjInfo p : projects) {
            List<ProjMilestone> ms = milestoneMapper.selectList(new LambdaQueryWrapper<ProjMilestone>()
                    .eq(ProjMilestone::getProjectId, p.getId())
                    .eq(ProjMilestone::getYear, y)
                    .orderByAsc(ProjMilestone::getPlanDate));
            for (ProjMilestone m : ms) {
                decorate(m, p);
            }

            List<ProjAnnualPlan> plans = planByProject.getOrDefault(p.getId(), List.of());
            ProjAnnualPlan plan = plans.isEmpty() ? null : plans.get(0);
            String annualGoal = plan == null ? "" : nvl(plan.getAnnualGoal());
            String planContent = plan == null ? "" : nvl(plan.getPlanContent());
            String annualStatus = plan == null ? "" : nvl(plan.getFinishStatus());
            boolean pendingAnnualAudit = ANNUAL_STATUS_PENDING_AUDIT.equals(annualStatus);
            boolean annualArchived = ANNUAL_STATUS_DONE.equals(annualStatus);
            boolean needCompile = ms.isEmpty() || ANNUAL_STATUS_RETURN.equals(annualStatus);

            if (pendingAnnualAudit) {
                if (canAuditAnnualPlan(p, currentUser)) {
                    todos.add(todo("COMPILE_AUDIT", p, null, y, "里程碑清单审核"));
                }
            } else if (needCompile) {
                todos.add(todo("COMPILE", p, null, y, "编制里程碑节点"));
            }
            for (ProjMilestone m : ms) {
                total++;
                if ("DONE".equals(m.getStatus())) {
                    done++;
                } else if (isCloseAuditStatus(m.getStatus())) {
                    if (canAuditCloseMilestone(p, currentUser, m.getStatus())) {
                        todos.add(todo("CLOSE_AUDIT", p, m, y, "销项审核"));
                    }
                } else if ((annualArchived || plan == null) && canCloseMilestone(p, currentUser)) {
                    todos.add(todo("CLOSE", p, m, y, "节点销项"));
                }
                if ("YELLOW".equals(m.getColorStatus())) {
                    yellow++;
                }
                if ("RED".equals(m.getColorStatus())) {
                    red++;
                }
            }

            List<String> colors = new ArrayList<>();
            colors.add(p.getWarnColor());
            for (ProjMilestone m : ms) {
                colors.add(m.getColorStatus());
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("projectId", p.getId());
            card.put("projectNo", p.getProjectNo());
            card.put("projectName", p.getName());
            card.put("ownerName", p.getOwnerName());
            card.put("annualGoal", annualGoal);
            card.put("planContent", planContent);
            card.put("annualStatus", annualStatus);
            card.put("annualAuditPending", pendingAnnualAudit);
            card.put("annualArchived", annualArchived);
            card.put("year", y);
            card.put("warnColor", ColorUtil.worstCode(colors));
            card.put("msDone", ms.stream().filter(x -> "DONE".equals(x.getStatus())).count());
            card.put("msTotal", ms.size());
            card.put("milestones", ms);
            boards.add(card);
        }

        long compile = todos.stream().filter(t -> "COMPILE".equals(t.get("taskType"))).count();
        long audit = todos.stream().filter(t -> "COMPILE_AUDIT".equals(t.get("taskType"))).count();
        long closeAudit = todos.stream().filter(t -> "CLOSE_AUDIT".equals(t.get("taskType"))).count();
        long close = todos.stream().filter(t -> "CLOSE".equals(t.get("taskType"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("year", y);
        summary.put("todo", todos.size());
        summary.put("compile", compile);
        summary.put("audit", audit);
        summary.put("closeAudit", closeAudit);
        summary.put("review", audit + closeAudit);
        summary.put("close", close);
        summary.put("yellow", yellow);
        summary.put("red", red);
        summary.put("total", total);
        summary.put("done", done);

        Map<String, Object> data = new HashMap<>();
        data.put("summary", summary);
        data.put("todos", todos);
        data.put("projects", boards);
        return R.ok(data);
    }


    @PostMapping("/milestones/annual-plan/audit")
    public R<Boolean> auditAnnualPlan(@RequestParam("projectId") Long projectId,
                                      @RequestParam(value = "year", required = false) Integer year,
                                      @RequestBody(required = false) Map<String, Object> body) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException("项目不存在");
        }
        int y = year == null ? LocalDate.now().getYear() : year;
        ProjInfo project = projInfoMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        flowAuditGuard.requireActors(projectId, "审核里程碑清单", "unitHead");
        ProjAnnualPlan plan = annualPlanMapper.selectOne(new LambdaQueryWrapper<ProjAnnualPlan>()
                .eq(ProjAnnualPlan::getProjectId, projectId)
                .eq(ProjAnnualPlan::getYear, y)
                .last("LIMIT 1"));
        if (plan == null) {
            throw new BusinessException("年度里程碑清单尚未提交审查");
        }
        boolean pass = body == null || body.get("pass") == null || Boolean.parseBoolean(String.valueOf(body.get("pass")));
        plan.setFinishStatus(pass ? ANNUAL_STATUS_DONE : ANNUAL_STATUS_RETURN);
        plan.setColorStatus(pass ? "GREEN" : "YELLOW");
        annualPlanMapper.updateById(plan);
        auditLogMapper.write("MILESTONE", pass ? "APPROVE" : "REJECT", "ANNUAL_PLAN", plan.getId(),
                (pass ? "审核通过" : "驳回") + "里程碑节点与交付物清单：" + project.getName() + "（" + y + "）");
        return R.ok(true);
    }

    @GetMapping("/milestones/{id}")
    public R<ProjMilestone> detail(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("里程碑不存在");
        }
        ProjMilestone m = milestoneMapper.selectById(id);
        if (m == null) {
            throw new BusinessException("里程碑不存在");
        }
        decorate(m, projInfoMapper.selectById(m.getProjectId()));
        return R.ok(m);
    }

    @GetMapping("/milestones/{id}/materials")
    public R<List<ProjMaterial>> materials(@PathVariable("id") Long id) {
        return R.ok(listMaterials(id));
    }

    @PostMapping("/milestones/{id}/materials")
    public R<Long> saveMaterial(@PathVariable("id") Long id, @RequestBody ProjMaterial body) {
        ProjMilestone milestoneRecord = milestoneMapper.selectById(id);
        if (milestoneRecord == null) {
            throw new BusinessException("里程碑不存在");
        }
        requireProjectOwner(milestoneRecord.getProjectId(), "上传里程碑销项材料");
        ProjMaterial current = materialMapper.selectOne(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, "MILESTONE")
                .eq(ProjMaterial::getBizId, id)
                .eq(ProjMaterial::getFieldCode, body.getFieldCode() == null ? "EVIDENCE" : body.getFieldCode())
                .last("LIMIT 1"));
        body.setBizType("MILESTONE");
        body.setBizId(id);
        body.setFieldCode(body.getFieldCode() == null ? "EVIDENCE" : body.getFieldCode());
        body.setFieldName(body.getFieldName() == null ? "节点完成佐证材料" : body.getFieldName());
        body.setUploadedBy(UserContext.getUsername());
        body.setUploadedAt(LocalDateTime.now());
        body.setRequired(1);
        body.setLocked(0);
        if (current == null) {
            body.setId(null);
            body.setVersion(1);
            materialMapper.insert(body);
        } else {
            body.setId(current.getId());
            body.setVersion((current.getVersion() == null ? 1 : current.getVersion()) + 1);
            materialMapper.updateById(body);
        }
        ProjMilestone milestone = new ProjMilestone();
        milestone.setId(id);
        milestone.setEvidence(1);
        milestoneMapper.updateById(milestone);
        return R.ok(body.getId());
    }

    private List<ProjMaterial> listMaterials(Long milestoneId) {
        if (milestoneId == null) {
            return List.of();
        }
        return materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, "MILESTONE")
                .eq(ProjMaterial::getBizId, milestoneId)
                .orderByDesc(ProjMaterial::getUploadedAt));
    }

    @PostMapping("/milestones")
    public R<Long> create(@RequestBody ProjMilestone body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增里程碑", "techLead", "owner", "contactLogin");
        body.setId(null);
        body.setColorStatus(ColorUtil.calcCode(body.getPlanDate(), false));
        if (body.getStatus() == null) {
            body.setStatus("DOING");
        }
        milestoneMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/milestones/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjMilestone body) {
        flowAuditGuard.requireIdentities("修改里程碑", "techLead", "owner", "contactLogin");
        ProjMilestone existing = milestoneMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("里程碑不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), "修改里程碑", "techLead", "owner", "contactLogin");
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        body.setColorStatus(ColorUtil.calcCode(body.getPlanDate(), "DONE".equals(body.getStatus())));
        milestoneMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/milestones/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        flowAuditGuard.requireIdentities("删除里程碑", "techLead", "owner", "contactLogin");
        ProjMilestone existing = milestoneMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("里程碑不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), "删除里程碑", "techLead", "owner", "contactLogin");
        milestoneMapper.deleteById(id);
        return R.ok(true);
    }

    /**
     * 闭环销项：必须已上传佐证材料；销项后转为绿色已完成
     */
    @PostMapping("/milestones/{id}/close")
    public R<Boolean> close(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjMilestone m = milestoneMapper.selectById(id);
        if (m == null) {
            throw new BusinessException("里程碑不存在");
        }
        requireProjectOwner(m.getProjectId(), "节点闭环销项");
        int evidence = hasCompletionEvidence(listMaterials(id)) ? 1 : 0;
        if (evidence == 0) {
            throw new BusinessException("请先上传节点佐证材料，再执行闭环销项");
        }
        if (isCloseAuditStatus(m.getStatus())) {
            throw new BusinessException("该节点销项正在审核中，请等待审核结果");
        }
        m.setStatus(MILESTONE_STATUS_CLOSE_DEPT_AUDIT);
        m.setActualDate(null);
        m.setEvidence(1);
        milestoneMapper.updateById(m);
        auditLogMapper.write("MILESTONE", "SUBMIT", "MILESTONE", id,
                "项目负责人提交里程碑销项审核：" + nvl(m.getName()));
        return R.ok(true);
    }

    /**
     * 里程碑销项审核：项目承担部门负责人初审，单位科研管理部门负责人终审。
     */
    @PostMapping("/milestones/{id}/close-audit")
    public R<Boolean> auditClose(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjMilestone m = milestoneMapper.selectById(id);
        if (m == null) {
            throw new BusinessException("里程碑不存在");
        }
        ProjInfo project = projInfoMapper.selectById(m.getProjectId());
        String status = nvl(m.getStatus());
        boolean pass = body == null || body.get("pass") == null || Boolean.parseBoolean(String.valueOf(body.get("pass")));
        String remark = body == null || body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
        if (MILESTONE_STATUS_CLOSE_DEPT_AUDIT.equals(status)) {
            requireCloseAuditor(project, MILESTONE_STATUS_CLOSE_DEPT_AUDIT, "项目承担部门负责人审核里程碑销项");
            if (pass) {
                m.setStatus(MILESTONE_STATUS_CLOSE_UNIT_AUDIT);
                milestoneMapper.updateById(m);
                auditLogMapper.write("MILESTONE", "APPROVE", "MILESTONE", id,
                        "项目承担部门负责人审核通过，流转单位科研管理部门负责人：" + nvl(m.getName()));
            } else {
                returnCloseToOwner(m, id, "项目承担部门负责人退回里程碑销项：", remark);
            }
            return R.ok(true);
        }
        if (MILESTONE_STATUS_CLOSE_UNIT_AUDIT.equals(status)) {
            requireCloseAuditor(project, MILESTONE_STATUS_CLOSE_UNIT_AUDIT, "单位科研管理部门负责人审核里程碑销项");
            if (pass) {
                m.setStatus("DONE");
                m.setActualDate(LocalDate.now());
                m.setColorStatus("GREEN");
                m.setEvidence(1);
                milestoneMapper.updateById(m);
                auditLogMapper.write("MILESTONE", "APPROVE", "MILESTONE", id,
                        "单位科研管理部门负责人审核通过，里程碑完成销项：" + nvl(m.getName()));
            } else {
                returnCloseToOwner(m, id, "单位科研管理部门负责人退回里程碑销项：", remark);
            }
            return R.ok(true);
        }
        throw new BusinessException(403, "当前节点不在销项审核环节");
    }

    private void returnCloseToOwner(ProjMilestone m, Long id, String prefix, String remark) {
        m.setStatus("DOING");
        m.setActualDate(null);
        m.setColorStatus(ColorUtil.calcCode(m.getPlanDate(), false));
        milestoneMapper.updateById(m);
        auditLogMapper.write("MILESTONE", "REJECT", "MILESTONE", id,
                prefix + nvl(m.getName()) + (remark.isBlank() ? "" : "，意见：" + remark));
    }

    /**
     * 超期延期：禁止直接修改日期，引导走【项目变更】模块
     */
    @PostMapping("/milestones/{id}/delay")
    public R<Boolean> delay(@PathVariable("id") Long id) {
        throw new BusinessException("节点已超期，禁止直接修改日期，请通过【项目变更】模块发起延期审批");
    }

    @GetMapping("/milestones/mine")
    public R<List<ProjMilestone>> mine() {
        return R.ok(milestoneMapper.selectList(
                new LambdaQueryWrapper<ProjMilestone>().isNotNull(ProjMilestone::getId)
                        .orderByAsc(ProjMilestone::getPlanDate)));
    }

    @GetMapping("/milestones/current-user")
    public R<Long> currentUser() {
        return R.ok(UserContext.getUserId());
    }

    private void decorate(ProjMilestone m, ProjInfo p) {
        m.setColorStatus(ColorUtil.calcCode(m.getPlanDate(), "DONE".equals(m.getStatus())));
        if ("RED".equals(m.getColorStatus()) && !"DONE".equals(m.getStatus()) && !isCloseAuditStatus(m.getStatus())) {
            m.setStatus("OVERDUE");
        }
        m.setMaterials(listMaterials(m.getId()));
        m.setEvidence(hasCompletionEvidence(m.getMaterials()) ? 1 : 0);
        if (p != null) {
            m.setProjectName(p.getName());
            m.setProjectNo(p.getProjectNo());
            m.setOwnerName(p.getOwnerName());
        }
    }

    private Set<Long> assignedProjectIds(SysUser user) {
        if (user == null) {
            return Set.of();
        }
        String emp = digits(user.getEmployeeNo());
        String name = user.getRealName() == null ? "" : user.getRealName().trim();
        if (emp.isEmpty() && name.isEmpty()) {
            return Set.of();
        }
        List<ProjTeamMember> rows = teamMemberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>()
                        .and(w -> {
                            boolean has = false;
                            if (!emp.isEmpty()) {
                                w.eq(ProjTeamMember::getEmployeeNo, emp);
                                has = true;
                            }
                            if (!name.isEmpty()) {
                                if (has) {
                                    w.or();
                                }
                                w.eq(ProjTeamMember::getUserName, name);
                            }
                        }));
        Set<Long> ids = new HashSet<>();
        for (ProjTeamMember row : rows) {
            if (row.getProjectId() != null) {
                ids.add(row.getProjectId());
            }
        }
        return ids;
    }

    private void requireProjectOwner(Long projectId, String actionLabel) {
        ProjInfo project = projectId == null ? null : projInfoMapper.selectById(projectId);
        SysUser user = flowAuditGuard.currentUser();
        if (!canCloseMilestone(project, user)) {
            throw new BusinessException(403, "仅项目团队负责人可办理「" + actionLabel + "」");
        }
    }

    private boolean canCloseMilestone(ProjInfo project, SysUser user) {
        if (project == null || user == null) {
            return false;
        }
        List<ProjTeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, project.getId()));
        if (members != null && !members.isEmpty()) {
            for (ProjTeamMember member : members) {
                if (isMemberRole(member, "PROJECT_LEADER", "项目负责人") && samePerson(user, member)) {
                    return true;
                }
            }
        }
        return matchesOwnerText(project.getOwnerName(), user);
    }

    private void requireCloseAuditor(ProjInfo project, String status, String actionLabel) {
        SysUser user = flowAuditGuard.currentUser();
        if (!canAuditCloseMilestone(project, user, status)) {
            throw new BusinessException(403, "仅当前销项审核节点指定办理人可办理「" + actionLabel + "」");
        }
    }

    private boolean canAuditCloseMilestone(ProjInfo project, SysUser user, String status) {
        if (project == null || user == null) {
            return false;
        }
        List<ProjTeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, project.getId()));
        if (MILESTONE_STATUS_CLOSE_DEPT_AUDIT.equals(status)) {
            ProjTeamMember deptHead = findMember(members, "DEPT_HEAD", "项目承担部门负责人");
            if (deptHead != null) {
                return samePerson(user, deptHead);
            }
            return "deptHead".equals(nvl(user.getIdentityCode())) && project.getOrgId() != null && project.getOrgId().equals(user.getOrgId());
        }
        if (MILESTONE_STATUS_CLOSE_UNIT_AUDIT.equals(status)) {
            ProjTeamMember unitHead = findMember(members, "UNIT_MINISTER", "单位科技部长", "单位科研管理部门负责人");
            if (unitHead != null) {
                return samePerson(user, unitHead);
            }
            return "unitHead".equals(nvl(user.getIdentityCode())) && project.getOrgId() != null && project.getOrgId().equals(user.getOrgId());
        }
        return false;
    }

    private static boolean isCloseAuditStatus(String status) {
        return MILESTONE_STATUS_CLOSE_DEPT_AUDIT.equals(status) || MILESTONE_STATUS_CLOSE_UNIT_AUDIT.equals(status);
    }

    private static String closeAuditNodeLabel(String status) {
        if (MILESTONE_STATUS_CLOSE_DEPT_AUDIT.equals(status)) {
            return "项目承担部门负责人审核";
        }
        if (MILESTONE_STATUS_CLOSE_UNIT_AUDIT.equals(status)) {
            return "单位科研管理部门负责人审核";
        }
        return "里程碑销项";
    }

    private boolean canAuditAnnualPlan(ProjInfo project, SysUser user) {
        if (user == null) {
            return false;
        }
        String identityCode = nvl(user.getIdentityCode());
        if ("admin".equals(identityCode)) {
            return true;
        }
        if (!"unitHead".equals(identityCode)) {
            return false;
        }
        List<ProjTeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, project.getId()));
        ProjTeamMember namedUnitHead = findMember(members, "UNIT_MINISTER", "单位科技部长", "单位科研管理部门负责人");
        if (namedUnitHead != null) {
            return samePerson(user, namedUnitHead);
        }
        return project.getOrgId() != null && project.getOrgId().equals(user.getOrgId());
    }

    private static ProjTeamMember findMember(List<ProjTeamMember> members, String... keys) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        for (ProjTeamMember member : members) {
            if (isMemberRole(member, keys)) {
                return member;
            }
        }
        return null;
    }

    private static boolean isMemberRole(ProjTeamMember member, String... keys) {
        if (member == null) {
            return false;
        }
        String roleCode = nvl(member.getRoleCode());
        String roleName = nvl(member.getRoleName());
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            if (key.equals(roleCode) || key.equals(roleName) || roleName.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePerson(SysUser user, ProjTeamMember member) {
        return matchesUserText(member == null ? null : member.getEmployeeNo(), user)
                || matchesUserText(member == null ? null : member.getUserName(), user);
    }

    private static boolean matchesOwnerText(String ownerName, SysUser user) {
        return matchesUserText(ownerName, user);
    }

    private static boolean matchesUserText(String text, SysUser user) {
        if (text == null || user == null) {
            return false;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return false;
        }
        String userEmp = digits(user.getEmployeeNo());
        String usernameNo = digits(user.getUsername());
        String valueNo = digits(value);
        if (!valueNo.isEmpty() && ((!userEmp.isEmpty() && valueNo.contains(userEmp))
                || (!usernameNo.isEmpty() && valueNo.contains(usernameNo)))) {
            return true;
        }
        String realName = nvl(user.getRealName()).trim();
        String username = nvl(user.getUsername()).trim();
        return (!realName.isEmpty() && (value.equals(realName) || value.contains(realName)))
                || (!username.isEmpty() && (value.equals(username) || value.contains(username)));
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private Map<String, Object> todo(String type, ProjInfo p, ProjMilestone m, int year, String typeLabel) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("taskType", type);
        t.put("typeLabel", typeLabel);
        t.put("projectId", p.getId());
        t.put("projectNo", p.getProjectNo());
        t.put("projectName", p.getName());
        t.put("ownerName", p.getOwnerName());
        t.put("year", year);
        if (m != null) {
            t.put("milestoneId", m.getId());
            t.put("milestoneName", m.getName());
            t.put("planDate", m.getPlanDate());
            t.put("colorStatus", m.getColorStatus());
            t.put("status", m.getStatus());
            t.put("flowNode", closeAuditNodeLabel(m.getStatus()));
            t.put("materialCount", m.getMaterials() == null ? 0 : m.getMaterials().size());
            t.put("materials", m.getMaterials());
        } else {
            t.put("milestoneId", null);
            t.put("milestoneName", "里程碑节点清单");
            t.put("planDate", year + "-04-30");
            t.put("colorStatus", "BLUE");
            t.put("status", "COMPILE_AUDIT".equals(type) ? ANNUAL_STATUS_PENDING_AUDIT : "DOING");
            t.put("materialCount", 0);
            t.put("materials", List.of());
        }
        return t;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 计划填报模板用于提前查看要求，不能被当作节点完成佐证。 */
    private static boolean hasCompletionEvidence(List<ProjMaterial> materials) {
        return materials != null && materials.stream().anyMatch(material ->
                material.getFileUrl() != null
                        && !material.getFileUrl().isBlank()
                        && !"PLAN_TEMPLATE".equalsIgnoreCase(material.getFieldCode()));
    }
}



