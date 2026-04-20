package com.xgjktech.reportrelation.base.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 批量查询汇报简易信息请求参数
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Data
public class ReportSimpleInfoBatchParam {

    @ApiModelProperty("汇报ID列表")
    private List<Long> reportIdList;
}
