package com.xgjktech.reportrelation.service;

import java.util.List;

import com.xgjktech.common.base.AbstractBaseService;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.mapper.ReportRelationBusinessMapper;

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
                .update();
    }
}
