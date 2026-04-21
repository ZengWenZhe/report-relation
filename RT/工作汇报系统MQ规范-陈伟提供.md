# 工作汇报系统消息队列完整规范文档

> 来源：陈伟（成伟），2026-04-21
> 说明：本文档包含工作协同系统全部 14 种 MQ 事件及格式定义

## 事件索引

| # | 事件名称 | 交换机 | 用途 |
|---|---------|--------|------|
| 1 | 提交汇报 | `ex_cwork_report_submit` | |
| 2 | 签批汇报完成 | `ex_cwork_report_sign_finish` | |
| 3 | 汇报完成 | `ex_cwork_report_finish` | |
| 4 | 创建工作任务 | `ex_cwork_plan_create` | |
| 5 | 更新工作任务 | `ex_cwork_plan_update` | |
| 6 | 关闭任务 | `ex_cwork_report_plan_closed` | |
| 7 | 重启任务 | `ex_cwork_report_plan_restart` | |
| 8 | 删除任务 | `ex_cwork_report_plan_delete` | |
| 9 | **汇报信息变更** | `ex_cwork_report_info_change` | ✅ Processing Core 需要 |
| 10 | **汇报关联对象变更** | `ex_cwork_report_object_change` | ✅ Processing Core 需要 |
| 11 | 任务发送提醒待办 | `ex_cwork_plan_send_reminder` | |
| 12 | 撤回/驳回汇报 | `ex_cwork_report_revoke_rebut` | |
| 13 | AI 消息分析 | `ex_cwork_ai_judge_new_message` | |
| 14 | AI 批量消息分析 | `ex_cwork_ai_judge_new_message_batch` | |

---

## 与 Processing Core 相关的 MQ 事件

### 事件 9：汇报信息变更

**交换机：** `ex_cwork_report_info_change`

```json
{
  "reportRecordId": 123,
  "operate": "update",
  "typeList": ["content", "reply", "attachment"],
  "addReplyId": 301,
  "deleteReplyId": 302,
  "reportReply": {
    "id": 301,
    "type": "suggest",
    "content": "这是回复内容",
    "replyEmpId": 3001,
    "createTime": 1747035208296
  }
}
```

**Processing Core 使用场景：**
- `typeList` 包含 `"content"` → 汇报正文变更，标记缓存失效
- `typeList` 包含 `"reply"` → 有新回复，标记缓存失效
- `typeList` 包含 `"attachment"` → 附件变更，标记缓存失效

**注意：** `reportReply` 字段只在 `operate=update` 且 `typeList` 包含 `reply` 时有值

---

### 事件 10：汇报关联对象变更

**交换机：** `ex_cwork_report_object_change`

```json
{
  "reportId": 123,
  "planId": 456,
  "reportRecordType": 5,
  "main": "周工作汇报",
  "writeEmpId": 3001,
  "writeEmpName": "张三",
  "reportTime": "2023-10-25 10:00:00",
  "currentEmpId": 4001,
  "addObjectList": [
    { }
  ],
  "deleteObjectList": [
    { }
  ]
}
```

**Processing Core 使用场景：**
- `addObjectList` 非空 → 汇报新增关联对象（含 BP 标签），初始化处理记录
- `deleteObjectList` 非空 → 汇报删除关联对象，删除处理记录

**⚠️ 问题：** `addObjectList` 和 `deleteObjectList` 的具体字段未在文档中定义，需要陈伟补充

---

## 其他关键发现

### reportTemplateId（事项 ID）

多个事件中包含 `reportTemplateId` 字段：
- 事件 1、9、10、12 中均有
- 这是关联的**事项 ID**，可能与 BP 标签不是同一个概念
- 需要确认：BP 标签和事项（template）的关系

### 回复类型

事件 9 中的 `reportReply.type`：
- `common` — 普通回复
- `suggest` — 建议
- `decide` — 决策

这对于判断评论是否"有意义"有帮助，但不是"收到"类过滤的解决方案。

### reportRecordType（汇报类型）

- 1-工作交流
- 2-工作指引
- 3-文件签批
- **4-AI汇报** ← 重要！这是 AI 汇报的识别方式
- 5-工作汇报

**问题：** AI 汇报类型中是否区分"搬运型"和"业务新增型"？需要在事项 5.2 中确认。

---

## MQ 事件评审结论

**C-3（MQ 事件通道）从"未定义"降级为"细节待确认"，Processing Core 的 MQ 接入方案基本可行。**

### ✅ 已确认可用的 MQ 事件

| 我们需要的 | 已有 MQ | 交换机 | 字段 |
|---------|--------|--------|------|
| 汇报正文变更 | ✅ | `ex_cwork_report_info_change` | `typeList` 含 `"content"` |
| 新增回复 | ✅ | `ex_cwork_report_info_change` | `typeList` 含 `"reply"` + `reportReply` |
| 附件变更 | ✅ | `ex_cwork_report_info_change` | `typeList` 含 `"attachment"` |
| 关联对象变更 | ✅ | `ex_cwork_report_object_change` | `addObjectList` / `deleteObjectList` |

### ⚠️ 需要陈伟补充的 4 个问题

| # | 补充项 | 说明 |
|---|--------|------|
| 1 | `addObjectList` / `deleteObjectList` 的字段结构 | 不知道 BP 标签 ID 在哪个字段 |
| 2 | `reportTemplateId` 与 BP 标签的关系 | 事项 ID 是否等同于 BP 标签？两者关系？ |
| 3 | AI 汇报类型（`reportRecordType=4`）子类型 | 搬运型 vs 业务新增型如何区分？ |
| 4 | 交换机路由键格式 | 是否需要 routing key 过滤？ |

请在回复中补充以上 4 个问题的具体信息。
