package com.xgjktech.reportrelation.service;

import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xgjktech.reportrelation.base.NacosConfig;
import com.xgjktech.reportrelation.exception.RateLimitException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiClient {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private static final String RATE_LIMIT_KEY = "ai:rate:limit:hourly";

    private static final long ONE_HOUR_MS = 3600_000L;

    @Resource
    private NacosConfig nacosConfig;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @return AI 回复文本，异常时返回 null
     * @throws RateLimitException 触发限流时抛出，调用方据此决定是否终止本轮
     */
    public String chat(String prompt) {
        if (!acquireRateLimit()) {
            throw new RateLimitException("AI调用触发小时限流(" + nacosConfig.getAiRateLimitHourly() + "次/h)");
        }

        try {
            return doChat(prompt);
        } catch (Exception e) {
            log.error("AI调用异常, error={}", e.getMessage(), e);
            return null;
        }
    }

    private String doChat(String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", nacosConfig.getAiApiModel());
        body.put("max_tokens", nacosConfig.getAiApiMaxTokens());

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);
        body.put("messages", messages);

        int timeout = nacosConfig.getAiApiTimeoutSeconds() != null ? nacosConfig.getAiApiTimeoutSeconds() : 120;
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(nacosConfig.getAiApiUrl())
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + nacosConfig.getAiApiKey())
                .post(RequestBody.create(JSON_MEDIA, body.toJSONString()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("AI接口HTTP错误, code={}, body={}", response.code(),
                        response.body() != null ? response.body().string() : "null");
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : null;
            if (StringUtils.isBlank(responseBody)) {
                log.error("AI接口返回空响应");
                return null;
            }

            return parseResponse(responseBody);
        }
    }

    private String parseResponse(String responseBody) {
        JSONObject resp = JSON.parseObject(responseBody);

        JSONObject baseResp = resp.getJSONObject("base_resp");
        if (baseResp != null && baseResp.getIntValue("status_code") != 0) {
            log.error("AI接口业务错误, status_code={}, status_msg={}",
                    baseResp.getIntValue("status_code"), baseResp.getString("status_msg"));
            return null;
        }

        String stopReason = resp.getString("stop_reason");
        if (stopReason != null && !"end_turn".equals(stopReason)) {
            log.warn("AI接口stop_reason异常: {}", stopReason);
        }

        JSONArray contentArr = resp.getJSONArray("content");
        if (contentArr == null || contentArr.isEmpty()) {
            log.error("AI接口content数组为空");
            return null;
        }

        return contentArr.stream()
                .map(item -> (JSONObject) item)
                .filter(item -> "text".equals(item.getString("type")))
                .map(item -> item.getString("text"))
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    /**
     * 只读预检：当前窗口是否已达限流上限（不消耗配额）
     */
    public boolean isRateLimited() {
        int limit = nacosConfig.getAiRateLimitHourly() != null ? nacosConfig.getAiRateLimitHourly() : 2000;
        long windowStart = System.currentTimeMillis() - ONE_HOUR_MS;
        stringRedisTemplate.opsForZSet().removeRangeByScore(RATE_LIMIT_KEY, 0, windowStart);
        Long count = stringRedisTemplate.opsForZSet().zCard(RATE_LIMIT_KEY);
        return count != null && count >= limit;
    }

    private boolean acquireRateLimit() {
        int limit = nacosConfig.getAiRateLimitHourly() != null ? nacosConfig.getAiRateLimitHourly() : 2000;
        long now = System.currentTimeMillis();
        long windowStart = now - ONE_HOUR_MS;

        stringRedisTemplate.opsForZSet().removeRangeByScore(RATE_LIMIT_KEY, 0, windowStart);

        Long count = stringRedisTemplate.opsForZSet().zCard(RATE_LIMIT_KEY);
        if (count != null && count >= limit) {
            return false;
        }

        stringRedisTemplate.opsForZSet().add(RATE_LIMIT_KEY, String.valueOf(now) + ":" + Thread.currentThread().getId(), now);
        stringRedisTemplate.expire(RATE_LIMIT_KEY, 1, TimeUnit.HOURS);
        return true;
    }
}
