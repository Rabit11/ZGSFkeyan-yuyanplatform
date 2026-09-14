package com.comac.rpm.modules.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import com.comac.rpm.modules.dict.mapper.ProjChannelMapper;
import com.comac.rpm.modules.system.entity.SysDict;
import com.comac.rpm.modules.system.mapper.SysDictMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据字典接口
 */
@RestController
@RequestMapping("/api/dict")
public class DictController {

    @Autowired
    private SysDictMapper sysDictMapper;
    @Autowired
    private ProjChannelMapper projChannelMapper;

    /**
     * 按类型查询字典
     */
    @GetMapping("/{type}")
    public R<List<SysDict>> listByType(@PathVariable("type") String type) {
        List<SysDict> list = sysDictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSort)
                .orderByAsc(SysDict::getId));
        return R.ok(list);
    }

    /**
     * 渠道列表，可按 levelCode 过滤
     */
    @GetMapping("/channels")
    public R<List<ProjChannel>> channels(@RequestParam(value = "levelCode", required = false) String levelCode) {
        LambdaQueryWrapper<ProjChannel> w = new LambdaQueryWrapper<>();
        if (levelCode != null && !levelCode.isEmpty()) {
            w.eq(ProjChannel::getLevelCode, levelCode);
        }
        w.eq(ProjChannel::getStatus, 1).orderByAsc(ProjChannel::getId);
        return R.ok(projChannelMapper.selectList(w));
    }

    /**
     * 渠道详情：含 flowNodes 拆分成数组、declareMaterial、filingMaterial
     * 前端据此"按项目来源自适应展示附件栏"
     */
    @GetMapping("/channel/{id}")
    public R<ProjChannel> channelDetail(@PathVariable("id") Long id) {
        ProjChannel channel = projChannelMapper.selectById(id);
        if (channel == null) {
            throw new BusinessException("渠道不存在");
        }
        channel.setFlowNodeList(ColorUtil.splitMaterials(channel.getFlowNodes() == null
                ? "" : channel.getFlowNodes().replace("→", ",")));
        channel.setDeclareMaterialList(ColorUtil.splitMaterials(channel.getDeclareMaterial()));
        channel.setFilingMaterialList(ColorUtil.splitMaterials(channel.getFilingMaterial()));
        return R.ok(channel);
    }
}
