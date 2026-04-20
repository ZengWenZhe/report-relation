package com.xgjktech.reportrelation.base.model;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskRelationReportExportVO {

    private Long id;

    private Long bizId;

    private Long taskId;

    private Long groupId;

    private LocalDateTime businessTime;

    private Long relationEmpId;

    private LocalDateTime relationTime;

    private Long corpId;
}
