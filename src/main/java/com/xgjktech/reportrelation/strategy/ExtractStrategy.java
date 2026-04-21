package com.xgjktech.reportrelation.strategy;

import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;

public interface ExtractStrategy {

    /**
     * 策略适用的业务类型
     */
    String bizType();

    /**
     * 对单条关联记录执行结构化提取
     */
    void extract(ReportRelationBusinessEntity record);
}
