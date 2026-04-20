package com.xgjktech.reportrelation.service;

import java.time.LocalDateTime;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.PmsFeign;
import com.xgjktech.reportrelation.base.param.ReportObjectParam;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.data.vo.ReportObjectChangedVO;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 汇报关联事件监听服务
 * 监听工作汇报系统的MQ事件，处理汇报与业务对象的关联变更
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Slf4j
@Service
public class ReportRelationEventService {

    private static final Long BP_TYPE_ID = 52L;

    private static final String BIZ_TYPE_BP = "BP";

    private static final String EX_CWORK_REPORT_OBJECT_CHANGE = "ex_cwork_report_object_change";

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private PmsFeign pmsFeign;

    /**
     * 监听汇报关联业务对象变更消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue_report_relation_report_object_changed"),
            exchange = @Exchange(name = EX_CWORK_REPORT_OBJECT_CHANGE, type = ExchangeTypes.FANOUT)))
    public void listenReportObjectChanged(String message) {
        log.info("收到汇报关联业务对象变更消息, message={}", message);

        try {
            ReportObjectChangedVO vo = JSON.parseObject(message, ReportObjectChangedVO.class);
            if (vo.getRecordId() == null) {
                vo.setRecordId(vo.getReportId());
            }

            if (CollectionUtils.isNotEmpty(vo.getAddObjectList())) {
                vo.getAddObjectList().stream()
                        .filter(obj -> BP_TYPE_ID.equals(obj.getTypeId()))
                        .forEach(obj -> handleAddRelation(vo, obj));
            }

            if (CollectionUtils.isNotEmpty(vo.getDeleteObjectList())) {
                vo.getDeleteObjectList().stream()
                        .filter(obj -> BP_TYPE_ID.equals(obj.getTypeId()))
                        .forEach(obj -> handleDeleteRelation(vo, obj));
            }

        } catch (Exception e) {
            log.error("处理汇报关联业务对象变更消息异常, message={}, error={}", message, e.getMessage(), e);
        }
    }

    private void handleAddRelation(ReportObjectChangedVO vo, ReportObjectParam obj) {
        try {
            Long bpTaskId = Long.parseLong(obj.getBizId());

            boolean exists = reportRelationBusinessService.existsRelation(
                    vo.getReportId(), BIZ_TYPE_BP, bpTaskId);
            if (exists) {
                log.info("关联关系已存在，reportId={}, bpTaskId={}", vo.getReportId(), bpTaskId);
                return;
            }

            Long corpId = fetchTaskCorpId(bpTaskId);

            ReportRelationBusinessEntity entity = new ReportRelationBusinessEntity();
            entity.setReportId(vo.getReportId());
            entity.setReportName(vo.getMain());
            entity.setBizType(BIZ_TYPE_BP);
            entity.setBizId(bpTaskId);
            entity.setExtractStatus(0);
            entity.setReportCreateTime(
                    vo.getReportTime() != null ? vo.getReportTime().toLocalDateTime() : null);
            entity.setRelationTime(LocalDateTime.now());
            entity.setDeleted(false);
            entity.setCorpId(corpId);
            entity.setCreateBy(vo.getCurrentEmpId());
            entity.setUpdateBy(vo.getCurrentEmpId());

            reportRelationBusinessService.save(entity);
            log.info("新增汇报关联成功，reportId={}, bpTaskId={}, corpId={}", vo.getReportId(), bpTaskId, corpId);

        } catch (NumberFormatException e) {
            log.error("BP任务ID格式错误，bizId={}", obj.getBizId());
        } catch (Exception e) {
            log.error("处理新增汇报关联异常，reportId={}, bizId={}, error={}",
                    vo.getReportId(), obj.getBizId(), e.getMessage(), e);
        }
    }

    private Long fetchTaskCorpId(Long taskId) {
        try {
            Result<Long> result = pmsFeign.getTaskCorpId(taskId);
            return (result != null) ? result.getData() : null;
        } catch (Exception e) {
            log.warn("获取任务corpId失败，taskId={}", taskId, e);
            return null;
        }
    }

    /**
     * 处理删除汇报关联（逻辑删除）
     */
    private void handleDeleteRelation(ReportObjectChangedVO vo, ReportObjectParam obj) {
        try {
            Long bpTaskId = Long.parseLong(obj.getBizId());

            boolean updated = reportRelationBusinessService.logicDeleteRelation(
                    vo.getReportId(), BIZ_TYPE_BP, bpTaskId, vo.getCurrentEmpId());

            if (updated) {
                log.info("删除汇报关联成功，reportId={}, bpTaskId={}", vo.getReportId(), bpTaskId);
            } else {
                log.info("汇报关联不存在或已删除，reportId={}, bpTaskId={}", vo.getReportId(), bpTaskId);
            }

        } catch (NumberFormatException e) {
            log.error("BP任务ID格式错误，bizId={}", obj.getBizId());
        } catch (Exception e) {
            log.error("处理删除汇报关联异常，reportId={}, bizId={}, error={}",
                    vo.getReportId(), obj.getBizId(), e.getMessage(), e);
        }
    }
}
