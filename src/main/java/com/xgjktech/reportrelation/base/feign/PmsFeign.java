package com.xgjktech.reportrelation.base.feign;

import java.util.List;

import com.alibaba.fastjson.JSONObject;
import com.xgjktech.cloud.common.Result;

import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PMS服务 Feign Client（获取BP上下文、历史关联数据）
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@FeignClient(name = "pms")
public interface PmsFeign {

    @ApiOperation("根据BP任务ID获取BP上下文信息（目标/KR/举措）")
    @GetMapping("/inner/bp/task/getBpContext")
    Result<JSONObject> getBpContext(@RequestParam("taskId") Long taskId);

    @ApiOperation("导出历史关联数据（type=report）")
    @PostMapping("/inner/bp/taskRelationReport/exportAll")
    Result<List<JSONObject>> exportRelationReports(@RequestBody JSONObject param);
}
