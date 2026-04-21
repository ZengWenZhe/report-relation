package com.xgjktech.reportrelation.service;

import java.time.LocalDateTime;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import com.xgjktech.reportrelation.base.param.ReportObjectParam;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.data.vo.ReportObjectChangedVO;
import com.xgjktech.reportrelation.strategy.BizTypeHandler;
import com.xgjktech.reportrelation.strategy.BizTypeHandlerRouter;


import org.apache.commons.collections4.CollectionUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportRelationEventService {

    private static final String EX_CWORK_REPORT_OBJECT_CHANGE = "ex_cwork_report_object_change";

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ReportExtractQueueService reportExtractQueueService;

    @Resource
    private BizTypeHandlerRouter bizTypeHandlerRouter;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue_report_relation_report_object_changed"),
            exchange = @Exchange(name = EX_CWORK_REPORT_OBJECT_CHANGE, type = ExchangeTypes.FANOUT)))
    public void listenReportObjectChanged(String message) {
        log.info("收到汇报关联业务对象变更消息, message={}", message);

        try {
            ReportObjectChangedVO vo = JSON.parseObject(message, ReportObjectChangedVO.class);
            if (vo == null) {
                log.warn("消息解析为null，跳过, message={}", message);
                return;
            }
            if (vo.getReportId() == null && vo.getRecordId() != null) {
                vo.setReportId(vo.getRecordId());
            } else if (vo.getRecordId() == null && vo.getReportId() != null) {
                vo.setRecordId(vo.getReportId());
            }

            if (vo.getReportId() == null) {
                log.warn("reportId和recordId均为空，跳过, message={}", message);
                return;
            }

            if (CollectionUtils.isNotEmpty(vo.getAddObjectList())) {
                vo.getAddObjectList().forEach(obj -> handleAddRelation(vo, obj));
            }

            if (CollectionUtils.isNotEmpty(vo.getDeleteObjectList())) {
                vo.getDeleteObjectList().forEach(obj -> handleDeleteRelation(vo, obj));
            }

        } catch (Exception e) {
            log.error("处理汇报关联业务对象变更消息异常, message={}, error={}", message, e.getMessage(), e);
        }
    }

    private void handleAddRelation(ReportObjectChangedVO vo, ReportObjectParam obj) {
        BizTypeHandler handler = bizTypeHandlerRouter.getHandler(obj.getTypeId());
        if (handler == null) {
            log.debug("typeId={}无对应处理器，跳过", obj.getTypeId());
            return;
        }

        try {
            Long bizId = Long.parseLong(obj.getBizId());
            String bizType = handler.bizType();

            boolean exists = reportRelationBusinessService.existsRelation(
                    vo.getReportId(), bizType, bizId);
            if (exists) {
                log.info("关联关系已存在，reportId={}, bizType={}, bizId={}", vo.getReportId(), bizType, bizId);
                return;
            }

            Long corpId = handler.fetchCorpId(bizId);
            if (corpId == null) {
                log.error("获取corpId失败，无法建立关联，reportId={}, bizType={}, bizId={}",
                        vo.getReportId(), bizType, bizId);
                return;
            }

            ReportRelationBusinessEntity entity = new ReportRelationBusinessEntity();
            entity.setReportId(vo.getReportId());
            entity.setReportName(vo.getMain());
            entity.setBizType(bizType);
            entity.setBizId(bizId);
            entity.setAuthorId(vo.getCurrentEmpId());
            entity.setExtractStatus(0);
            entity.setReportSendTime(
                    vo.getReportTime() != null ? vo.getReportTime().toLocalDateTime() : null);
            entity.setRelationTime(LocalDateTime.now());
            entity.setDeleted(false);
            entity.setCorpId(corpId);
            entity.setCreateBy(vo.getCurrentEmpId());
            entity.setUpdateBy(vo.getCurrentEmpId());

            reportRelationBusinessService.save(entity);
            reportExtractQueueService.enqueue(vo.getReportId());
            log.info("新增汇报关联成功并入提取队列，reportId={}, bizType={}, bizId={}, corpId={}",
                    vo.getReportId(), bizType, bizId, corpId);

        } catch (NumberFormatException e) {
            log.error("业务ID格式错误，bizId={}", obj.getBizId());
        } catch (Exception e) {
            log.error("处理新增汇报关联异常，reportId={}, bizId={}, error={}",
                    vo.getReportId(), obj.getBizId(), e.getMessage(), e);
        }
    }

    private void handleDeleteRelation(ReportObjectChangedVO vo, ReportObjectParam obj) {
        BizTypeHandler handler = bizTypeHandlerRouter.getHandler(obj.getTypeId());
        if (handler == null) {
            log.debug("typeId={}无对应处理器，跳过", obj.getTypeId());
            return;
        }

        try {
            Long bizId = Long.parseLong(obj.getBizId());
            String bizType = handler.bizType();

            boolean updated = reportRelationBusinessService.logicDeleteRelation(
                    vo.getReportId(), bizType, bizId, vo.getCurrentEmpId());

            if (updated) {
                log.info("删除汇报关联成功，reportId={}, bizType={}, bizId={}", vo.getReportId(), bizType, bizId);
            } else {
                log.info("汇报关联不存在或已删除，reportId={}, bizType={}, bizId={}", vo.getReportId(), bizType, bizId);
            }

        } catch (NumberFormatException e) {
            log.error("业务ID格式错误，bizId={}", obj.getBizId());
        } catch (Exception e) {
            log.error("处理删除汇报关联异常，reportId={}, bizId={}, error={}",
                    vo.getReportId(), obj.getBizId(), e.getMessage(), e);
        }
    }
}
