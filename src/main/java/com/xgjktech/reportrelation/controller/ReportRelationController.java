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

    @ApiOperation("根据批量业务ID重新生成结构化extractSchema")
    @PostMapping("/reExtractSchema")
    public Result<Integer> reExtractSchema(
            @ApiParam("业务类型") @RequestParam(defaultValue = "BP") String bizType,
            @ApiParam("业务ID列表") @RequestParam List<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            throw new BusinessException("bizIds不能为空");
        }
        return Result.success(extractSchemaService.reExtractByBizIds(bizType, bizIds));
    }

}
