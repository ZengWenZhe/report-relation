# RT Intake - cms-work-adapter

> 状态：需求讨论基本完成，待正式立项
> 创建时间：2026-04-21
> 更新：2026-04-21（JSON Schema 已确认）

---

## 1. 原始描述（Raw Description）

- 用户原话（Evan）：
  > 工作协同系统和 BP 系统之间需要增加一种新的交换规范，而不是直接以工作协同的原文进行交换。
  > 工作协同是服务提供方，不知道消费者是谁。需要引入一个第三方适配器，对工作协同内容做二次深加工。
  > BP 系统消费适配器输出的数据，而非原始汇报。
  > 适配器通用模板：输入 BP ID + Report ID → 输出结构化举证材料（JSON）。

---

## 2. 架构定位

```
工作协同系统（Provider，内容提供方）
    ↓ 原始汇报（人阅读格式，含 HTML、讨论过程、附件）
适配器（新增系统）
    └─ 阶段二：汇报 + BP 详情 → BP 专属结构化提取 JSON
                      ↓
BP 系统（Consumer，主动调接口获取）
```

**关键约束：**
- 工作协同不知道 BP 的存在——纯粹内容提供方
- BP 不知道适配器的存在——只调接口，不关心数据怎么加工
- 适配器对两边都可见，是唯一的新增依赖方
- 适配器按 (report_id, bp_id) 组合独立处理，无法跨 BP 共享

**调用模型（混合模型）：**
- 工作协同侧 → Processing Core：**Push**（MQ 事件驱动：关联变更、内容变更、回复新增）
- Processing Core 内部 → Redis Buffer：**Pull**（Scheduler 定时 Pop）
- BP 侧 → Processing Core：**Pull**（BP 主动调 API 获取结果）

---

## 3. 核心处理逻辑（阶段二）

**输入：**
- BP ID → 适配器调用 BP 系统接口，获取 BP 详情（Action/KR/Goal 名称、衡量标准、时间范围）
- Report ID → 获取汇报原文（正文 + 评论 + 附件链接）

**处理：**
- AI 对比 BP 要求 ↔ 汇报原文
- 只输出与该 BP 相关的有效信息，无关内容全部丢弃
- 不做主观判断（相关性判断、证据质量判断、灯色判断均在 BP 汇总层）

**输出：BP 专属结构化提取 JSON**

---

## 4. 输出 JSON Schema（已确认）

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

### 字段定义

| 字段路径 | 类型 | 含义 |
|----------|------|------|
| `content` | string | AI 精炼摘要，仅涵盖与该 BP 相关的核心内容，1-3 句话 |
| `quantitativeResults[].reportedMetric` | string | 汇报原文提到的指标名（与 BP 相关） |
| `quantitativeResults[].reportedValue` | string | 汇报原文对应的数值 |
| `actionsDone[].description` | string | 本期推进动作，一句话描述 |
| `actionsDone[].milestone` | string \| null | 是否达到里程碑节点；无则 null |
| `blockers[].description` | string | 汇报中用户实际提到的风险/偏差点，原文提取，不加主观判断 |
| `milestoneReached` | string \| null | 本月内达到的最重要里程碑；无则 null |
| `nextPeriodPlan` | string \| null | 汇报中提到的下期计划；无则 null |

---

## 5. 需转派给工作协同负责人（成伟）的事项

### 事项 5.1：阶段一完整方案
- 当前阶段一描述为初步描述
- 需要成伟出具完整方案，包括：
  - 文件包目录结构
  - `index.md` 内容排版规范
  - 附件处理方式（链接格式、摘要提取方式）
  - 缓存失效策略
- **负责人：成伟**

### 事项 5.2：AI 汇报类型处理
- 以下类型的 AI 汇报应该如何处理？
  1. **搬运型 AI 汇报**：业务驱动，AI 对已有汇报整理汇总——无新信息，可忽略
  2. **业务系统调用发送接口产生的 AI 汇报**：全新信息，应纳入
- 工作协同侧应该在哪一步打标？
- **负责人：成伟**

---

## 6. 待确认问题

### Q1：BP 系统接口确认
- 适配器调用 BP 系统哪个接口获取 Action/KR/Goal 详情？
- BP 系统是否提供批量接口？
- **状态：Open**

### Q2：工作协同系统接口确认
- 适配器调用工作协同系统哪个接口获取汇报详情（正文、评论、附件）？
- **状态：Open**

### Q3：附件处理
- 附件内容是否纳入 AI 提取范围？
- 附件摘要（100-200 字）由谁提取？
- **状态：待成伟方案**

---

## 7. 类型与范围

- 类型：**新系统 / Feature**
- 预估影响范围：工作协同系统（无侵入）、BP 系统（新增接口调用）、新增适配器服务

---

## 8. 相关文档

- `references/report-extract-schema.md` — 汇报结构化提取 Schema 参考模板（来自 bp-monthly-report 项目，仅供参照）
- `../cms-bp-monthly-report/references/` — BP 月报系统相关参考
