# report-relation 项目代码评审报告

> 评审人：Codex（需求方/验收方）
> 被评审项目：https://github.com/ZengWenZhe/report-relation
> 评审时间：2026-04-21
> 角色定位：需求方/验收方，不做代码实现，只提供评审建议

---

## 一、总体评价

项目整体架构与我们的方案设计高度吻合，核心实现路径正确。代码结构清晰，MQ 监听、BP 上下文获取、AI 提取三段式设计符合方案预期。

**结论：有条件通过，建议按评审意见优化后继续推进。**

---

## 二、架构符合性评审

### ✅ 符合设计的地方

| 方案设计 | 代码实现 | 评价 |
|---------|---------|------|
| MQ 监听关联变更 | `ReportRelationEventService` 监听 `ex_cwork_report_object_change` | ✅ 完全一致 |
| BP 类型 ID 过滤 | `BP_TYPE_ID = 52L`，通过 `typeId` 过滤 BP 标签 | ✅ 一致 |
| 关联关系存储 | `report_relation_business` 表，(reportId, bizType, bizId) | ✅ 一致 |
| BP 上下文获取 | `PmsFeign.getBpContext(taskId)` 获取 Action/KR/Goal | ✅ 一致 |
| 汇报内容获取 | `WorkReportFeign` 获取正文/附件/回复 | ✅ 一致 |
| 模板 Prompt | `ExtractPromptConfigService` 动态加载 Prompt | ✅ 一致 |
| AI 提取 + JSON 输出 | `FilegptFeign` 调用 AI，输出 JSON | ✅ 一致 |
| 批量处理 | `batchExtract(limit)` 定时任务驱动 | ✅ 一致 |
| 缓存失效 | 通过 MQ 事件触发（`ex_cwork_report_object_change`） | ✅ 一致 |

---

## 三、Critical 问题

### C-1：只监听了"关联变更"MQ，缺少"汇报内容变更"监听

**问题描述：**

当前 `ReportRelationEventService` 只监听了 `ex_cwork_report_object_change`（汇报关联对象变更），用于处理：
- 新增 BP 关联 → 创建处理记录
- 删除 BP 关联 → 逻辑删除处理记录

但根据陈伟 MQ 规范，**"汇报信息变更"（`ex_cwork_report_info_change`）** 也需要监听，用于：
- 汇报正文变更 → 标记缓存失效，触发重新提取
- 新增回复 → 标记缓存失效
- 附件变更 → 标记缓存失效

**当前代码中完全没有这部分实现。**

**影响：** 当汇报内容发生变更时，已缓存的 `extractSchema` 不会更新，BP 系统拿到的可能是过期数据。

**建议：** 增加对 `ex_cwork_report_info_change` 的监听：

```java
@RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "queue_report_relation_report_info_change"),
        exchange = @Exchange(name = "ex_cwork_report_info_change", type = ExchangeTypes.FANOUT)))
public void listenReportInfoChanged(String message) {
    // typeList 包含 "content" → 标记缓存失效
    // typeList 包含 "reply" → 标记缓存失效
    // typeList 包含 "attachment" → 标记缓存失效
}
```

---

### C-2：BP 上下文获取存在重复调用风险

**问题描述：**

`fetchBpContext()` 方法每次提取都单独调用 `pmsFeign.getBpContext(bizId)`。

但 BP 上下文（Goal/KR/Action 信息）相对稳定，一个 BP 可能有几十篇关联汇报。如果每篇汇报都单独获取 BP 上下文，会造成大量重复调用。

**建议：** 增加 BP 上下文缓存（按 bizId 缓存 5-10 分钟），减少重复调用：

```java
private String fetchBpContext(Long bizId) {
    // 增加本地缓存或 Redis 缓存
    // key: "bp_context:{bizId}", ttl: 10分钟
}
```

---

## 四、Important 问题

### I-1：AI 返回 JSON 解析失败后无降级处理

**位置：** `ExtractSchemaService.extractSingle()` 第 130 行附近

```java
try {
    JSON.parseObject(answer);
} catch (Exception e) {
    log.error("AI返回结果JSON解析失败，reportId={}, answer={}", record.getReportId(), answer, e);
    reportRelationBusinessService.updateExtractStatus(record.getId(), 3, null);
    return;
}
```

**问题：** AI 返回非 JSON 时，直接标记失败，没有任何降级处理（比如保留原始回答作为文本）。

**建议：** 增加 fallback 逻辑——如果 JSON 解析失败，保存原始回答到 `extractSchema` 字段，status 标记为特殊状态（如 4=需人工确认），便于人工修复。

---

### I-2：批量处理无并发控制，高并发场景可能打爆 AI 服务

**位置：** `batchExtract()` 第 70 行附近

```java
for (ReportRelationBusinessEntity record : pendingList) {
    extractSingle(record);  // 串行执行
    successCount++;
}
```

**问题：** 当前是串行处理，如果 `limit=50`，50 次 AI 调用串行执行，耗时长。

**建议：** 如果未来并发量增加，可以考虑增加并发控制（但当前阶段串行是可接受的，先不做）。

---

### I-3：`fetchReportContent` 返回格式未定义

**位置：** `ExtractSchemaService.fetchReportContent()`

**问题：** 方法只声明了返回 `String`，但未看到完整实现。不确定返回的格式是纯文本、Markdown 还是 JSON。

**建议：** 确认 `WorkReportFeign` 返回的内容格式（是否去 HTML？附件如何处理？），并在代码注释中明确说明。

---

### I-4：缺少 `updateTime` 比较逻辑，缓存刷新依据不明确

**问题：** 当 MQ 事件触发缓存失效时，没有看到 `reportUpdateTime` 与 `lastSyncTime` 的比较逻辑。

**建议：** 增加：只有当 `reportUpdateTime` > `lastSyncTime` 时，才真正触发重新提取，避免重复处理。

---

### I-5：`cleanAiJsonOutput` 依赖深度足够的 `}` 结尾

**位置：** `ExtractSchemaService.cleanAiJsonOutput()`

```java
if (depth == 0) {
    return cleaned.substring(start, i + 1);
}
```

**问题：** 如果 AI 返回的 JSON 被截断（末尾的 `}` 缺失），这个方法会返回整个字符串而不是有效 JSON。

**建议：** 增加 `try-catch`，如果最终解析仍失败，抛出明确异常而不是返回脏数据。

---

## 五、Minor 问题

### M-1：`ExtractPromptConfigService` 的 Prompt 版本管理缺失

当前 `getPromptByCode()` 每次都从数据库加载，如果 Prompt 需要更新（换词、换 Schema），已缓存的结果不会受影响，但新的提取会使用新 Prompt。

**建议：** 这个问题不大，先保持现状，等模板管理需求明确后再处理。

### M-2：日志中的敏感信息

```java
log.info("AI结构化提取成功，reportId={}, answer前100字={}", ...);
```

如果汇报内容涉及敏感信息，打印日志可能泄露。

**建议：** 将 `answer` 改为只打印长度，不打印内容：`StringUtils.abbreviate(answer, 100)` 本身是截断的，这个没问题，但如果原始 answer 很长，截断后可能仍包含敏感字段。建议确认日志规范。

### M-3：缺少对 `bizId` 类型转换异常的捕获

`handleAddRelation` 中有 `NumberFormatException` 处理：

```java
Long bpTaskId = Long.parseLong(obj.getBizId());
```

但 `bizId` 在这里已经是 `Long` 类型（来自 `ReportObjectParam`），不需要 parse。

**建议：** 确认 `ReportObjectParam.bizId` 的实际类型。

---

## 六、JSON Schema 字段对比（report-extract-schema.md vs 最终确认版）

> ⚠️ 重要：`report-extract-schema.md` 是 bp-monthly-report 项目中定义的原始 Schema，与我们在 cms-work-adapter 项目中最终确认的 Schema 有较大差异。

### 原始 Schema（report-extract-schema.md）

包含字段：
- `reportId`, `reportTime`, `title`, `authorType`, `report_type`（元数据）
- `bpContext.*`（BP 上下文）
- `extract.actions_done`, `extract.quantitative_results`（含 `vs_measure_standard`）
- `extract.completion_progress`, `extract.blockers`（含 `severity`）
- `extract.reply_insights`, `extract.evidence_quality`, `extract.lamp_signal`

### 最终确认 Schema（cms-work-adapter）

7 个字段：
- `content`, `quantitativeResults`, `actionsDone`, `blockers`, `milestoneReached`, `nextPeriodPlan`

### 差异处理建议

1. **如果继续使用 `report-extract-schema.md` 作为 Prompt 模板**：需要同步更新 Prompt，确保 AI 输出的 JSON 与当前项目最终确认的 7 字段 Schema 一致
2. **如果需要切换到最终确认的 7 字段 Schema**：需要修改 `ExtractPromptConfig` 中的 Prompt 模板，并更新数据库中已存储的历史数据

**建议：** 明确 `report-extract-schema.md` 和 cms-work-adapter 最终 Schema 的关系——是同一套 Schema 的不同版本，还是两个独立项目的不同定义？

---

## 七、总结

| 级别 | 数量 | 说明 |
|------|------|------|
| Critical | 2 | C-1: 缺汇报内容变更 MQ 监听；C-2: BP 上下文重复调用 |
| Important | 5 | I-1~I-5：JSON 降级、并发控制、内容格式、缓存刷新、日志 |
| Minor | 3 | M-1~M-3 |

**优先级建议：**
1. **立即修复**：C-1（汇报内容变更 MQ 监听）
2. **近期优化**：C-2（BP 上下文缓存）、I-1（JSON 解析失败降级）
3. **后续迭代**：I-2~I-5、M-1~M-3

**总体建议：** 核心架构设计正确，C-1 是当前最大的功能缺口（汇报内容变更不会触发重新提取），建议优先补充。
