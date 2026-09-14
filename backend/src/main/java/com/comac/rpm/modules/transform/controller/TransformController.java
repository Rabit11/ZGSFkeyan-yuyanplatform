package com.comac.rpm.modules.transform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.*;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.system.mapper.SysDictMapper;
import com.comac.rpm.modules.system.entity.SysDict;
import com.comac.rpm.modules.transform.entity.*;
import com.comac.rpm.modules.transform.mapper.*;
import com.comac.rpm.modules.transform.TransformAccess;
import com.comac.rpm.modules.file.TransformFileService;
import com.comac.rpm.modules.file.TransformFilePolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

/** 成果包：绑定、填报、审核、备案均在同一事务内完成。 */
@RestController
@RequestMapping("/api/transforms")
public class TransformController {
    @Autowired private AchvTransformMapper transformMapper;
    @Autowired private AchvTransformItemMapper itemMapper;
    @Autowired private ProjDeliverableMapper deliverableMapper;
    @Autowired private ProjInfoMapper projectMapper;
    @Autowired private SysDictMapper dictMapper;
    @Autowired private FlowAuditGuard flowAuditGuard;
    @Autowired private TransformAccess access;
    @Autowired private TransformFileService transformFiles;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @GetMapping("/permissions")
    public R<Map<String,Object>> permissions(@RequestParam("projectId") Long projectId) {
        readable(projectId);
        return R.ok(Map.of("allowedActions",allowedActions(projectId)));
    }

    @GetMapping
    public R<PageVO<AchvTransform>> page(
            @RequestParam(value="page", defaultValue="1") long page,
            @RequestParam(value="size", defaultValue="10") long size,
            @RequestParam(value="projectId", required=false) Long projectId,
            @RequestParam(value="status", required=false) String status,
            @RequestParam(value="transformWay", required=false) String transformWay,
            @RequestParam(value="keyword", required=false) String keyword,
            @RequestParam(value="dutyOrg", required=false) String dutyOrg,
            @RequestParam(value="workflowStatus", required=false) String workflowStatus) {
        var w = new LambdaQueryWrapper<AchvTransform>();
        var visible = visibleProjectIds();
        if (visible != null) w.in(AchvTransform::getProjectId, visible.isEmpty() ? List.of(-1L) : visible);
        w.eq(projectId != null, AchvTransform::getProjectId, projectId)
         .apply(has(status), "COALESCE(reported_status, status) = {0}", status)
         .eq(has(transformWay), AchvTransform::getTransformWay, transformWay)
         .eq(has(dutyOrg), AchvTransform::getDutyOrg, dutyOrg)
         .eq(has(workflowStatus), AchvTransform::getWorkflowStatus, workflowStatus);
        if (has(keyword)) w.and(q -> q.like(AchvTransform::getName,keyword)
                .or().like(AchvTransform::getAchievementNo,keyword).or().like(AchvTransform::getProjectNo,keyword));
        w.orderByDesc(AchvTransform::getId);
        var result = transformMapper.selectPage(new Page<>(Math.max(1,page),Math.min(200,Math.max(1,size))),w);
        result.getRecords().forEach(this::enrich);
        return R.ok(PageVO.of(result));
    }

    @GetMapping("/{id}")
    public R<AchvTransform> detail(@PathVariable("id") Long id) {
        var t = find(id, false);
        enrich(t);
        t.setDeliverables(bound(t));
        t.setDeliverableIds(t.getDeliverables().stream().map(ProjDeliverable::getId).toList());
        t.setItemCount(t.getDeliverables().size());
        return R.ok(t);
    }

    @PostMapping
    @Transactional
    public R<Long> create(@RequestBody AchvTransform body) {
        if (body.getProjectId()==null) throw new BusinessException("请选择所属项目");
        readable(body.getProjectId());
        require(body.getProjectId(), "fill");
        var p = projectMapper.selectById(body.getProjectId());
        if (p==null) throw new BusinessException("项目不存在");
        var t = new AchvTransform();
        t.setProjectId(p.getId()); t.setProjectNo(p.getProjectNo());
        t.setAchievementNo("CG"+LocalDate.now().getYear()+UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        t.setWorkflowStatus("DRAFT"); t.setRevision(0L); t.setItemCount(0); t.setHistoryJson("[]"); t.setLegacyRecord(false);
        copyFields(body,t);
        validate(t);
        prepareProgress(t);
        transformMapper.insert(t);
        t.setStatus(t.getReportedStatus());
        t.setActualDate(t.getReportedActualDate());
        replaceBindings(t,body.getDeliverableIds());
        event(t,"CREATE","");
        prepareProgress(t);
        transformMapper.updateById(t);
        return R.ok(t.getId());
    }

    @PutMapping("/{id}")
    @Transactional
    public R<Boolean> update(@PathVariable("id") Long id,@RequestBody AchvTransform body) {
        var t = find(id,true);
        require(t.getProjectId(),"fill");
        checkRevision(t,body.getRevision());
        editable(t);
        copyFields(body,t);
        validate(t);
        if (body.getDeliverableIds()!=null) replaceBindings(t,body.getDeliverableIds());
        if ("DONE".equals(t.getStatus()) && bound(t).isEmpty()) throw new BusinessException("完成转化必须绑定交付物");
        event(t,"UPDATE","");
        persist(t);
        return R.ok(true);
    }

    @PostMapping("/{id}/bind")
    @Transactional
    public R<Boolean> bind(@PathVariable("id") Long id,@RequestBody AchvTransform body) {
        var t=find(id,true); require(t.getProjectId(),"fill"); checkRevision(t,body.getRevision()); editable(t);
        replaceBindings(t,body.getDeliverableIds());
        event(t,"BIND",""); persist(t); return R.ok(true);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public R<Boolean> delete(@PathVariable("id") Long id,@RequestParam("revision") Long revision) {
        var t=find(id,true); require(t.getProjectId(),"fill"); checkRevision(t,revision);
        if (Boolean.TRUE.equals(t.getLegacyRecord()) || !"DRAFT".equals(t.getWorkflowStatus()) || parseList(t.getHistoryJson()).stream().anyMatch(e -> "SUBMIT".equals(e.get("action")))) throw new BusinessException("仅从未提交过的新草稿可删除，历史记录与办理履历必须保留");
        clearBindings(t); transformMapper.deleteById(id); return R.ok(true);
    }

    @PostMapping("/{id}/workflow")
    @Transactional
    public R<Boolean> workflow(@PathVariable("id") Long id,@RequestBody Map<String,Object> body) {
        var t=find(id,true);
        checkRevision(t,body.get("revision") instanceof Number n ? n.longValue() : null);
        String action=String.valueOf(body.get("action"));
        String note=Objects.toString(body.get("note"),"").trim();
        if (note.length()>1000) throw new BusinessException("办理意见不能超过1000字");
        String state=t.getWorkflowStatus();
        switch(action) {
            case "SUBMIT" -> {
                require(t.getProjectId(),"submit"); editable(t); validate(t);
                if (bound(t).isEmpty()) throw new BusinessException("请先绑定已交付交付物");
                if (bound(t).stream().anyMatch(d -> !"DELIVERED".equals(d.getStatus()))) throw new BusinessException("存在未交付成果，请重新核对");
                t.setWorkflowStatus("UNIT_REVIEW");
            }
            case "APPROVE", "REJECT" -> {
                require(t.getProjectId(),"audit");
                if (!"UNIT_REVIEW".equals(state)) throw new BusinessException("当前不在二级单位审核节点");
                if ("REJECT".equals(action) && note.isBlank()) throw new BusinessException("请填写退回原因");
                t.setWorkflowStatus("APPROVE".equals(action) ? "HQ_RECORD" : "RETURNED");
            }
            case "RECORD" -> {
                require(t.getProjectId(),"record");
                if (!"HQ_RECORD".equals(state)) throw new BusinessException("请先完成二级单位审核");
                validate(t);
                t.setWorkflowStatus("RECORDED");
            }
            case "REOPEN" -> {
                require(t.getProjectId(),"fill");
                if (!"RECORDED".equals(state)) throw new BusinessException("仅已备案成果包可更新进展");
                t.setWorkflowStatus("DRAFT");
            }
            default -> throw new BusinessException("不支持的办理动作");
        }
        event(t,action,note); persist(t); return R.ok(true);
    }

    private void copyFields(AchvTransform b,AchvTransform t) {
        // 白名单：编号、所属项目、审核记录、版本、数量不能由客户端覆盖。
        t.setName(b.getName()); t.setIntro(b.getIntro()); t.setTransformWay(b.getTransformWay());
        t.setTransformForm(b.getTransformForm()); t.setPlanDate(b.getPlanDate()); t.setActualDate(b.getActualDate());
        t.setStatus(b.getStatus()==null ? "NOT_STARTED" : b.getStatus());
        t.setIntroDetail(b.getIntroDetail()); t.setDutyOrg(b.getDutyOrg());
        t.setEvidenceJson(b.getEvidenceJson()==null ? "[]" : b.getEvidenceJson());
    }

    private void validate(AchvTransform t) {
        if (!has(t.getName()) || t.getName().length()>255) throw new BusinessException("成果名称必填且不超过255字");
        if (t.getIntro()!=null && t.getIntro().length()>100) throw new BusinessException("成果简介不能超过100字");
        if (!has(t.getDutyOrg()) || t.getDutyOrg().length()>128 || t.getPlanDate()==null) throw new BusinessException("请填写计划转化时间和责任单位（不超过128字）");
        if (!has(t.getIntroDetail()) || t.getIntroDetail().length()>2000) throw new BusinessException("请填写转化简介（不超过2000字）");
        dictionary("TRANSFORM_WAY",t.getTransformWay(),null);
        dictionary("TRANSFORM_FORM",t.getTransformForm(),t.getTransformWay());
        dictionary("TRANSFORM_STATUS",t.getStatus(),null);
        var files=parseList(t.getEvidenceJson());
        if (files.size()>20) throw new BusinessException("最多上传20份佐证材料");
        for (var f:files) {
            String url=Objects.toString(f.get("fileUrl"),"");
            if (!has(Objects.toString(f.get("fileName"),"")) || !url.startsWith(TransformFilePolicy.URL_PREFIX))
                throw new BusinessException("佐证材料必须使用平台上传的附件");
            transformFiles.validateEvidence(t.getProjectId(),url);
        }
        if ("DONE".equals(t.getStatus()) && (t.getActualDate()==null || files.isEmpty())) throw new BusinessException("完成转化必须填写实际日期并上传成效佐证");
        if (t.getActualDate()!=null && t.getActualDate().isAfter(LocalDate.now())) throw new BusinessException("实际转化时间不能晚于今天");
    }

    private void dictionary(String type,String code,String parent) {
        var list=dictMapper.selectList(new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictType,type).eq(SysDict::getDictCode,code).eq(SysDict::getStatus,1));
        if (list.isEmpty() || (parent!=null && !parent.equals(list.get(0).getParentCode()))) throw new BusinessException("转化方式、形式或状态不符合当前数据字典");
    }

    private void replaceBindings(AchvTransform t,List<Long> ids) {
        if (ids==null || ids.isEmpty()) throw new BusinessException("至少选择一项已交付交付物");
        if (ids.stream().anyMatch(Objects::isNull)) throw new BusinessException("交付物编号无效");
        var unique=new TreeSet<>(ids);
        if (unique.size()!=ids.size()) throw new BusinessException("交付物不能重复选择");
        // 同一项目的绑定操作串行化，避免两个成果包同时抢占交付物。
        jdbc.queryForList("SELECT id FROM proj_info WHERE id=? FOR UPDATE",t.getProjectId());
        for (Long id:unique) {
            var d=deliverableMapper.selectOne(new LambdaQueryWrapper<ProjDeliverable>().eq(ProjDeliverable::getId,id).last("FOR UPDATE"));
            if (d==null || !Objects.equals(d.getProjectId(),t.getProjectId())) throw new BusinessException("只能绑定本项目交付物");
            if (!"DELIVERED".equals(d.getStatus())) throw new BusinessException("仅已交付交付物可纳入成果包");
            if (has(d.getAchievementNo()) && !d.getAchievementNo().equals(t.getAchievementNo())) throw new BusinessException("交付物已被其他成果包占用");
            if (itemMapper.selectCount(new LambdaQueryWrapper<AchvTransformItem>().eq(AchvTransformItem::getDeliverableId,id).ne(AchvTransformItem::getTransformId,t.getId()))>0)
                throw new BusinessException("交付物已被其他成果包绑定，请先核对历史数据");
        }
        clearBindings(t);
        for (Long id:unique) {
            var item=new AchvTransformItem(); item.setTransformId(t.getId()); item.setDeliverableId(id); itemMapper.insert(item);
            deliverableMapper.update(null,new LambdaUpdateWrapper<ProjDeliverable>().eq(ProjDeliverable::getId,id).set(ProjDeliverable::getAchievementNo,t.getAchievementNo()));
        }
        t.setItemCount(unique.size());
    }

    private void clearBindings(AchvTransform t) {
        deliverableMapper.update(null,new LambdaUpdateWrapper<ProjDeliverable>().eq(ProjDeliverable::getAchievementNo,t.getAchievementNo()).set(ProjDeliverable::getAchievementNo,null));
        itemMapper.delete(new LambdaQueryWrapper<AchvTransformItem>().eq(AchvTransformItem::getTransformId,t.getId()));
    }
    private List<ProjDeliverable> bound(AchvTransform t) {
        return deliverableMapper.selectList(new LambdaQueryWrapper<ProjDeliverable>().eq(ProjDeliverable::getAchievementNo,t.getAchievementNo()).eq(ProjDeliverable::getProjectId,t.getProjectId()));
    }
    private AchvTransform find(Long id,boolean lock) {
        var w=new LambdaQueryWrapper<AchvTransform>().eq(AchvTransform::getId,id);
        if (lock) w.last("FOR UPDATE");
        var t=transformMapper.selectOne(w);
        if (t==null) throw new BusinessException("成果包不存在");
        readable(t.getProjectId());
        captureConfirmed(t);
        if (has(t.getReportedStatus())) { t.setStatus(t.getReportedStatus()); t.setActualDate(t.getReportedActualDate()); }
        return t;
    }
    private void checkRevision(AchvTransform t,Long revision) {
        if (revision==null || !revision.equals(t.getRevision())) throw new BusinessException(409,"数据已更新，请关闭并重新打开成果包后再办理");
    }
    private void editable(AchvTransform t) {
        if (!List.of("DRAFT","RETURNED").contains(t.getWorkflowStatus())) throw new BusinessException("审核或备案中的成果包不能修改；已备案请先发起进展更新");
    }
    private void persist(AchvTransform t) {
        t.setRevision(t.getRevision()+1);
        prepareProgress(t);
        transformMapper.updateById(t);
        // updateById 的默认 NOT_NULL 策略不能清空日期，显式处理撤销实际日期。
        if (t.getReportedActualDate()==null) transformMapper.update(null,new LambdaUpdateWrapper<AchvTransform>().eq(AchvTransform::getId,t.getId()).set(AchvTransform::getReportedActualDate,null));
        if (t.getActualDate()==null) transformMapper.update(null,new LambdaUpdateWrapper<AchvTransform>().eq(AchvTransform::getId,t.getId()).set(AchvTransform::getActualDate,null));
    }
    private void prepareProgress(AchvTransform t) {
        t.setReportedStatus(t.getStatus());
        t.setReportedActualDate(t.getActualDate());
        if (!"RECORDED".equals(t.getWorkflowStatus())) {
            if (has(t.getConfirmedStatus())) {
                // 历史正式值或上次备案结果必须保留，不能被本轮草稿/退回覆盖。
                t.setStatus(t.getConfirmedStatus()); t.setActualDate(t.getConfirmedActualDate());
            } else {
                t.setActualDate(null);
                if ("DONE".equals(t.getStatus())) t.setStatus("SIGNED");
            }
        }
        t.setColorStatus(ColorUtil.calcCode(t.getPlanDate(),"DONE".equals(t.getStatus())));
    }
    private void captureConfirmed(AchvTransform t) {
        if (!has(t.getConfirmedStatus())) {t.setConfirmedStatus(t.getStatus());t.setConfirmedActualDate(t.getActualDate());}
    }
    private void enrich(AchvTransform t) {
        captureConfirmed(t);
        if (has(t.getReportedStatus())) { t.setStatus(t.getReportedStatus()); t.setActualDate(t.getReportedActualDate()); }
        t.setColorStatus(ColorUtil.calcCode(t.getPlanDate(),"DONE".equals(t.getConfirmedStatus())));
        var p=t.getProjectId()==null?null:projectMapper.selectById(t.getProjectId());
        boolean orphan=p==null || Integer.valueOf(1).equals(p.getDeleted());
        t.setOrphanedProject(orphan);
        if(p!=null) t.setProjectName(p.getName());
        t.setAllowedActions(orphan?List.of():allowedActions(t.getProjectId()));
    }
    private List<String> allowedActions(Long projectId) {
        var actions=new ArrayList<String>();
        for(String a:List.of("fill","submit","audit","record")) { try {require(projectId,a);actions.add(a);} catch(BusinessException ignored) {} }
        return actions;
    }
    private void require(Long projectId,String action) {
        access.requireAction(projectId,action);
    }
    private List<Long> visibleProjectIds() {
        return access.visibleProjectIds();
    }
    private void readable(Long projectId) {
        access.requireReadable(projectId);
    }
    private List<Map<String,Object>> parseList(String value) {
        try { var result = json.readValue(value==null?"[]":value,new TypeReference<List<Map<String,Object>>>(){}); if (result==null || result.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException(); return result; }
        catch(Exception e) {throw new BusinessException("材料或履历格式无效");}
    }
    private void event(AchvTransform t,String action,String note) {
        var events=new ArrayList<>(parseList(t.getHistoryJson()));
        var u=flowAuditGuard.currentUser(); var e=new LinkedHashMap<String,Object>();
        e.put("action",action);e.put("note",note);e.put("actor",u.getRealName());e.put("actorId",u.getId());e.put("at",LocalDateTime.now().toString());e.put("workflowStatus",t.getWorkflowStatus());
        var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("name",Objects.toString(t.getName(),""));snapshot.put("status",t.getStatus());
        snapshot.put("intro",Objects.toString(t.getIntro(),""));snapshot.put("introDetail",Objects.toString(t.getIntroDetail(),""));
        snapshot.put("transformWay",Objects.toString(t.getTransformWay(),""));snapshot.put("transformForm",Objects.toString(t.getTransformForm(),""));
        snapshot.put("dutyOrg",Objects.toString(t.getDutyOrg(),""));snapshot.put("planDate",Objects.toString(t.getPlanDate(),""));
        snapshot.put("actualDate",Objects.toString(t.getActualDate(),""));snapshot.put("evidence",parseList(t.getEvidenceJson()));
        snapshot.put("deliverableIds",bound(t).stream().map(ProjDeliverable::getId).toList());
        e.put("snapshot",snapshot);
        events.add(e);
        try {t.setHistoryJson(json.writeValueAsString(events));} catch(Exception ex) {throw new BusinessException("履历保存失败");}
    }
    private static boolean has(String v) {return v!=null && !v.isBlank();}
}
