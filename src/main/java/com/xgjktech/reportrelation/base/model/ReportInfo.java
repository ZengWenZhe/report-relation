package com.xgjktech.reportrelation.base.model;

import java.sql.Timestamp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 汇报基本信息VO（对应 work-report 的 getReportInfoByIds 返回）
 */
@Data
public class ReportInfo {

    @ApiModelProperty("汇报ID")
    private Long recordId;

    @ApiModelProperty("汇报标题")
    private String main;

    @ApiModelProperty("任务ID")
    private Long planId;

    @ApiModelProperty("汇报人ID")
    private Long writeEmpId;

    @ApiModelProperty("汇报人姓名")
    private String writeEmpName;

    @ApiModelProperty("汇报时间")
    private Timestamp reportTime;

    @ApiModelProperty("工作汇报类型: 1-工作交流、2-工作指引、3-文件签批、4-AI汇报、5-工作汇报")
    private Integer reportRecordType;
}
