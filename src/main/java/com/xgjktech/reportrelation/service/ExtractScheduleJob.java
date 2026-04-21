package com.xgjktech.reportrelation.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import com.xgjktech.reportrelation.base.NacosConfig;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.strategy.ExtractStrategy;
import com.xgjktech.reportrelation.strategy.ExtractStrategyRouter;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ExtractScheduleJob {

    private static final int BATCH_SIZE = 50;

    private static final int CONCURRENT_THREADS = 12;

    private final ExecutorService extractExecutor = Executors.newFixedThreadPool(CONCURRENT_THREADS);

    @Resource
    private NacosConfig nacosConfig;

    @Resource
    private ReportExtractQueueService reportExtractQueueService;

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ExtractStrategyRouter extractStrategyRouter;

    @PreDestroy
    public void shutdown() {
        extractExecutor.shutdown();
    }

    @Scheduled(fixedDelayString = "${extract.schedule.fixedDelay:5000}")
    public void execute() {
        if (!Boolean.TRUE.equals(nacosConfig.getJobEnable())) {
            return;
        }

        List<Long> reportIds = reportExtractQueueService.batchDequeue(BATCH_SIZE);
        if (CollectionUtils.isEmpty(reportIds)) {
            return;
        }

        log.info("定时任务取出{}条待提取reportId", reportIds.size());

        List<ReportRelationBusinessEntity> records = reportRelationBusinessService.listByReportIds(reportIds);
        if (records.isEmpty()) {
            log.info("根据reportIds未查到关联记录，reportIds={}", reportIds);
            return;
        }

        Map<String, List<ReportRelationBusinessEntity>> grouped = records.stream()
                .collect(Collectors.groupingBy(ReportRelationBusinessEntity::getBizType));

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        CompletableFuture.allOf(grouped.entrySet().stream()
                .flatMap(entry -> {
                    String bizType = entry.getKey();
                    ExtractStrategy strategy = extractStrategyRouter.getStrategy(bizType);
                    if (strategy == null) {
                        log.warn("bizType={}无对应策略，跳过{}条", bizType, entry.getValue().size());
                        return java.util.stream.Stream.empty();
                    }
                    return entry.getValue().stream()
                            .map(record -> CompletableFuture.runAsync(() -> {
                                try {
                                    strategy.extract(record);
                                    successCount.incrementAndGet();
                                } catch (Exception e) {
                                    failCount.incrementAndGet();
                                    log.error("提取失败，id={}, reportId={}, bizType={}, error={}",
                                            record.getId(), record.getReportId(), bizType, e.getMessage(), e);
                                    reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
                                }
                            }, extractExecutor));
                })
                .toArray(CompletableFuture[]::new)).join();

        log.info("定时提取完成，总记录={}，成功={}，失败={}", records.size(), successCount.get(), failCount.get());
    }
}
