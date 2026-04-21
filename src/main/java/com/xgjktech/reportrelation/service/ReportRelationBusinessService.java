package com.xgjktech.reportrelation.service;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import com.xgjktech.common.base.AbstractBaseService;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.mapper.ReportRelationBusinessMapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 汇报业务关联Service
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Slf4j
@Service
public class ReportRelationBusinessService extends AbstractBaseService<ReportRelationBusinessMapper, ReportRelationBusinessEntity> {

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 判断某个reportId是否存在任意未删除的关联记录
     */
    public boolean existsByReportId(Long reportId) {
        return this.lambdaQuery()
                .eq(ReportRelationBusinessEntity::getReportId, reportId)
                .eq(ReportRelationBusinessEntity::getDeleted, false)
                .last("LIMIT 1")
                .count() > 0;
    }

    /**
     * 判断关联关系是否已存在
     */
    public boolean existsRelation(Long reportId, String bizType, Long bizId) {
        return this.lambdaQuery()
                .eq(ReportRelationBusinessEntity::getReportId, reportId)
                .eq(ReportRelationBusinessEntity::getBizType, bizType)
                .eq(ReportRelationBusinessEntity::getBizId, bizId)
                .eq(ReportRelationBusinessEntity::getDeleted, false)
                .count() > 0;
    }

    /**
     * 逻辑删除关联关系
     */
    public boolean logicDeleteRelation(Long reportId, String bizType, Long bizId, Long operatorId) {
        return this.lambdaUpdate()
                .eq(ReportRelationBusinessEntity::getReportId, reportId)
                .eq(ReportRelationBusinessEntity::getBizType, bizType)
                .eq(ReportRelationBusinessEntity::getBizId, bizId)
                .eq(ReportRelationBusinessEntity::getDeleted, false)
                .set(ReportRelationBusinessEntity::getDeleted, true)
                .set(ReportRelationBusinessEntity::getUpdateBy, operatorId)
                .update();
    }

    /**
     * 查询指定业务下所有未删除的关联记录
     */
    public List<ReportRelationBusinessEntity> listByBizId(String bizType, Long bizId) {
        return this.lambdaQuery()
                .eq(ReportRelationBusinessEntity::getBizType, bizType)
                .eq(ReportRelationBusinessEntity::getBizId, bizId)
                .eq(ReportRelationBusinessEntity::getDeleted, false)
                .list();
    }



    /**
     * 更新提取状态。
     * status=3 时自动处理重试计数，返回是否需要入队重试（由调用方决定如何入队）。
     * status=2 时重置 retryCount。
     *
     * @return true=需要入队重试（仅 status=3 且 retryCount < MAX 时返回 true）
     */
    public boolean updateExtractStatus(Long id, Integer status, String extractSchema, Long extractConfigId) {
        if (Integer.valueOf(3).equals(status)) {
            return handleFailWithRetry(id, extractSchema, extractConfigId);
        }

        doUpdateStatus(id, status, extractSchema, extractConfigId, null);

        if (Integer.valueOf(2).equals(status)) {
            this.lambdaUpdate()
                    .eq(ReportRelationBusinessEntity::getId, id)
                    .set(ReportRelationBusinessEntity::getRetryCount, 0)
                    .set(ReportRelationBusinessEntity::getUpdateTime, new Timestamp(System.currentTimeMillis()))
                    .update();
        }
        return false;
    }

    /**
     * @return true=需要入队重试
     */
    private boolean handleFailWithRetry(Long id, String extractSchema, Long extractConfigId) {
        ReportRelationBusinessEntity record = this.getById(id);
        if (record == null) {
            log.warn("更新提取状态时记录不存在，id={}", id);
            return false;
        }

        int currentRetry = record.getRetryCount() != null ? record.getRetryCount() : 0;

        if (currentRetry < MAX_RETRY_COUNT) {
            int newRetry = currentRetry + 1;
            doUpdateStatus(id, 3, extractSchema, extractConfigId, newRetry);
            log.info("提取失败，需重试，id={}, reportId={}, retryCount={}/{}",
                    id, record.getReportId(), newRetry, MAX_RETRY_COUNT);
            return true;
        } else {
            doUpdateStatus(id, 3, extractSchema, extractConfigId, currentRetry);
            log.warn("提取失败且已达最大重试次数，不再重试，id={}, reportId={}, retryCount={}",
                    id, record.getReportId(), currentRetry);
            return false;
        }
    }

    private void doUpdateStatus(Long id, Integer status, String extractSchema, Long extractConfigId, Integer retryCount) {
        boolean isFail = Integer.valueOf(3).equals(status);
        this.lambdaUpdate()
                .eq(ReportRelationBusinessEntity::getId, id)
                .set(ReportRelationBusinessEntity::getExtractStatus, status)
                .set(isFail || extractSchema != null, ReportRelationBusinessEntity::getExtractSchema, extractSchema)
                .set(extractConfigId != null, ReportRelationBusinessEntity::getExtractConfigId, extractConfigId)
                .set(retryCount != null, ReportRelationBusinessEntity::getRetryCount, retryCount)
                .set(ReportRelationBusinessEntity::getUpdateTime, new Timestamp(System.currentTimeMillis()))
                .update();
    }

    /**
     * 根据reportId列表查询所有未删除的关联记录
     */
    public List<ReportRelationBusinessEntity> listByReportIds(Collection<Long> reportIds) {
        if (CollectionUtils.isEmpty(reportIds)) {
            return Collections.emptyList();
        }
        return ListUtils.partition(new java.util.ArrayList<>(reportIds), 500).stream()
                .flatMap(batch -> this.lambdaQuery()
                        .in(ReportRelationBusinessEntity::getReportId, batch)
                        .eq(ReportRelationBusinessEntity::getDeleted, false)
                        .list()
                        .stream())
                .collect(Collectors.toList());
    }

    /**
     * 根据批量bizId查询所有未删除的关联记录
     */
    public List<ReportRelationBusinessEntity> listByBizIds(String bizType, Collection<Long> bizIds) {
        if (CollectionUtils.isEmpty(bizIds)) {
            return Collections.emptyList();
        }
        return ListUtils.partition(new ArrayList<>(bizIds), 500).stream()
                .flatMap(batch -> this.lambdaQuery()
                        .eq(ReportRelationBusinessEntity::getBizType, bizType)
                        .in(ReportRelationBusinessEntity::getBizId, batch)
                        .eq(ReportRelationBusinessEntity::getDeleted, false)
                        .list()
                        .stream())
                .collect(Collectors.toList());
    }

    /**
     * 批量检查已存在的关联关系，返回已存在的 key 集合。
     * key 格式：reportId:bizType:bizId
     * 使用分片查询避免 IN 子句过长。
     */
    public Set<String> batchCheckExists(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptySet();
        }

        Set<Long> reportIds = keys.stream()
                .map(k -> Long.valueOf(k.split(":")[0]))
                .collect(Collectors.toSet());

        List<List<Long>> partitions = ListUtils.partition(
                new java.util.ArrayList<>(reportIds), 500);

        return partitions.stream()
                .flatMap(batch -> this.lambdaQuery()
                        .select(ReportRelationBusinessEntity::getReportId,
                                ReportRelationBusinessEntity::getBizType,
                                ReportRelationBusinessEntity::getBizId)
                        .in(ReportRelationBusinessEntity::getReportId, batch)
                        .eq(ReportRelationBusinessEntity::getDeleted, false)
                        .list()
                        .stream())
                .map(e -> e.getReportId() + ":" + e.getBizType() + ":" + e.getBizId())
                .collect(Collectors.toSet());
    }

    /**
     * 查询需要全量初始化提取的 distinct reportId 列表
     *
     * @param forceReExtract true=所有记录, false=仅 extract_status IN (0,3) 的
     */
    public List<Long> listReportIdsForInit(boolean forceReExtract) {
        if (forceReExtract) {
            return this.lambdaQuery()
                    .select(ReportRelationBusinessEntity::getReportId)
                    .eq(ReportRelationBusinessEntity::getDeleted, false)
                    .list()
                    .stream()
                    .map(ReportRelationBusinessEntity::getReportId)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return this.lambdaQuery()
                .select(ReportRelationBusinessEntity::getReportId)
                .eq(ReportRelationBusinessEntity::getDeleted, false)
                .in(ReportRelationBusinessEntity::getExtractStatus, Arrays.asList(0, 3))
                .list()
                .stream()
                .map(ReportRelationBusinessEntity::getReportId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 重置指定状态记录的 retryCount 为 0
     */
    public void resetRetryCount(List<Integer> statusList) {
        this.lambdaUpdate()
                .eq(ReportRelationBusinessEntity::getDeleted, false)
                .in(ReportRelationBusinessEntity::getExtractStatus, statusList)
                .set(ReportRelationBusinessEntity::getRetryCount, 0)
                .update();
    }
}
