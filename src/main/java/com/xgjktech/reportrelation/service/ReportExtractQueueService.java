package com.xgjktech.reportrelation.service;

import java.util.Collections;
import java.util.List;
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
     * 入队：先检查是否已存在，不存在则 ZADD，score 为当前时间戳。
     * 非严格原子操作，但 ZSet 天然去重，极端并发下仅可能覆盖 score，不影响正确性。
     */
    public void enqueue(Long reportId) {
        if (reportId == null) {
            return;
        }
        String member = String.valueOf(reportId);
        Double existingScore = stringRedisTemplate.opsForZSet().score(QUEUE_KEY, member);
        if (existingScore != null) {
            log.debug("reportId={} 已在队列中，跳过", reportId);
            return;
        }
        stringRedisTemplate.opsForZSet().add(QUEUE_KEY, member, System.currentTimeMillis());
        log.debug("reportId={} 入队成功", reportId);
    }

    /**
     * 批量出队：Lua 脚本原子操作，取出 count 条并从 ZSet 中移除
     */
    public List<Long> batchDequeue(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        List<String> result = stringRedisTemplate.execute(
                batchDequeueScript,
                Collections.singletonList(QUEUE_KEY),
                String.valueOf(count));

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
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
