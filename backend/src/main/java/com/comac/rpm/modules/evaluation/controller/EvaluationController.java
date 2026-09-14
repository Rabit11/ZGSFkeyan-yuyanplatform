package com.comac.rpm.modules.evaluation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.declaration.entity.ProjMaterial;
import com.comac.rpm.modules.declaration.mapper.ProjMaterialMapper;
import com.comac.rpm.modules.evaluation.entity.ProjEvaluation;
import com.comac.rpm.modules.evaluation.mapper.ProjEvaluationMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评估检查：按渠道提报中期/季度/年度/阶段/督导材料，上传评审结论与佐证。
 * <p>
 * 审批流（表5-15）：项目团队填报 → 二级单位主管部门初审 → 总部对应管理部门终审；
 * 部分公司级渠道（如大飞机先进材料创新联盟）二级单位审查确认即终审。
 * 评估不合格（FAIL）须启动整改，整改完成并复审通过后方可归档（DONE）。
 */
@RestController
@RequestMapping("/api")
public class EvaluationController {

    @Autowired
    private ProjEvaluationMapper evaluationMapper;
    @Autowired
    private ProjMaterialMapper materialMapper;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    private static final String MATERIAL_BIZ = "EVALUATION";

    @GetMapping("/projects/{projectId}/evaluations")
    public R<List<ProjEvaluation>> list(@PathVariable("projectId") Long projectId) {
        return R.ok(evaluationMapper.selectList(
                new LambdaQueryWrapper<ProjEvaluation>().eq(ProjEvaluation::getProjectId, projectId)
                        .orderByDesc(ProjEvaluation::getDueDate)));
    }

    @PostMapping("/evaluations")
    public R<Long> create(@RequestBody ProjEvaluation body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增评估检查", "owner", "projectPm", "contactLogin");
        body.setId(null);
        body.setStatus("DRAFT");
        body.setRectifyDone(0);
        body.setSubmitBy(null);
        body.setAuditBy(null);
        body.setAuditAt(null);
        fillChannel(body);
        evaluationMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/evaluations/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjEvaluation body) {
        ProjEvaluation existing = writable(id, "修改评估检查");
        if (!isTeamEditable(existing.getStatus())) {
            throw new BusinessException("当前状态不可编辑：仅草稿/已驳回/整改中可修改");
        }
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        // 审批状态与流程字段仅由流程接口维护，防止前端覆盖
        body.setStatus(existing.getStatus());
        body.setRectifyDone(existing.getRectifyDone());
        body.setSubmitBy(existing.getSubmitBy());
        body.setAuditBy(existing.getAuditBy());
        body.setAuditAt(existing.getAuditAt());
        body.setChannelCode(existing.getChannelCode());
        evaluationMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/evaluations/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        ProjEvaluation existing = writable(id, "删除评估检查");
        if (!"DRAFT".equals(existing.getStatus()) && !"REJECTED".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿或已驳回的评估检查可删除");
        }
        evaluationMapper.deleteById(id);
        materialMapper.delete(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, MATERIAL_BIZ)
                .eq(ProjMaterial::getBizId, id));
        return R.ok(true);
    }

    /** 提交：项目团队负责人提交，进入二级单位主管部门初审。 */
    @PostMapping("/evaluations/{id}/submit")
    public R<Boolean> submit(@PathVariable("id") Long id) {
        ProjEvaluation eva = require(id);
        if (!isTeamEditable(eva.getStatus())) {
            throw new BusinessException("当前状态不可提交");
        }
        flowAuditGuard.requireActors(eva.getProjectId(), "提交评估检查", "owner");
        if (eva.getName() == null || eva.getName().isBlank()) {
            throw new BusinessException("请填写评估名称");
        }
        if (eva.getResult() == null || eva.getResult().isBlank()) {
            throw new BusinessException("请填写评审结论（合格/不合格）");
        }
        SysUser user = flowAuditGuard.currentUser();
        ProjEvaluation up = new ProjEvaluation();
        up.setId(id);
        up.setStatus("SUBMITTED");
        up.setSubmitBy(user.getRealName());
        evaluationMapper.updateById(up);
        return R.ok(true);
    }

    /**
     * 审核：按当前状态判断节点。
     * SUBMITTED → 二级单位主管部门初审；UNIT_OK → 总部对应管理部门终审。
     * 通过后若为不合格且未整改完成，则转入整改（RECTIFYING）；否则归档（DONE）或进入终审。
     */
    @PostMapping("/evaluations/{id}/audit")
    public R<Boolean> audit(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjEvaluation eva = require(id);
        boolean pass = body == null || !Boolean.FALSE.equals(body.get("pass"));
        String status = eva.getStatus() == null ? "" : eva.getStatus();
        SysUser user = flowAuditGuard.currentUser();
        ProjEvaluation up = new ProjEvaluation();
        up.setId(id);
        up.setAuditBy(user.getRealName());
        up.setAuditAt(LocalDateTime.now());
        if ("SUBMITTED".equals(status)) {
            flowAuditGuard.requireActors(eva.getProjectId(), "评估检查二级单位初审", "unitHead");
            if (!pass) {
                up.setStatus("REJECTED");
            } else if (needsHqFinal(eva)) {
                up.setStatus("UNIT_OK");
            } else {
                up.setStatus(nextAfterFinalPass(eva));
            }
        } else if ("UNIT_OK".equals(status)) {
            flowAuditGuard.requireActors(eva.getProjectId(), "评估检查总部终审", "hqHead", "hqStaff");
            up.setStatus(pass ? nextAfterFinalPass(eva) : "REJECTED");
        } else {
            throw new BusinessException("当前状态不可审核：" + status);
        }
        evaluationMapper.updateById(up);
        return R.ok(true);
    }

    /** 整改：不合格评估进入整改后，由项目团队填报整改说明并标记完成，重新进入审批复核。 */
    @PostMapping("/evaluations/{id}/rectify")
    public R<Boolean> rectify(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        ProjEvaluation eva = require(id);
        if (!"RECTIFYING".equals(eva.getStatus())) {
            throw new BusinessException("仅整改中的评估检查可提交整改");
        }
        flowAuditGuard.requireActors(eva.getProjectId(), "提交评估整改", "owner", "projectPm", "contactLogin");
        String note = body == null ? null : String.valueOf(body.getOrDefault("rectifyNote", "")).trim();
        if (note == null || note.isEmpty()) {
            throw new BusinessException("请填写整改说明");
        }
        SysUser user = flowAuditGuard.currentUser();
        ProjEvaluation up = new ProjEvaluation();
        up.setId(id);
        up.setRectifyNote(note);
        up.setRectifyDone(1);
        up.setStatus("SUBMITTED");
        up.setSubmitBy(user.getRealName());
        evaluationMapper.updateById(up);
        return R.ok(true);
    }

    @GetMapping("/evaluations/{id}/materials")
    public R<List<ProjMaterial>> materials(@PathVariable("id") Long id) {
        return R.ok(listMaterials(id));
    }

    /** 上传佐证材料 / 评审结论文件（复用 proj_material，biz_type=EVALUATION）。 */
    @PostMapping("/evaluations/{id}/materials")
    public R<Long> uploadMaterial(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        ProjEvaluation eva = require(id);
        flowAuditGuard.requireActors(eva.getProjectId(), "上传评估佐证材料",
                "owner", "projectPm", "contactLogin");
        String fileName = body.get("fileName") == null ? "" : String.valueOf(body.get("fileName")).trim();
        String fileUrl = body.get("fileUrl") == null ? "" : String.valueOf(body.get("fileUrl")).trim();
        if (fileName.isEmpty() || fileUrl.isEmpty()) {
            throw new BusinessException("请先上传文件");
        }
        SysUser user = flowAuditGuard.currentUser();
        ProjMaterial m = new ProjMaterial();
        m.setBizType(MATERIAL_BIZ);
        m.setBizId(id);
        m.setFieldCode(body.get("fieldCode") == null ? "evidence" : String.valueOf(body.get("fieldCode")));
        m.setFieldName(body.get("fieldName") == null ? "评估佐证材料" : String.valueOf(body.get("fieldName")));
        m.setFileName(fileName);
        m.setFileUrl(fileUrl);
        m.setVersion(1);
        m.setRequired(0);
        m.setLocked(0);
        m.setUploadedBy(user.getRealName());
        m.setUploadedAt(LocalDateTime.now());
        materialMapper.insert(m);
        return R.ok(m.getId());
    }

    @DeleteMapping("/evaluations/{id}/materials/{materialId}")
    public R<Boolean> deleteMaterial(@PathVariable("id") Long id, @PathVariable("materialId") Long materialId) {
        ProjEvaluation eva = require(id);
        flowAuditGuard.requireActors(eva.getProjectId(), "删除评估佐证材料",
                "owner", "projectPm", "contactLogin");
        ProjMaterial m = materialMapper.selectById(materialId);
        if (m != null && MATERIAL_BIZ.equals(m.getBizType()) && id.equals(m.getBizId())) {
            materialMapper.deleteById(materialId);
        }
        return R.ok(true);
    }

    /* ==================== 内部方法 ==================== */

    private List<ProjMaterial> listMaterials(Long evaluationId) {
        return materialMapper.selectList(new LambdaQueryWrapper<ProjMaterial>()
                .eq(ProjMaterial::getBizType, MATERIAL_BIZ)
                .eq(ProjMaterial::getBizId, evaluationId)
                .orderByAsc(ProjMaterial::getId));
    }

    /** 终审通过后的落点：不合格且未整改完成 → 整改中；否则归档完成。 */
    private String nextAfterFinalPass(ProjEvaluation eva) {
        boolean fail = "FAIL".equals(eva.getResult());
        boolean rectified = Integer.valueOf(1).equals(eva.getRectifyDone());
        if (fail && !rectified) {
            return "RECTIFYING";
        }
        return "DONE";
    }

    /**
     * 是否需要总部终审：默认需要；公司级"先进材料创新联盟"由二级单位审查确认即终审。
     */
    private boolean needsHqFinal(ProjEvaluation eva) {
        String channel = eva.getChannelCode() == null ? "" : eva.getChannelCode();
        return !(channel.contains("材料创新联盟") || channel.contains("先进材料"));
    }

    private boolean isTeamEditable(String status) {
        return status == null || "DRAFT".equals(status) || "REJECTED".equals(status) || "RECTIFYING".equals(status);
    }

    private void fillChannel(ProjEvaluation body) {
        if (body.getChannelCode() != null && !body.getChannelCode().isBlank()) {
            return;
        }
        if (body.getProjectId() != null) {
            ProjInfo project = projectMapper.selectById(body.getProjectId());
            if (project != null) {
                body.setChannelCode(project.getChannelName());
            }
        }
    }

    private ProjEvaluation require(Long id) {
        ProjEvaluation existing = evaluationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("评估检查不存在");
        }
        return existing;
    }

    private ProjEvaluation writable(Long id, String action) {
        flowAuditGuard.requireIdentities(action, "owner", "projectPm", "contactLogin");
        ProjEvaluation existing = require(id);
        flowAuditGuard.requireActors(existing.getProjectId(), action, "owner", "projectPm", "contactLogin");
        return existing;
    }
}
