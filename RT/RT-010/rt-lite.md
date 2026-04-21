# Plan-Lite: RT-010 - cms-work-adapter 系统设计

> **Profile**: Spec-Lite
> **Status**: 需求确认完成，待成伟补充阶段一方案 + BP API 确认

---

## 1. 修改点（Change Points）

**本次 RT 的交付物：**

1. 系统架构设计文档（Architecture Document）
   - 适配器在整体系统中的定位
   - 与工作协同系统、BP 系统的交互关系
   - 混合调用模型（工作协同→Processing Core: Push；BP→Processing Core: Pull）

2. 输出 JSON Schema 正式版（已确认）
   - 7 个字段，详见 §2

3. API 接口设计草案
   - 待 BP 系统 API 确认后补充

---

## 2. 方案描述（Solution Outline）

### 2.1 系统定位

**cms-work-adapter**（工作协同内容适配器）：工作协同系统与 BP 系统之间的数据交换规范层。

```
工作协同系统（Provider）
    ↓ 原始汇报（人阅读格式）
适配器（cms-work-adapter）
    └─ 阶段二：汇报 + BP 详情 → BP 专属结构化提取 JSON
BP 系统（Consumer）
```

**调用模型（混合模型）：**
- 工作协同侧 → Processing Core：**Push**（MQ 事件驱动）
- BP 侧 → Processing Core：**Pull**（BP 主动调 API 获取结果）
- Processing Core 内部：Redis Buffer 削峰，Scheduler 定时拉取

**缓存**：按 (report_id, bp_id) 组合缓存，汇报变更时失效重加工。

---

### 2.2 核心处理逻辑

**阶段二（本次 RT 范围）：**

```
输入：
  - BP ID（来自 BP 系统调用时携带）
  - Report ID（来自 BP 系统调用时携带）

处理：
  1. 根据 BP ID 调用 BP 系统接口，获取 Action/KR/Goal 详情
  2. 根据 Report ID 调用工作协同接口，获取汇报原文
  3. AI 对比 BP 要求 ↔ 汇报原文
  4. 只输出与该 BP 相关的有效信息，无关内容丢弃
  5. 不做主观判断

输出：结构化 JSON
```

---

### 2.3 输出 JSON Schema（正式版）

```json
{
  "content": "string",

  "quantitativeResults": [
    {
      "reportedMetric": "string",
      "reportedValue": "string"
    }
  ],

  "actionsDone": [
    {
      "description": "string",
      "milestone": "string | null"
    }
  ],

  "blockers": [
    {
      "description": "string"
    }
  ],

  "milestoneReached": "string | null",

  "nextPeriodPlan": "string | null"
}
```

| 字段路径 | 类型 | 含义 |
|----------|------|------|
| `content` | string | AI 精炼摘要，仅涵盖与该 BP 相关的核心内容，1-3 句话 |
| `quantitativeResults[].reportedMetric` | string | 汇报原文提到的指标名（与 BP 相关） |
| `quantitativeResults[].reportedValue` | string | 汇报原文对应的数值 |
| `actionsDone[].description` | string | 本期推进动作，一句话描述 |
| `actionsDone[].milestone` | string \| null | 是否达到里程碑节点；无则 null |
| `blockers[].description` | string | 汇报中用户实际提到的风险/偏差点，原文提取 |
| `milestoneReached` | string \| null | 本月内达到的最重要里程碑；无则 null |
| `nextPeriodPlan` | string \| null | 汇报中提到的下期计划；无则 null |

---

### 2.4 适配器接口草案

**接口路径（草案，待确认）：**

```
GET /adapter/v1/extract
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `bpId` | string | 是 | BP/举措 ID |
| `reportId` | string | 是 | 汇报 ID |

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": "...",
    "quantitativeResults": [...],
    "actionsDone": [...],
    "blockers": [...],
    "milestoneReached": "...",
    "nextPeriodPlan": "..."
  }
}
```

> ⚠️ 具体接口路径和认证方式待与工作协同、BP 两边确认后修订。

---

## 3. 风险与注意事项（Risks & Caveats）

### 潜在风险

| 风险 | 影响 | 应对 |
|------|------|------|
| BP 系统 API 尚未确认 | 适配器无法完整实现 | 先行设计抽象接口层，具体实现后置 |
| 成伟阶段一方案未出 | 汇报原文获取方式不确定 | 先用 Mock 数据验证阶段二逻辑 |
| AI 提取质量不稳定 | 输出 JSON 字段为空或不准确 | 后续增加质量校验环节 |

### 需要注意的边界情形

- **汇报与 BP 完全不相关**：输出 `content` 精简为空 or 通用表述，其他字段为空数组
- **汇报内容为空**：正常返回空结构，不报错
- **BP ID / Report ID 不存在**：返回错误码，不返回空数据

---

## 4. 目标与验证（Goals & Verification）

| # | 目标 | 验证方式 |
|---|------|---------|
| 1 | 架构设计文档完成，包含系统定位、交互关系、混合调用模型说明 | 文档评审 |
| 2 | JSON Schema 7 个字段定义清晰，可直接用于 AI Prompt 编写 | Prompt 编写时直接引用 |
| 3 | API 接口草案完成，可与 BP 系统对接验证 | 联调测试 |

---

## 5. 实施边界（Execution Boundaries）

### 明确不做（Out of Scope）

- **阶段一实现**：文件包生成、缓存管理（待成伟方案）
- **BP 系统 API 对接实现**：具体接口调用逻辑（待 BP API 确认）
- **工作协同系统 API 对接实现**：具体接口调用逻辑（待成伟方案）
- **汇总逻辑**：在 BP 侧实现，不在适配器侧

### 是否存在无关重构/额外功能引入风险

- 无

### 下一步（Next RT）

- 等成伟阶段一方案 + BP API 确认后，进入详细实现设计
