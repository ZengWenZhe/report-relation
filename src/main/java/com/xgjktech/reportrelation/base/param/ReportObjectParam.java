package com.xgjktech.reportrelation.base.param;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 汇报关联业务对象参数
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Getter
@Setter
public class ReportObjectParam {

    @NotNull(message = "The parameter typeId must not be null")
    @ApiModelProperty("业务类型id")
    private Long typeId;

    @NotBlank(message = "The parameter bizId must not be blank")
    @ApiModelProperty("业务id")
    private String bizId;

    @NotBlank(message = "The parameter productName must not be blank")
    @ApiModelProperty("业务名称")
    private String productName;
}
