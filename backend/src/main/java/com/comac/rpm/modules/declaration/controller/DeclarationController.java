package com.comac.rpm.modules.declaration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.SeqUtil;
import com.comac.rpm.modules.declaration.entity.ProjDeclaration;
import com.comac.rpm.modules.declaration.DeclarationWorkflow;
import com.comac.rpm.modules.declaration.entity.ProjDeclarationPost;
import com.comac.rpm.modules.declaration.entity.ProjFiling;
import com.comac.rpm.modules.declaration.entity.ProjMaterial;
import com.comac.rpm.modules.declaration.mapper.ProjDeclarationMapper;
import com.comac.rpm.modules.declaration.mapper.ProjDeclarationPostMapper;
import com.comac.rpm.modules.declaration.mapper.ProjFilingMapper;
import com.comac.rpm.modules.declaration.mapper.ProjMaterialMapper;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import com.comac.rpm.modules.dict.mapper.ProjChannelMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 立项阶段：项目申报 + 项目立项备案
 * <p>
 * 按所选项目渠道自适应生成材料栏：非本渠道所需材料字段 locked=1，前端锁定不可编辑。
 */
@RestController
@RequestMapping("/api/declarations")
public class DeclarationController {
    private static final DateTimeFormatter PROJECT_NO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 全量材料字典（与前端一致） */
    private static final List<String> ALL_FIELDS = Arrays.asList(
            "建议书", "建议书意见", "申请书", "申请书评审", "榜单答疑", "任务清单", "任务清单评估",
            "申报通知", "委员会审议", "学术委员会审议", "合作需求", "需求对接总结",
            "技术发展战略委员会审议", "项目申请书", "波音指导委员会会议纪要");

    /** 申报审签链：项目负责人 → 项目承担部门负责人 → 二级总师 → … */
    private static final List<String> DECLARE_AUDIT_CHAIN = Arrays.asList(
            "项目负责人",
            "项目承担部门负责人",
            "二级总师",
            "单位财务部门负责人",
            "单位科技部门负责人",
            "单位分管领导",
            "一级总师",
            "总部科研项目处");

    /** 申报岗位是全周期唯一人员来源；立项时按同一角色编码写入项目团队。 */
    private static final List<DeclarationRole> DECLARATION_ROLES = Arrays.asList(
            new DeclarationRole("contact", "TECH", "PROJECT_CONTACT", "项目联系人"),
            new DeclarationRole("leader", "TECH", "PROJECT_LEADER", "项目负责人"),
            new DeclarationRole("techLeader", "TECH", "TECH_LEADER", "技术负责人"),
            new DeclarationRole("supervisor", "TECH", "PROJECT_SUPERVISOR", "项目主管"),
            new DeclarationRole("chief1", "EXPERT", "L1_CHIEF", "一级总师"),
            new DeclarationRole("chief2", "EXPERT", "L2_CHIEF", "二级总师"),
            new DeclarationRole("deptHead", "MGMT", "DEPT_HEAD", "项目承担部门负责人"),
            new DeclarationRole("hqDirector", "MGMT", "HQ_DIRECTOR", "总部处室处长"),
            new DeclarationRole("hqSupervisor", "MGMT", "HQ_SUPERVISOR", "总部处室主管"),
            new DeclarationRole("unitTechDirector", "MGMT", "UNIT_MINISTER", "单位科技部长"),
            new DeclarationRole("unitTechSupervisor", "MGMT", "UNIT_SUPERVISOR", "单位科技主管"),
            new DeclarationRole("hqFinance", "FIN", "HQ_FINANCE", "总部财务主管"),
            new DeclarationRole("unitFinanceDirector", "FIN", "UNIT_FIN_MINISTER", "单位财务部长"),
            new DeclarationRole("unitFinanceSupervisor", "FIN", "UNIT_FIN_SUPERVISOR", "单位财务主管"));
    private static final Pattern PERSON_LABEL = Pattern.compile("^\\s*(.*?)\\s*[（(]\\s*([^）)]+)\\s*[）)]\\s*$");
    private static final String DECLARATION_POSTS_REMARK_PREFIX = "__DECLARATION_POSTS__:";

    private record DeclarationRole(String key, String groupCode, String roleCode, String roleName) {}
    private record PersonValue(String name, String employeeNo) {}

    @Autowired
    private ProjDeclarationMapper declarationMapper;
    @Autowired
    private ProjDeclarationPostMapper declarationPostMapper;
    @Autowired
    private ProjMaterialMapper materialMapper;
    @Autowired
    private ProjFilingMapper filingMapper;
    @Autowired
    private ProjChannelMapper channelMapper;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private ProjTeamMemberMapper teamMemberMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;
    @Autowired
    private SysAuditLogMapper auditLogMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DeclarationWorkflow workflow;

    @GetMapping
    public R<PageVO<ProjDeclaration>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "channelId", required = false) Long channelId,
            @RequestParam(value = "orgId", required = false) Long orgId) {
        LambdaQueryWrapper<ProjDeclaration> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            w.like(ProjDeclaration::getName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            w.eq(ProjDeclaration::getStatus, status);
        }
        if (channelId != null) {
            w.eq(ProjDeclaration::getChannelId, channelId);
        }
        if (orgId != null) {
            w.eq(ProjDeclaration::getOrgId, orgId);
        } else if (!UserContext.isHeadquarter()) {
            List<Long> assignedIds = assignedDeclarationIdsForCurrentUser();
            // 普通岗位除本单位数据外，还必须能看到以本人作为申报人的任务。
            // 新建申报在单位字段尚未补齐时 org_id 可能为空，若只按单位过滤，
            // 项目负责人会在流转图中成为当前办理人，却在自己的列表里看不到任务。
            w.and(scope -> {
                scope.eq(ProjDeclaration::getOrgId, UserContext.getOrgId())
                        .or().eq(ProjDeclaration::getApplicantId, UserContext.getUserId())
                        .or().like(ProjDeclaration::getApplicant, UserContext.getUsername());
                if (!assignedIds.isEmpty()) {
                    scope.or().in(ProjDeclaration::getId, assignedIds);
                }
            });
        }
        w.orderByDesc(ProjDeclaration::getId);
        Page<ProjDeclaration> result = declarationMapper.selectPage(new Page<>(page, size), w);
        result.getRecords().forEach(this::hydratePosts);
        return R.ok(PageVO.of(result));
    }

    /**
     * 当前登录用户的项目申报待办。待办按当前节点和申报岗位快照计算，
     * 不受普通列表分页大小限制，并兼容迁移前保存在 remark 中的岗位快照。
     */
    @GetMapping("/pending")
    public R<List<ProjDeclaration>> pending() {
        List<ProjDeclaration> candidates = declarationMapper.selectList(
                new LambdaQueryWrapper<ProjDeclaration>()
                        .in(ProjDeclaration::getStatus, Arrays.asList("SUBMITTED", "APPROVING"))
                        .orderByDesc(ProjDeclaration::getId));
        if (UserContext.isAdmin()) {
            candidates.forEach(this::hydratePosts);
            return R.ok(candidates);
        }
        List<ProjDeclaration> result = new ArrayList<>();
        for (ProjDeclaration declaration : candidates) {
            hydratePosts(declaration);
            if (isCurrentUserDeclarationApprover(declaration)) {
                result.add(declaration);
            }
        }
        return R.ok(result);
    }

    private boolean isCurrentUserDeclarationApprover(ProjDeclaration declaration) {
        if (declaration == null) return false;
        SysUser user = flowAuditGuard.currentUser();
        Map<String, String> posts = declaration.getPosts() == null ? loadPosts(declaration) : declaration.getPosts();
        DeclarationWorkflow.Node node = workflow.current(declaration, posts);
        if (node == null) return false;
        String[] roleKeys = node.roleKeys().toArray(String[]::new);
        List<String> labels = new ArrayList<>();
        for (String roleKey : roleKeys) {
            String label = posts.get(roleKey);
            if (label != null && !label.isBlank()) labels.add(label);
        }
        if (!labels.isEmpty()) {
            return canActOnDeclarationLabels(labels, user);
        }
        if (posts.containsKey("__workflow")) return false;
        // 没有岗位快照时保留原有身份兜底，同时要求单位范围或申报人为本人。
        String identity = user.getIdentityCode() == null ? "" : user.getIdentityCode();
        boolean identityAllowed = false;
        for (String code : FlowAuditGuard.identitiesForFlowNode(declaration.getFlowNode())) {
            if (code.equals(identity)) {
                identityAllowed = true;
                break;
            }
        }
        if (!identityAllowed) return false;
        if ("COMPANY".equalsIgnoreCase(user.getDataScope()) || "admin".equals(identity)) return true;
        if (declaration.getOrgId() != null && declaration.getOrgId().equals(user.getOrgId())) return true;
        if (declaration.getApplicantId() != null && declaration.getApplicantId().equals(user.getId())) return true;
        return declaration.getApplicant() != null && declaration.getApplicant().trim().equals(user.getRealName());
    }

    private static boolean canActOnDeclarationLabels(List<String> labels, SysUser user) {
        String employeeNo = digits(user.getEmployeeNo());
        String realName = user.getRealName() == null ? "" : user.getRealName().trim();
        for (String label : labels) {
            PersonValue person = parsePerson(label);
            if (!employeeNo.isEmpty() && employeeNo.equals(digits(person.employeeNo()))) return true;
            if (!realName.isEmpty() && realName.equals(person.name())) return true;
        }
        return false;
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") Long id) {
        Map<String, Object> map = new HashMap<>();
        ProjDeclaration d = declarationMapper.selectById(id);
        hydratePosts(d);
        map.put("declaration", d);
        map.put("materials", listMaterials(id));
        ProjFiling filing = filingMapper.selectOne(new LambdaQueryWrapper<ProjFiling>()
                .eq(ProjFiling::getDeclarationId, id).orderByDesc(ProjFiling::getId).last("LIMIT 1"));
        map.put("filing", filing);
        if (filing != null) map.put("project", projectMapper.selectById(filing.getProjectId()));
        if (d != null && d.getChannelId() != null) {
            ProjChannel ch = channelMapper.selectById(d.getChannelId());
            if (ch != null) {
                ch.setDeclareMaterialList(split(ch.getDeclareMaterial()));
                ch.setFilingMaterialList(split(ch.getFilingMaterial()));
                map.put("channel", ch);
                map.put("declareMaterial", ch.getDeclareMaterial());
                map.put("filingMaterial", ch.getFilingMaterial());
            }
        }
        return R.ok(map);
    }

    /** 新建申报：根据渠道自适应生成材料栏 */
    @PostMapping
    @Transactional
    public R<Long> create(@RequestBody ProjDeclaration body) {
        flowAuditGuard.requireActors(null, "申报信息填写", "contactLogin", "owner", "techLead", "projectPm");
        body.setId(null);
        body.setApplyNo(SeqUtil.applyNo(declarationMapper.selectCount(null) + 1));
        body.setStatus("DRAFT");
        body.setApplicantId(UserContext.getUserId());
        if (body.getApplicant() == null || body.getApplicant().isEmpty()) {
            body.setApplicant(UserContext.getUsername());
        }
        body.setApplyAt(LocalDateTime.now());
        if (body.getNeedApproval() == null) {
            body.setNeedApproval(1);
        }
        normalizeOrgFields(body);
        if (body.getChannelId() != null) {
            ProjChannel ch = channelMapper.selectById(body.getChannelId());
            if (ch != null) {
                body.setChannelName(ch.getChannelName());
                body.setLevelCode(ch.getLevelCode());
            }
        }
        Map<String, String> initialPosts = new LinkedHashMap<>(body.getPosts() == null ? Map.of() : body.getPosts());
        String version = workflow.selectForNew(body.getChannelId() == null ? null : channelMapper.selectById(body.getChannelId()));
        initialPosts.put("__workflow", version);
        body.setPosts(initialPosts);
        body.setRemark(DECLARATION_POSTS_REMARK_PREFIX + writePostsJson(initialPosts));
        body.setNeedApproval(version.contains("report-") ? 0 : 1);
        body.setFlowNode(workflow.nodes(version).get(0).title());
        declarationMapper.insert(body);
        savePosts(body.getId(), body.getPosts());
        initMaterials(body.getId(), body.getChannelId());
        return R.ok(body.getId());
    }

    @PutMapping("/{id}")
    @Transactional
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjDeclaration body) {
        ProjDeclaration current = lockDeclaration(id);
        requireDeclarationPostActor(id, "编辑申报", "contact", "leader", "techLeader", "supervisor");
        if (!Arrays.asList("DRAFT", "REJECTED").contains(current.getStatus())) {
            throw new BusinessException("申报已进入审批，不能编辑或改变流程状态");
        }
        body.setStatus(current.getStatus());
        body.setFlowNode(current.getFlowNode());
        body.setOpinion(current.getOpinion());
        body.setApplicantId(current.getApplicantId());
        body.setId(id);
        Map<String, String> updatedPosts = new LinkedHashMap<>(body.getPosts() == null ? loadPosts(current) : body.getPosts());
        Long updatedChannelId = body.getChannelId() == null ? current.getChannelId() : body.getChannelId();
        String currentVersion = workflow.version(current, loadPosts(current));
        String updatedVersion = currentVersion.startsWith("yzh-")
                ? workflow.selectForNew(updatedChannelId == null ? null : channelMapper.selectById(updatedChannelId)) : currentVersion;
        updatedPosts.put("__workflow", updatedVersion);
        body.setPosts(updatedPosts);
        body.setRemark(DECLARATION_POSTS_REMARK_PREFIX + writePostsJson(updatedPosts));
        body.setNeedApproval(updatedVersion.contains("report-") ? 0 : 1);
        body.setFlowNode(workflow.nodes(updatedVersion).get(0).title());
        normalizeOrgFields(body);
        if (body.getChannelId() != null) {
            ProjChannel ch = channelMapper.selectById(body.getChannelId());
            if (ch != null) {
                body.setChannelName(ch.getChannelName());
                body.setLevelCode(ch.getLevelCode());
            }
        }
        declarationMapper.updateById(body);
        savePosts(id, body.getPosts());
        return R.ok(true);
    }

    /** 列表责任单位字段与组织名称互相同步，避免前端 leadOrgName / orgName 一侧为空 */
    private static void normalizeOrgFields(ProjDeclaration body) {
        if (body.getLeadOrgName() != null && !body.getLeadOrgName().isEmpty()) {
            if (body.getOrgName() == null || body.getOrgName().isEmpty()) {
                body.setOrgName(body.getLeadOrgName());
            }
        } else if (body.getOrgName() != null && !body.getOrgName().isEmpty()) {
            body.setLeadOrgName(body.getOrgName());
        }
    }

    @GetMapping("/{id}/materials")
    public R<List<ProjMaterial>> materials(@PathVariable("id") Long id) {
        return R.ok(listMaterials(id));
    }

    /** 上传/替换材料：版本号自增，支持流程中经办人/审核人替换附件 */
    @PostMapping("/{id}/materials")
    @Transactional
    public R<Boolean> uploadMaterial(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjDeclaration declaration = lockDeclaration(id);
        requireDeclarationPostActor(id, "上传申报或备案材料", "contact", "leader", "techLeader", "supervisor");
        ProjFiling currentFiling = filingMapper.selectOne(new LambdaQueryWrapper<ProjFiling>()
                .eq(ProjFiling::getDeclarationId, id).orderByDesc(ProjFiling::getId).last("LIMIT 1"));
        if ("APPROVING".equals(declaration.getStatus()) || (currentFiling != null
                && Arrays.asList("AUDIT", "ARCHIVED").contains(currentFiling.getStatus()))) {
            throw new BusinessException("材料正在审核或已归档，不能修改");
        }
        String fieldCode = body.get("fieldCode") == null ? null : String.valueOf(body.get("fieldCode"));
        String fileName = body.get("fileName") == null ? null : String.valueOf(body.get("fileName"));
        ProjMaterial m = materialMapper.selectOne(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, "DECLARATION")
                .eq(ProjMaterial::getBizId, id)
                .eq(ProjMaterial::getFieldCode, fieldCode)
                .last("LIMIT 1"));
        if (m == null) {
            String fieldName = body.get("fieldName") == null ? "" : String.valueOf(body.get("fieldName")).trim();
            if (fieldName.isEmpty()) {
                throw new BusinessException("材料栏位不存在或当前渠道不适用");
            }
            m = new ProjMaterial();
            m.setBizType("DECLARATION");
            m.setBizId(id);
            m.setFieldCode(fieldCode == null || fieldCode.isBlank() ? "F_" + fieldName : fieldCode);
            m.setFieldName(fieldName);
            m.setRequired(1);
            m.setLocked(0);
            m.setVersion(1);
            materialMapper.insert(m);
        }
        if (Integer.valueOf(1).equals(m.getLocked())) {
            ProjChannel filingChannel = declaration.getChannelId() == null ? null : channelMapper.selectById(declaration.getChannelId());
            if (!Arrays.asList("APPROVED", "REPORTED").contains(declaration.getStatus()) || filingChannel == null
                    || !split(filingChannel.getFilingMaterial()).contains(m.getFieldName())) {
                throw new BusinessException("该材料栏位已锁定，当前渠道不适用");
            }
            m.setLocked(0);
        }
        m.setFileName(fileName);
        if (body.get("fileUrl") != null) {
            m.setFileUrl(String.valueOf(body.get("fileUrl")));
        }
        if (body.get("fileSize") != null) {
            try {
                m.setFileSize(Long.valueOf(String.valueOf(body.get("fileSize"))));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        m.setVersion((m.getVersion() == null ? 1 : m.getVersion()) + 1);
        m.setUploadedBy(UserContext.getUsername());
        m.setUploadedAt(LocalDateTime.now());
        materialMapper.updateById(m);
        return R.ok(true);
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public R<Boolean> submit(@PathVariable("id") Long id) {
        ProjDeclaration exist = lockDeclaration(id);
        if (exist == null) {
            throw new BusinessException("申报记录不存在，请先暂存");
        }
        // 以本申报实名岗位为准：项目联系人、负责人、技术负责人、项目主管均可提交。
        // 不再叠加全局任职身份校验，避免项目负责人已被点名却因账号身份映射差异无法提交。
        requireDeclarationPostActor(id, "提交项目申报", "contact", "leader", "techLeader", "supervisor");
        if (!Arrays.asList("DRAFT", "REJECTED").contains(exist.getStatus())) {
            throw new BusinessException("仅草稿或退回的申报可以提交审核");
        }
        List<String> missing = new ArrayList<>();
        for (ProjMaterial m : listMaterials(id)) {
            if (Integer.valueOf(1).equals(m.getRequired()) && !Integer.valueOf(1).equals(m.getLocked())) {
                if (m.getFileName() == null || m.getFileName().trim().isEmpty()) {
                    missing.add(m.getFieldName());
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("渠道材料未齐，请上传：" + String.join("、", missing));
        }
        ProjDeclaration d = new ProjDeclaration();
        d.setId(id);
        ProjChannel channel = exist.getChannelId() == null ? null : channelMapper.selectById(exist.getChannelId());
        Map<String, String> posts = loadPosts(exist);
        String version = workflow.version(exist, posts);
        List<String> missingActors = workflow.nodes(version).stream()
                .filter(n -> !n.roleKeys().isEmpty() && n.roleKeys().stream()
                        .noneMatch(key -> posts.get(key) != null && !posts.get(key).isBlank()))
                .map(DeclarationWorkflow.Node::title).toList();
        if (!missingActors.isEmpty()) throw new BusinessException("请指定渠道节点办理人：" + String.join("、", missingActors));
        posts.put("__workflow", version);
        d.setRemark(DECLARATION_POSTS_REMARK_PREFIX + writePostsJson(posts));
        savePosts(id, posts);
        d.setNeedApproval(version.contains("report-") ? 0 : 1);
        d.setStatus("APPROVING");
        d.setFlowNode(workflow.auditNodes(version).get(0).title());
        declarationMapper.updateById(d);
        return R.ok(true);
    }

    /** 审批：驳回自动退回初始填报节点并标注驳回意见，所有驳回记录留痕 */
    @PostMapping("/{id}/audit")
    @Transactional
    public R<Boolean> audit(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjDeclaration exist = lockDeclaration(id);
        if (exist == null) {
            return R.fail("申报记录不存在");
        }
        Map<String, String> posts = loadPosts(exist);
        String version = workflow.version(exist, posts);
        DeclarationWorkflow.Node node = workflow.current(exist, posts);
        if (!"APPROVING".equals(exist.getStatus()) || node == null) {
            throw new BusinessException("申报当前不在有效审核节点，不能重复审核或改变流程");
        }
        String[] actorRoleKeys = node.roleKeys().toArray(String[]::new);
        if (posts.containsKey("__workflow") && !isCurrentUserDeclarationApprover(exist))
            throw new BusinessException(403, "仅本节点指定办理人可以办理");
        if (actorRoleKeys.length > 0 && hasDeclarationPostActor(exist, actorRoleKeys)) {
            // 优先按申报时选定的具体人员流转；历史数据没有岗位快照时才回退到身份校验。
            requireDeclarationPostActor(id, exist.getFlowNode() == null ? "申报审核" : exist.getFlowNode(), actorRoleKeys);
        } else {
            flowAuditGuard.requireFlowNode(exist.getFlowNode(), null, exist.getFlowNode() == null ? "申报审核" : exist.getFlowNode());
        }
        if (body == null || !(body.get("pass") instanceof Boolean)) throw new BusinessException("请选择通过或退回");
        boolean pass = Boolean.TRUE.equals(body.get("pass"));
        String evidence = body.get("evidence") == null ? "" : String.valueOf(body.get("evidence")).trim();
        if (pass && node.evidence() && (evidence.isBlank() || body.get("opinion") == null
                || String.valueOf(body.get("opinion")).isBlank()))
            throw new BusinessException("请填写办理结论及评审纪要/发布文件等佐证引用");
        ProjDeclaration d = new ProjDeclaration();
        d.setId(id);
        if (!pass) {
            d.setStatus("REJECTED");
            d.setFlowNode(workflow.nodes(version).get(0).title());
        } else {
            List<DeclarationWorkflow.Node> chain = workflow.auditNodes(version);
            int index = chain.indexOf(node);
            String next = index + 1 < chain.size() ? chain.get(index + 1).title() : null;
            boolean directReport = next == null && Integer.valueOf(0).equals(exist.getNeedApproval());
            if (directReport) {
                d.setStatus("REPORTED");
                d.setFlowNode("线上报备归档");
            } else if (next == null) {
                d.setStatus("APPROVED");
                d.setFlowNode("归档");
            } else {
                d.setStatus("APPROVING");
                d.setFlowNode(next);
            }
        }
        if (body != null && body.get("opinion") != null) {
            d.setOpinion(String.valueOf(body.get("opinion")));
        }
        declarationMapper.updateById(d);
        auditLogMapper.write("DECLARATION", pass ? "APPROVE" : "REJECT", "DECLARATION", id,
                (pass ? "项目申报审核通过：" : "项目申报审核退回：")
                        + safe(exist.getFlowNode()) + "，" + safe(exist.getName())
                        + "，结论：" + safe(d.getOpinion()) + "，佐证：" + safe(evidence));
        return R.ok(true);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private boolean hasDeclarationPostActor(ProjDeclaration declaration, String... roleKeys) {
        if (declaration == null || roleKeys == null || roleKeys.length == 0) {
            return false;
        }
        Map<String, String> posts = loadPosts(declaration);
        List<String> effectiveRoleKeys = new ArrayList<>();
        for (String roleKey : roleKeys) {
            if (!effectiveRoleKeys.contains(roleKey)) {
                effectiveRoleKeys.add(roleKey);
            }
            if ("contact".equals(roleKey) && !effectiveRoleKeys.contains("leader")) {
                effectiveRoleKeys.add("leader");
            }
        }
        for (String roleKey : effectiveRoleKeys) {
            String label = posts.get(roleKey);
            if (label != null && !label.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String[] declarationRoleKeysForFlowNode(String node) {
        String t = node == null ? "" : node.trim();
        if (t.contains("联系人")) {
            return new String[]{"contact"};
        }
        if (t.contains("项目负责人") && !t.contains("处")) {
            return new String[]{"leader"};
        }
        if (t.contains("承担部门") || t.contains("承办部门")) {
            return new String[]{"deptHead"};
        }
        if (t.contains("二级总师")) {
            return new String[]{"chief2"};
        }
        if (t.contains("一级总师")) {
            return new String[]{"chief1"};
        }
        if (t.contains("总部") && t.contains("财务")) {
            return new String[]{"hqFinance"};
        }
        if (t.contains("财务")) {
            return new String[]{"unitFinanceDirector", "unitFinanceSupervisor"};
        }
        if (t.contains("总部") || t.contains("科研项目处")) {
            return new String[]{"hqDirector", "hqSupervisor"};
        }
        if (t.contains("科技部门")) {
            return new String[]{"unitTechDirector", "unitTechSupervisor"};
        }
        if (t.contains("分管")) {
            return new String[]{"unitTechDirector", "unitTechSupervisor"};
        }
        return new String[0];
    }

    /** 申报尚未生成项目主表时，按申报岗位快照校验到具体办理人。 */
    private void requireDeclarationPostActor(Long declarationId, String actionLabel, String... roleKeys) {
        if (UserContext.isAdmin()) {
            return;
        }
        ProjDeclaration declaration = declarationMapper.selectById(declarationId);
        if (declaration == null) {
            throw new BusinessException("申报记录不存在");
        }
        SysUser user = flowAuditGuard.currentUser();
        Map<String, String> posts = loadPosts(declaration);
        String employeeNo = digits(user.getEmployeeNo());
        String realName = user.getRealName() == null ? "" : user.getRealName().trim();
        List<String> assignedNames = new ArrayList<>();
        List<String> effectiveRoleKeys = new ArrayList<>();
        for (String roleKey : roleKeys) {
            if (!effectiveRoleKeys.contains(roleKey)) {
                effectiveRoleKeys.add(roleKey);
            }
            if ("contact".equals(roleKey) && !effectiveRoleKeys.contains("leader")) {
                effectiveRoleKeys.add("leader");
            }
        }
        for (String roleKey : effectiveRoleKeys) {
            String label = posts.get(roleKey);
            if (label == null || label.isBlank()) {
                continue;
            }
            PersonValue person = parsePerson(label);
            assignedNames.add(person.name());
            if (!employeeNo.isEmpty() && employeeNo.equals(digits(person.employeeNo()))) {
                return;
            }
            if (!realName.isEmpty() && realName.equals(person.name())) {
                return;
            }
        }
        String assigned = assignedNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("待指定");
        throw new BusinessException(403, "仅本项目指定办理人可完成「" + actionLabel + "」，当前指定：" + assigned);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String nextAuditNode(String current) {
        String cur = current == null ? "" : current.trim();
        if ("承办部门负责人".equals(cur)) {
            cur = "项目承担部门负责人";
        }
        int idx = DECLARE_AUDIT_CHAIN.indexOf(cur);
        if (idx < 0 && !cur.isEmpty()) {
            for (int i = 0; i < DECLARE_AUDIT_CHAIN.size(); i++) {
                String t = DECLARE_AUDIT_CHAIN.get(i);
                if (cur.contains(t) || t.contains(cur)) {
                    idx = i;
                    break;
                }
            }
        }
        if (idx < 0) {
            return DECLARE_AUDIT_CHAIN.get(0);
        }
        if (idx >= DECLARE_AUDIT_CHAIN.size() - 1) {
            return null;
        }
        return DECLARE_AUDIT_CHAIN.get(idx + 1);
    }

    /** 撤销：仅填报人可发起，撤销后回归草稿状态 */
    @PostMapping("/{id}/revoke")
    @Transactional
    public R<Boolean> revoke(@PathVariable("id") Long id) {
        ProjDeclaration d = lockDeclaration(id);
        if (d == null) {
            return R.fail("申报记录不存在");
        }
        if (d.getApplicantId() != null && !d.getApplicantId().equals(UserContext.getUserId())) {
            throw new BusinessException("仅填报人可发起撤销");
        }
        if (!"APPROVING".equals(d.getStatus()) || !"项目负责人".equals(d.getFlowNode())) {
            throw new BusinessException("仅负责人尚未审核的申报可以撤销");
        }
        d.setStatus("DRAFT");
        declarationMapper.updateById(d);
        return R.ok(true);
    }

    private ProjDeclaration lockDeclaration(Long id) {
        ProjDeclaration d = declarationMapper.selectOne(new LambdaQueryWrapper<ProjDeclaration>()
                .eq(ProjDeclaration::getId, id).last("FOR UPDATE"));
        if (d == null) throw new BusinessException("申报记录不存在");
        return d;
    }

    private void requireDeclarationApproved(ProjDeclaration d) {
        if (!("APPROVED".equals(d.getStatus()) && "归档".equals(d.getFlowNode()))
                && !("REPORTED".equals(d.getStatus()) && "线上报备归档".equals(d.getFlowNode()))) {
            throw new BusinessException("申报审签或线上报备尚未办结，不能进入立项备案");
        }
    }

    private Map<String, Object> filingResult(ProjFiling f) {
        Map<String, Object> res = new HashMap<>();
        res.put("projectId", f.getProjectId());
        res.put("filingId", f.getId());
        return res;
    }

    /** 负责人只能在申报办结后提交备案材料，不能自行批准备案或进入实施。 */
    @PostMapping("/{id}/filing-submit")
    @Transactional
    public R<Map<String, Object>> submitFiling(@PathVariable("id") Long id) {
        ProjDeclaration d = lockDeclaration(id);
        requireDeclarationApproved(d);
        requireDeclarationPostActor(id, "提交备案材料", "leader");
        ProjFiling existing = filingMapper.selectOne(new LambdaQueryWrapper<ProjFiling>()
                .eq(ProjFiling::getDeclarationId, id).orderByDesc(ProjFiling::getId).last("LIMIT 1"));
        if (existing != null && Arrays.asList("AUDIT", "ARCHIVED").contains(existing.getStatus())) {
            return R.ok(filingResult(existing));
        }
        ProjChannel ch = d.getChannelId() == null ? null : channelMapper.selectById(d.getChannelId());
        if (ch == null) throw new BusinessException("项目渠道不存在，无法核对备案材料");
        List<ProjMaterial> materials = listMaterials(id);
        for (String name : split(ch.getFilingMaterial())) {
            if (materials.stream().noneMatch(m -> name.equals(m.getFieldName())
                    && m.getFileName() != null && !m.getFileName().isBlank()
                    && m.getFileUrl() != null && !m.getFileUrl().isBlank())) {
                throw new BusinessException("立项备案材料未齐，请上传：" + name);
            }
        }
        if (existing != null) {
            existing.setStatus("AUDIT");
            filingMapper.updateById(existing);
            return R.ok(filingResult(existing));
        }
        String filingDept = "总部科技部科研项目处";

        ProjInfo p = new ProjInfo();
        p.setProjectNo(nextProjectNo());
        p.setName(d.getName());
        p.setLevelCode(d.getLevelCode());
        p.setChannelId(d.getChannelId());
        p.setChannelName(d.getChannelName());
        p.setFilingDept(filingDept);
        p.setOrgId(d.getOrgId());
        p.setOrgName(d.getOrgName());
        p.setLeadOrgName(d.getOrgName());
        p.setGoal(d.getGoal());
        p.setStartDate(d.getStartDate());
        p.setEndDate(d.getEndDate());
        p.setTotalFund(d.getApplyFund());
        p.setMajor1(d.getMajor1());
        p.setMajor2(d.getMajor2());
        p.setMainWork(d.getLeadWorkContent());
        Map<String, String> posts = loadPosts(d);
        PersonValue owner = parsePerson(posts.get("leader"));
        PersonValue contact = parsePerson(posts.get("contact"));
        p.setOwnerName(owner.name());
        p.setCreateByName(contact.name());
        p.setStatus("FILING");
        p.setWarnColor("BLUE");
        p.setCreateBy(UserContext.getUserId());
        projectMapper.insert(p);
        syncProjectMembers(p.getId(), posts);

        ProjFiling f = new ProjFiling();
        f.setDeclarationId(id);
        f.setProjectId(p.getId());
        f.setFilingNo(SeqUtil.filingNo(filingMapper.selectCount(null) + 1));
        f.setFilingDate(LocalDate.now());
        f.setFilingDept(filingDept);
        f.setStatus("AUDIT");
        filingMapper.insert(f);
        auditLogMapper.write("DECLARATION", "SUBMIT", "DECLARATION", id, "提交总部备案审核：" + safe(d.getName()));
        return R.ok(filingResult(f));
    }

    /** 兼容旧地址，但只允许指定总部审核人办理已提交的备案。 */
    @PostMapping("/{id}/filing")
    @Transactional
    public R<Map<String, Object>> filing(@PathVariable("id") Long id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        ProjDeclaration d = lockDeclaration(id);
        requireDeclarationPostActor(id, "总部审核备案", "hqDirector", "hqSupervisor");
        requireDeclarationApproved(d);
        ProjFiling f = filingMapper.selectOne(new LambdaQueryWrapper<ProjFiling>()
                .eq(ProjFiling::getDeclarationId, id).orderByDesc(ProjFiling::getId).last("LIMIT 1"));
        if (f == null || !"AUDIT".equals(f.getStatus())) throw new BusinessException("暂无待审核的备案材料");
        if (body == null || !(body.get("pass") instanceof Boolean)) throw new BusinessException("请选择审核通过或退回");
        boolean pass = Boolean.TRUE.equals(body.get("pass"));
        f.setStatus(pass ? "ARCHIVED" : "REJECTED");
        f.setRemark(body.get("opinion") == null ? "" : String.valueOf(body.get("opinion")));
        filingMapper.updateById(f);
        ProjInfo p = new ProjInfo();
        p.setId(f.getProjectId());
        p.setStatus(pass ? "IMPLEMENTING" : "FILING");
        projectMapper.updateById(p);
        if (pass) {
            d.setStatus("REPORTED");
            d.setFlowNode("备案归档");
            declarationMapper.updateById(d);
        }
        auditLogMapper.write("DECLARATION", pass ? "APPROVE" : "REJECT", "DECLARATION", id,
                (pass ? "总部备案审核通过：" : "总部备案退回补正：") + safe(d.getName()));
        return R.ok(filingResult(f));
    }

    private String nextProjectNo() {
        String prefix = "XM" + LocalDate.now().format(PROJECT_NO_DATE_FMT);
        String latest = projectMapper.selectMaxProjectNoByPrefix(prefix);
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

    private void hydratePosts(ProjDeclaration declaration) {
        if (declaration != null && declaration.getId() != null) {
            declaration.setPosts(loadPosts(declaration));
        }
    }

    private List<Long> assignedDeclarationIdsForCurrentUser() {
        List<Long> ids = new ArrayList<>();
        SysUser user = flowAuditGuard.currentUser();
        String employeeNo = digits(user.getEmployeeNo());
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String realName = user.getRealName() == null ? "" : user.getRealName().trim();
        if (!employeeNo.isEmpty()) {
            collectAssignedDeclarationIds(ids, new LambdaQueryWrapper<ProjDeclarationPost>()
                    .eq(ProjDeclarationPost::getEmployeeNo, employeeNo));
        }
        if (!username.isEmpty()) {
            collectAssignedDeclarationIds(ids, new LambdaQueryWrapper<ProjDeclarationPost>()
                    .eq(ProjDeclarationPost::getUserName, username));
        }
        if (!realName.isEmpty()) {
            collectAssignedDeclarationIds(ids, new LambdaQueryWrapper<ProjDeclarationPost>()
                    .eq(ProjDeclarationPost::getUserName, realName));
        }
        return ids;
    }

    private void collectAssignedDeclarationIds(List<Long> ids, LambdaQueryWrapper<ProjDeclarationPost> wrapper) {
        List<ProjDeclarationPost> rows = declarationPostMapper.selectList(wrapper);
        for (ProjDeclarationPost row : rows) {
            Long declarationId = row.getDeclarationId();
            if (declarationId != null && !ids.contains(declarationId)) {
                ids.add(declarationId);
            }
        }
    }

    /**
     * 新岗位表为权威来源；兼容迁移前记录保存在 remark 中的岗位快照。
     * 更早的历史样本没有岗位快照时，以实名申报人恢复项目负责人，确保其可继续审核。
     */
    private Map<String, String> loadPosts(ProjDeclaration declaration) {
        Map<String, String> posts = new LinkedHashMap<>();
        if (declaration == null || declaration.getId() == null) return posts;
        List<ProjDeclarationPost> rows = declarationPostMapper.selectList(
                new LambdaQueryWrapper<ProjDeclarationPost>()
                        .eq(ProjDeclarationPost::getDeclarationId, declaration.getId())
                        .orderByAsc(ProjDeclarationPost::getSort));
        for (ProjDeclarationPost row : rows) {
            if (row.getRoleKey() == null) continue;
            String label = row.getUserName() == null ? "" : row.getUserName();
            if (row.getEmployeeNo() != null && !row.getEmployeeNo().isBlank()) {
                label += "（" + row.getEmployeeNo() + "）";
            }
            posts.put(row.getRoleKey(), label);
        }
        if (posts.isEmpty()) {
            String remark = declaration.getRemark();
            if (remark != null && remark.startsWith(DECLARATION_POSTS_REMARK_PREFIX)) {
                String json = remark.substring(DECLARATION_POSTS_REMARK_PREFIX.length());
                try {
                    Map<String, String> legacyPosts = objectMapper.readValue(
                            json, new TypeReference<Map<String, String>>() {});
                    if (legacyPosts != null) {
                        legacyPosts.forEach((key, value) -> {
                            if (key != null && value != null && !value.isBlank()) {
                                posts.put(key, value);
                            }
                        });
                    }
                } catch (Exception ignored) {
                    // 旧备注不是有效岗位快照时继续使用申报人兜底，不影响普通备注读取。
                }
            }
        }
        String remark = declaration.getRemark();
        if (remark != null && remark.startsWith(DECLARATION_POSTS_REMARK_PREFIX)) {
            try {
                Map<String, String> snapshot = objectMapper.readValue(remark.substring(DECLARATION_POSTS_REMARK_PREFIX.length()), new TypeReference<Map<String, String>>() {});
                if (snapshot != null && snapshot.get("__workflow") != null) posts.put("__workflow", snapshot.get("__workflow"));
            } catch (Exception ignored) { }
        }
        if (!posts.containsKey("leader")
                && declaration.getApplicant() != null
                && !declaration.getApplicant().isBlank()) {
            posts.put("leader", declaration.getApplicant());
        }
        return posts;
    }

    private String writePostsJson(Map<String, String> posts) {
        try { return objectMapper.writeValueAsString(posts == null ? Map.of() : posts); }
        catch (Exception e) { return "{}"; }
    }

    /** 新建/编辑申报时同步岗位人员；传入 null 表示本次不修改。 */
    private void savePosts(Long declarationId, Map<String, String> posts) {
        if (declarationId == null || posts == null) return;
        declarationPostMapper.delete(new LambdaQueryWrapper<ProjDeclarationPost>()
                .eq(ProjDeclarationPost::getDeclarationId, declarationId));
        int sort = 1;
        for (DeclarationRole role : DECLARATION_ROLES) {
            String label = posts.get(role.key());
            if (label == null || label.isBlank()) continue;
            PersonValue person = parsePerson(label);
            ProjDeclarationPost row = new ProjDeclarationPost();
            row.setDeclarationId(declarationId);
            row.setGroupCode(role.groupCode());
            row.setRoleKey(role.key());
            row.setRoleCode(role.roleCode());
            row.setRoleName(role.roleName());
            row.setUserName(person.name());
            row.setEmployeeNo(person.employeeNo());
            row.setSort(sort++);
            declarationPostMapper.insert(row);
        }
    }

    /** 备案生成项目时，把申报阶段选定人员原样传递给后续实施、验收、转化流程。 */
    private void syncProjectMembers(Long projectId, Map<String, String> posts) {
        if (projectId == null || posts == null) return;
        int sort = 1;
        for (DeclarationRole role : DECLARATION_ROLES) {
            String label = posts.get(role.key());
            if (label == null || label.isBlank()) continue;
            PersonValue person = parsePerson(label);
            ProjTeamMember member = new ProjTeamMember();
            member.setProjectId(projectId);
            member.setGroupCode(role.groupCode());
            member.setRoleCode(role.roleCode());
            member.setRoleName(role.roleName());
            member.setUserName(person.name());
            member.setEmployeeNo(person.employeeNo());
            member.setSort(sort++);
            teamMemberMapper.insert(member);
        }
    }

    private static PersonValue parsePerson(String label) {
        String raw = label == null ? "" : label.trim();
        Matcher matcher = PERSON_LABEL.matcher(raw);
        if (matcher.matches()) {
            return new PersonValue(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new PersonValue(raw, null);
    }

    private List<ProjMaterial> listMaterials(Long declarationId) {
        List<ProjMaterial> list = materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, "DECLARATION")
                .eq(ProjMaterial::getBizId, declarationId)
                .orderByAsc(ProjMaterial::getId));
        if (list.isEmpty()) {
            ProjDeclaration d = declarationMapper.selectById(declarationId);
            if (d != null) {
                initMaterials(declarationId, d.getChannelId());
                list = materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                        .eq(ProjMaterial::getBizType, "DECLARATION")
                        .eq(ProjMaterial::getBizId, declarationId)
                        .orderByAsc(ProjMaterial::getId));
            }
        }
        return list;
    }

    /** 按渠道生成材料栏：不在本渠道所需清单中的字段 locked=1 */
    private void initMaterials(Long declarationId, Long channelId) {
        List<String> need = new ArrayList<>();
        if (channelId != null) {
            ProjChannel ch = channelMapper.selectById(channelId);
            if (ch != null) {
                need.addAll(split(ch.getDeclareMaterial()));
                need.addAll(split(ch.getFilingMaterial()));
            }
        }
        for (String f : ALL_FIELDS) {
            boolean applicable = need.contains(f);
            ProjMaterial m = new ProjMaterial();
            m.setBizType("DECLARATION");
            m.setBizId(declarationId);
            m.setFieldCode("F_" + f);
            m.setFieldName(f);
            m.setRequired(applicable ? 1 : 0);
            m.setLocked(applicable ? 0 : 1);
            m.setVersion(1);
            materialMapper.insert(m);
        }
    }

    private List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String s : text.split("[,，、;；/|]")) {
            String v = s.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }
}
