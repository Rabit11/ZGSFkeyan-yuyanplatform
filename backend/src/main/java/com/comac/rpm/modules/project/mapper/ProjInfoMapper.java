package com.comac.rpm.modules.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ProjInfo Mapper
 */
@Mapper
public interface ProjInfoMapper extends BaseMapper<ProjInfo> {
    @Select("SELECT MAX(project_no) FROM proj_info WHERE project_no REGEXP CONCAT('^', #{prefix}, '[0-9]{6}$')")
    String selectMaxProjectNoByPrefix(@Param("prefix") String prefix);
}
