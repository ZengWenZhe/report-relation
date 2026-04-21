package com.xgjktech.reportrelation.strategy;

/**
 * 业务类型处理器接口
 * 每个业务类型注册: typeId -> bizType 的映射 + 业务特有的 corpId 获取逻辑
 * MQ 监听器通过此接口解耦，不再硬编码 BP 特有常量
 */
public interface BizTypeHandler {

    /**
     * 关联业务对象的 typeId (MQ消息中的业务类型标识)
     */
    Long typeId();

    /**
     * 业务类型编码 (存入 report_relation_business 表的 biz_type 字段)
     */
    String bizType();

    /**
     * 根据业务ID获取企业ID (不同业务获取方式不同)
     */
    Long fetchCorpId(Long bizId);
}
