package com.xgjktech.reportrelation.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExtractQueueService {

    private static final String QUEUE_KEY = "report:extract:queue";

    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<List<String>> batchDequeueScript;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @PostConstruct
    public void init() {
        batchDequeueScript = new DefaultRedisScript<>();
        batchDequeueScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/batch_dequeue.lua")));
        batchDequeueScript.setResultType((Class) List.class);
    }

    /**
     * 入队：ZADD 覆盖 score 为当前时间戳。
     * 若 reportId 已在队列中，更新 score（代表"这个时间点之后需要重新提取"）。
     * 用于：MQ 汇报变更、手动触发重提取等场景。
     */
    public void enqueue(Long reportId) {
        if (reportId == null) {
            return;
        }
        String member = String.valueOf(reportId);
        stringRedisTemplate.opsForZSet().add(QUEUE_KEY, member, System.currentTimeMillis());
        log.debug("reportId={} 入队/更新score成功", reportId);
    }

    /**
     * 用指定 score 回塞入队：若队列中已存在则不覆盖，不存在则用原始 score 写入。
     * 用于：失败重试回塞、限流回塞，保留原始入队时间戳，
     * 避免其他已成功的 record 被误判为需要重新提取。
     *
     * @param originalScore 原始出队时的 score（入队时间戳）
     */
    public void enqueueWithScore(Long reportId, long originalScore) {
        if (reportId == null) {
            return;
        }
        String member = String.valueOf(reportId);
        Double existingScore = stringRedisTemplate.opsForZSet().score(QUEUE_KEY, member);
        if (existingScore != null) {
            log.debug("reportId={} 已在队列中(score={})，跳过回塞", reportId, existingScore);
            return;
        }
        stringRedisTemplate.opsForZSet().add(QUEUE_KEY, member, originalScore);
        log.debug("reportId={} 回塞入队成功，score={}", reportId, originalScore);
    }

    /**
     * 批量出队：Lua 脚本原子操作，取出 count 条并从 ZSet 中移除。
     *
     * @return reportId → enqueueTimestamp（入队时间戳），保序
     */
    public Map<Long, Long> batchDequeue(int count) {
        if (count <= 0) {
            return Collections.emptyMap();
        }

        List<String> result = stringRedisTemplate.execute(
                batchDequeueScript,
                Collections.singletonList(QUEUE_KEY),
                String.valueOf(count));

        if (result == null || result.size() < 2) {
            return Collections.emptyMap();
        }

        Map<Long, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < result.size() - 1; i += 2) {
            Long reportId = Long.valueOf(result.get(i));
            long score = Double.valueOf(result.get(i + 1)).longValue();
            map.put(reportId, score);
        }
        return map;
    }

    public Long queueSize() {
        Long size = stringRedisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size != null ? size : 0L;
    }

    /**
     * 查看队列中所有待提取的reportId（仅查看不移除）
     */
    public List<Long> listQueuedReportIds(int limit) {
        Set<String> members = stringRedisTemplate.opsForZSet()
                .rangeByScore(QUEUE_KEY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, limit);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return members.stream().map(Long::valueOf).collect(Collectors.toList());
    }
}
