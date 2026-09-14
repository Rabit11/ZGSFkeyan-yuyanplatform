package com.comac.rpm.modules.milestone.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.common.util.SeqUtil;
import com.comac.rpm.modules.change.entity.ProjChange;
import com.comac.rpm.modules.change.mapper.ProjChangeMapper;
import com.comac.rpm.modules.declaration.entity.ProjMaterial;
import com.comac.rpm.modules.declaration.mapper.ProjMaterialMapper;
import com.comac.rpm.modules.file.MinioStorageService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 年度里程碑：四色预警、闭环销项（两级审核）、延期走项目变更、填报看板。
 * <p>
 * 管控约束（后端强制，不依赖前端）：
 * <ul>
 *   <li>PUT 只接受 name/budget/year/planDate，状态、佐证、实际完成日期不可直接写；</li>
 *   <li>计划日期进入基线（年度清单审核存档）或已逾期后锁定，只能通过 /delay 生成变更单，审批通过后回写；</li>
 *   <li>佐证材料必须是 MinIO 中真实存在的对象；</li>
 *   <li>逾期节点销项必须登记滞后原因；销项进入两级审核，不直接转绿；</li>
 *   <li>删除为逻辑删除，且仅限 DOING、无佐证、未进基线的节点。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class MilestoneController {

    private static final String ANNUAL_STATUS_PENDING_AUDIT = "PENDING_AUDIT";
    private static final String ANNUAL_STATUS_DONE = "DONE";
    private static final String ANNUAL_STATUS_RETURN = "RETURN";
    private static final String STATUS_DOING = "DOING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_CLOSE_DEPT_AUDIT = "CLOSE_DEPT_AUDIT";
    private static final String STATUS_CLOSE_UNIT_AUDIT = "CLOSE_UNIT_AUDIT";
    private static final String MATERIAL_BIZ = "MILESTONE";

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
    @Autowired
    private ProjChangeMapper changeMapper;
    @Autowired
    private MinioStorageService storageService;

    // ------------------------------------------------------------------ 查询

    @GetMapping("/projects/{projectId}/milestones")
    public R<List<ProjMilestone>> list(@PathVariable("projectId") Long projectId,
                                       @RequestParam(value = "year", required = false) Integer year) {
        flowAuditGuard.requireProjectAccess(projectId);
        LambdaQueryWrapper<ProjMilestone> w = new LambdaQueryWrapper<ProjMilestone>()
                .eq(ProjMilestone::getProjectId, projectId);
        if (year != null) {
            w.eq(ProjMilestone::getYear, year);
        }
        w.orderByAsc(ProjMilestone::getPlanDate);
        List<ProjMilestone> list = milestoneMapper.selectList(w);
        ProjInfo project = projInfoMapper.selectById(projectId);
        Map<Long, List<ProjMaterial>> materials = materialsByMilestone(list);
        boolean finance = financeViewer();
        for (ProjMilestone m : list) {
            decorate(m, project, materials.getOrDefault(m.getId(), List.of()));
            if (finance) {
                trimForFinance(m);
            }
        }
        return R.ok(list);
    }

    /**
     * 填报看板：汇总卡片 + 我的待办 + 分项目节点清单（按数据范围）
     */
    @GetMapping("/milestones/board")
    public R<Map<String, Object>> board(@RequestParam(value = "year", required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        SysUser currentUser = flowAuditGuard.currentUser();
        Set<Long> assignedProjectIds = flowAuditGuard.assignedProjectIds(currentUser);
        LambdaQueryWrapper<ProjInfo> pw = new LambdaQueryWrapper<>();
        if (!FlowAuditGuard.isHqIdentity(FlowAuditGuard.identityOf(currentUser))) {
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
        List<Long> projectIds = projects.stream().map(ProjInfo::getId).collect(Collectors.toList());

        Map<Long, List<ProjAnnualPlan>> planByProject = projectIds.isEmpty() ? Map.of() : annualPlanMapper.selectList(
                        new LambdaQueryWrapper<ProjAnnualPlan>().eq(ProjAnnualPlan::getYear, y)
                                .in(ProjAnnualPlan::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjAnnualPlan::getProjectId));
        List<ProjMilestone> allMs = projectIds.isEmpty() ? List.of() : milestoneMapper.selectList(
                new LambdaQueryWrapper<ProjMilestone>().in(ProjMilestone::getProjectId, projectIds)
                        .eq(ProjMilestone::getYear, y).orderByAsc(ProjMilestone::getPlanDate));
        Map<Long, List<ProjMaterial>> materialsMap = materialsByMilestone(allMs);
        Map<Long, List<ProjMilestone>> msByProject = allMs.stream().collect(Collectors.groupingBy(ProjMilestone::getProjectId));
        Map<Long, List<ProjTeamMember>> membersByProject = projectIds.isEmpty() ? Map.of() : teamMemberMapper.selectList(
                        new LambdaQueryWrapper<ProjTeamMember>().in(ProjTeamMember::getProjectId, projectIds))
                .stream().collect(Collectors.groupingBy(ProjTeamMember::getProjectId));

        List<Map<String, Object>> todos = new ArrayList<>();
        List<Map<String, Object>> boards = new ArrayList<>();
        boolean financeBoard = FlowAuditGuard.isFinanceIdentity(FlowAuditGuard.identityOf(currentUser));
        int total = 0;
        int done = 0;
        int yellow = 0;
        int red = 0;

        for (ProjInfo p : projects) {
            List<ProjMilestone> ms = msByProject.getOrDefault(p.getId(), List.of());
            List<ProjTeamMember> members = membersByProject.getOrDefault(p.getId(), List.of());
            for (ProjMilestone m : ms) {
                decorate(m, p, materialsMap.getOrDefault(m.getId(), List.of()));
                if (financeBoard) {
                    trimForFinance(m);
                }
            }

            List<ProjAnnualPlan> plans = planByProject.getOrDefault(p.getId(), List.of());
            ProjAnnualPlan plan = plans.isEmpty() ? null : plans.get(0);
            String annualGoal = plan == null ? "" : nvl(plan.getAnnualGoal());
            String planContent = plan == null ? "" : nvl(plan.getPlanContent());
            String annualStatus = plan == null ? "" : nvl(plan.getFinishStatus());
            boolean pendingAnnualAudit = ANNUAL_STATUS_PENDING_AUDIT.equals(annualStatus);
            boolean annualArchived = ANNUAL_STATUS_DONE.equals(annualStatus);
            boolean needCompile = ms.isEmpty() || ANNUAL_STATUS_RETURN.equals(annualStatus);

            boolean isOwner = canCloseMilestone(p, currentUser, members);
            if (pendingAnnualAudit) {
                if (canAuditAnnualPlan(p, currentUser, members)) {
                    todos.add(todo("COMPILE_AUDIT", p, null, y, "里程碑清单审核"));
                }
            } else if (needCompile && flowAuditGuard.canActAs(p.getId(), currentUser, "techLead", "owner", "contactLogin", "projectPm")) {
                todos.add(todo("COMPILE", p, null, y, "编制里程碑节点"));
            }
            for (ProjMilestone m : ms) {
                total++;
                if (STATUS_DONE.equals(m.getStatus())) {
                    done++;
                } else if (isCloseAuditStatus(m.getStatus())) {
                    if (canAuditCloseMilestone(p, currentUser, m.getStatus(), members)) {
                        todos.add(todo("CLOSE_AUDIT", p, m, y, "销项审核"));
                    }
                } else if ((annualArchived || plan == null) && isOwner) {
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
            card.put("orgId", p.getOrgId());
            card.put("teamMembers", members);
            card.put("annualGoal", annualGoal);
            card.put("planContent", planContent);
            card.put("annualStatus", annualStatus);
            card.put("annualAuditPending", pendingAnnualAudit);
            card.put("annualArchived", annualArchived);
            card.put("year", y);
            card.put("warnColor", ColorUtil.worstCode(colors));
            card.put("msDone", ms.stream().filter(x -> STATUS_DONE.equals(x.getStatus())).count());
            card.put("msTotal", ms.size());
            card.put("canFill", flowAuditGuard.canActAs(p.getId(), currentUser, "techLead", "owner", "contactLogin", "projectPm"));
            card.put("canClose", isOwner);
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

    @GetMapping("/milestones/{id}")
    public R<ProjMilestone> detail(@PathVariable("id") Long id) {
        ProjMilestone m = requireMilestone(id);
        flowAuditGuard.requireProjectAccess(m.getProjectId());
        decorate(m, projInfoMapper.selectById(m.getProjectId()), listMaterials(id));
        if (financeViewer()) {
            trimForFinance(m);
        }
        return R.ok(m);
    }

    @GetMapping("/milestones/{id}/materials")
    public R<List<ProjMaterial>> materials(@PathVariable("id") Long id) {
        ProjMilestone m = requireMilestone(id);
        flowAuditGuard.requireProjectAccess(m.getProjectId());
        if (financeViewer()) {
            return R.ok(List.of());
        }
        return R.ok(listMaterials(id));
    }

    /** 当前用户数据范围内的全部里程碑（总部全量；二级单位本单位 + 本人参与项目） */
    @GetMapping("/milestones/mine")
    public R<List<ProjMilestone>> mine() {
        SysUser user = flowAuditGuard.currentUser();
        LambdaQueryWrapper<ProjMilestone> w = new LambdaQueryWrapper<ProjMilestone>().orderByAsc(ProjMilestone::getPlanDate);
        if (!FlowAuditGuard.isHqIdentity(FlowAuditGuard.identityOf(user))) {
            Set<Long> ids = flowAuditGuard.assignedProjectIds(user);
            List<Long> orgProjects = projInfoMapper.selectList(new LambdaQueryWrapper<ProjInfo>()
                            .eq(ProjInfo::getOrgId, UserContext.getOrgId()).select(ProjInfo::getId))
                    .stream().map(ProjInfo::getId).collect(Collectors.toList());
            ids.addAll(orgProjects);
            if (ids.isEmpty()) {
                return R.ok(List.of());
            }
            w.in(ProjMilestone::getProjectId, ids);
        }
        List<ProjMilestone> list = milestoneMapper.selectList(w);
        Map<Long, List<ProjMaterial>> materials = materialsByMilestone(list);
        Map<Long, ProjInfo> projects = new HashMap<>();
        for (ProjMilestone m : list) {
            ProjInfo p = projects.computeIfAbsent(m.getProjectId(), projInfoMapper::selectById);
            decorate(m, p, materials.getOrDefault(m.getId(), List.of()));
        }
        return R.ok(list);
    }

    @GetMapping("/milestones/current-user")
    public R<Long> currentUser() {
        return R.ok(UserContext.getUserId());
    }

    // ------------------------------------------------------------------ 年度清单

    /**
     * 年度清单审核：二级单位科技部门审核存档。通过后固化基线日期；驳回退回编制。
     */
    @PostMapping("/milestones/annual-plan/audit")
    @Transactional(rollbackFor = Exception.class)
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
        if (plan == null || !ANNUAL_STATUS_PENDING_AUDIT.equals(nvl(plan.getFinishStatus()))) {
            throw new BusinessException("年度里程碑清单尚未提交审查");
        }
        boolean pass = body == null || body.get("pass") == null || Boolean.parseBoolean(String.valueOf(body.get("pass")));
        String remark = body == null || body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
        plan.setFinishStatus(pass ? ANNUAL_STATUS_DONE : ANNUAL_STATUS_RETURN);
        plan.setColorStatus(pass ? "GREEN" : "YELLOW");
        annualPlanMapper.updateById(plan);
        if (pass) {
            // 固化基线：清单存档后计划日期只能通过项目变更调整
            List<ProjMilestone> ms = milestoneMapper.selectList(new LambdaQueryWrapper<ProjMilestone>()
                    .eq(ProjMilestone::getProjectId, projectId).eq(ProjMilestone::getYear, y));
            for (ProjMilestone m : ms) {
                if (m.getBaselinePlanDate() == null && m.getPlanDate() != null) {
                    ProjMilestone patch = new ProjMilestone();
                    patch.setId(m.getId());
                    patch.setBaselinePlanDate(m.getPlanDate());
                    milestoneMapper.updateById(patch);
                }
            }
        }
        auditLogMapper.write("MILESTONE", pass ? "APPROVE" : "REJECT", "ANNUAL_PLAN", plan.getId(),
                (pass ? "审核通过并存档" : "驳回") + "里程碑节点与交付物清单：" + project.getName() + "（" + y + "）"
                        + (remark.isBlank() ? "" : "，意见：" + remark));
        return R.ok(true);
    }

    // ------------------------------------------------------------------ 节点维护

    @PostMapping("/milestones")
    @Transactional(rollbackFor = Exception.class)
    public R<Long> create(@RequestBody Map<String, Object> body) {
        Long projectId = asLong(body.get("projectId"));
        if (projectId == null) {
            throw new BusinessException("缺少项目");
        }
        ProjInfo project = projInfoMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        flowAuditGuard.requireActors(projectId, "新增里程碑", "techLead", "owner", "contactLogin", "projectPm");
        Integer planYear = asInt(body.get("year"));
        LocalDate planDateForYear = asDate(body.get("planDate"));
        int yearForCheck = planYear != null ? planYear : (planDateForYear != null ? planDateForYear.getYear() : LocalDate.now().getYear());
        ProjAnnualPlan yearPlan = annualPlanMapper.selectOne(new LambdaQueryWrapper<ProjAnnualPlan>()
                .eq(ProjAnnualPlan::getProjectId, projectId).eq(ProjAnnualPlan::getYear, yearForCheck).last("LIMIT 1"));
        if (yearPlan != null && ANNUAL_STATUS_PENDING_AUDIT.equals(nvl(yearPlan.getFinishStatus()))) {
            throw new BusinessException("年度里程碑清单正在审核中，审核结束前不能新增节点");
        }
        ProjMilestone m = new ProjMilestone();
        m.setProjectId(projectId);
        m.setName(requireText(body.get("name"), "里程碑名称"));
        m.setPlanDate(asDate(body.get("planDate")));
        if (m.getPlanDate() == null) {
            throw new BusinessException("请填写计划完成时间");
        }
        validatePlanDate(project, m.getPlanDate());
        Integer y = asInt(body.get("year"));
        m.setYear(y == null ? m.getPlanDate().getYear() : y);
        m.setBudget(asDecimal(body.get("budget")));
        m.setStatus(STATUS_DOING);
        m.setEvidence(0);
        m.setDelayCount(0);
        m.setColorStatus(ColorUtil.calcCode(m.getPlanDate(), false));
        m.setCreatedAt(LocalDateTime.now());
        milestoneMapper.insert(m);
        auditLogMapper.write("MILESTONE", "CREATE", "MILESTONE", m.getId(),
                "新增里程碑节点：" + m.getName() + "（" + project.getName() + "，计划 " + m.getPlanDate() + "）");
        return R.ok(m.getId());
    }

    /**
     * 修改节点：只接受 name / budget / year / planDate。
     * 状态、佐证、实际完成日期、基线不可通过本接口写入；日期锁定后修改会被拒绝。
     */
    @PutMapping("/milestones/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjMilestone existing = requireMilestone(id);
        flowAuditGuard.requireActors(existing.getProjectId(), "修改里程碑", "techLead", "owner", "contactLogin", "projectPm");
        if (!isEditableStatus(existing.getStatus())) {
            throw new BusinessException("节点已完成或正在销项审核，不能修改");
        }
        ProjInfo project = projInfoMapper.selectById(existing.getProjectId());
        boolean baselined = existing.getBaselinePlanDate() != null;
        ProjMilestone patch = new ProjMilestone();
        patch.setId(id);
        List<String> changes = new ArrayList<>();
        if (body.containsKey("name")) {
            String name = requireText(body.get("name"), "里程碑名称");
            if (!name.equals(existing.getName())) {
                if (baselined) {
                    throw new BusinessException(400, "该节点已进入审核存档的清单基线，名称修改请通过「数据变更」办理");
                }
                patch.setName(name);
                changes.add("名称 " + existing.getName() + " → " + name);
            }
        }
        if (body.containsKey("budget") && body.get("budget") != null) {
            BigDecimal budget = asDecimal(body.get("budget"));
            if (existing.getBudget() == null || budget.compareTo(existing.getBudget()) != 0) {
                if (baselined) {
                    throw new BusinessException(400, "该节点已进入审核存档的清单基线，预算修改请通过「数据变更」办理");
                }
                patch.setBudget(budget);
                changes.add("预算 " + existing.getBudget() + " → " + budget);
            }
        }
        if (body.containsKey("year") && body.get("year") != null) {
            patch.setYear(asInt(body.get("year")));
        }
        LocalDate newDate = asDate(body.get("planDate"));
        if (newDate != null && !newDate.equals(existing.getPlanDate())) {
            if (isDateLocked(existing)) {
                throw new BusinessException(400, "该节点计划日期已进入基线或已逾期，禁止直接修改，请通过【项目变更】发起延期审批");
            }
            validatePlanDate(project, newDate);
            patch.setPlanDate(newDate);
            patch.setColorStatus(ColorUtil.calcCode(newDate, false));
            changes.add("计划日期 " + existing.getPlanDate() + " → " + newDate);
        }
        if (changes.isEmpty() && patch.getYear() == null) {
            return R.ok(true);
        }
        milestoneMapper.updateById(patch);
        auditLogMapper.write("MILESTONE", "UPDATE", "MILESTONE", id,
                "修改里程碑节点 " + existing.getName() + "：" + String.join("；", changes));
        return R.ok(true);
    }

    @DeleteMapping("/milestones/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> delete(@PathVariable("id") Long id) {
        ProjMilestone existing = requireMilestone(id);
        flowAuditGuard.requireActors(existing.getProjectId(), "删除里程碑", "techLead", "owner", "contactLogin", "projectPm");
        List<ProjMaterial> materials = listMaterials(id);
        if (!canDelete(existing, materials)) {
            throw new BusinessException(400, "该节点已进入基线、已有佐证材料或已进入销项流程，不能删除；如需调整请通过【项目变更】");
        }
        milestoneMapper.deleteById(id);
        auditLogMapper.write("MILESTONE", "DELETE", "MILESTONE", id, "删除里程碑节点（逻辑删除）：" + existing.getName());
        return R.ok(true);
    }

    // ------------------------------------------------------------------ 佐证材料

    /**
     * 登记佐证材料：必须先经 /api/files/upload 上传，body 携带 objectKey；后端校验对象真实存在。
     */
    @PostMapping("/milestones/{id}/materials")
    @Transactional(rollbackFor = Exception.class)
    public R<Long> saveMaterial(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjMilestone milestone = requireMilestone(id);
        requireProjectOwner(milestone.getProjectId(), "上传里程碑销项材料");
        if (STATUS_DONE.equals(nvl(milestone.getStatus()))) {
            throw new BusinessException("节点已完成销项，佐证材料已归档，不能再修改");
        }
        if (isCloseAuditStatus(milestone.getStatus())) {
            throw new BusinessException("节点销项正在审核中，佐证材料已锁定；如需补正请先由审核人退回");
        }
        String objectKey = MinioStorageService.extractObjectKey(str(body.get("objectKey")));
        if (objectKey == null) {
            objectKey = MinioStorageService.extractObjectKey(str(body.get("fileUrl")));
        }
        if (objectKey == null || !storageService.exists(objectKey)) {
            throw new BusinessException(400, "佐证材料未上传成功或对象不存在，请重新上传后再登记");
        }
        String fieldCode = str(body.get("fieldCode")) == null ? "EVIDENCE" : str(body.get("fieldCode"));
        String fileName = str(body.get("fileName"));
        if (fileName == null) {
            fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        }
        ProjMaterial current = materialMapper.selectOne(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, MATERIAL_BIZ)
                .eq(ProjMaterial::getBizId, id)
                .eq(ProjMaterial::getFieldCode, fieldCode)
                .last("LIMIT 1"));
        ProjMaterial record = new ProjMaterial();
        record.setBizType(MATERIAL_BIZ);
        record.setBizId(id);
        record.setFieldCode(fieldCode);
        record.setFieldName(str(body.get("fieldName")) == null ? "节点完成佐证材料" : str(body.get("fieldName")));
        record.setFileName(fileName);
        record.setFileUrl(storageService.buildAccessUrl(objectKey));
        record.setFileSize(asLong(body.get("fileSize")));
        record.setUploadedBy(UserContext.getUsername());
        record.setUploadedAt(LocalDateTime.now());
        record.setRequired(1);
        record.setLocked(0);
        if (current == null) {
            record.setVersion(1);
            materialMapper.insert(record);
        } else {
            record.setId(current.getId());
            record.setVersion((current.getVersion() == null ? 1 : current.getVersion()) + 1);
            materialMapper.updateById(record);
        }
        if (!"PLAN_TEMPLATE".equalsIgnoreCase(fieldCode)) {
            ProjMilestone patch = new ProjMilestone();
            patch.setId(id);
            patch.setEvidence(1);
            milestoneMapper.updateById(patch);
        }
        auditLogMapper.write("MILESTONE", "UPLOAD", "MILESTONE", id,
                "登记里程碑佐证材料：" + fileName + "（v" + record.getVersion() + "）");
        return R.ok(record.getId());
    }

    // ------------------------------------------------------------------ 滞后 / 延期 / 销项

    /** 登记滞后原因与处理措施（项目负责人） */
    @PostMapping("/milestones/{id}/lag")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> lag(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjMilestone m = requireMilestone(id);
        requireProjectOwner(m.getProjectId(), "登记滞后原因");
        String reason = requireText(body.get("lagReason"), "滞后原因");
        ProjMilestone patch = new ProjMilestone();
        patch.setId(id);
        patch.setLagReason(reason);
        patch.setLagMeasure(str(body.get("lagMeasure")));
        milestoneMapper.updateById(patch);
        auditLogMapper.write("MILESTONE", "UPDATE", "MILESTONE", id, "登记滞后原因：" + m.getName() + "：" + reason);
        return R.ok(true);
    }

    /**
     * 延期申请：不直接改日期，生成【项目变更】延期单（MILESTONE_DELAY），审批通过后由变更模块回写 plan_date。
     */
    @PostMapping("/milestones/{id}/delay")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> delay(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjMilestone m = requireMilestone(id);
        flowAuditGuard.requireActors(m.getProjectId(), "里程碑延期申请", "owner", "projectPm", "techLead", "contactLogin");
        if (STATUS_DONE.equals(nvl(m.getStatus()))) {
            throw new BusinessException("节点已完成，无需延期");
        }
        LocalDate newDate = asDate(body.get("newPlanDate"));
        if (newDate == null) {
            throw new BusinessException("请填写新的计划完成时间");
        }
        if (m.getPlanDate() != null && !newDate.isAfter(m.getPlanDate())) {
            throw new BusinessException("新计划日期必须晚于当前计划日期 " + m.getPlanDate());
        }
        String reason = requireText(body.get("reason"), "延期理由");
        ProjInfo project = projInfoMapper.selectById(m.getProjectId());
        validatePlanDate(project, newDate);
        long pending = changeMapper.selectCount(new LambdaQueryWrapper<ProjChange>()
                .eq(ProjChange::getMilestoneId, id)
                .in(ProjChange::getStatus, "DRAFT", "APPROVING"));
        if (pending > 0) {
            throw new BusinessException("该节点已有未办结的延期变更单，请先完成审批");
        }
        ProjChange c = new ProjChange();
        c.setChangeNo(nextChangeNo());
        c.setProjectId(m.getProjectId());
        c.setProjectName(project == null ? null : project.getName());
        c.setChangeType("PROJECT");
        c.setCategory("MILESTONE_DELAY");
        c.setTitle("里程碑延期：" + m.getName());
        c.setReason(reason);
        c.setBeforeValue(String.valueOf(m.getPlanDate()));
        c.setAfterValue(String.valueOf(newDate));
        c.setMilestoneId(id);
        c.setNewPlanDate(newDate);
        c.setLegalReview(0);
        c.setStatus("DRAFT");
        c.setApplicant(UserContext.getUsername());
        c.setCreatedAt(LocalDateTime.now());
        changeMapper.insert(c);
        auditLogMapper.write("MILESTONE", "SUBMIT", "CHANGE", c.getId(),
                "发起里程碑延期变更单 " + c.getChangeNo() + "：" + m.getName() + " " + m.getPlanDate() + " → " + newDate);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changeId", c.getId());
        data.put("changeNo", c.getChangeNo());
        data.put("status", c.getStatus());
        return R.ok(data);
    }

    /**
     * 闭环销项申请：必须已上传真实佐证材料；逾期节点必须登记滞后原因；提交后进入两级审核。
     */
    @PostMapping("/milestones/{id}/close")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> close(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjMilestone m = requireMilestone(id);
        requireProjectOwner(m.getProjectId(), "节点闭环销项");
        if (STATUS_DONE.equals(nvl(m.getStatus()))) {
            throw new BusinessException("节点已完成销项");
        }
        if (isCloseAuditStatus(m.getStatus())) {
            throw new BusinessException("该节点销项正在审核中，请等待审核结果");
        }
        ProjAnnualPlan plan = annualPlanMapper.selectOne(new LambdaQueryWrapper<ProjAnnualPlan>()
                .eq(ProjAnnualPlan::getProjectId, m.getProjectId())
                .eq(ProjAnnualPlan::getYear, m.getYear()).last("LIMIT 1"));
        if (plan == null || !ANNUAL_STATUS_DONE.equals(nvl(plan.getFinishStatus()))) {
            throw new BusinessException("年度里程碑清单尚未审核存档，存档后方可销项");
        }
        List<ProjMaterial> evidenceList = listMaterials(id);
        if (!hasCompletionEvidence(evidenceList)) {
            throw new BusinessException("请先上传节点佐证材料，再执行闭环销项");
        }
        boolean anyObjectExists = evidenceList.stream()
                .filter(x -> !"PLAN_TEMPLATE".equalsIgnoreCase(x.getFieldCode()))
                .map(x -> MinioStorageService.extractObjectKey(x.getFileUrl()))
                .anyMatch(storageService::exists);
        if (!anyObjectExists) {
            throw new BusinessException("佐证材料文件在对象存储中不存在（可能是历史数据缺件），请重新上传后再销项");
        }
        boolean overdue = m.getPlanDate() != null && m.getPlanDate().isBefore(LocalDate.now());
        String lagReason = body == null ? null : str(body.get("lagReason"));
        String lagMeasure = body == null ? null : str(body.get("lagMeasure"));
        if (overdue && lagReason == null && (m.getLagReason() == null || m.getLagReason().isBlank())) {
            throw new BusinessException(400, "该节点已逾期，销项前必须填写滞后原因");
        }
        ProjMilestone patch = new ProjMilestone();
        patch.setId(id);
        patch.setStatus(STATUS_CLOSE_DEPT_AUDIT);
        patch.setEvidence(1);
        if (lagReason != null) {
            patch.setLagReason(lagReason);
        }
        if (lagMeasure != null) {
            patch.setLagMeasure(lagMeasure);
        }
        milestoneMapper.updateById(patch);
        auditLogMapper.write("MILESTONE", "SUBMIT", "MILESTONE", id,
                "项目负责人提交里程碑销项审核：" + nvl(m.getName()) + (overdue ? "（逾期，滞后原因：" + (lagReason == null ? m.getLagReason() : lagReason) + "）" : ""));
        return R.ok(true);
    }

    /**
     * 销项审核：项目承担部门负责人初审 → 单位科研管理部门负责人终审；终审通过转绿并记实际完成日期。
     */
    @PostMapping("/milestones/{id}/close-audit")
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> auditClose(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjMilestone m = requireMilestone(id);
        ProjInfo project = projInfoMapper.selectById(m.getProjectId());
        List<ProjTeamMember> members = membersOf(m.getProjectId());
        String status = nvl(m.getStatus());
        boolean pass = body == null || body.get("pass") == null || Boolean.parseBoolean(String.valueOf(body.get("pass")));
        String remark = body == null || body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
        SysUser auditor = flowAuditGuard.currentUser();
        if (!isCloseAuditStatus(status)) {
            throw new BusinessException(403, "当前节点不在销项审核环节");
        }
        boolean deptStage = STATUS_CLOSE_DEPT_AUDIT.equals(status);
        String stageLabel = deptStage ? "项目承担部门负责人" : "单位科研管理部门负责人";
        if (!canAuditCloseMilestone(project, auditor, status, members)) {
            throw new BusinessException(403, "仅当前销项审核节点指定办理人（" + stageLabel + "）可办理");
        }
        ProjMilestone patch = new ProjMilestone();
        patch.setId(id);
        patch.setAuditBy(auditorLabel(auditor));
        patch.setAuditAt(LocalDateTime.now());
        patch.setAuditOpinion(remark);
        if (!pass) {
            patch.setStatus(STATUS_DOING);
            patch.setColorStatus(ColorUtil.calcCode(m.getPlanDate(), false));
            milestoneMapper.updateById(patch);
            auditLogMapper.write("MILESTONE", "REJECT", "MILESTONE", id,
                    stageLabel + "退回里程碑销项：" + nvl(m.getName()) + (remark.isBlank() ? "" : "，意见：" + remark));
            return R.ok(true);
        }
        if (deptStage) {
            patch.setStatus(STATUS_CLOSE_UNIT_AUDIT);
            milestoneMapper.updateById(patch);
            auditLogMapper.write("MILESTONE", "APPROVE", "MILESTONE", id,
                    "项目承担部门负责人审核通过，流转单位科研管理部门负责人：" + nvl(m.getName()));
        } else {
            patch.setStatus(STATUS_DONE);
            patch.setActualDate(LocalDate.now());
            patch.setColorStatus("GREEN");
            patch.setEvidence(1);
            milestoneMapper.updateById(patch);
            auditLogMapper.write("MILESTONE", "APPROVE", "MILESTONE", id,
                    "单位科研管理部门负责人审核通过，里程碑完成销项：" + nvl(m.getName()));
        }
        return R.ok(true);
    }

    // ------------------------------------------------------------------ 内部

    private ProjMilestone requireMilestone(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("里程碑不存在");
        }
        ProjMilestone m = milestoneMapper.selectById(id);
        if (m == null) {
            throw new BusinessException("里程碑不存在");
        }
        return m;
    }

    private List<ProjTeamMember> membersOf(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        return teamMemberMapper.selectList(new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, projectId));
    }

    private List<ProjMaterial> listMaterials(Long milestoneId) {
        if (milestoneId == null) {
            return List.of();
        }
        return materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, MATERIAL_BIZ)
                .eq(ProjMaterial::getBizId, milestoneId)
                .orderByDesc(ProjMaterial::getUploadedAt));
    }

    private Map<Long, List<ProjMaterial>> materialsByMilestone(List<ProjMilestone> list) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = list.stream().map(ProjMilestone::getId).collect(Collectors.toList());
        return materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                        .eq(ProjMaterial::getBizType, MATERIAL_BIZ)
                        .in(ProjMaterial::getBizId, ids)
                        .orderByDesc(ProjMaterial::getUploadedAt))
                .stream().collect(Collectors.groupingBy(ProjMaterial::getBizId));
    }

    private boolean financeViewer() {
        SysUser u = flowAuditGuard.currentUserOrNull();
        return u != null && FlowAuditGuard.isFinanceIdentity(FlowAuditGuard.identityOf(u));
    }

    /** 财务团队仅关联查看节点预算：不下发佐证材料、滞后原因、审核意见等技术信息 */
    private static void trimForFinance(ProjMilestone m) {
        m.setMaterials(List.of());
        m.setLagReason(null);
        m.setLagMeasure(null);
        m.setAuditOpinion(null);
        m.setAuditBy(null);
        m.setCanDelete(false);
    }

    private void decorate(ProjMilestone m, ProjInfo p, List<ProjMaterial> materials) {
        boolean done = STATUS_DONE.equals(m.getStatus());
        m.setColorStatus(ColorUtil.calcCode(m.getPlanDate(), done));
        m.setMaterials(materials == null ? List.of() : materials);
        m.setEvidence(hasCompletionEvidence(m.getMaterials()) ? 1 : 0);
        m.setDateLocked(isDateLocked(m));
        m.setCanDelete(canDelete(m, m.getMaterials()));
        if (m.getDelayCount() == null) {
            m.setDelayCount(0);
        }
        if ("RED".equals(m.getColorStatus()) && !done && !isCloseAuditStatus(m.getStatus())) {
            m.setStatus("OVERDUE");
        }
        if (p != null) {
            m.setProjectName(p.getName());
            m.setProjectNo(p.getProjectNo());
            m.setOwnerName(p.getOwnerName());
        }
    }

    /** 历史数据可能把展示态 OVERDUE 落了库，一律按 DOING 处理 */
    private static boolean isEditableStatus(String status) {
        String s = nvl(status);
        return STATUS_DOING.equals(s) || "OVERDUE".equals(s) || s.isEmpty();
    }

    /** 日期锁定：已进入基线，或已逾期，或不在 DOING 状态 */
    private static boolean isDateLocked(ProjMilestone m) {
        if (m.getBaselinePlanDate() != null) {
            return true;
        }
        if (!isEditableStatus(m.getStatus())) {
            return true;
        }
        return m.getPlanDate() != null && m.getPlanDate().isBefore(LocalDate.now());
    }

    private static boolean canDelete(ProjMilestone m, List<ProjMaterial> materials) {
        if (!isEditableStatus(m.getStatus())) {
            return false;
        }
        if (m.getBaselinePlanDate() != null) {
            return false;
        }
        return !hasCompletionEvidence(materials);
    }

    private static void validatePlanDate(ProjInfo project, LocalDate date) {
        if (project == null || date == null) {
            return;
        }
        if (project.getStartDate() != null && date.isBefore(project.getStartDate())) {
            throw new BusinessException("计划完成时间 " + date + " 早于项目开始时间 " + project.getStartDate());
        }
        if (project.getEndDate() != null && date.isAfter(project.getEndDate())) {
            throw new BusinessException("计划完成时间 " + date + " 晚于项目结束时间 " + project.getEndDate() + "，整体周期调整请走项目变更");
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

    private void requireProjectOwner(Long projectId, String actionLabel) {
        ProjInfo project = projectId == null ? null : projInfoMapper.selectById(projectId);
        SysUser user = flowAuditGuard.currentUser();
        if ("admin".equals(FlowAuditGuard.identityOf(user))) {
            throw new BusinessException(403, "超级管理员禁止直接修改业务数据，「" + actionLabel + "」请由项目负责人办理");
        }
        if (!canCloseMilestone(project, user, membersOf(projectId))) {
            throw new BusinessException(403, "仅项目团队负责人可办理「" + actionLabel + "」");
        }
    }

    private boolean canCloseMilestone(ProjInfo project, SysUser user, List<ProjTeamMember> members) {
        if (project == null || user == null) {
            return false;
        }
        ProjTeamMember owner = FlowAuditGuard.findMember(members, "owner");
        if (owner != null) {
            return FlowAuditGuard.samePerson(user, owner);
        }
        return FlowAuditGuard.matchesOwnerLabel(project.getOwnerName(), user);
    }

    private boolean canAuditCloseMilestone(ProjInfo project, SysUser user, String status, List<ProjTeamMember> members) {
        if (project == null || user == null) {
            return false;
        }
        String ident = STATUS_CLOSE_DEPT_AUDIT.equals(status) ? "deptHead"
                : STATUS_CLOSE_UNIT_AUDIT.equals(status) ? "unitHead" : null;
        if (ident == null) {
            return false;
        }
        ProjTeamMember named = FlowAuditGuard.findMember(members, ident);
        if (named != null) {
            return FlowAuditGuard.samePerson(user, named);
        }
        return ident.equals(FlowAuditGuard.identityOf(user)) && project.getOrgId() != null
                && project.getOrgId().equals(user.getOrgId());
    }

    private boolean canAuditAnnualPlan(ProjInfo project, SysUser user, List<ProjTeamMember> members) {
        if (user == null) {
            return false;
        }
        String identityCode = FlowAuditGuard.identityOf(user);
        if (!"unitHead".equals(identityCode)) {
            return false;
        }
        ProjTeamMember named = FlowAuditGuard.findMember(members, "unitHead");
        if (named != null) {
            return FlowAuditGuard.samePerson(user, named);
        }
        return project.getOrgId() != null && project.getOrgId().equals(user.getOrgId());
    }

    private static boolean isCloseAuditStatus(String status) {
        return STATUS_CLOSE_DEPT_AUDIT.equals(status) || STATUS_CLOSE_UNIT_AUDIT.equals(status);
    }

    private static String closeAuditNodeLabel(String status) {
        if (STATUS_CLOSE_DEPT_AUDIT.equals(status)) {
            return "项目承担部门负责人审核";
        }
        if (STATUS_CLOSE_UNIT_AUDIT.equals(status)) {
            return "单位科研管理部门负责人审核";
        }
        return "里程碑销项";
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
            t.put("lagReason", m.getLagReason());
        } else {
            t.put("milestoneId", null);
            t.put("milestoneName", "里程碑节点清单");
            t.put("planDate", year + "-04-30");
            t.put("colorStatus", "BLUE");
            t.put("status", "COMPILE_AUDIT".equals(type) ? ANNUAL_STATUS_PENDING_AUDIT : STATUS_DOING);
            t.put("materialCount", 0);
            t.put("materials", List.of());
        }
        return t;
    }

    private static String auditorLabel(SysUser u) {
        if (u == null) {
            return null;
        }
        String name = nvl(u.getRealName());
        String no = nvl(u.getEmployeeNo());
        return no.isEmpty() ? name : name + "（" + no + "）";
    }

    /** 计划填报模板用于提前查看要求，不能被当作节点完成佐证。 */
    private static boolean hasCompletionEvidence(List<ProjMaterial> materials) {
        return materials != null && materials.stream().anyMatch(material ->
                material.getFileUrl() != null
                        && !material.getFileUrl().isBlank()
                        && !"PLAN_TEMPLATE".equalsIgnoreCase(material.getFieldCode()));
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static String requireText(Object o, String label) {
        String s = str(o);
        if (s == null) {
            throw new BusinessException("请填写" + label);
        }
        return s;
    }

    private static Long asLong(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asInt(Object o) {
        Long v = asLong(o);
        return v == null ? null : v.intValue();
    }

    private static BigDecimal asDecimal(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("预算金额格式不正确");
        }
    }

    private static LocalDate asDate(Object o) {
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

    /** 供其他模块（预警扫描）复用：按项目批量取里程碑并附材料 */
    public List<ProjMilestone> decoratedMilestonesOf(Long projectId) {
        List<ProjMilestone> list = milestoneMapper.selectList(new LambdaQueryWrapper<ProjMilestone>()
                .eq(ProjMilestone::getProjectId, projectId).orderByAsc(ProjMilestone::getPlanDate));
        Map<Long, List<ProjMaterial>> materials = materialsByMilestone(list);
        ProjInfo p = projInfoMapper.selectById(projectId);
        for (ProjMilestone m : list) {
            decorate(m, p, materials.getOrDefault(m.getId(), Collections.emptyList()));
        }
        return list;
    }
}
