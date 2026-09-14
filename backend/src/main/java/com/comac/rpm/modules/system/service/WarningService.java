package com.comac.rpm.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.plan.entity.ProjPlan;
import com.comac.rpm.modules.plan.mapper.ProjPlanMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.entity.SysWarning;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.comac.rpm.modules.system.mapper.SysWarningMapper;
import com.comac.rpm.modules.transform.entity.AchvTransform;
import com.comac.rpm.modules.transform.mapper.AchvTransformMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预警扫描：到期前 30 天黄色、超期红色；按工号投递给项目团队 + 对应管理团队；同时刷新项目四色。
 * <p>
 * 由 {@link WarningScheduler} 每日定时调用，也可由管理员手动触发。
 */
@Service
public class WarningService {

    private static final Logger log = LoggerFactory.getLogger(WarningService.class);

    /** 项目团队接收身份（团队岗位） */
    private static final Set<String> TEAM_ROLE_CODES = Set.of("PROJECT_LEADER", "TECH_LEADER", "PROJECT_SUPERVISOR", "PROJECT_CONTACT");
    /** 管理团队接收身份（任职身份） */
    private static final Set<String> UNIT_MGMT_IDENTITIES = Set.of("unitHead", "unitStaff", "deptHead");
    private static final Set<String> HQ_MGMT_IDENTITIES = Set.of("hqHead", "hqStaff");

    private final SysWarningMapper warningMapper;
    private final ProjInfoMapper projectMapper;
    private final ProjMilestoneMapper milestoneMapper;
    private final ProjPlanMapper planMapper;
    private final ProjDeliverableMapper deliverableMapper;
    private final AchvTransformMapper transformMapper;
    private final ProjTeamMemberMapper teamMemberMapper;
    private final SysUserMapper userMapper;

    public WarningService(SysWarningMapper warningMapper, ProjInfoMapper projectMapper,
                          ProjMilestoneMapper milestoneMapper, ProjPlanMapper planMapper,
                          ProjDeliverableMapper deliverableMapper, AchvTransformMapper transformMapper,
                          ProjTeamMemberMapper teamMemberMapper, SysUserMapper userMapper) {
        this.warningMapper = warningMapper;
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
        this.planMapper = planMapper;
        this.deliverableMapper = deliverableMapper;
        this.transformMapper = transformMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.userMapper = userMapper;
    }

    /**
     * 全量扫描：同一业务、同一天、同一级别只生成一条；返回新增条数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int scan() {
        List<ProjInfo> projects = projectMapper.selectList(null);
        Map<Long, ProjInfo> byId = new LinkedHashMap<>();
        for (ProjInfo p : projects) {
            byId.put(p.getId(), p);
        }
        LocalDate today = LocalDate.now();
        List<SysWarning> result = new ArrayList<>();
        Map<Long, List<String>> projectColors = new LinkedHashMap<>();

        for (ProjMilestone m : milestoneMapper.selectList(null)) {
            String c = ColorUtil.calcCode(m.getPlanDate(), "DONE".equals(m.getStatus()));
            projectColors.computeIfAbsent(m.getProjectId(), k -> new ArrayList<>()).add(c);
            if (isWarn(c)) {
                ProjInfo p = byId.get(m.getProjectId());
                result.add(build("MILESTONE", m.getId(), m.getProjectId(), p == null ? null : p.getName(), c,
                        ("RED".equals(c) ? "里程碑逾期：" : "里程碑临期：") + m.getName(),
                        "计划完成时间 " + m.getPlanDate() + (m.getLagReason() == null ? "" : "；滞后原因：" + m.getLagReason()), today));
            }
        }
        for (ProjPlan p : planMapper.selectList(null)) {
            if (!"TODO".equals(p.getPlanType())) {
                continue;
            }
            String c = ColorUtil.calcCode(p.getDueDate(), false);
            projectColors.computeIfAbsent(p.getProjectId(), k -> new ArrayList<>()).add(c);
            if (isWarn(c)) {
                ProjInfo pi = byId.get(p.getProjectId());
                result.add(build("PLAN", p.getId(), p.getProjectId(), pi == null ? null : pi.getName(), c,
                        ("RED".equals(c) ? "计划超期：" : "计划临期：") + p.getTitle(),
                        "待办计划到期日 " + p.getDueDate(), today));
            }
        }
        for (ProjDeliverable d : deliverableMapper.selectList(null)) {
            String c = ColorUtil.calcCode(d.getDueDate(), "DELIVERED".equals(d.getStatus()));
            projectColors.computeIfAbsent(d.getProjectId(), k -> new ArrayList<>()).add(c);
            if (isWarn(c)) {
                ProjInfo pi = byId.get(d.getProjectId());
                result.add(build("DELIVERABLE", d.getId(), d.getProjectId(), pi == null ? null : pi.getName(), c,
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

        // 刷新项目四色（台账/看板读取即时值）
        for (ProjInfo p : projects) {
            List<String> colors = projectColors.getOrDefault(p.getId(), List.of());
            String worst = colors.isEmpty() ? (p.getWarnColor() == null ? "BLUE" : p.getWarnColor()) : ColorUtil.worstCode(colors);
            String status = p.getStatus();
            if ("RED".equals(worst) && "IMPLEMENTING".equals(status)) {
                status = "DELAYED";
            } else if (!"RED".equals(worst) && "DELAYED".equals(status)) {
                status = "IMPLEMENTING";
            }
            if (!worst.equals(p.getWarnColor()) || (status != null && !status.equals(p.getStatus()))) {
                ProjInfo patch = new ProjInfo();
                patch.setId(p.getId());
                patch.setWarnColor(worst);
                patch.setStatus(status);
                projectMapper.updateById(patch);
            }
        }

        // 接收人：项目团队 + 单位管理团队 + 总部管理团队
        Map<Long, String> receiverCache = new LinkedHashMap<>();
        int count = 0;
        for (SysWarning w : result) {
            long exists = warningMapper.selectCount(new LambdaQueryWrapper<SysWarning>()
                    .eq(SysWarning::getBizType, w.getBizType())
                    .eq(SysWarning::getBizId, w.getBizId())
                    .eq(SysWarning::getWarnLevel, w.getWarnLevel())
                    .ge(SysWarning::getCreatedAt, today.atStartOfDay()));
            if (exists == 0) {
                w.setReceiverNos(receiverCache.computeIfAbsent(w.getProjectId(), pid -> receiversOf(byId.get(pid))));
                warningMapper.insert(w);
                count++;
            }
        }
        log.info("预警扫描完成：候选 {} 条，新增 {} 条", result.size(), count);
        return count;
    }

    /** 项目团队（团队表）+ 本单位管理团队 + 总部管理团队 的工号集合 */
    public String receiversOf(ProjInfo project) {
        Set<String> nos = new LinkedHashSet<>();
        if (project == null) {
            return "";
        }
        for (ProjTeamMember m : teamMemberMapper.selectList(new LambdaQueryWrapper<ProjTeamMember>()
                .eq(ProjTeamMember::getProjectId, project.getId()))) {
            if (m.getRoleCode() != null && TEAM_ROLE_CODES.contains(m.getRoleCode()) && m.getEmployeeNo() != null) {
                nos.add(m.getEmployeeNo().trim());
            }
        }
        if (project.getOwnerName() != null) {
            String d = project.getOwnerName().replaceAll("\\D", "");
            if (!d.isEmpty()) {
                nos.add(d);
            }
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getIdentityCode, unionOf(UNIT_MGMT_IDENTITIES, HQ_MGMT_IDENTITIES)));
        for (SysUser u : users) {
            if (u.getEmployeeNo() == null) {
                continue;
            }
            boolean hq = HQ_MGMT_IDENTITIES.contains(u.getIdentityCode());
            boolean sameUnit = project.getOrgId() != null && project.getOrgId().equals(u.getOrgId());
            if (hq || sameUnit) {
                nos.add(u.getEmployeeNo().trim());
            }
        }
        return String.join(",", nos);
    }

    private static List<String> unionOf(Set<String> a, Set<String> b) {
        List<String> l = new ArrayList<>(a);
        l.addAll(b);
        return l;
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

    /** 工具：逗号分隔工号是否包含 */
    public static boolean containsNo(String csv, String employeeNo) {
        if (csv == null || employeeNo == null || employeeNo.isBlank()) {
            return false;
        }
        return Arrays.stream(csv.split(",")).map(String::trim).anyMatch(employeeNo.trim()::equals);
    }

    public static String appendNo(String csv, String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return csv;
        }
        Set<String> s = csv == null || csv.isBlank() ? new LinkedHashSet<>()
                : Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        s.add(employeeNo.trim());
        return String.join(",", s);
    }
}
