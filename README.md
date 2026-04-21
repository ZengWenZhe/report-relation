# Report Relation —— 汇报关联服务

基于 Spring Cloud 的微服务，负责管理工作汇报与 BP（业务规划）任务之间的关联关系，并借助 AI 大模型对汇报内容进行结构化提取。

## 核心功能

- **汇报-BP 任务关联管理** —— 维护工作汇报与 BP 任务的绑定关系，支持新增、删除（逻辑删除）和查询
- **MQ 事件驱动同步** —— 通过 RabbitMQ 监听汇报对象变更事件，自动同步关联关系
- **AI 结构化提取** —— 调用 AI 大模型（filegpt）对汇报内容进行语义分析，提取与 BP 任务相关的结构化数据（进展、风险、计划等）
- **数据迁移** —— 提供从 PMS 系统批量迁移历史关联数据的接口

## 技术栈

| 领域 | 技术 |
|------|------|
| 框架 | Spring Boot / Spring Cloud |
| 注册 & 配置中心 | Nacos |
| 服务调用 | OpenFeign（filegpt / pms / work-report） |
| 数据库 | MySQL + MyBatis-Plus |
| 缓存 | Redis |
| 消息队列 | RabbitMQ |
| API 文档 | Knife4j (Swagger2) |
| JSON 处理 | Fastjson |
| 构建工具 | Maven |

## 项目结构

```
src/main/java/com/xgjktech/reportrelation/
├── Application.java              # 启动类
├── Knife4jConfig.java            # Swagger 配置
├── base/                         # 基础设施层
│   ├── NacosConfig.java          # Nacos 动态配置
│   ├── config/                   # Feign 超时 & 重试配置
│   ├── feign/                    # Feign 客户端（filegpt / pms / work-report）
│   ├── model/                    # Feign 请求/响应模型
│   └── param/                    # AI 请求参数
├── controller/                   # REST 接口
│   ├── ReportRelationController  # 关联关系 CRUD
│   └── DataMigrationController   # 数据迁移
├── data/
│   ├── entity/                   # MyBatis-Plus 实体
│   └── vo/                       # 消息 DTO
├── enums/                        # 枚举（Prompt Code 等）
├── mapper/                       # MyBatis-Plus Mapper
└── service/                      # 业务逻辑 & MQ 消费者 & AI 提取
```

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/reportRelation/listByBizId` | 根据业务类型和业务 ID 查询关联关系 |
| POST | `/reportRelation/reExtractSchema` | 重新触发 AI 结构化提取 |
| POST | `/migration/syncFromPms` | 从 PMS 系统同步历史关联数据 |

## AI 提取流程

1. 从 `extract_prompt_config` 表加载 Prompt 模板
2. 通过 Feign 获取汇报全文（work-report）和 BP 上下文（pms）
3. 调用 AI 大模型（filegpt）进行语义分析
4. 解析返回的 JSON，判断关联程度（strong / weak / none）
5. 将结构化结果写入 `extract_schema` 字段，更新提取状态

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- Nacos（注册中心 & 配置中心）
- MySQL、Redis、RabbitMQ

### 本地运行

```bash
# 编译打包
mvn clean package -DskipTests

# 运行（需要 Nacos 等中间件就绪）
java -jar target/report-relation.war
```

### 配置说明

核心配置通过 Nacos 统一管理，本地 `bootstrap.properties` 仅包含：

- 服务端口：`10600`
- Nacos 地址及命名空间
- 共享配置：`common.properties`、`common-mysql.properties`、`common-redis.properties`、`common-rabbitmq.properties`

## 数据库

DDL 和初始化数据位于 `docs/sql/` 目录：

- `report_relation_business_init.sql` —— 关联关系表
- `extract_prompt_config_init.sql` —— Prompt 配置表
- `extract_prompt_config_data_init.sql` —— Prompt 模板初始数据

## License

Private — 内部项目，仅限授权使用。
