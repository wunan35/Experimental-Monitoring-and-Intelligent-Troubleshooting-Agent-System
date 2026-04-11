# 土木实验智能监控 Agent 系统架构文档

## 1. 系统概述

### 1.1 项目背景

本系统是基于 Spring AI Alibaba 框架构建的智能运维 Agent 系统，专门用于土木实验监控和告警分析。系统通过多 Agent 协作模式，实现告警自动分析、日志查询、规范检索等智能化运维能力。

### 1.2 技术栈

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| 基础框架 | Spring Boot | 3.2.0 |
| AI 框架 | Spring AI Alibaba | 1.1.0.0-RC2 |
| 大模型 | 通义千问 (DashScope) | qwen-plus |
| 向量数据库 | Milvus | 2.6.10 |
| 缓存/会话 | Redis | - |
| 监控指标 | Micrometer + Prometheus | - |
| 限流熔断 | Resilience4j | 2.2.0 |
| API 文档 | SpringDoc OpenAPI | 2.3.0 |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            前端应用层                                    │
│                     (Web UI / 移动端 / API 调用)                         │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          API Gateway 层                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ 限流拦截器    │  │ 追踪拦截器    │  │ 认证拦截器    │                  │
│  └──────────────┘  └──────────────┘  └──────────────┘                  │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Controller 层                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      │
│  │ ChatController   │  │ FileUploadCtrl   │  │ MilvusCheckCtrl  │      │
│  │ - /api/chat      │  │ - /api/upload    │  │ - /milvus/health │      │
│  │ - /api/chat_stream│ │                  │  │                  │      │
│  │ - /api/ai_ops    │  │                  │  │                  │      │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           Service 层                                     │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Agent 服务层                                 │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │   │
│  │  │ ChatService  │  │ AiOpsService │  │PromptService │          │   │
│  │  │ (对话服务)   │  │ (运维服务)   │  │ (提示词管理) │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      基础服务层                                   │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │   │
│  │  │VectorSearch  │  │SessionStorage│  │AgentExecutor │          │   │
│  │  │ (向量搜索)   │  │ (会话存储)   │  │ (并发控制)   │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      缓存服务层                                   │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │   │
│  │  │EmbeddingCache│  │SearchCache   │  │PromptCache   │          │   │
│  │  │ (向量缓存)   │  │ (检索缓存)   │  │ (提示词缓存) │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Agent 工具层                                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      │
│  │ QueryMetricsTools│  │ QueryLogsTools   │  │ InternalDocsTools│      │
│  │ - 告警查询       │  │ - 日志查询       │  │ - 规范文档检索   │      │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘      │
│  ┌──────────────────┐  ┌──────────────────┐                           │
│  │ DateTimeTools    │  │ MCP 工具集        │                           │
│  │ - 时间获取       │  │ - 外部工具扩展    │                           │
│  └──────────────────┘  └──────────────────┘                           │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          外部依赖层                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │  DashScope   │  │   Milvus     │  │    Redis     │  │Prometheus  │ │
│  │  (大模型)    │  │ (向量数据库) │  │  (缓存/会话) │  │ (监控指标) │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件说明

#### 2.2.1 Controller 层

| 控制器 | 职责 | 主要接口 |
|--------|------|---------|
| `ChatController` | 对话和运维入口 | `/api/chat`, `/api/chat_stream`, `/api/ai_ops` |
| `FileUploadController` | 文件上传和索引 | `/api/upload` |
| `MilvusCheckController` | 健康检查 | `/milvus/health` |

#### 2.2.2 Service 层

| 服务 | 职责 |
|------|------|
| `ChatService` | 创建模型、构建提示词、管理 ReactAgent |
| `AiOpsService` | 多 Agent 协作编排，告警分析流程 |
| `PromptService` | 提示词版本管理和动态加载 |
| `VectorSearchService` | 向量检索和 Embedding 生成 |
| `SessionStorageService` | 会话持久化存储（支持本地/Redis） |
| `AgentExecutorService` | Agent 并发控制和执行管理 |

#### 2.2.3 工具层

| 工具 | 功能 | 返回数据 |
|------|------|---------|
| `QueryMetricsTools` | 查询实验告警 | 告警列表（类型、严重程度、持续时间） |
| `QueryLogsTools` | 查询实验日志 | 日志条目（时间戳、级别、内容） |
| `InternalDocsTools` | 检索规范文档 | 相关文档片段和相似度分数 |
| `DateTimeTools` | 获取当前时间 | ISO 8601 格式时间字符串 |

---

## 3. 核心流程

### 3.1 对话流程（/api/chat_stream）

```
┌─────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  用户   │────▶│ChatController│────▶│ ChatService  │────▶│  ReactAgent  │
│         │     │              │     │              │     │              │
└─────────┘     └──────────────┘     └──────────────┘     └──────┬───────┘
                                                                 │
                    ┌────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           ReactAgent 执行循环                            │
│                                                                          │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐        │
│   │  思考    │───▶│  决策    │───▶│  执行    │───▶│  反思    │        │
│   │(Reasoning)│   │(Decision)│   │(Execution)│   │(Reflection)│       │
│   └──────────┘    └──────────┘    └──────────┘    └──────────┘        │
│        │                              │                                  │
│        │         ┌────────────────────┘                                  │
│        │         │                                                        │
│        │         ▼                                                        │
│        │   ┌──────────────────────────────────────┐                      │
│        │   │            工具调用                   │                      │
│        │   │  ┌────────────┐  ┌────────────┐     │                      │
│        │   │  │告警查询    │  │日志查询    │     │                      │
│        │   │  │QueryMetrics│  │QueryLogs   │     │                      │
│        │   │  └────────────┘  └────────────┘     │                      │
│        │   │  ┌────────────┐  ┌────────────┐     │                      │
│        │   │  │文档检索    │  │时间获取    │     │                      │
│        │   │  │InternalDocs│  │DateTime    │     │                      │
│        │   │  └────────────┘  └────────────┘     │                      │
│        │   └──────────────────────────────────────┘                      │
│        │                                                                 │
│        └─────────────────────────────────────────────────────────────────│
│                           (循环直到完成任务)                              │
└─────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
              ┌──────────┐
              │ SSE 流式 │
              │  返回    │
              └──────────┘
```

### 3.2 AI 运维流程（/api/ai_ops）

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      SupervisorAgent 编排流程                            │
│                                                                          │
│                      ┌──────────────────┐                               │
│                      │ SupervisorAgent  │                               │
│                      │    (总调度器)     │                               │
│                      └────────┬─────────┘                               │
│                               │                                          │
│            ┌──────────────────┼──────────────────┐                      │
│            │                  │                  │                      │
│            ▼                  ▼                  ▼                      │
│     ┌────────────┐     ┌────────────┐     ┌────────────┐               │
│     │  Planner   │     │  Executor  │     │   Tools    │               │
│     │  Agent     │     │   Agent    │     │  (工具集)  │               │
│     └────────────┘     └────────────┘     └────────────┘               │
│                                                                          │
│  流程步骤：                                                               │
│  1. Supervisor 接收任务                                                  │
│  2. Planner 分析告警，拆解排查步骤                                        │
│  3. Executor 执行首个排查步骤，调用工具获取数据                           │
│  4. Planner 根据反馈再规划，生成最终报告                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.3 向量检索流程

```
┌─────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  用户查询   │────▶│ Embedding 生成   │────▶│   Milvus 检索    │
│             │     │  (DashScope)     │     │   (相似度匹配)   │
└─────────────┘     └──────────────────┘     └──────────────────┘
                           │                        │
                           ▼                        ▼
                    ┌──────────────────┐     ┌──────────────────┐
                    │ Embedding 缓存   │     │  检索结果缓存    │
                    │   (Redis)        │     │    (Redis)       │
                    │   TTL: 24h       │     │    TTL: 10min    │
                    └──────────────────┘     └──────────────────┘
```

---

## 4. 数据模型

### 4.1 会话数据模型

```java
public class SessionData {
    private String sessionId;           // 会话唯一标识
    private List<MessagePair> messageHistory;  // 消息历史
    private Instant createTime;         // 创建时间
    private Instant updateTime;         // 更新时间
    
    public static class MessagePair {
        private String userMessage;     // 用户消息
        private String assistantMessage; // AI 响应
    }
}
```

### 4.2 告警数据模型

```java
public class SimplifiedExperimentAlert {
    private String alertId;          // 告警ID
    private String alertType;        // 告警类型
    private String experimentId;     // 实验ID
    private String parameterName;    // 参数名称
    private Double measuredValue;    // 测量值
    private Double thresholdValue;   // 阈值
    private String unit;             // 单位
    private String severity;         // 严重程度
    private String description;      // 描述
    private String location;         // 位置
    private String activeAt;         // 触发时间
    private String duration;         // 持续时间
}
```

### 4.3 API 响应模型

```java
public class ApiResponse<T> {
    private int code;        // 状态码
    private String message;  // 消息
    private T data;          // 数据
}

public class SseMessage {
    private String type;     // content | error | done
    private String data;     // 内容
}
```

---

## 5. 配置说明

### 5.1 核心配置项

```yaml
# application.yml

# AI 模型配置
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7

# 会话存储配置
session:
  storage:
    type: ${SESSION_STORAGE_TYPE:local}  # local | redis
    expire-seconds: 1800

# Agent 并发控制
agent:
  concurrency:
    max-concurrent: 10
    queue-capacity: 100

# RAG 配置
rag:
  top-k: 3
  embedding-cache-ttl: 86400  # 24小时
  search-cache-ttl: 600       # 10分钟

# 限流配置
resilience4j:
  ratelimiter:
    instances:
      chat:
        limitForPeriod: 10
        limitRefreshPeriod: 1s
```

### 5.2 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DASHSCOPE_API_KEY` | DashScope API 密钥 | - |
| `SESSION_STORAGE_TYPE` | 会话存储类型 | `local` |
| `REDIS_HOST` | Redis 主机地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `MILVUS_HOST` | Milvus 主机地址 | `localhost` |
| `MILVUS_PORT` | Milvus 端口 | `19530` |

---

## 6. 监控与可观测性

### 6.1 健康检查端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 综合健康状态 |
| `/actuator/health/dashscope` | DashScope 连接状态 |
| `/actuator/health/milvus` | Milvus 连接状态 |
| `/actuator/health/redis` | Redis 连接状态 |
| `/actuator/health/mcp` | MCP 配置状态 |
| `/actuator/health/agent` | Agent 执行队列状态 |

### 6.2 Prometheus 指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `agent_chat_requests_total` | Counter | 对话请求总数 |
| `agent_chat_duration_seconds` | Timer | 对话耗时 |
| `agent_tool_invocations_total` | Counter | 工具调用次数 |
| `agent_tool_duration_seconds` | Timer | 工具调用耗时 |
| `agent_vector_search_total` | Counter | 向量搜索次数 |
| `agent_session_active_count` | Gauge | 活跃会话数 |
| `agent_executor_active_count` | Gauge | 活跃执行数 |
| `agent_executor_queue_size` | Gauge | 队列大小 |

### 6.3 结构化日志

```json
{
  "@timestamp": "2024-03-22T14:30:00.123+08:00",
  "level": "INFO",
  "logger": "org.example.service.ChatService",
  "message": "ReactAgent 对话完成",
  "traceId": "abc123",
  "spanId": "def456",
  "sessionId": "session-001",
  "duration": 2500
}
```

---

## 7. 安全设计

### 7.1 API 安全

- **限流保护**：基于 Resilience4j 实现接口级别限流
- **熔断机制**：工具调用失败时自动熔断
- **重试策略**：指数退避重试，最大 3 次

### 7.2 敏感信息保护

- API Key 通过环境变量注入
- 敏感配置不提交代码仓库
- 日志脱敏处理

---

## 8. 部署架构

### 8.1 单机部署

```
┌─────────────────────────────────────────────────────────────┐
│                      单机部署架构                            │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Nginx       │  │  Spring Boot │  │  Prometheus  │      │
│  │  (反向代理)  │──│  Application │──│  (监控)      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│                           │                                  │
│         ┌─────────────────┼─────────────────┐              │
│         │                 │                 │              │
│         ▼                 ▼                 ▼              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Redis      │  │   Milvus     │  │  MinIO       │      │
│  │  (缓存)      │  │ (向量库)     │  │ (文件存储)   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 分布式部署

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          分布式部署架构                                  │
│                                                                          │
│  ┌────────────────┐     ┌────────────────┐                             │
│  │  Load Balancer │────▶│  API Gateway   │                             │
│  └────────────────┘     └────────────────┘                             │
│                                │                                         │
│              ┌─────────────────┼─────────────────┐                      │
│              │                 │                 │                      │
│              ▼                 ▼                 ▼                      │
│       ┌───────────┐     ┌───────────┐     ┌───────────┐                │
│       │ Agent Pod │     │ Agent Pod │     │ Agent Pod │                │
│       │   (实例1) │     │   (实例2) │     │   (实例3) │                │
│       └───────────┘     └───────────┘     └───────────┘                │
│              │                 │                 │                      │
│              └─────────────────┼─────────────────┘                      │
│                                │                                         │
│              ┌─────────────────┼─────────────────┐                      │
│              │                 │                 │                      │
│              ▼                 ▼                 ▼                      │
│       ┌───────────┐     ┌───────────┐     ┌───────────┐                │
│       │   Redis   │     │  Milvus   │     │ Prometheus│                │
│       │  Cluster  │     │  Cluster  │     │  + Grafana│                │
│       └───────────┘     └───────────┘     └───────────┘                │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 9. 扩展指南

### 9.1 添加新工具

1. 创建工具类，添加 `@Component` 注解
2. 定义工具方法，添加 `@Tool` 注解
3. 在 `ChatService.buildMethodToolsArray()` 中注册

```java
@Component
public class NewTools {
    
    @Tool(description = "工具描述")
    public String newTool(@ToolParam(description = "参数描述") String param) {
        // 工具逻辑
        return result;
    }
}
```

### 9.2 添加新 Agent

1. 定义 Agent 配置和提示词
2. 在 `PromptService` 中注册提示词模板
3. 在 `AiOpsService` 中编排 Agent 协作

### 9.3 接入新的知识源

1. 实现 `VectorIndexService` 的文档解析
2. 配置 Embedding 模型和向量维度
3. 创建 Milvus Collection 并导入数据

---

## 10. 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| 1.0.0 | 2024-03 | 初始版本，支持对话和运维分析 |
| 1.1.0 | 2024-04 | 新增 Redis 会话存储、可观测性、缓存优化 |

---

## 附录

### A. 相关文档

- [API 文档](http://localhost:8080/swagger-ui.html)
- [健康检查](http://localhost:8080/actuator/health)
- [Prometheus 指标](http://localhost:8080/actuator/prometheus)

### B. 联系方式

- 项目团队: OnCall Agent Team
- 技术支持: support@example.com
