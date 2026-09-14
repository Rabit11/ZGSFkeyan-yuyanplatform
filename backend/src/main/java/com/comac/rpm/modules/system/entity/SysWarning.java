package com.comac.rpm.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预警消息
 */
@Data
@TableName("sys_warning")
public class SysWarning implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** MILESTONE/PLAN/ACCEPTANCE/FUND/TRANSFORM/POST_EVAL */
    private String bizType;
    private Long bizId;
    private Long projectId;
    private String projectName;
    /** YELLOW临期 / RED逾期 */
    private String warnLevel;
    private String title;
    private String content;
    /** 接收角色，逗号分隔 */
    private String receiver;
    /** 接收人工号，逗号分隔 */
    private String receiverNos;
    private Integer isRead;
    /** 已读人工号，逗号分隔 */
    private String readNos;
    private LocalDateTime createdAt;
}
