package com.xgjktech.reportrelation.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.PmsFeign;
import com.xgjktech.reportrelation.base.model.TaskRelationReportExportVO;
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
                Result<List<TaskRelationReportExportVO>> result = pmsFeign.exportRelationReports(page, pageSize);
                if (result == null || CollectionUtils.isEmpty(result.getData())) {
                    break;
                }

                List<TaskRelationReportExportVO> dataList = result.getData();
                totalCount += dataList.size();
                log.info("拉取第{}页，本页{}条", page, dataList.size());

                List<TaskRelationReportExportVO> validList = dataList.stream()
                        .filter(vo -> vo.getBizId() != null && vo.getTaskId() != null)
                        .collect(Collectors.toList());
                skipCount += (dataList.size() - validList.size());

                if (!validList.isEmpty()) {
                    Set<String> existingKeys = reportRelationBusinessService.batchCheckExists(
                            validList.stream()
                                    .map(vo -> vo.getBizId() + ":" + BIZ_TYPE_BP + ":" + vo.getTaskId())
                                    .collect(Collectors.toList()));

                    List<ReportRelationBusinessEntity> toInsert = new ArrayList<>();
                    for (TaskRelationReportExportVO vo : validList) {
                        String key = vo.getBizId() + ":" + BIZ_TYPE_BP + ":" + vo.getTaskId();
                        if (existingKeys.contains(key)) {
                            skipCount++;
                            continue;
                        }

                        ReportRelationBusinessEntity entity = new ReportRelationBusinessEntity();
                        entity.setReportId(vo.getBizId());
                        entity.setBizType(BIZ_TYPE_BP);
                        entity.setBizId(vo.getTaskId());
                        entity.setExtractStatus(0);
                        entity.setRelationTime(vo.getRelationTime());
                        entity.setDeleted(false);
                        entity.setCorpId(vo.getCorpId());
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
}
