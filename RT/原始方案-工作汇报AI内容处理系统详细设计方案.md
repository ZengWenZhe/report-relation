# 工作汇报 AI 内容处理系统详细设计方案（原始版本 - Evan 发送）

## 1. 方案目标
本方案旨在构建一套通用的汇报内容处理系统。通过 AI 技术，根据汇报关联的业务对象，从非结构化的工作汇报中，按照**模板配置**（Markdown 或 JSON）进行内容的重构与沉淀。系统采用事件驱动与 Redis 异步缓冲机制，确保在高并发变动场景下的高效处理。

## 2. 核心架构设计

### 2.1 系统交互角色
1. **工作汇报系统**：推送汇报关联变更及内容变动 MQ 事件；提供汇报正文、回复、附件等原始数据。
2. **业务系统**：提供业务对象（如 BP）及其父级上下文的查询接口。
3. **Redis (Dirty Buffer)**：作为脏数据缓冲区，存储发生变动的 `report_id`，实现去重与削峰填谷。
4. **本系统 (Processing Core)**：
   * **Consumer**: 监听关联关系变更与内容变动（修改/回复）MQ 消息。
   * **Worker**: 核心重塑引擎。组装上下文，驱动 AI 生成内容并持久化。
   * **Scheduler**: 定时从 Redis 中 Pop 变动的汇报 ID 列表，触发批量更新逻辑。
   * **API**: 提供处理后内容（Processed Content）的查询接口。

### 2.2 系统协同流程图

（见原始文档 mermaid 流程图）

## 3. 数据库设计

### 3.1 内容处理记录表 (`report_process_record`)
| 字段名 | 类型 | 说明 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | BigInt | 主键 ID | |
| `report_id` | BigInt | 汇报 ID | |
| `business_id` | BigInt | 业务对象 ID | |
| `template_id` | BigInt | 模板 ID | 对应具体的处理模版及版本 |
| `processed_content` | Text | 处理后结果 | Markdown 或 JSON 字符串 |
| `status` | Int | 状态 | 0: 待处理, 1: 成功, 2: 失败 |
| `report_update_time`| DateTime | 汇报最后更新时间 | |
| `last_sync_time` | DateTime | 最近处理成功时间 | |
| `retry_count` | Int | 重试次数 | 简单重试逻辑，上限 3 次 |

### 3.2 任务处理模板表 (`process_template`)
| 字段名 | 类型 | 说明 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | BigInt | 主键 ID | |
| `business_type` | String | 业务类型 | 普通索引，如 `BP` |
| `template_code` | String | 模板编码 | 唯一标识处理场景 |
| `is_default` | Boolean | 是否默认 | |
| `output_format` | String | 结果格式 | `JSON` 或 `MARKDOWN` |
| `prompt_template` | Text | 提示词模板 | 占位符: `{background}`, `{report_content}` |
| `format_config` | Text | 格式定义 | Schema 或 MD 层级框架 |
| `sys_context` | Text | 系统背景 | AI 角色设定 |
| `version` | Int | 版本号 | 用于策略迭代管理 |

## 4. 核心工作流设计

### 4.1 关联变更流 (Association Stream)
1. **新增关联**：接收 MQ -> 插入 `report_process_record` (status=待处理) -> 若是即时需求则入队，否则等定时任务消费。
2. **删除关联**：接收 MQ -> 逻辑/物理删除对应记录。

### 4.2 汇报内容变更流 (Dirty Buffer Stream)
1. **事件入池**：
   - 监听 `ContentModified` 和 `ReplyAdded` MQ。
   - 将 `report_id` 存入 Redis Set (`SET:DIRTY_REPORTS`)。
2. **定时拉取**：
   - Scheduler 定时（如每 5 分钟）Pop 全量 `report_id`。
   - 批量更新数据库中对应 `report_id` 的记录状态为 `待处理`。

### 4.3 AI 处理管道 (Worker Pipeline)
1. **上下文聚合**：按 `business_type` 调用策略接口获取业务详情。
2. **噪声过滤**：针对评论列表应用基础过滤规则（剔除极短、无实质意义的礼貌性回复），判别是否有实质内容更新。
3. **内容重塑**：按 `template_id` 加载提示词，调用 LLM 进行内容生成。
4. **持久化**：保存结果至 `processed_content`，更新同步时间戳。

## 5. 重点机制实现说明

### 5.1 事件驱动下的削峰填谷
引入 Redis Buffer 后，系统不再需要对汇报数据库进行大范围扫描。即使有大量用户在某一时刻同时更新汇报，也仅体现为 Redis 中 ID 的增加，由 Scheduler 按设定的节奏分批拉取处理，避免对 AI 服务造成瞬间压垮。

### 5.2 灵活的场景管理
通过 `template_id` 关联，一个业务对象可以同时拥有"简报"和"详报"两种提取结果副本，只需在 MQ 触发或业务需要时分配不同的模板 ID 即可。

## 6. 验证方案
1. **并发测试**：模拟短时间内多次重复修改同一汇报，校验 Redis 是否能有效去重且最终只产出一次 AI 处理。
2. **模板切换校验**：同一个汇报关联 BP 后，切换不同 `template_id` 观察输出格式（JSON vs MD）的准确性。
3. **过滤器验证**：增加大量"收到"、"已阅"评论，校验是否会触发无效的 AI API 调用。
