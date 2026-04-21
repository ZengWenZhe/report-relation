package com.xgjktech.reportrelation.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Resource
    private NacosConfig nacosConfig;

    @Resource
    private ReportExtractQueueService reportExtractQueueService;

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ExtractStrategyRouter extractStrategyRouter;

    @Resource
    private ExtractSchemaService extractSchemaService;

    private volatile ExecutorService extractExecutor;

    private volatile int currentThreads = 0;

    @PreDestroy
    public void shutdown() {
        if (extractExecutor != null) {
            extractExecutor.shutdown();
            try {
                if (!extractExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    extractExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                extractExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Scheduled(fixedDelayString = "${extract.schedule.fixedDelay:1800000}")
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
            log.warn("所有reportId在DB中无关联记录，丢弃不回队：{}", reportIds);
            return;
        }

        ExecutorService executor = getOrRebuildExecutor();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Map<Long, List<ReportRelationBusinessEntity>> byReportId = records.stream()
                .collect(Collectors.groupingBy(ReportRelationBusinessEntity::getReportId));

        ConcurrentHashMap<Long, String> contentCache = new ConcurrentHashMap<>();

        CompletableFuture.allOf(byReportId.entrySet().stream()
                .map(reportGroup -> CompletableFuture.runAsync(() -> {
                    Long reportId = reportGroup.getKey();
                    List<ReportRelationBusinessEntity> groupRecords = reportGroup.getValue();

                    String reportContent = contentCache.computeIfAbsent(reportId,
                            id -> extractSchemaService.fetchReportContent(id));

                    for (ReportRelationBusinessEntity record : groupRecords) {
                        ExtractStrategy strategy = extractStrategyRouter.getStrategy(record.getBizType());
                        if (strategy == null) {
                            log.warn("bizType={}无对应策略，跳过record id={}", record.getBizType(), record.getId());
                            continue;
                        }
                        try {
                            strategy.extract(record, reportContent);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            log.error("提取失败，id={}, reportId={}, bizType={}, error={}",
                                    record.getId(), record.getReportId(), record.getBizType(), e.getMessage(), e);
                            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
                            reportExtractQueueService.enqueue(record.getReportId());
                        }
                    }
                }, executor))
                .toArray(CompletableFuture[]::new)).join();

        log.info("定时提取完成，总记录={}，去重reportId={}，成功={}，失败={}",
                records.size(), byReportId.size(), successCount.get(), failCount.get());
    }

    private ExecutorService getOrRebuildExecutor() {
        int configThreads = nacosConfig.getExtractConcurrentThreads() != null
                ? nacosConfig.getExtractConcurrentThreads() : 4;
        if (extractExecutor == null || configThreads != currentThreads) {
            synchronized (this) {
                if (extractExecutor == null || configThreads != currentThreads) {
                    if (extractExecutor != null) {
                        extractExecutor.shutdown();
                        log.info("线程池大小变更 {} -> {}, 重建线程池", currentThreads, configThreads);
                    }
                    extractExecutor = Executors.newFixedThreadPool(configThreads);
                    currentThreads = configThreads;
                }
            }
        }
        return extractExecutor;
    }
}
