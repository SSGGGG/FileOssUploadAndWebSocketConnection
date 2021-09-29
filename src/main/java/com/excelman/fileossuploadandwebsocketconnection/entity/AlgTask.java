package com.excelman.fileossuploadandwebsocketconnection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * @author Excelman
 * @date 2021/9/27 下午7:03
 * @description
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_alg_task")
public class AlgTask implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String userName;

    private String apiName;

    private String moduleName;

    private String url;

    private String method;

    private String input;

    private String output;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @Version
    private int version;

    private long responseTime;


}