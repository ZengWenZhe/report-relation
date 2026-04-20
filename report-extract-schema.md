# 汇报结构化提取 Schema

> **文件用途**：定义 Phase 2.5（新增，位于 merge_evidence 之后、phase3_prepare 之前）中，
> AI 对每篇汇报的 `content` + `reply` 进行结构化提取的标准输出格式。
>
> **输出文件**：`process/report_extract_{reportId}.json`（每篇汇报一个文件）
>
> **下游消费**：`phase3_prepare.py` 读取此文件，拼合 `judgment_input_{actionId}.md`，
> 不再直接拼合 content 全文。

---

## 设计约束

Phase 2.5 提取时，AI 的输入**仅包含**：

1. 当前汇报的 `content` + `reply`
2. `bpContext`：当前汇报关联的举措 / KR / 目标信息（由程序从 goal_data 填入）

**不包含**：其他 BP 节点信息、其他汇报内容、同 KR 下其他举措的情况。

因此，所有字段必须能在此输入范围内被 AI 可靠判断，否则移出此阶段。

> 移出的字段说明：
> - `support_level`（举措对 KR 的支撑强度）：需对比同 KR 下所有举措才能评估，移至 Phase 3c（KR 聚合层）
> - `cross_reference_nodes`（交叉引用）：需知道其他 BP 节点信息，Phase 2.5 无此输入，移出

---

## 一、完整 JSON Schema

```json
{
  "reportId":    "2013548661241520130",
  "reportTime":  "2026-01-20",
  "title":       "关于正式启动2026年度个人BP目标制定与系统录入工作的通知",

  "authorType":  "self",
  "report_type": "manual",

  "bpContext": {
    "goalId":              "P4939-8",
    "goalName":            "集团战略绩效管理体系已高效运行，质量与效果评估达标",
    "goalPlanDateRange":   "2026-01-01 ~ 2026-12-31",
    "krId":                "P4939-8.1",
    "krName":              "战略目标线上分解与绩效制度已全面生效且执行无偏差",
    "krMeasureStandard":   "…原样复制，去除<p></p>标签，不裁剪…",
    "krPlanDateRange":     "2026-01-01 ~ 2026-06-30",
    "actionId":            "P4939-8.1.1",
    "actionName":          "组织各层级完成BP初稿输出，澳门年会现场签约锁定",
    "actionPlanDateRange": "2026-01-01 ~ 2026-03-31"
  },

  "extract": {
    "actions_done": [
      {
        "description":   "发出启动通知，要求各中心1月28日前完成BP目标系统录入",
        "milestone":     "BP目标制定正式启动",
        "is_quantified": false
      }
    ],
    "quantitative_results": [
      {
        "metric":              "BP录入截止时间",
        "value":               "1月28日（已明确）",
        "vs_measure_standard": "on_track"
      }
    ],
    "milestone_reached": "启动通知已发出，系统操作培训已完成，各中心已知悉",
    "completion_progress": {
      "percent":     null,
      "stage":       "启动阶段",
      "description": "通知已发出，各中心录入工作正式推进中，尚未到截止日期"
    },
    "blockers": [
      {
        "description": "部分中心反馈系统操作不熟练，可能影响录入质量",
        "severity":    "low",
        "impact":      "若录入质量不达标，将影响后续BP锁定节奏，进而延迟澳门年会签约",
        "root_cause":  "培训时间距截止日较近，部分人员对新系统操作流程尚不熟悉"
      }
    ],
    "next_period_plan": "2月完成澳门年会现场BP签约锁定，跟进各中心录入完成情况",
    "reply_insights": [
      {
        "from":      "直属上级（姓名或职务）",
        "content":   "认可推进节奏，要求重点跟进各中心录入完成率",
        "sentiment": "positive"
      }
    ],
    "evidence_quality":        "strong",
    "evidence_quality_reason": "有明确发文节点（1月20日）+ 培训已完成 + 截止时间明确，证据链完整",
    "lamp_signal":          "GREEN",
    "lamp_signal_reason":   "启动通知已发出，截止时间明确，培训已完成，无偏差迹象",
    "lamp_signal_triggers": []
  }
}
```

---

## 二、字段说明

### 元数据区（程序填写，不经过 AI）

| 字段 | 类型 | 说明 |
|------|------|------|
| `reportId` | string | 汇报唯一ID，原样使用，禁止数值转换 |
| `reportTime` | string | 汇报发出时间（businessTime），格式 `YYYY-MM-DD` |
| `title` | string | 汇报标题原文 |
| `authorType` | enum | `self`=本人主动撰写（主证据）/ `other`=他人汇报关联到本节点（辅证） |
| `report_type` | enum | `manual`=手动撰写 / `ai_generated`=AI自动生成。用于附录统计分类 |

### BP 上下文区（程序从 goal_data JSON 填入，AI 提取时参考）

| 字段 | 类型 | 说明 |
|------|------|------|
| `bpContext.goalId` | string | 目标ID |
| `bpContext.goalName` | string | 目标名称（去除HTML标签） |
| `bpContext.goalPlanDateRange` | string | 目标计划时间范围，格式 `YYYY-MM-DD ~ YYYY-MM-DD`。提供整体时间背景 |
| `bpContext.krId` | string | 关键成果ID |
| `bpContext.krName` | string | 关键成果名称（去除HTML标签） |
| `bpContext.krMeasureStandard` | string | **衡量标准原文，不裁剪**。AI 判断 `quantitative_results` 的 `vs_measure_standard` 时必须参照此字段 |
| `bpContext.krPlanDateRange` | string | KR 计划时间范围，格式 `YYYY-MM-DD ~ YYYY-MM-DD`。判断 KR 层面进度偏差时参照 |
| `bpContext.actionId` | string | 举措ID |
| `bpContext.actionName` | string | 举措名称（去除HTML标签） |
| `bpContext.actionPlanDateRange` | string | 举措计划时间范围，格式 `YYYY-MM-DD ~ YYYY-MM-DD`。**AI 判断时间类触发条件时的主要参照** |

### AI 提取区（AI 从 content + reply 中提取）

#### `actions_done[]` — 本期推进动作
**对应报告**：举措「推进动作摘要（1-3句）」

| 子字段 | 类型 | 说明 |
|--------|------|------|
| `description` | string | 本期做了什么，一句话描述 |
| `milestone` | string \| null | 是否达到明确里程碑节点（如：制度发布、系统上线、合同签署）。无则填 `null` |
| `is_quantified` | boolean | 该动作是否有量化数据支撑（数字/百分比/数量） |

---

#### `quantitative_results[]` — 量化结果（对标 KR 衡量标准）
**对应报告**：KR「本月结果」、「距离衡量标准」

> ⚠️ AI 必须参照 `bpContext.krMeasureStandard` 来判断 `vs_measure_standard`
> ⚠️ **content 中无量化数据时，`vs_measure_standard` 必须填 `no_data`，禁止推断或凭感觉判断**

| 子字段 | 类型 | 说明 |
|--------|------|------|
| `metric` | string | 指标名称（如：覆盖率、录入完成数、达成率）。必须来自 content 原文，不得推断 |
| `value` | string | 具体数值或描述。必须来自 content 原文，不得推断 |
| `vs_measure_standard` | enum | `ahead`=content 中有明确数据显示超出衡量标准 / `on_track`=有明确数据显示符合预期 / `gap`=有明确数据显示落后 / `no_data`=content 无量化依据，禁止推断 |

若内容中无任何量化信息，此数组为空 `[]`。

---

#### `milestone_reached` — 关键里程碑
**对应报告**：目标「承诺与实际对照 → 本月实际」

- 类型：string | null
- 本月内达到的最重要里程碑，一句话
- 无里程碑则填 `null`

---

#### `completion_progress` — 当前进度
**对应报告**：举措「当前进度：[完成度或阶段] — 一句话」

| 子字段 | 类型 | 说明 |
|--------|------|------|
| `percent` | integer \| null | content 中**明确出现**的完成度数字（整数），content 未提及则填 `null`，禁止推算 |
| `stage` | string | 见下方判断规则表。四选一，不得自造值 |
| `description` | string | 一句话进度说明，必须基于 content 原文概括，不得推断 |

**`stage` 判断规则（按优先级匹配，命中即停）：**

| stage | 判断依据（基于 content 描述） |
|-------|-----------------------------|
| `未启动` | content 无任何推进动作描述，或仅有计划意向表述 |
| `启动阶段` | content 提到首次发起/立项/启动/发出通知/开始筹备等 |
| `推进阶段` | content 描述持续推进中，已有实质动作但未完成 |
| `收尾阶段` | content 提到完成/交付/验收/结项/上线/发布等终态词 |

---

#### `blockers[]` — 偏差与风险
**对应报告**：「偏差问题与原因分析」—— 偏差点 / 影响 / 原因假设

> ⚠️ **所有子字段只提取 content 中明确出现的内容，content 未说明时填固定值，禁止推断**

| 子字段 | 类型 | 说明 |
|--------|------|------|
| `description` | string | 偏差或风险描述，**必须来自 content 原文**，一句话 |
| `severity` | enum | 基于 content 描述判断：`high`=明确提到无法完成/严重延误 / `medium`=提到明显偏差/需整改 / `low`=提到轻微问题/可控 |
| `impact` | string | content 明确描述了影响则提取；**content 未说明则填 `"汇报未说明"`**，禁止推断 |
| `root_cause` | string | content 明确描述了原因则提取；**content 未说明则填 `"汇报未说明"`**，禁止推断 |

无偏差时为空数组 `[]`。

---

#### `next_period_plan` — 下期计划
**对应报告**：「下月纠偏方向」

- 类型：string | null
- 本篇汇报中提到的下期计划，一句话。无则填 `null`

---

#### `reply_insights[]` — 上级回复要点
**对应报告**：JudgmentWorker 判断理由中的「上级视角」佐证

| 子字段 | 类型 | 说明 |
|--------|------|------|
| `from` | string | 回复人姓名或职务 |
| `content` | string | 回复核心内容，一句话 |
| `sentiment` | enum | `positive`=明确认可 / `concern`=提出担忧 / `directive`=明确指令 |

无实质回复时为空数组 `[]`。

---

#### `evidence_quality` — 证据质量评估
**对应**：辅助 Phase 3b 灯色预判

| 值 | 含义 |
|----|------|
| `strong` | 有明确里程碑 + 量化数据/交付物，证据链完整 |
| `moderate` | 有实质推进描述，但缺量化或交付物 |
| `weak` | 仅有计划意向性表述，无实质推进证据 |

配套字段 `evidence_quality_reason`（string）：判断依据，一句话。

> ⚠️ **与 `lamp_signal` 的区别**：
> `evidence_quality` = "这篇汇报写得质量如何"；
> `lamp_signal` = "内容反映的进展状态如何"。
> 两者独立：写得很充分的汇报（`strong`）也可以反映出黄/红信号。

---

#### `lamp_signal` — 单篇灯色信号
**对应**：举措多篇汇报聚合时的参考信号

> ⚠️ 这是**单篇信号**，举措最终灯色由 Phase 3b 聚合多篇后综合判断。
> 判断时间类触发条件须参照 `bpContext.actionPlanDateRange`。

| 值 | 含义 |
|----|------|
| `GREEN` | 本篇内容显示推进正常，无明显偏差 |
| `YELLOW` | 本篇内容显示有偏差/风险/明显滞后 |
| `RED` | 本篇内容显示严重偏离/失控 |

配套字段：
- `lamp_signal_reason`（string）：判断理由，一句话，必须基于 content + bpContext，不得凭感觉
- `lamp_signal_triggers`（string[]）：命中的触发条件

  > ⚠️ **只能从以下合法值中选取，逐字使用，禁止自造描述**

  | 合法触发条件值 | 对应灯色 |
  |--------------|--------|
  | `"关键节点完成率低于50%，且滞后明显"` | YELLOW |
  | `"实际完成时间晚于计划超过2周，已影响后续关键节奏"` | YELLOW |
  | `"存在高风险已触发，不干预将大概率无法达成"` | YELLOW |
  | `"关键节点基本未完成，后续计划无法推进"` | RED |
  | `"实际完成时间严重滞后（超过1个月），已错过交付窗口"` | RED |
  | `"风险已造成不可逆影响，成果基本失效"` | RED |

  GREEN 时为空数组 `[]`。

---

## 三、字段总索引（共 18 个）

| # | 字段路径 | 填写方 | 对应报告位置 |
|---|---------|--------|------------|
| 1 | `reportId` | 程序 | 附录 R编号索引 |
| 2 | `reportTime` | 程序 | 附录证据台账 |
| 3 | `title` | 程序 | 附录台账标题列 |
| 4 | `authorType` | 程序 | 附录证据级别列 |
| 5 | `report_type` | 程序 | 附录统计摘要 |
| 6 | `bpContext.*` | 程序 | AI 提取上下文（不输出报告） |
| 7 | `extract.actions_done` | AI | 举措「推进动作摘要（1-3句）」 |
| 8 | `extract.quantitative_results` | AI | KR「本月结果」+「距离衡量标准」 |
| 9 | `extract.milestone_reached` | AI | 目标「承诺与实际对照→本月实际」 |
| 10 | `extract.completion_progress` | AI | 举措「当前进度」 |
| 11 | `extract.blockers[].description` | AI | 「偏差点」 |
| 12 | `extract.blockers[].severity` | AI | 偏差严重程度（辅助灯色） |
| 13 | `extract.blockers[].impact` | AI | 「影响」 |
| 14 | `extract.blockers[].root_cause` | AI | 「原因假设」 |
| 15 | `extract.next_period_plan` | AI | 「下月纠偏方向」 |
| 16 | `extract.reply_insights` | AI | 判断理由中的上级视角 |
| 17 | `extract.evidence_quality` | AI | 辅助灯色预判 |
| 18 | `extract.lamp_signal` + triggers | AI | 举措聚合时的单篇灯色信号 |

---

## 四、移出字段说明

以下字段在评审中因**超出 Phase 2.5 输入边界**而移出：

| 字段 | 移出原因 | 建议阶段 |
|------|---------|---------|
| `support_level` | 评估"举措对 KR 的贡献强度"需对比同 KR 下全部举措，单篇提取时无此信息 | Phase 3c（KR 聚合层）综合所有举措提取结果后评估 |
| `cross_reference_nodes` | 需要知道其他 BP 节点信息（nodeId/nodeName），Phase 2.5 输入中不包含 | Phase 4 证据索引生成时（已有全量 BP 结构）处理 |

---

## 五、拼合后的 judgment_input Markdown 格式

`phase3_prepare.py` 读取 `report_extract_{reportId}.json`，
拼合为 `judgment_input_{actionId}.md`，格式如下：

```markdown
## {title}

> 时间: {reportTime} | {self ? "本人主证" : "他人辅证"} | {manual ? "手动" : "AI生成"} | 证据强度: {evidence_quality} | 单篇信号: {lamp_signal}

**推进动作：**
- {actions_done[0].description}（里程碑：{milestone ?? "无"}）
- {actions_done[1].description}（如有）

**当前进度：** {stage} — {description}（完成度：{percent ?? "无量化"}）

**量化结果：**
- {metric}：{value}（vs 衡量标准：{vs_measure_standard}）

**上级回复：**
- {from}：{content}（{sentiment}）

**偏差与风险：**
- [{severity}] {description}
  - 影响：{impact}
  - 原因假设：{root_cause}

**下期计划：** {next_period_plan ?? "未提及"}

**单篇灯色信号：** {lamp_signal}（{lamp_signal_reason}）
{triggers.length > 0 ? "触发条件：" + triggers.join("、") : ""}

---
```

---

## 六、枚举值速查

| 字段 | 合法值 |
|------|--------|
| `authorType` | `self` / `other` |
| `report_type` | `manual` / `ai_generated` |
| `vs_measure_standard` | `ahead` / `on_track` / `gap` / `no_data` |
| `blockers[].severity` | `high` / `medium` / `low` |
| `reply_insights[].sentiment` | `positive` / `concern` / `directive` |
| `evidence_quality` | `strong` / `moderate` / `weak` |
| `lamp_signal` | `GREEN` / `YELLOW` / `RED` |
| `completion_progress.stage` | `启动阶段` / `推进阶段` / `收尾阶段` / `未启动` |

---


