package com.xgjktech.reportrelation.base.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 根据汇报ID列表获取附件详情请求参数
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Data
public class ReportListSimpleInfoByIdsParam {

    @ApiModelProperty("汇报ID列表")
    private List<Long> reportIds;
}
