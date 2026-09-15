package com.comac.rpm.modules.acceptance.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjAcceptance 实体（表 proj_acceptance）
 */
@Data
@TableName("proj_acceptance")
public class ProjAcceptance {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 验收层级 */
    private String acceptLevel;

    /** 状态 */
    private String status;

    /** 当前审批节点 */
    private String currentNode;

    /** 最近流程意见 */
    private String latestOpinion;

    /** 最近流程处理时间 */
    private LocalDateTime latestProcessAt;

    /** 申请时间 */
    private LocalDateTime applyAt;

    /** 办结时间 */
    private LocalDateTime finishAt;

    /** 验收结论 */
    private String conclusion;

    /** 是否需责任总师技术复核 */
    private Integer expertReview;

    /** 协作单位评价到期日 */
    private LocalDate partnerDueDate;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
