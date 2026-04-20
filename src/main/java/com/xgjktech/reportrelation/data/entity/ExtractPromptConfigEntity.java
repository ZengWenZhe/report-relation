package com.xgjktech.reportrelation.data.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xgjktech.common.base.BaseEntity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 结构化提取提示词配置实体
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Getter
@Setter
@ApiModel(description = "结构化提取提示词配置")
@TableName("extract_prompt_config")
public class ExtractPromptConfigEntity extends BaseEntity {

    @ApiModelProperty("提示词编码，唯一标识")
    private String promptCode;

    @ApiModelProperty("提示词名称")
    private String promptName;

    @ApiModelProperty("提示词模板内容")
    private String promptContent;

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
