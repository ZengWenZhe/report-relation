package com.xgjktech.reportrelation.service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import com.xgjktech.reportrelation.base.NacosConfig;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.exception.RateLimitException;
import com.xgjktech.reportrelation.strategy.ExtractStrategy;
import com.xgjktech.reportrelation.strategy.ExtractStrategyRouter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ExtractScheduleJob {

    private static final int DEFAULT_BATCH_SIZE = 100;

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

    @Resource
    private AiClient aiClient;

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

    @Scheduled(fixedDelayString = "${extract.schedule.fixedDelay:60000}")
    public void execute() {
        if (!Boolean.TRUE.equals(nacosConfig.getJobEnable())) {
            return;
        }
        if (!Boolean.TRUE.equals(nacosConfig.getExtractEnable())) {
            log.info("提取开关已关闭(extract.enable=false)，跳过本轮");
            return;
        }
        if (aiClient.isRateLimited()) {
            log.info("AI限流窗口未恢复，跳过本轮调度");
            return;
        }

        int batchSize = nacosConfig.getExtractBatchSize() != null ? nacosConfig.getExtractBatchSize() : DEFAULT_BATCH_SIZE;
        Map<Long, Long> dequeued = reportExtractQueueService.batchDequeue(batchSize);
        if (dequeued.isEmpty()) {
            return;
        }

        log.info("定时任务取出{}条待提取reportId", dequeued.size());

        List<ReportRelationBusinessEntity> records = reportRelationBusinessService.listByReportIds(dequeued.keySet());
        if (records.isEmpty()) {
            log.warn("所有reportId在DB中无关联记录，跳过：{}", dequeued.keySet());
            return;
        }

        ExecutorService executor = getOrRebuildExecutor();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        AtomicBoolean rateLimited = new AtomicBoolean(false);

        Map<Long, List<ReportRelationBusinessEntity>> byReportId = records.stream()
                .collect(Collectors.groupingBy(ReportRelationBusinessEntity::getReportId));

        ConcurrentHashMap<Long, String> contentCache = new ConcurrentHashMap<>();

        CompletableFuture.allOf(byReportId.entrySet().stream()
                .map(reportGroup -> CompletableFuture.runAsync(() -> {
                    Long reportId = reportGroup.getKey();
                    List<ReportRelationBusinessEntity> groupRecords = reportGroup.getValue();
                    long enqueueTime = dequeued.getOrDefault(reportId, 0L);

                    if (rateLimited.get()) {
                        reportExtractQueueService.enqueueWithScore(reportId, enqueueTime);
                        return;
                    }

                    String reportContent = contentCache.computeIfAbsent(reportId,
                            id -> extractSchemaService.fetchReportContent(id));

                    boolean needRequeue = false;
                    for (ReportRelationBusinessEntity record : groupRecords) {
                        if (rateLimited.get()) {
                            reportExtractQueueService.enqueueWithScore(reportId, enqueueTime);
                            return;
                        }

                        if (needSkip(record, enqueueTime)) {
                            skippedCount.incrementAndGet();
                            continue;
                        }

                        ExtractStrategy strategy = extractStrategyRouter.getStrategy(record.getBizType());
                        if (strategy == null) {
                            log.warn("bizType={}无对应策略，跳过record id={}", record.getBizType(), record.getId());
                            continue;
                        }
                        try {
                            boolean success = strategy.extract(record, reportContent);
                            if (success) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                                needRequeue = true;
                            }
                        } catch (RateLimitException e) {
                            rateLimited.set(true);
                            reportExtractQueueService.enqueueWithScore(reportId, enqueueTime);
                            log.warn("AI限流触发，reportId={}回塞队列，本轮剩余任务将跳过", reportId);
                            return;
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            log.error("提取失败，id={}, reportId={}, bizType={}, error={}",
                                    record.getId(), record.getReportId(), record.getBizType(), e.getMessage(), e);
                            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null, null);
                            needRequeue = true;
                        }
                    }

                    if (needRequeue) {
                        reportExtractQueueService.enqueueWithScore(reportId, enqueueTime);
                    }
                }, executor))
                .toArray(CompletableFuture[]::new)).join();

        if (rateLimited.get()) {
            log.warn("本轮因AI限流提前终止，成功={}，失败={}，跳过={}，剩余已回塞队列",
                    successCount.get(), failCount.get(), skippedCount.get());
        } else {
            log.info("定时提取完成，总记录={}，去重reportId={}，成功={}，失败={}，跳过（入队后已成功）={}",
                    records.size(), byReportId.size(),
                    successCount.get(), failCount.get(), skippedCount.get());
        }
    }

    /**
     * 判断是否跳过：仅当 status=2（成功）且 updateTime > enqueueTime 时跳过
     * 说明该记录在入队之后已经被成功提取过，不需要重复提取
     */
    private boolean needSkip(ReportRelationBusinessEntity record, long enqueueTime) {
        if (!Integer.valueOf(2).equals(record.getExtractStatus())) {
            return false;
        }
        if (enqueueTime <= 0) {
            return false;
        }
        Timestamp updateTime = record.getUpdateTime();
        if (updateTime == null) {
            return false;
        }
        return updateTime.getTime() > enqueueTime;
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
