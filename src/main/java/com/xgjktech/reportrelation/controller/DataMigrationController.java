package com.xgjktech.reportrelation.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSONObject;
import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.PmsFeign;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.service.ReportRelationBusinessService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Resource
    private PmsFeign pmsFeign;

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @ApiOperation("从pms迁移bp_task_relation_report历史数据")
    @PostMapping("/syncFromPms")
    public Result<String> syncFromPms() {
        log.info("开始从pms迁移历史关联数据");

        try {
            JSONObject param = new JSONObject();
            param.put("type", "report");
            Result<List<JSONObject>> result = pmsFeign.exportRelationReports(param);

            if (result == null || CollectionUtils.isEmpty(result.getData())) {
                return Result.success("无历史数据需要迁移");
            }

            List<JSONObject> dataList = result.getData();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger skipCount = new AtomicInteger(0);

            dataList.stream().forEach(item -> {
                try {
                    Long reportId = item.getLong("bizId");
                    Long taskId = item.getLong("taskId");

                    if (reportId == null || taskId == null) {
                        skipCount.incrementAndGet();
                        return;
                    }

                    boolean exists = reportRelationBusinessService.existsRelation(
                            reportId, BIZ_TYPE_BP, taskId);
                    if (exists) {
                        skipCount.incrementAndGet();
                        return;
                    }

                    ReportRelationBusinessEntity entity = new ReportRelationBusinessEntity();
                    entity.setReportId(reportId);
                    entity.setReportName(item.getString("main"));
                    entity.setBizType(BIZ_TYPE_BP);
                    entity.setBizId(taskId);
                    entity.setExtractStatus(0);
                    entity.setReportCreateTime(item.getObject("businessTime", LocalDateTime.class));
                    entity.setRelationTime(item.getObject("relationTime", LocalDateTime.class));
                    entity.setDeleted(false);
                    entity.setCorpId(item.getLong("corpId"));
                    entity.setCreateBy(item.getLong("createBy"));
                    entity.setUpdateBy(item.getLong("updateBy"));

                    reportRelationBusinessService.save(entity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("迁移单条数据失败，item={}, error={}", item, e.getMessage(), e);
                }
            });

            String msg = String.format("迁移完成：总数=%d, 成功=%d, 跳过=%d",
                    dataList.size(), successCount.get(), skipCount.get());
            log.info(msg);
            return Result.success(msg);

        } catch (Exception e) {
            log.error("迁移历史数据失败，error={}", e.getMessage(), e);
            return Result.success("迁移失败：" + e.getMessage());
        }
    }
}
