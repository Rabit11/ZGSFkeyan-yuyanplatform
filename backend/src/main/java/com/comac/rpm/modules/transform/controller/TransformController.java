package com.comac.rpm.modules.transform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.common.util.SeqUtil;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.transform.entity.AchvTransform;
import com.comac.rpm.modules.transform.entity.AchvTransformItem;
import com.comac.rpm.modules.transform.mapper.AchvTransformItemMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 成果转化：以成果包为最小管理单元，与交付物通过成果编号双向绑定
 */
@RestController
@RequestMapping("/api/transforms")
public class TransformController {

    @Autowired
    private AchvTransformMapper transformMapper;
    @Autowired
    private AchvTransformItemMapper itemMapper;
    @Autowired
    private ProjDeliverableMapper deliverableMapper;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping
    public R<PageVO<AchvTransform>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "transformWay", required = false) String transformWay,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "dutyOrg", required = false) String dutyOrg) {
        LambdaQueryWrapper<AchvTransform> w = new LambdaQueryWrapper<>();
        if (projectId != null) {
            w.eq(AchvTransform::getProjectId, projectId);
        }
        if (status != null && !status.isEmpty()) {
            w.eq(AchvTransform::getStatus, status);
        }
        if (transformWay != null && !transformWay.isEmpty()) {
            w.eq(AchvTransform::getTransformWay, transformWay);
        }
        if (dutyOrg != null && !dutyOrg.isEmpty()) {
            w.eq(AchvTransform::getDutyOrg, dutyOrg);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(AchvTransform::getName, keyword)
                    .or().like(AchvTransform::getAchievementNo, keyword)
                    .or().like(AchvTransform::getProjectNo, keyword));
        }
        w.orderByDesc(AchvTransform::getId);
        Page<AchvTransform> p = transformMapper.selectPage(new Page<>(page, size), w);
        java.util.Map<Long, ProjInfo> cache = new java.util.HashMap<>();
        for (AchvTransform t : p.getRecords()) {
            t.setColorStatus(ColorUtil.calcCode(t.getPlanDate(), "DONE".equals(t.getStatus())));
            if (t.getProjectId() != null) {
                ProjInfo proj = cache.computeIfAbsent(t.getProjectId(), projectMapper::selectById);
                if (proj != null) {
                    t.setProjectName(proj.getName());
                    if (t.getProjectNo() == null) {
                        t.setProjectNo(proj.getProjectNo());
                    }
                }
            }
        }
        return R.ok(PageVO.of(p));
    }

    @GetMapping("/{id}")
    public R<AchvTransform> detail(@PathVariable("id") Long id) {
        AchvTransform t = transformMapper.selectById(id);
        if (t == null) {
            return R.fail("成果包不存在");
        }
        List<AchvTransformItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<AchvTransformItem>().eq(AchvTransformItem::getTransformId, id));
        List<ProjDeliverable> dvs = new ArrayList<>();
        for (AchvTransformItem it : items) {
            ProjDeliverable d = deliverableMapper.selectById(it.getDeliverableId());
            if (d != null) {
                dvs.add(d);
            }
        }
        t.setItemCount(dvs.size());
        return R.ok(t);
    }

    @PostMapping
    public R<Long> create(@RequestBody AchvTransform body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增成果转化", "owner", "contactLogin");
        body.setId(null);
        body.setAchievementNo(SeqUtil.achievementNo(transformMapper.selectCount(null) + 1));
        if (body.getStatus() == null) {
            body.setStatus("NOT_STARTED");
        }
        if (body.getItemCount() == null) {
            body.setItemCount(0);
        }
        body.setColorStatus(ColorUtil.calcCode(body.getPlanDate(), false));
        if (body.getProjectId() != null) {
            ProjInfo p = projectMapper.selectById(body.getProjectId());
            if (p != null) {
                body.setProjectNo(p.getProjectNo());
            }
        }
        transformMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody AchvTransform body) {
        AchvTransform existing = writable(id, "修改成果转化");
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        body.setColorStatus(ColorUtil.calcCode(body.getPlanDate(), "DONE".equals(body.getStatus())));
        transformMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        writable(id, "删除成果转化");
        transformMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<AchvTransformItem>().eq(AchvTransformItem::getTransformId, id));
        return R.ok(true);
    }

    /**
     * 绑定交付物：仅「已交付」的交付物可纳入成果包；更新 itemCount 并回写成果编号
     */
    @PostMapping("/{id}/bind")
    public R<Boolean> bind(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        AchvTransform t = writable(id, "绑定成果转化交付物");
        Object idsObj = body.get("deliverableIds");
        if (idsObj == null) {
            throw new BusinessException("请选择要绑定的交付物");
        }
        List<?> rawList;
        if (idsObj instanceof List<?>) {
            rawList = (List<?>) idsObj;
        } else {
            rawList = java.util.Collections.singletonList(idsObj);
        }
        itemMapper.delete(new LambdaQueryWrapper<AchvTransformItem>().eq(AchvTransformItem::getTransformId, id));
        int count = 0;
        for (Object o : rawList) {
            Long dvId = Long.valueOf(String.valueOf(o));
            ProjDeliverable d = deliverableMapper.selectById(dvId);
            if (d == null) {
                continue;
            }
            if (!"DELIVERED".equals(d.getStatus())) {
                throw new BusinessException("仅状态为「已交付」的交付物可纳入成果转化包：" + d.getName());
            }
            AchvTransformItem it = new AchvTransformItem();
            it.setTransformId(id);
            it.setDeliverableId(dvId);
            itemMapper.insert(it);
            // 回写成果编号，实现从交付物溯源转化进展
            deliverableMapper.update(null, new LambdaUpdateWrapper<ProjDeliverable>()
                    .eq(ProjDeliverable::getId, dvId)
                    .set(ProjDeliverable::getAchievementNo, t.getAchievementNo()));
            count++;
        }
        transformMapper.update(null, new LambdaUpdateWrapper<AchvTransform>()
                .eq(AchvTransform::getId, id)
                .set(AchvTransform::getItemCount, count));
        return R.ok(true);
    }

    private AchvTransform writable(Long id, String action) {
        flowAuditGuard.requireIdentities(action, "owner", "contactLogin");
        AchvTransform existing = transformMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("成果包不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), action, "owner", "contactLogin");
        return existing;
    }
}
