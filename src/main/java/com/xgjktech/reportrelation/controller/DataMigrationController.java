package com.xgjktech.reportrelation.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.PmsFeign;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.service.ReportRelationBusinessService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * 历史数据迁移接口
 * 将pms的bp_task_relation_report历史数据迁移到report_relation_business
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Slf4j
@Api(tags = "数据迁移")
@RestController
@RequestMapping("/migration")
public class DataMigrationController {

    private static final String BIZ_TYPE_BP = "BP";

    private static final int SAVE_BATCH_SIZE = 500;

    @Resource
    private PmsFeign pmsFeign;

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @ApiOperation("从pms迁移bp_task_relation_report历史数据（分页拉取+批量写入）")
    @PostMapping("/syncFromPms")
    public Result<String> syncFromPms(
            @ApiParam("每页拉取条数") @RequestParam(defaultValue = "500") int pageSize) {
        log.info("开始从pms迁移历史关联数据, pageSize={}", pageSize);

        int totalCount = 0, successCount = 0, skipCount = 0;
        int page = 1;

        try {
            while (true) {
                Result<List<Map<String, Object>>> result = pmsFeign.exportRelationReports(page, pageSize);
                if (result == null || CollectionUtils.isEmpty(result.getData())) {
                    break;
                }

                List<Map<String, Object>> dataList = result.getData();
                totalCount += dataList.size();
                log.info("拉取第{}页，本页{}条", page, dataList.size());

                List<Map<String, Object>> validList = dataList.stream()
                        .filter(item -> toLong(item.get("bizId")) != null && toLong(item.get("taskId")) != null)
                        .collect(Collectors.toList());
                skipCount += (dataList.size() - validList.size());

                if (!validList.isEmpty()) {
                    Set<String> existingKeys = reportRelationBusinessService.batchCheckExists(
                            validList.stream()
                                    .map(item -> toLong(item.get("bizId")) + ":" + BIZ_TYPE_BP + ":" + toLong(item.get("taskId")))
                                    .collect(Collectors.toList()));

                    List<ReportRelationBusinessEntity> toInsert = new ArrayList<>();
                    for (Map<String, Object> item : validList) {
                        Long reportId = toLong(item.get("bizId"));
                        Long taskId = toLong(item.get("taskId"));
                        String key = reportId + ":" + BIZ_TYPE_BP + ":" + taskId;

                        if (existingKeys.contains(key)) {
                            skipCount++;
                            continue;
                        }

                        ReportRelationBusinessEntity entity = new ReportRelationBusinessEntity();
                        entity.setReportId(reportId);
                        entity.setBizType(BIZ_TYPE_BP);
                        entity.setBizId(taskId);
                        entity.setExtractStatus(0);
                        entity.setReportCreateTime(toLocalDateTime(item.get("businessTime")));
                        entity.setRelationTime(toLocalDateTime(item.get("relationTime")));
                        entity.setDeleted(false);
                        entity.setCorpId(toLong(item.get("corpId")));
                        toInsert.add(entity);
                    }

                    if (!toInsert.isEmpty()) {
                        reportRelationBusinessService.saveBatch(toInsert, SAVE_BATCH_SIZE);
                        successCount += toInsert.size();
                    }
                }

                if (dataList.size() < pageSize) {
                    break;
                }
                page++;
            }

            String msg = String.format("迁移完成：总数=%d, 成功=%d, 跳过=%d", totalCount, successCount, skipCount);
            log.info(msg);
            return Result.success(msg);

        } catch (Exception e) {
            log.error("迁移历史数据失败，已处理到第{}页，error={}", page, e.getMessage(), e);
            String msg = String.format("迁移中断：已成功=%d, 已跳过=%d, 错误=%s", successCount, skipCount, e.getMessage());
            return Result.success(msg);
        }
    }

    private Long toLong(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.valueOf(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof LocalDateTime) {
            return (LocalDateTime) val;
        }
        try {
            return LocalDateTime.parse(val.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }
}
