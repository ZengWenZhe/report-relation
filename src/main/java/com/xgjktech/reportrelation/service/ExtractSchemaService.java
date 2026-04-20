package com.xgjktech.reportrelation.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import com.xgjktech.cloud.common.Result;
import com.xgjktech.reportrelation.base.feign.FilegptFeign;
import com.xgjktech.reportrelation.base.feign.PmsFeign;
import com.xgjktech.reportrelation.base.feign.WorkReportFeign;
import com.xgjktech.reportrelation.base.model.ReportListSimpleInfoByIdsParam;
import com.xgjktech.reportrelation.base.model.ReportSimpleInfoForGptVO;
import com.xgjktech.reportrelation.base.param.AiData;
import com.xgjktech.reportrelation.base.param.AiModel;
import com.xgjktech.reportrelation.data.entity.ReportRelationBusinessEntity;
import com.xgjktech.reportrelation.enums.ExtractPromptCodeEnum;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 汇报结构化提取服务
 * 负责调度AI对汇报内容进行结构化提取
 *
 * @author zengwenzhe
 * @since 2026-04-20
 */
@Slf4j
@Service
public class ExtractSchemaService {

    private static final int AI_TYPE = 14;

    private static final String BIZ_CODE = "bp_report_relation_extract";

    private static final int DEFAULT_BATCH_SIZE = 20;

    private static final int CONCURRENT_THREADS = 5;

    private final ExecutorService extractExecutor = Executors.newFixedThreadPool(CONCURRENT_THREADS);

    @Resource
    private ReportRelationBusinessService reportRelationBusinessService;

    @Resource
    private ExtractPromptConfigService extractPromptConfigService;

    @Resource
    private WorkReportFeign workReportFeign;

    @Resource
    private PmsFeign pmsFeign;

    @Resource
    private FilegptFeign filegptFeign;

    @PreDestroy
    public void shutdown() {
        extractExecutor.shutdown();
    }

    /**
     * 批量提取（并发执行，线程数=CONCURRENT_THREADS）
     *
     * @param limit 本次最大处理条数，0或负数使用默认值
     * @return 成功提取的数量
     */
    public int batchExtract(int limit) {
        int batchSize = limit > 0 ? limit : DEFAULT_BATCH_SIZE;
        List<ReportRelationBusinessEntity> pendingList =
                reportRelationBusinessService.listPendingExtract(batchSize);

        if (pendingList.isEmpty()) {
            log.info("无待提取记录");
            return 0;
        }

        log.info("开始批量提取，待处理数量={}，并发线程数={}", pendingList.size(), CONCURRENT_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);

        CompletableFuture.allOf(pendingList.stream()
                .map(record -> CompletableFuture.runAsync(() -> {
                    try {
                        extractSingle(record);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("提取失败，id={}, reportId={}, bizId={}, error={}",
                                record.getId(), record.getReportId(), record.getBizId(), e.getMessage(), e);
                        reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
                    }
                }, extractExecutor)).toArray(CompletableFuture[]::new)).join();

        log.info("批量提取完成，总数={}，成功={}", pendingList.size(), successCount.get());
        return successCount.get();
    }

    /**
     * 单条提取
     */
    public void extractSingle(ReportRelationBusinessEntity record) {
        log.info("开始结构化提取，id={}, reportId={}, bizId={}",
                record.getId(), record.getReportId(), record.getBizId());

        reportRelationBusinessService.updateExtractStatus(record.getId(), 1, null);

        String reportContent = fetchReportContent(record.getReportId());
        if (StringUtils.isBlank(reportContent)) {
            log.warn("汇报内容为空，跳过提取，reportId={}", record.getReportId());
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
            return;
        }

        String bpContextJson = fetchBpContext(record.getBizId());

        String promptTemplate = extractPromptConfigService.getPromptByCode(
                ExtractPromptCodeEnum.REPORT_EXTRACT_SCHEMA);
        String prompt = promptTemplate
                .replace("{{REPORT_CONTENT}}", reportContent)
                .replace("{{BP_CONTEXT}}", StringUtils.defaultString(bpContextJson, "{}"));

        AiModel model = new AiModel();
        model.setType(AI_TYPE);
        model.setPrompt(prompt);
        model.setTemperature(0);
        model.setBizCode(BIZ_CODE);

        Result<AiData> result = filegptFeign.getScript(model);
        if (result == null || result.getData() == null || StringUtils.isBlank(result.getData().getAnswer())) {
            log.warn("AI结构化提取返回为空，reportId={}", record.getReportId());
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
            return;
        }

        String answer = cleanAiJsonOutput(result.getData().getAnswer());
        log.info("AI结构化提取成功，reportId={}, answer前100字={}",
                record.getReportId(), StringUtils.abbreviate(answer, 100));

        try {
            JSON.parseObject(answer);
        } catch (Exception e) {
            log.error("AI返回结果JSON解析失败，reportId={}, answer={}", record.getReportId(), answer, e);
            reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
            return;
        }

        reportRelationBusinessService.updateExtractStatus(record.getId(), 2, answer);
        log.info("结构化提取完成，id={}, reportId={}", record.getId(), record.getReportId());
    }

    /**
     * 获取汇报完整内容（正文+附件+回复）
     */
    private String fetchReportContent(Long reportId) {
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

    /**
     * 获取BP上下文 markdown（从 getBpContext 接口返回的 Map 中提取）
     */
    private String fetchBpContext(Long taskId) {
        try {
            Map<String, Object> data = fetchBpContextMap(taskId);
            if (data != null) {
                Object md = data.get("markdown");
                return md != null ? md.toString() : null;
            }
        } catch (Exception e) {
            log.error("获取BP上下文失败，taskId={}, error={}", taskId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取BP上下文完整 Map（包含 markdown / corpId / groupId 等元数据）
     */
    public Map<String, Object> fetchBpContextMap(Long taskId) {
        try {
            Result<Map<String, Object>> result = pmsFeign.getBpContext(taskId);
            if (result != null && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            log.error("获取BP上下文失败，taskId={}, error={}", taskId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 清理AI返回的JSON（去除markdown代码块标记，提取第一个完整JSON对象）
     */
    private String cleanAiJsonOutput(String raw) {
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
