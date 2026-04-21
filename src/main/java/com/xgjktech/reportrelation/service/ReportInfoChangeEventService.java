package com.xgjktech.reportrelation.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import com.xgjktech.reportrelation.data.vo.ReportInfoChangedVO;

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
public class ReportInfoChangeEventService {

    private static final String EX_CWORK_REPORT_INFO_CHANGE = "ex_cwork_report_info_change";

    private static final Set<String> EXTRACT_TRIGGER_TYPES = new HashSet<>(
            Arrays.asList("content", "reply", "attachment"));

    @Resource
    private ReportExtractQueueService reportExtractQueueService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "queue_report_relation_report_info_changed"),
            exchange = @Exchange(name = EX_CWORK_REPORT_INFO_CHANGE, type = ExchangeTypes.FANOUT)))
    public void listenReportInfoChanged(String message) {
        log.info("收到汇报信息变更消息, message={}", message);

        try {
            ReportInfoChangedVO vo = JSON.parseObject(message, ReportInfoChangedVO.class);

            if (!"update".equals(vo.getOperate())) {
                log.debug("非update操作，跳过, operate={}", vo.getOperate());
                return;
            }

            if (vo.getReportRecordId() == null) {
                log.warn("汇报ID为空，跳过");
                return;
            }

            if (CollectionUtils.isEmpty(vo.getTypeList())) {
                log.debug("typeList为空，跳过");
                return;
            }

            boolean needExtract = vo.getTypeList().stream()
                    .anyMatch(EXTRACT_TRIGGER_TYPES::contains);

            if (needExtract) {
                reportExtractQueueService.enqueue(vo.getReportRecordId());
                log.info("汇报信息变更触发重提取入队, reportId={}, typeList={}",
                        vo.getReportRecordId(), vo.getTypeList());
            }

        } catch (Exception e) {
            log.error("处理汇报信息变更消息异常, message={}, error={}", message, e.getMessage(), e);
        }
    }
}
