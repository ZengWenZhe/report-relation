package com.xgjktech.reportrelation.data.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xgjktech.common.base.BaseEntity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiModel(description = "结构化提取配置")
@TableName("report_extract_config")
public class ReportExtractConfigEntity extends BaseEntity {

    @ApiModelProperty("配置编码，唯一标识")
    private String configCode;

    @ApiModelProperty("配置名称")
    private String configName;

    @ApiModelProperty("配置内容（提示词模板等）")
    private String configContent;

    @ApiModelProperty("处理结果（JSON/Markdown）")
    private String extractResult;

    @ApiModelProperty("适用业务类型")
    private String bizType;

    @ApiModelProperty("版本号")
    private Integer version;

    @ApiModelProperty("状态：1=启用，0=禁用")
    private Integer status;

    @ApiModelProperty("删除标记")
    private Boolean deleted;

    @ApiModelProperty("备注说明")
    private String remark;
}
