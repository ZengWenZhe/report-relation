package com.xgjktech.reportrelation.base.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * AI请求参数
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Data
public class AiModel {

    @ApiModelProperty("ai类型")
    Integer type;

    String prompt;

    Integer temperature;

    String bizCode;
}
