package com.xgjktech.reportrelation.data.vo;

import java.sql.Timestamp;
import java.util.List;

import com.xgjktech.reportrelation.base.param.ReportObjectParam;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 汇报关联业务对象变更MQ消息VO
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Getter
@Setter
public class ReportObjectChangedVO {

    @ApiModelProperty("汇报id（MQ事件使用）")
    private Long reportId;

    @ApiModelProperty("汇报id（兼容recordId）")
    private Long recordId;

    @ApiModelProperty("汇报标题")
    private String main;

    @ApiModelProperty("汇报时间")
    private Timestamp reportTime;

    @ApiModelProperty("当前操作用户id")
    private Long currentEmpId;

    @ApiModelProperty("新增关联业务列表")
    private List<ReportObjectParam> addObjectList;

    @ApiModelProperty("删除关联业务列表")
    private List<ReportObjectParam> deleteObjectList;
}
