package com.xgjktech.reportrelation.base.feign;

import java.util.List;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.model.TaskRelationReportExportVO;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PMS服务 Feign Client（获取BP上下文、历史关联数据）
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@FeignClient(name = "pms")
public interface PmsFeign {

    @GetMapping("/inner/task/getBpContext")
    Result<String> getBpContext(@RequestParam("taskId") Long taskId);

    @GetMapping("/inner/task/exportRelationReports")
    Result<List<TaskRelationReportExportVO>> exportRelationReports(
            @RequestParam("page") int page,
            @RequestParam("pageSize") int pageSize);

    @GetMapping("/inner/task/getTaskCorpId")
    Result<Long> getTaskCorpId(@RequestParam("taskId") Long taskId);
}
