package com.comac.rpm.common.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核办理权：仅当前节点对应任职身份、且与项目相关的人员可办结。
 * <p>
 * 人员匹配一律按「工号全等」或「姓名全等」，不再使用子串包含（避免 "王" 命中 "王建国"、100001 命中 1000012）。
 */
@Component
public class FlowAuditGuard {

    /** 总部全量可见：总部处长/主管、总部财务、公司领导（管理员另判） */
    private static final Set<String> HQ_SCOPE = new HashSet<>(Arrays.asList(
            "hqHead", "hqStaff", "finHq", "leader"));

    /** 仅看本人关联项目：项目团队 + 责任总师（需求：总师查看本人经手项目） */
    private static final Set<String> TEAM_SCOPE = new HashSet<>(Arrays.asList(
            "owner", "contactLogin", "techLead", "projectPm", "chief1", "chief2"));

    /** 表单维护导入项目的台账直接编辑身份（管理团队）；已立项项目的修改一律走审批/变更；管理员只做运维 */
    private static final Set<String> LEDGER_EDIT_SCOPE = new HashSet<>(Arrays.asList(
            "hqHead", "hqStaff", "unitHead", "unitStaff"));

    /** 任职身份 → 项目团队岗位编码/名称，用于指定到人 */
    private static final Map<String, String[]> IDENTITY_ROLE_KEYS = new HashMap<>();

    static {
        IDENTITY_ROLE_KEYS.put("contactLogin", new String[]{"PROJECT_CONTACT", "项目联系人"});
        IDENTITY_ROLE_KEYS.put("owner", new String[]{"PROJECT_LEADER", "项目负责人"});
        IDENTITY_ROLE_KEYS.put("techLead", new String[]{"TECH_LEADER", "技术负责人"});
        IDENTITY_ROLE_KEYS.put("projectPm", new String[]{"PROJECT_SUPERVISOR", "项目主管"});
        IDENTITY_ROLE_KEYS.put("chief1", new String[]{"L1_CHIEF", "一级总师"});
        IDENTITY_ROLE_KEYS.put("chief2", new String[]{"L2_CHIEF", "二级总师"});
        IDENTITY_ROLE_KEYS.put("hqHead", new String[]{"HQ_DIRECTOR", "总部处室处长"});
        IDENTITY_ROLE_KEYS.put("hqStaff", new String[]{"HQ_SUPERVISOR", "总部处室主管"});
        IDENTITY_ROLE_KEYS.put("unitHead", new String[]{"UNIT_MINISTER", "单位科技部长"});
        IDENTITY_ROLE_KEYS.put("unitStaff", new String[]{"UNIT_SUPERVISOR", "单位科技主管"});
        IDENTITY_ROLE_KEYS.put("unitLeader", new String[]{"UNIT_LEADER", "单位分管领导"});
        IDENTITY_ROLE_KEYS.put("deptHead", new String[]{"DEPT_HEAD", "项目承担部门负责人"});
        IDENTITY_ROLE_KEYS.put("finHq", new String[]{"HQ_FINANCE", "总部财务主管"});
        IDENTITY_ROLE_KEYS.put("finHead", new String[]{"UNIT_FIN_MINISTER", "单位财务部长"});
        IDENTITY_ROLE_KEYS.put("finStaff", new String[]{"UNIT_FIN_SUPERVISOR", "单位财务主管"});
    }

    @Autowired
    private SysUserMapper userMapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate publicationJdbc;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private ProjTeamMemberMapper memberMapper;

    public SysUser currentUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(401, "未登录");
        }
        SysUser u = userMapper.selectById(uid);
        if (u == null) {
            throw new BusinessException(401, "用户不存在");
        }
        return u;
    }

    public SysUser currentUserOrNull() {
        try {
            return currentUser();
        } catch (BusinessException e) {
            return null;
        }
    }

    public static String identityOf(SysUser u) {
        return u == null || u.getIdentityCode() == null ? "" : u.getIdentityCode();
    }

    public static boolean isHqIdentity(String identityCode) {
        return "admin".equals(identityCode) || HQ_SCOPE.contains(identityCode);
    }

    /** 项目团队 / 责任总师：只看本人关联项目 */
    public static boolean isTeamScopeIdentity(String identityCode) {
        return TEAM_SCOPE.contains(identityCode);
    }

    public static boolean isFinanceIdentity(String identityCode) {
        return "finHq".equals(identityCode) || "finHead".equals(identityCode) || "finStaff".equals(identityCode);
    }

    public boolean isLedgerEditor(SysUser u) {
        return LEDGER_EDIT_SCOPE.contains(identityOf(u));
    }

    public void requireIdentities(String actionLabel, String... codes) {
        SysUser u = currentUser();
        String mine = identityOf(u);
        if ("admin".equals(mine)) {
            return;
        }
        for (String c : codes) {
            if (c != null && c.equals(mine)) {
                return;
            }
        }
        throw new BusinessException(403, "仅相关岗位人员可办理「" + actionLabel + "」，当前身份："
                + (u.getIdentity() == null ? "未配置" : u.getIdentity()));
    }

    /** 仅平台管理员可执行的全局运维操作。 */
    public void requireAdmin(String actionLabel) {
        SysUser u = currentUser();
        if (!"admin".equals(u.getIdentityCode())) {
            throw new BusinessException(403, "仅平台管理员可办理「" + actionLabel + "」");
        }
    }

    /** 台账直接编辑权：管理团队 / 管理员。项目团队须走基本信息审批流。 */
    public void requireLedgerEditor(String actionLabel) {
        SysUser u = currentUser();
        if (!isLedgerEditor(u)) {
            throw new BusinessException(403, "「" + actionLabel + "」仅管理团队可直接办理，项目团队请通过“项目基本信息”提交审批");
        }
    }

    /**
     * 数据范围：当前用户能否查看该项目（总部/管理员全量；二级单位本单位；项目团队成员与负责人本人）。
     */
    public boolean canAccessProject(Long projectId) {
        if (projectId == null) {
            return false;
        }
        SysUser u = currentUserOrNull();
        if (u == null) {
            return UserContext.isAdmin();
        }
        String code = identityOf(u);
        if (isHqIdentity(code)) {
            return true;
        }
        ProjInfo p = projectMapper.selectById(projectId);
        if (p == null) {
            return false;
        }
        if (matchesOwnerLabel(p.getOwnerName(), u) || isTeamMember(projectId, u)) {
            return true;
        }
        if("FORM_MAINT".equals(p.getDataSource()) && u.getEmployeeNo()!=null && publicationJdbc!=null) {
            var snapshots=publicationJdbc.queryForList("SELECT payload FROM proj_supplement_history WHERE project_id=? AND section_key='team' AND action='APPROVE' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.status'))='APPROVED' ORDER BY id DESC LIMIT 1",projectId);
            if(!snapshots.isEmpty())try {
                var snapshot=new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(snapshots.get(0).get("payload")));
                for(var row:snapshot.path("rows"))if(u.getEmployeeNo().trim().equals(row.path("employeeNo").asText().trim()))return true;
            }catch(Exception e){throw new BusinessException(500,"已审核团队信息损坏");}
        }
        // 项目团队 / 总师：不是成员就不可见，同单位也不行
        if (isTeamScopeIdentity(code)) {
            return false;
        }
        // 单位管理团队 / 单位财务 / 承担部门负责人：本单位项目
        return p.getOrgId() != null && p.getOrgId().equals(u.getOrgId());
    }

    public void requireProjectAccess(Long projectId) {
        if (!canAccessProject(projectId)) {
            throw new BusinessException(403, "无权访问该项目数据");
        }
    }

    /** 当前用户参与的项目 ID（按团队实名/工号匹配） */
    public Set<Long> assignedProjectIds(SysUser user) {
        Set<Long> ids = new HashSet<>();
        if (user == null) {
            return ids;
        }
        String emp = digits(user.getEmployeeNo());
        String name = user.getRealName() == null ? "" : user.getRealName().trim();
        if (emp.isEmpty() && name.isEmpty()) {
            return ids;
        }
        List<ProjTeamMember> rows = memberMapper.selectList(new LambdaQueryWrapper<ProjTeamMember>()
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
        for (ProjTeamMember row : rows) {
            if (row.getProjectId() != null && samePerson(user, row)) {
                ids.add(row.getProjectId());
            }
        }
        return ids;
    }

    private boolean isTeamMember(Long projectId, SysUser u) {
        List<ProjTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, projectId));
        for (ProjTeamMember m : members) {
            if (samePerson(u, m)) {
                return true;
            }
        }
        return false;
    }

    /** 按审批节点中文名解析办理身份，并校验与项目的关联关系 */
    public void requireFlowNode(String flowNode, Long projectId, String actionLabel) {
        String[] codes = identitiesForFlowNode(flowNode);
        requireIdentities(actionLabel == null ? (flowNode == null ? "审核" : flowNode) : actionLabel, codes);
        if (projectId != null) {
            requireProjectRelated(projectId, codes);
        }
    }

    public void requireProjectRelated(Long projectId, String... identityCodes) {
        if (projectId == null) {
            return;
        }
        SysUser u = currentUser();
        String code = identityOf(u);
        if (HQ_SCOPE.contains(code) || "admin".equals(code)) {
            return;
        }
        ProjInfo p = projectMapper.selectById(projectId);
        if (p == null) {
            return;
        }
        if (p.getOrgId() != null && p.getOrgId().equals(u.getOrgId())) {
            return;
        }
        if (matchesOwnerLabel(p.getOwnerName(), u)) {
            return;
        }
        if (!isTeamMember(projectId, u)) {
            throw new BusinessException(403, "您不是该项目相关办理人，无法完成审核");
        }
    }

    /**
     * 指定到人：优先匹配本项目团队实名；已点名则仅该办理人可写；
     * 未点名时回落到任职身份 + 项目关联。公司领导只读。
     */
    public void requireActors(Long projectId, String actionLabel, String... identityCodes) {
        SysUser u = currentUser();
        String mine = identityOf(u);
        if ("admin".equals(mine)) {
            // 需求 V19.1：超级管理员仅做权限配置与运维，禁止直接填报/审批业务数据
            throw new BusinessException(403, "超级管理员禁止直接修改业务数据，「" + actionLabel + "」请由对应岗位人员办理");
        }
        if ("leader".equals(mine)) {
            throw new BusinessException(403, "公司领导为只读，不能办理「" + actionLabel + "」");
        }
        List<ProjTeamMember> members = projectId == null ? null : memberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, projectId));
        for (String ident : identityCodes) {
            ProjTeamMember hit = findMember(members, ident);
            if (hit != null && samePerson(u, hit)) {
                return;
            }
        }
        boolean allowed = false;
        for (String c : identityCodes) {
            if (c != null && c.equals(mine)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BusinessException(403, "仅相关岗位人员可办理「" + actionLabel + "」，当前身份："
                    + (u.getIdentity() == null ? "未配置" : u.getIdentity()));
        }
        ProjTeamMember named = findMember(members, mine);
        if (named != null && !samePerson(u, named)) {
            String who = named.getUserName() == null ? "已指定办理人" : named.getUserName();
            throw new BusinessException(403, "本项已指定给 " + who + "，您不是该项目该岗位办理人");
        }
        if (projectId != null) {
            requireProjectRelated(projectId, identityCodes);
        }
    }

    /** 当前用户是否可作为某身份办理该项目（不抛异常版本，用于待办计算） */
    public boolean canActAs(Long projectId, SysUser u, String... identityCodes) {
        if (u == null) {
            return false;
        }
        String mine = identityOf(u);
        if ("admin".equals(mine) || "leader".equals(mine)) {
            return false;
        }
        List<ProjTeamMember> members = projectId == null ? null : memberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, projectId));
        for (String ident : identityCodes) {
            ProjTeamMember hit = findMember(members, ident);
            if (hit != null) {
                if (samePerson(u, hit)) {
                    return true;
                }
                continue;
            }
            if (ident != null && ident.equals(mine)) {
                ProjInfo p = projectId == null ? null : projectMapper.selectById(projectId);
                if (p == null || HQ_SCOPE.contains(mine)) {
                    return true;
                }
                if (p.getOrgId() != null && p.getOrgId().equals(u.getOrgId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canLedgerEdit() {
        SysUser u = currentUserOrNull();
        return u != null && isLedgerEditor(u);
    }

    public boolean canLedgerDelete() {
        SysUser u = currentUserOrNull();
        String code = identityOf(u);
        return "admin".equals(code) || "hqHead".equals(code) || "unitHead".equals(code);
    }

    public static ProjTeamMember findMember(List<ProjTeamMember> members, String identityCode) {
        if (members == null || members.isEmpty() || identityCode == null) {
            return null;
        }
        String[] keys = IDENTITY_ROLE_KEYS.get(identityCode);
        if (keys == null) {
            return null;
        }
        for (ProjTeamMember m : members) {
            String code = m.getRoleCode() == null ? "" : m.getRoleCode();
            String name = m.getRoleName() == null ? "" : m.getRoleName();
            for (String key : keys) {
                if (key.equals(code) || key.equals(name) || name.contains(key)) {
                    return m;
                }
            }
        }
        return null;
    }

    /** 工号全等或姓名全等 */
    public static boolean samePerson(SysUser u, ProjTeamMember m) {
        if (u == null || m == null) {
            return false;
        }
        String emp = digits(u.getEmployeeNo());
        String mEmp = digits(m.getEmployeeNo());
        if (!emp.isEmpty() && !mEmp.isEmpty()) {
            return emp.equals(mEmp);
        }
        String name = u.getRealName() == null ? "" : u.getRealName().trim();
        String mName = m.getUserName() == null ? "" : m.getUserName().trim();
        return !name.isEmpty() && name.equals(mName);
    }

    /**
     * 负责人文本形如 "林晚晴" / "林晚晴（100012）" / "100012"：按工号全等或姓名全等匹配。
     */
    public static boolean matchesOwnerLabel(String ownerLabel, SysUser u) {
        if (ownerLabel == null || u == null) {
            return false;
        }
        String label = ownerLabel.trim();
        if (label.isEmpty()) {
            return false;
        }
        String emp = digits(u.getEmployeeNo());
        String labelNo = digits(label);
        if (!emp.isEmpty() && !labelNo.isEmpty()) {
            return emp.equals(labelNo);
        }
        String name = u.getRealName() == null ? "" : u.getRealName().trim();
        String labelName = label.replaceAll("[（(].*?[)）]", "").replaceAll("\\d", "").trim();
        return !name.isEmpty() && name.equals(labelName);
    }

    public static String digits(String v) {
        return v == null ? "" : v.replaceAll("\\D", "");
    }

    public static String[] identitiesForFlowNode(String node) {
        String t = node == null ? "" : node;
        if (t.contains("联系人")) {
            return new String[]{"contactLogin", "owner"};
        }
        if (t.contains("承担部门") || t.contains("承办部门")) {
            return new String[]{"deptHead"};
        }
        if (t.contains("项目负责人") && !t.contains("处")) {
            return new String[]{"owner"};
        }
        if (t.contains("二级总师")) {
            return new String[]{"chief2"};
        }
        if (t.contains("一级总师")) {
            return new String[]{"chief1"};
        }
        if (t.contains("总部") && t.contains("财务")) {
            return new String[]{"finHq"};
        }
        if (t.contains("财务")) {
            return new String[]{"finHead", "finStaff"};
        }
        if (t.contains("法务")) {
            return new String[]{"hqHead", "hqStaff"};
        }
        if (t.contains("总部") || t.contains("科研项目处") || t.contains("科技主管")) {
            return new String[]{"hqHead", "hqStaff"};
        }
        if (t.contains("分管领导")) {
            return new String[]{"unitLeader", "unitHead"};
        }
        if (t.contains("二级单位") || t.contains("内审") || t.contains("科技部门")
                || t.contains("分管") || t.contains("科技管理部") || t.contains("主管部门")) {
            return new String[]{"unitHead", "unitStaff"};
        }
        return new String[]{"unitHead", "hqHead", "hqStaff"};
    }
}
