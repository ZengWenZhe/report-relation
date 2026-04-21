package com.xgjktech.reportrelation.strategy;

import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;

public interface ExtractStrategy {

    /**
     * 策略适用的业务类型
     */
    String bizType();

    /**
     * 对单条关联记录执行结构化提取（自行获取汇报内容）
     *
     * @return true=提取成功, false=提取失败
     */
    boolean extract(ReportRelationBusinessEntity record);

    /**
     * 对单条关联记录执行结构化提取（复用已获取的汇报内容，避免同reportId重复拉取）
     *
     * @return true=提取成功, false=提取失败
     */
    boolean extract(ReportRelationBusinessEntity record, String reportContent);
}
