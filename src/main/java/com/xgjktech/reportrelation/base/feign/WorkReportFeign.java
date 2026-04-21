package com.xgjktech.reportrelation.base.feign;

import java.util.List;

import javax.validation.Valid;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.model.ReportInfo;
import com.xgjktech.reportrelation.base.model.ReportListSimpleInfoByIdsParam;
import com.xgjktech.reportrelation.base.model.ReportSimpleInfoBatchVO;
import com.xgjktech.reportrelation.base.model.ReportSimpleInfoForGptVO;
import com.xgjktech.reportrelation.base.param.ReportSimpleInfoBatchParam;

import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 工作汇报服务 Feign Client（仅保留本服务需要的接口）
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@FeignClient(name = "work-report")
public interface WorkReportFeign {

    @ApiOperation(value = "根据汇报id列表获取附件详情-gpt用", response = ReportSimpleInfoForGptVO.class)
    @PostMapping("/inner/report/record/getFileContentListByIdsForGpt")
    Result<ReportSimpleInfoForGptVO> getFileContentListByIdsForGpt(@Valid @RequestBody ReportListSimpleInfoByIdsParam param);

    @ApiOperation(value = "根据汇报ID列表批量查询汇报简易信息", response = ReportSimpleInfoBatchVO.class)
    @PostMapping("/inner/report/record/listSimpleInfoBatch")
    Result<List<ReportSimpleInfoBatchVO>> listSimpleInfoBatch(@Valid @RequestBody ReportSimpleInfoBatchParam param);

    @ApiOperation(value = "根据汇报id集合获取汇报基本信息(汇报人、标题、时间)", response = ReportInfo.class)
    @PostMapping("/inner/report/getReportInfoByIds")
    Result<List<ReportInfo>> getReportInfoByIds(@RequestBody List<Long> idList);
}
