# Tool Gateway

**多协议、多语言的工具注册与调用网关。** 统一管理所有工具（Java 实现、Python 脚本、HTTP 微服务、MCP 服务），提供集中注册/发现、统一调用协议、执行护栏和全链路可观测。

## 架构

```
Python/LangChain Agent                Java Client               Go Service
   │ MCP / HTTP                          │ gRPC                     │ HTTP
   ▼                                     ▼                          ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           Gateway Layer                                   │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐                          │
│  │ McpAdapter │  │ HttpAdapter│  │(GrpcAdapter)│   ← 协议 → ToolRequest  │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘                          │
│        └───────────────┼───────────────┘                                 │
│                        ▼                                                  │
├──────────────────────────────────────────────────────────────────────────┤
│                           Guard Layer (AOP)                               │
│  ① Auth ────► ② Timeout ────► ③ Retry ────► ④ CircuitBreaker            │
├──────────────────────────────────────────────────────────────────────────┤
│                           Registry Layer                                  │
│     register / unregister / discover / invoke  (ConcurrentHashMap)        │
├──────────────────────────────────────────────────────────────────────────┤
│                           Execution Layer                                 │
│  ┌─────────┐  ┌──────────────┐  ┌────────────┐  ┌────────────────┐       │
│  │ LOCAL   │  │ HTTP_SIDECAR │  │ SUBPROCESS  │  │  MCP_NATIVE    │       │
│  │ (Java)  │  │ (Python/Go)  │  │ (py/js/sh)  │  │  (MCP Server)  │       │
│  └─────────┘  └──────────────┘  └────────────┘  └────────────────┘       │
└──────────────────────────────────────────────────────────────────────────┘
      │               │                │                │
      ▼               ▼                ▼                ▼
   [MySQL]     [HTTP Service]    [subprocess]    [MCP Server]
```

### 六层职责

| 层 | 模块 | 职责 |
|---|------|------|
| **Gateway** | `HttpAdapter`、`McpAdapter` | 外部协议 → 统一 `ToolRequest`，防腐层隔离 |
| **Guard** | `ToolGuardAspect` | AOP 织入：角色鉴权 → 超时控制 → 重试 → 熔断 |
| **Registry** | `ToolRegistry` | 工具注册/注销、关键字+标签发现、统一调用入口 |
| **Execution** | `ToolHandler` 实现类 | 执行工具逻辑；支持本地/HTTP代理/子进程/MCP四种模式 |
| **Persistence** | H2（默认）/ MySQL | 工具元数据持久化，启动时全量加载到内存 |
| **Observability** | Micrometer + Prometheus | QPS、延迟分位数、错误率、熔断状态全量指标 |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+

### 启动

```bash
cd toolgateway
mvn spring-boot:run
```

启动后访问 `http://localhost:8080`，H2 控制台在 `/h2-console`。

### 注册一个工具

只需加 `@Tool` 注解，实现 `ToolHandler` 接口，启动即自动注册：

```java
@Component
@Tool(name = "echo", description = "回显工具", tags = {"demo"})
@ToolGuard(roles = {"admin"}, timeoutMs = 5000, retries = 1)
public class EchoTool implements ToolHandler {
    @Override
    public ToolResponse<?> execute(ToolRequest req) {
        return ToolResponse.success(req.params(), System.currentTimeMillis());
    }
}
```

### 外部工具注册（YAML 驱动）

Python/Go/Node.js 工具只需部署为 HTTP 服务，然后在 `application.yml` 中声明一条配置，**启动时自动注册**，零 Java 代码：

```yaml
gateway:
  external-tools:
    - name: python_search
      description: Python 实现的语义搜索工具
      url: http://localhost:8000/search
      timeout: 30s
      tags: [search, python]
    - name: go_image_proc
      description: Go 实现的图像处理工具
      url: http://localhost:9090/process
      timeout: 10s
      tags: [image, go]
```

实现原理：`ExternalToolRegistrar` 在启动时读取配置，为每条记录创建 `HttpProxyToolHandler` 代理并注册到 `ToolRegistry`。

### 子进程工具

```bash
echo '{"_action":"search","query":"test"}' | python3 examples/python/runner.py
```

## 统一数据格式

所有工具调用遵循统一的 JSON 契约，**字段名使用 snake_case**（由 Jackson `PropertyNamingStrategies.SNAKE_CASE` 控制）。

### 请求格式（ToolRequest）

网关发给工具的 JSON —— 无论工具是本地 Java 类还是远程 Python 服务，收到的都是这个结构：

```json
{
  "tool_name": "python_search",
  "params":    {"query": "test", "limit": 10},
  "trace_id":  "trace-001",
  "context": {
    "protocol": "http",
    "tenant":   "default",
    "caller":   "agent-42",
    "role":     "admin"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `tool_name` | string | 工具唯一名称 |
| `params` | object | 工具参数键值对 |
| `trace_id` | string | 全链路追踪 ID |
| `context` | object | 上下文元信息（协议、租户、调用方、角色） |

### 响应格式（ToolResponse）

工具必须返回这个结构，**字段名同样用 snake_case**：

**成功：**
```json
{
  "success": true,
  "data": {"rows": [...], "row_count": 5},
  "cost_ms": 23
}
```

**失败：**
```json
{
  "success": false,
  "error_code": "E200",
  "error_msg": "connection refused",
  "cost_ms": 0
}
```

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| `success` | bool | 是 | 是否成功 |
| `data` | any | 成功时 | 业务数据 |
| `error_code` | string | 失败时 | 错误码，见下方错误码表 |
| `error_msg` | string | 失败时 | 错误描述 |
| `cost_ms` | number | 否 | 执行耗时（毫秒） |

> **Python 工具注意：** 字段名是 `tool_name` 不是 `toolName`，`error_code` 不是 `errorCode`。参考 `examples/python/tool_server.py` 中的 Pydantic 模型定义。

## API 接口

### HTTP 协议

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/tools` | 列出所有工具。`?keyword=sql` 模糊搜索，`?tags=db,read` 按标签过滤 |
| `GET` | `/api/tools/{name}` | 查看工具元数据（参数 schema、版本、标签等） |
| `POST` | `/api/tools/{name}` | 调用工具。Body: `{"params":{...}}`，Headers: `X-Role`、`X-Trace-Id`、`X-Tenant` |

**示例：**

```bash
# 列出工具
curl http://localhost:8080/api/tools

# 按关键字搜索
curl "http://localhost:8080/api/tools?keyword=sql"

# 按标签过滤
curl "http://localhost:8080/api/tools?tags=script"

# 调用工具（admin 角色）
curl -X POST http://localhost:8080/api/tools/mysql_query \
  -H "Content-Type: application/json" \
  -H "X-Role: admin" \
  -H "X-Trace-Id: trace-001" \
  -d '{"params":{"sql":"SELECT 1 AS val"}}'
```

**成功响应：**
```json
{
  "success": true,
  "data": {"columns": 1, "rows": [{"VAL": 1}], "rowCount": 1},
  "costMs": 16
}
```

**失败响应：**
```json
{
  "success": false,
  "error": {"code": "E100", "message": "Access denied: mysql_query"},
  "costMs": 0
}
```

### MCP 协议

| 方法 | 说明 |
|------|------|
| `tools/list` | 列出所有已注册工具 |
| `tools/call` | 调用指定工具 |

```bash
# 列出工具
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list"}'

# 调用工具（role 通过 params 传递）
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":"2",
    "method":"tools/call",
    "params":{
      "name":"mysql_query",
      "arguments":{"sql":"SELECT 1 AS val"},
      "role":"admin"
    }
  }'
```

### 监控指标

```bash
curl http://localhost:8080/actuator/prometheus | grep tool_
```

| 指标 | 说明 |
|------|------|
| `tool_invoke_count` | 调用次数（按工具名分桶） |
| `tool_invoke_duration_seconds` | 调用耗时 P50/P99 |
| `tool_error_count` | 错误次数（按工具名+错误码分桶） |
| `tool_registry_size` | 当前已注册工具数 |

## 错误码

| 错误码 | 含义 | 来源层 |
|--------|------|--------|
| `E001` | 工具未找到 | 协议层 |
| `E002` | 参数无效 | 协议层 |
| `E003` | 未知协议 | 协议层 |
| `E100` | 认证拒绝 | 护栏层 |
| `E101` | 频率限制 | 护栏层 |
| `E102` | 执行超时 | 护栏层 |
| `E103` | 熔断器打开 | 护栏层 |
| `E200` | 工具执行失败 | 执行层 |
| `E201` | 工具内部错误 | 执行层 |
| `E999` | 系统内部错误 | 系统 |

## @ToolGuard 护栏配置

在工具类上声明式配置四道护栏：

```java
@ToolGuard(
    roles                    = {"admin", "operator"},  // 允许的角色
    timeoutMs                = 30_000,                 // 超时（毫秒），0=不限
    retries                  = 3,                      // 重试次数，0=不重试
    circuitBreakerThreshold  = 0.5,                    // 熔断阈值（50% 错误率触发）
    fallback                 = "myFallbackMethod"       // 熔断兜底方法名
)
```

## 工具运行模式

| `RunnerType` | 说明 | 适用场景 |
|--------------|------|---------|
| `LOCAL` | 与网关同 JVM 运行，直接实现 `ToolHandler` | Java 工具 |
| `HTTP_SIDECAR` | 独立 HTTP 服务，网关通过 `HttpProxyToolHandler` 代理调用 | Python/Go/Node.js 长生命周期服务 |
| `GRPC_SIDECAR` | 独立 gRPC 服务，通过 Protobuf 调用 | 高性能内部服务 |
| `SUBPROCESS` | 启动子进程，stdin 传参 stdout 收结果 | 一次性脚本、批处理 |
| `MCP_NATIVE` | 工具本身暴露 MCP 协议，网关作为 MCP 客户端调用 | MCP 生态工具复用 |

## 项目结构

```
src/main/java/com/toolgateway/
├── ToolGatewayApplication.java        # 启动入口
├── config/
│   ├── AppConfig.java                 #   Bean 配置（ObjectMapper SNAKE_CASE、Resilience4j）
│   ├── ExternalToolProperties.java    #   外部工具 YAML 配置绑定
│   └── ExternalToolRegistrar.java     #   启动时自动注册外部工具
├── core/
│   ├── model/                         # 统一协议模型
│   │   ├── ToolRequest.java           #   统一请求（snake_case 序列化）
│   │   ├── ToolResponse.java          #   统一响应（snake_case 序列化）
│   │   ├── ToolMetadata.java          #   工具元数据
│   │   ├── ErrorCode.java             #   错误码枚举
│   │   └── RunnerType.java            #   运行模式枚举
│   ├── annotation/                    # 自定义注解
│   │   ├── Tool.java                  #   @Tool 标记工具类
│   │   └── ToolGuard.java             #   @ToolGuard 护栏配置
│   ├── handler/ToolHandler.java       # 工具执行接口
│   └── registry/ToolRegistry.java     # 注册中心（注册/发现/调用）
├── gateway/                           # 网关层
│   ├── GatewayController.java         #   路由分发
│   ├── adapter/
│   │   ├── ProtocolAdapter.java       #   适配器接口
│   │   ├── HttpAdapter.java           #   HTTP 协议适配
│   │   └── McpAdapter.java            #   MCP 协议适配
│   └── error/GlobalExceptionHandler.java
├── guard/                             # 护栏层
│   ├── aspect/ToolGuardAspect.java    #   四道护栏 AOP 切面
│   └── exception/                     #   护栏异常
└── execution/                         # 执行层
    ├── tool/
    │   ├── MySQLQueryTool.java        #   本地 SQL 工具示例
    │   └── SubprocessTool.java        #   子进程工具
    └── proxy/HttpProxyToolHandler.java#   HTTP Sidecar 代理
```

## 技术栈

| 组件 | 用途 |
|------|------|
| Spring Boot 3.3 | 应用框架 |
| Java 21 | 虚拟线程、Record |
| Resilience4j | 超时/重试/熔断 |
| Micrometer + Prometheus | 指标采集 |
| H2 / MySQL | 元数据存储 |
| Jackson | JSON 序列化 |
| Lombok | 减少样板代码 |
