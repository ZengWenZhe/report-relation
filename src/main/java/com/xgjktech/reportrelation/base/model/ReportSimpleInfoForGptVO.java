package com.xgjktech.reportrelation.base.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * GPT用汇报完整内容VO（包含正文、附件、回复）
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Data
public class ReportSimpleInfoForGptVO {

    @ApiModelProperty("是否超过token限制")
    private boolean overToken;

    @ApiModelProperty("汇报完整内容（包含正文、附件、回复）")
    private String content;
}
