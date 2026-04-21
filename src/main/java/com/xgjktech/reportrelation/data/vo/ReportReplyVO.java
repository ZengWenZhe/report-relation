package com.xgjktech.reportrelation.data.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportReplyVO {

    @ApiModelProperty("主键id")
    private Long id;

    @ApiModelProperty("创建时间")
    private String createTime;

    @ApiModelProperty("父id")
    private Long parentId;

    @ApiModelProperty("report_record表id")
    private Long reportRecordId;

    @ApiModelProperty("评论内容")
    private String content;

    @ApiModelProperty("富文本评论内容")
    private String contentHtml;

    @ApiModelProperty("回复类型:common-普通回复、suggest-建议、decide-决策")
    private String type;

    @ApiModelProperty("翻译内容")
    private String translateContent;

    @ApiModelProperty("回复用户id")
    private Long replyEmpId;

    @ApiModelProperty("被回复用户id")
    private Long toReplyEmpId;

    @ApiModelProperty("评论带附件 1有附件")
    private Integer isMedia;

    @ApiModelProperty("操作：fill-填写、agree-同意、disagree-不同意")
    private String operate;
}
