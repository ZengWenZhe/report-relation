package com.xgjktech.reportrelation.controller;

import java.util.List;

import javax.annotation.Resource;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.cloud.common.exception.BusinessException;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.service.ExtractSchemaService;
import com.xgjktech.reportrelation.service.ReportRelationBusinessService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 汇报关联管理接口
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Api(tags = "汇报关联管理")
@RestController
@RequestMapping("/reportRelation")
public class ReportRelationController {

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ExtractSchemaService extractSchemaService;

    @ApiOperation("查询指定业务的关联汇报列表")
    @GetMapping("/listByBizId")
    public Result<List<ReportRelationBusinessEntity>> listByBizId(
            @ApiParam("业务类型") @RequestParam(defaultValue = "BP") String bizType,
            @ApiParam("业务ID") @RequestParam Long bizId) {
        return Result.success(reportRelationBusinessService.listByBizId(bizType, bizId));
    }

    @ApiOperation("手动触发批量结构化提取")
    @PostMapping("/extract/batch")
    public Result<Integer> batchExtract(
            @ApiParam("本次最大处理条数") @RequestParam(defaultValue = "50") int limit) {
        int count = extractSchemaService.batchExtract(limit);
        return Result.success(count);
    }

    @ApiOperation("手动触发单条结构化提取")
    @PostMapping("/extract/single")
    public Result<Boolean> extractSingle(
            @ApiParam("关联记录ID") @RequestParam Long id) {
        ReportRelationBusinessEntity record = reportRelationBusinessService.getById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        extractSchemaService.extractSingle(record);
        return Result.success(true);
    }
}
