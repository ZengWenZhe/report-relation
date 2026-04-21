package com.xgjktech.reportrelation.controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Resource;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.cloud.common.exception.BusinessException;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.service.ReportExtractQueueService;
import com.xgjktech.reportrelation.service.ReportRelationBusinessService;
import com.xgjktech.reportrelation.strategy.ExtractStrategy;
import com.xgjktech.reportrelation.strategy.ExtractStrategyRouter;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "汇报关联管理")
@RestController
@RequestMapping("/reportRelation")
public class ReportRelationController {

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ExtractStrategyRouter extractStrategyRouter;

    @Resource
    private ReportExtractQueueService reportExtractQueueService;

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

        ExtractStrategy strategy = extractStrategyRouter.getStrategy(bizType);
        if (strategy == null) {
            throw new BusinessException("不支持的业务类型：" + bizType);
        }

        List<ReportRelationBusinessEntity> records =
                reportRelationBusinessService.listByBizIds(bizType, bizIds);
        if (records.isEmpty()) {
            return Result.success(0);
        }

        AtomicInteger successCount = new AtomicInteger(0);
        CompletableFuture.allOf(records.stream()
                .map(record -> CompletableFuture.runAsync(() -> {
                    try {
                        strategy.extract(record);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
                    }
                })).toArray(CompletableFuture[]::new)).join();

        return Result.success(successCount.get());
    }

    @ApiOperation("查询提取队列大小")
    @GetMapping("/queueSize")
    public Result<Long> queueSize() {
        return Result.success(reportExtractQueueService.queueSize());
    }
}
