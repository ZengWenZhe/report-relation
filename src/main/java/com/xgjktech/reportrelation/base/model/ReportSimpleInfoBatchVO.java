package com.xgjktech.reportrelation.base.model;

import java.sql.Timestamp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 汇报简易信息VO（批量查询返回）
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Data
public class ReportSimpleInfoBatchVO {

    @ApiModelProperty("汇报ID")
    private Long id;

    @ApiModelProperty("汇报名称（主题）")
    private String main;

    @ApiModelProperty("汇报时间")
    private Timestamp createTime;

    @ApiModelProperty("汇报人ID")
    private Long writeEmpId;

    @ApiModelProperty("汇报人姓名")
    private String writeEmpName;
}
