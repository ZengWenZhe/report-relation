package com.xgjktech.reportrelation.service;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.FilegptFeign;
import com.xgjktech.reportrelation.base.feign.WorkReportFeign;
import com.xgjktech.reportrelation.base.model.ReportListSimpleInfoByIdsParam;
import com.xgjktech.reportrelation.base.model.ReportSimpleInfoForGptVO;
import com.xgjktech.reportrelation.base.param.AiData;
import com.xgjktech.reportrelation.base.param.AiModel;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.enums.ExtractConfigCodeEnum;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 公共AI结构化提取服务
 * 负责: 获取汇报内容、调用AI、清理JSON输出、更新提取状态
 * 不含任何业务特有逻辑(如BP上下文获取等)
 */
@Slf4j
@Service
public class ExtractSchemaService {

    private static final int AI_TYPE = 92;

    private static final String BIZ_CODE = "report_extract_schema";

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ReportExtractConfigService reportExtractConfigService;

    @Resource
    private WorkReportFeign workReportFeign;

    @Resource
    private FilegptFeign filegptFeign;

    /**
     * 通用提取流程：获取汇报内容 -> 由调用方提供业务上下文组装prompt -> 调AI -> 解析结果
     *
     * @param record        关联记录
     * @param configCode    配置编码(决定prompt模板)
     * @param bizContext    业务上下文JSON(由各策略提供, 用于替换模板中的占位符)
     * @param contextPlaceholder 模板中业务上下文的占位符名称
     */
    public void extractSingle(ReportRelationBusinessEntity record,
                              ExtractConfigCodeEnum configCode,
                              String bizContext,
                              String contextPlaceholder) {
        log.info("开始结构化提取，id={}, reportId={}, bizId={}, bizType={}",
                record.getId(), record.getReportId(), record.getBizId(), record.getBizType());

        reportRelationBusinessService.updateExtractStatus(record.getId(), 1, null);

        String reportContent = fetchReportContent(record.getReportId());
        if (StringUtils.isBlank(reportContent)) {
            log.warn("汇报内容为空，跳过提取，reportId={}", record.getReportId());
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
            return;
        }

        String promptTemplate = reportExtractConfigService.getConfigContentByCode(configCode);
        String prompt = promptTemplate
                .replace("{{REPORT_CONTENT}}", reportContent)
                .replace(contextPlaceholder, StringUtils.defaultString(bizContext, "{}"));

        String answer = callAi(prompt);
        if (answer == null) {
            log.warn("AI结构化提取返回为空，reportId={}", record.getReportId());
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
            return;
        }

        log.info("AI结构化提取成功，reportId={}, answer前100字={}",
                record.getReportId(), StringUtils.abbreviate(answer, 100));

        JSONObject jsonResult = parseJson(answer);
        if (jsonResult == null) {
            log.error("AI返回结果JSON解析失败，reportId={}, answer={}", record.getReportId(), answer);
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
            return;
        }

        jsonResult.put("authorId", record.getCreateBy());
        String finalAnswer = jsonResult.toJSONString();

        reportRelationBusinessService.updateExtractStatus(record.getId(), 2, finalAnswer);
        log.info("结构化提取完成，id={}, reportId={}", record.getId(), record.getReportId());
    }

    public String fetchReportContent(Long reportId) {
        try {
            ReportListSimpleInfoByIdsParam param = new ReportListSimpleInfoByIdsParam();
            param.setReportIds(Collections.singletonList(reportId));

            Result<ReportSimpleInfoForGptVO> result = workReportFeign.getFileContentListByIdsForGpt(param);
            if (result == null || result.getData() == null) {
                return null;
            }

            ReportSimpleInfoForGptVO gptVO = result.getData();
            if (gptVO.isOverToken()) {
                log.warn("汇报内容超过token限制，reportId={}", reportId);
            }

            return gptVO.getContent();
        } catch (Exception e) {
            log.error("获取汇报内容失败，reportId={}, error={}", reportId, e.getMessage(), e);
            return null;
        }
    }

    private String callAi(String prompt) {
        AiModel model = new AiModel();
        model.setType(AI_TYPE);
        model.setPrompt(prompt);
        model.setTemperature(0);
        model.setBizCode(BIZ_CODE);

        Result<AiData> result = filegptFeign.getScript(model);
        if (result == null || result.getData() == null || StringUtils.isBlank(result.getData().getAnswer())) {
            return null;
        }

        return cleanAiJsonOutput(result.getData().getAnswer());
    }

    private JSONObject parseJson(String json) {
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            log.error("JSON解析失败, error={}", e.getMessage());
            return null;
        }
    }

    String cleanAiJsonOutput(String raw) {
        if (StringUtils.isBlank(raw)) {
            return raw;
        }
        String cleaned = raw.trim();

        Pattern thinkPattern = Pattern.compile("(?is)<think>(.*?)</think>");
        Matcher thinkMatcher = thinkPattern.matcher(cleaned);
        cleaned = thinkMatcher.replaceAll("").trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        int start = cleaned.indexOf('{');
        if (start < 0) {
            return cleaned;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return cleaned.substring(start, i + 1);
                }
            }
        }
        return cleaned.substring(start);
    }
}
