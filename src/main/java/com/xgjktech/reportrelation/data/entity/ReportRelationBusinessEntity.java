package com.xgjktech.reportrelation.data.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xgjktech.common.base.BaseEntity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 汇报业务关联实体
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Getter
@Setter
@ApiModel(description = "汇报业务关联")
@TableName("report_relation_business")
public class ReportRelationBusinessEntity extends BaseEntity {

    @ApiModelProperty("汇报ID")
    private Long reportId;

    @ApiModelProperty("汇报名称/标题")
    private String reportName;

    @ApiModelProperty("汇报作者ID")
    private Long authorId;

    @ApiModelProperty("业务类型")
    private String bizType;

    @ApiModelProperty("业务ID")
    private Long bizId;

    @ApiModelProperty("结构化提取结果 JSON | MarkDown")
    private String extractSchema;

    @ApiModelProperty("本次提取使用的配置ID（关联report_extract_config.id）")
    private Long extractConfigId;

    @ApiModelProperty("提取状态：0=未提取，1=提取中，2=成功，3=失败")
    private Integer extractStatus;

    @ApiModelProperty("提取重试次数")
    private Integer retryCount;

    @ApiModelProperty("汇报发送时间")
    private LocalDateTime reportSendTime;

    @ApiModelProperty("关联时间")
    private LocalDateTime relationTime;

    @ApiModelProperty("删除标记：0=未删除，1=已删除")
    private Boolean deleted;
}
