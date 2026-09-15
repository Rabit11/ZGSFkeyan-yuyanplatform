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
 */
@Component
public class FlowAuditGuard {

    private static final Set<String> HQ_SCOPE = new HashSet<>(Arrays.asList(
            "hqHead", "hqStaff", "finHq", "chief1", "leader"));

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
        IDENTITY_ROLE_KEYS.put("deptHead", new String[]{"DEPT_HEAD", "项目承担部门负责人"});
        IDENTITY_ROLE_KEYS.put("finHq", new String[]{"HQ_FINANCE", "总部财务主管"});
        IDENTITY_ROLE_KEYS.put("finHead", new String[]{"UNIT_FIN_MINISTER", "单位财务部长"});
        IDENTITY_ROLE_KEYS.put("finStaff", new String[]{"UNIT_FIN_SUPERVISOR", "单位财务主管"});
    }

    @Autowired
    private SysUserMapper userMapper;
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

    public void requireIdentities(String actionLabel, String... codes) {
        SysUser u = currentUser();
        String mine = u.getIdentityCode() == null ? "" : u.getIdentityCode();
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
        String code = u.getIdentityCode() == null ? "" : u.getIdentityCode();
        if (HQ_SCOPE.contains(code)) {
            return;
        }
        ProjInfo p = projectMapper.selectById(projectId);
        if (p == null) {
            return;
        }
        if (p.getOrgId() != null && p.getOrgId().equals(u.getOrgId())) {
            return;
        }
        if (p.getOwnerName() != null && u.getRealName() != null && p.getOwnerName().contains(u.getRealName())) {
            return;
        }
        List<ProjTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, projectId));
        if (members == null || members.isEmpty()) {
            throw new BusinessException(403, "您不是该项目相关办理人，无法完成审核");
        }
        String emp = u.getEmployeeNo() == null ? "" : u.getEmployeeNo().replaceAll("\\D", "");
        String name = u.getRealName() == null ? "" : u.getRealName().trim();
        for (ProjTeamMember m : members) {
            String mEmp = m.getEmployeeNo() == null ? "" : m.getEmployeeNo().replaceAll("\\D", "");
            if (!emp.isEmpty() && emp.equals(mEmp)) {
                return;
            }
            if (!name.isEmpty() && name.equals(m.getUserName())) {
                return;
            }
        }
        throw new BusinessException(403, "您不是该项目相关办理人，无法完成审核");
    }

    /**
     * 指定到人：优先匹配本项目团队实名；已点名则仅该办理人可写；
     * 未点名时回落到任职身份 + 项目关联。公司领导只读。
     */
    public void requireActors(Long projectId, String actionLabel, String... identityCodes) {
        SysUser u = currentUser();
        String mine = u.getIdentityCode() == null ? "" : u.getIdentityCode();
        if ("admin".equals(mine)) {
            return;
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

    public boolean canLedgerEdit() {
        try {
            SysUser u = currentUser();
            String code = u.getIdentityCode() == null ? "" : u.getIdentityCode();
            if ("admin".equals(code) || "hqHead".equals(code) || "hqStaff".equals(code)
                    || "unitHead".equals(code) || "unitStaff".equals(code)) {
                return true;
            }
        } catch (BusinessException ignored) {
            return false;
        }
        return false;
    }

    public boolean canLedgerDelete() {
        try {
            SysUser u = currentUser();
            String code = u.getIdentityCode() == null ? "" : u.getIdentityCode();
            return "admin".equals(code) || "hqHead".equals(code) || "unitHead".equals(code);
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private static ProjTeamMember findMember(List<ProjTeamMember> members, String identityCode) {
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

    private static boolean samePerson(SysUser u, ProjTeamMember m) {
        String emp = digits(u.getEmployeeNo());
        String mEmp = digits(m.getEmployeeNo());
        if (!emp.isEmpty() && emp.equals(mEmp)) {
            return true;
        }
        String name = u.getRealName() == null ? "" : u.getRealName().trim();
        String mName = m.getUserName() == null ? "" : m.getUserName().trim();
        return !name.isEmpty() && name.equals(mName);
    }

    private static String digits(String v) {
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
        if (t.contains("二级单位") || t.contains("内审") || t.contains("科技部门")
                || t.contains("分管") || t.contains("主管部门")) {
            return new String[]{"unitHead"};
        }
        return new String[]{"unitHead", "hqHead", "hqStaff"};
    }
}
