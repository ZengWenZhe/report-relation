package com.xgjktech.reportrelation.service;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.WorkReportFeign;
import com.xgjktech.reportrelation.base.model.ReportListSimpleInfoByIdsParam;
import com.xgjktech.reportrelation.base.model.ReportSimpleInfoForGptVO;
import com.xgjktech.reportrelation.data.entity.ReportExtractConfigEntity;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.enums.ExtractConfigCodeEnum;
import com.xgjktech.reportrelation.exception.RateLimitException;

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

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ReportExtractConfigService reportExtractConfigService;

    @Resource
    private WorkReportFeign workReportFeign;

    @Resource
    private AiClient aiClient;


    /**
     * 可复用已有汇报内容的提取入口
     *
     * @param preloadedContent 预加载的汇报内容，null 时自行拉取
     * @return true=提取成功, false=提取失败
     */
    public boolean extractSingle(ReportRelationBusinessEntity record,
                                 ExtractConfigCodeEnum configCode,
                                 String bizContext,
                                 String contextPlaceholder,
                                 String preloadedContent) {
        log.info("开始结构化提取，id={}, reportId={}, bizId={}, bizType={}",
                record.getId(), record.getReportId(), record.getBizId(), record.getBizType());

        reportRelationBusinessService.updateExtractStatus(record.getId(), 1, null, null);

        try {
            return doExtract(record, configCode, bizContext, contextPlaceholder, preloadedContent);
        } catch (RateLimitException e) {
            log.warn("AI限流，id={}, reportId={}，状态回退为未提取", record.getId(), record.getReportId());
            reportRelationBusinessService.updateExtractStatus(record.getId(), 0, null, null);
            throw e;
        } catch (Exception e) {
            log.error("结构化提取异常，id={}, reportId={}, error={}",
                    record.getId(), record.getReportId(), e.getMessage(), e);
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null, null);
            return false;
        }
    }

    private boolean doExtract(ReportRelationBusinessEntity record,
                              ExtractConfigCodeEnum configCode,
                              String bizContext,
                              String contextPlaceholder,
                              String preloadedContent) {
        String reportContent = StringUtils.isNotBlank(preloadedContent) ? preloadedContent : fetchReportContent(record.getReportId());
        if (StringUtils.isBlank(reportContent)) {
            log.warn("汇报内容为空，跳过提取，reportId={}", record.getReportId());
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null, null);
            return false;
        }

        ReportExtractConfigEntity config = reportExtractConfigService.getActiveConfig(configCode, record.getBizType());
        Long configId = config.getId();

        String prompt = config.getConfigContent()
                .replace("{{REPORT_CONTENT}}", reportContent)
                .replace(contextPlaceholder, StringUtils.defaultString(bizContext, "{}"));

        String answer = callAi(prompt);
        if (answer == null) {
            log.warn("AI结构化提取返回为空，reportId={}", record.getReportId());
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null, configId);
            return false;
        }

        log.info("AI结构化提取成功，reportId={}, configId={}, answer前100字={}",
                record.getReportId(), configId, StringUtils.abbreviate(answer, 100));

        JSONObject jsonResult = parseJson(answer);
        if (jsonResult == null) {
            log.error("AI返回结果JSON解析失败，reportId={}, answer={}", record.getReportId(), answer);
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null, configId);
            return false;
        }

        String finalAnswer = jsonResult.toJSONString();

        reportRelationBusinessService.updateExtractStatus(record.getId(), 2, finalAnswer, configId);
        log.info("结构化提取完成，id={}, reportId={}, configId={}", record.getId(), record.getReportId(), configId);
        return true;
    }

    private static final int MAX_CONTENT_LENGTH = 150000;

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

            String content = gptVO.getContent();
            if (content != null && content.length() > MAX_CONTENT_LENGTH) {
                log.warn("汇报内容超过{}字，已截断，reportId={}, 原始长度={}", MAX_CONTENT_LENGTH, reportId, content.length());
                content = content.substring(0, MAX_CONTENT_LENGTH);
            }
            return content;
        } catch (Exception e) {
            log.error("获取汇报内容失败，reportId={}, error={}", reportId, e.getMessage(), e);
            return null;
        }
    }

    private String callAi(String prompt) {
        String rawAnswer = aiClient.chat(prompt);
        if (StringUtils.isBlank(rawAnswer)) {
            return null;
        }
        return cleanAiJsonOutput(rawAnswer);
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
