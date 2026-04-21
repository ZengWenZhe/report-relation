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
                .orderByDesc(ReportRelationBusinessEntity::getReportCreateTime)
                .list();
    }

    /**
     * 查询待提取或提取失败的记录
     *
     * @param limit 查询数量限制
     */
    public List<ReportRelationBusinessEntity> listPendingExtract(int limit) {
        return this.lambdaQuery()
                .eq(ReportRelationBusinessEntity::getDeleted, false)
                .in(ReportRelationBusinessEntity::getExtractStatus, 0, 3)
                .orderByAsc(ReportRelationBusinessEntity::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 更新提取状态
     */
    public void updateExtractStatus(Long id, Integer status, String extractSchema) {
        this.lambdaUpdate()
                .eq(ReportRelationBusinessEntity::getId, id)
                .set(ReportRelationBusinessEntity::getExtractStatus, status)
                .set(extractSchema != null, ReportRelationBusinessEntity::getExtractSchema, extractSchema)
                .set( ReportRelationBusinessEntity::getUpdateTime, new Timestamp(System.currentTimeMillis()))
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
}
