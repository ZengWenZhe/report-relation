package com.xgjktech.reportrelation.data.vo;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportInfoChangedVO {

    @ApiModelProperty("汇报ID")
    private Long reportRecordId;

    @ApiModelProperty("操作：create-新增、update-更新、delete-删除")
    private String operate;

    @ApiModelProperty("类型列表: content-正文变更、reply-回复变更、attachment-附件、plan-任务变更")
    private List<String> typeList;

    @ApiModelProperty("新增正文附件ID列表")
    private List<Long> addContentFileIds;

    @ApiModelProperty("删除正文附件ID列表")
    private List<Long> deleteContentFileIds;

    @ApiModelProperty("新增回复附件ID列表")
    private List<Long> addReplyFileIds;

    @ApiModelProperty("删除回复附件ID列表")
    private List<Long> deleteReplyFileIds;

    @ApiModelProperty("任务ID")
    private Long planId;

    @ApiModelProperty("新增回复ID")
    private Long addReplyId;

    @ApiModelProperty("撤回回复ID")
    private Long deleteReplyId;

    @ApiModelProperty("事项ID")
    private Long reportTemplateId;

    @ApiModelProperty("回复内容信息")
    private ReportReplyVO reportReply;
}
