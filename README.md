<img src="docs/assets/logo.svg" alt="DeepCover Logo" width="128" height="128" align="right">
# DeepCover - 代码全链路精准分析采集 Agent

**[中文](README.md)** | [English](README_EN.md) | [日本語](README_JA.md) | [Francais](README_FR.md) | [Portugues](README_PT.md) | [Русский](README_RU.md)

<div align="center">

![CI](https://img.shields.io/github/actions/workflow/status/xiaobin1187-git/deepcover/ci.yml?branch=main)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-1.8-orange)
![Maven](https://img.shields.io/badge/Maven-3.5-blue)
![Tests](https://img.shields.io/badge/Tests-52_passed-brightgreen)

</div>

> 基于 JVM Sandbox 的 Java 精准分析采集工具，实现无侵入的应用代码行级精准分析监控

## 简介

DeepCover 是一个基于 Alibaba JVM Sandbox 的**无侵入式 Java 精准分析采集 Agent**，可以在不修改应用源码的情况下，实时采集应用运行的代码行执行情况。

### 主要特性

- **无侵入采集** -- 基于 JVM Sandbox 字节码增强技术，无需修改应用代码
- **代码行级精准分析** -- 精确到每一行代码的执行记录
- **HTTP 请求追踪** -- 自动识别和追踪 HTTP Servlet 请求
- **高性能设计** -- 异步队列 + 批量发送，减少对应用性能影响
- **灵活配置** -- 支持类名、方法名、采样率等细粒度配置，支持配置中心动态热更新
- **多种导出方式** -- 支持 HTTP、Kafka 两种数据导出方式
- **OpenTelemetry 集成** -- 兼容 OpenTelemetry 标准，便于链路追踪

### 系统整体架构

从代码采集到数据处理入库的完整链路：

```
┌─────────────────────────────────────────────────────────────────────┐
│                        目标应用 JVM                                  │
│                                                                     │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────────┐  │
│  │ HTTP Request │───>│  JVM Sandbox     │───>│  DeepCover Agent  │  │
│  │              │    │  (javaagent)      │    │  (采集模块)        │  │
│  └─────────────┘    └──────────────────┘    └────────┬──────────┘  │
│                                                        │            │
│                              ┌──────────────────────────┘            │
│                              │                                       │
│                     ┌────────┴────────┐                              │
│                     │  本地异步队列     │  批量攒批                      │
│                     │  LocalAsyncQueue │  (queue x N)                  │
│                     └────────┬────────┘                              │
│                              │                                       │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                  │
     ┌────────┴────────┐              ┌─────────┴─────────┐
     │  数据中心 (HTTP)  │              │  Kafka Cluster     │
     │  /api/collect    │              │  precision-analysis     │
     └────────┬────────┘              └─────────┬─────────┘
              │                                  │
              └────────────┬─────────────────────┘
                           │
              ┌────────────┴────────────┐
              │  数据处理 / 存储服务      │
              │                         │
              │  - 精准分析计算         │
              │  - 差异精准分析         │
              │  - 采集数据持久化         │
              │  - 精准分析报告生成         │
              └─────────────────────────┘
```

### 采集器内部架构

Agent 在单个 JVM 内部的采集流程：

```
                        ┌─────────────────────────────────────┐
                        │          HTTP Request               │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │       HttpServlet.service()          │
                        │       (字节码增强切入点)               │
                        └──────────────┬──────────────────────┘
                                       │
 Event Flow:                           │
 ┌─────────────────────────────────────┼──────────────────────────┐
 │                                     │                          │
 │  [before]                                                      │
 │  ├─ 创建 CodeEntity, 绑定到 ProcessTop (ThreadLocal)            │
 │  ├─ 采样率检查 (基于 traceId 哈希)                                │
 │  ├─ URL 过滤 (ignoreUrls 正则匹配)                               │
 │  │                                                             │
 │  [beforeLine]  <── 每个被监控方法调用时触发                        │
 │  ├─ 从 ProcessTop 取出 CodeEntity                               │
 │  ├─ NPE 保护: codeEntity==null / isSend==1 / codeInfo==null     │
 │  ├─ 构造 LineEntity (className, methodName, 行号)               │
 │  │   └─ 行号自动去重 (LinkedHashSet, O(1))                      │
 │  ├─ 行号执行次数阈值检查 (limitCodeMethodLineSize)                │
 │  │   └─ 超阈值: 标记 REFUSE, 停止该方法的行号采集                 │
 │  │                                                             │
 │  [after]                                                       │
 │  ├─ 采集节点数阈值检查 (limitCodeMethodSize)                     │
 │  ├─ 标记 isSend=1, 防止重复发送                                 │
 │  ├─ 投递到 LocalAsyncQueue                                     │
 │  └─ finally: 清理 ThreadLocal, 防止内存泄漏                      │
 │                                                                │
 └────────────────────────────────────────────────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │        LocalAsyncEngine              │
                        │                                     │
                        │  ┌─────────┐  ┌─────────┐           │
                        │  │ Queue 0 │  │ Queue 1 │  ...       │
                        │  │ (批量攒批)│  │         │           │
                        │  └────┬────┘  └────┬────┘           │
                        │       │             │                │
                        │  ┌────┴─────────────┴────┐           │
                        │  │   Consumer Thread      │           │
                        │  │   HTTP / Kafka 发送     │           │
                        │  └───────────────────────┘           │
                        │                                     │
                        │  异常熔断: exceptionOverflow()       │
                        │  └─ 时间窗口内超阈值 -> 暂停采集       │
                        └─────────────────────────────────────┘
```

### 配置热更新机制

运行时配置通过两条路径动态更新，无需重启：

```
┌────────────────┐     定时轮询 (reportPeriod)     ┌──────────────────┐
│  配置中心        │ ──────────────────────────────> │  DeepCover Agent │
│  (deepcover-    │                                 │                  │
│   brain)        │ <────────────────────────────── │  reportServerInfo│
└────────────────┘     返回最新配置 + 版本号         │                  │
                                                │  版本比较:         │
┌────────────────┐     syncConfig 命令            │  info.version >   │
│  Sandbox HTTP  │ ──────────────────────────────> │  configVersion   │
│  /deepcover/   │                                 │  -> 热更新 18 项  │
│  syncCfg       │                                 │                  │
└────────────────┘                                 └──────────────────┘
```

## 前置要求

- Java 8+ (推荐 Java 1.8.0_202+)
- Maven 3.5+
- Alibaba JVM Sandbox 1.4.0
- 应用运行在 JVM 上

## 快速开始

### 1. 编译项目

```bash
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

编译成功后，在 `target/` 目录下生成 `deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`（包含所有依赖的完整 JAR）。

### 2. 配置应用启动参数

在目标应用的 JVM 参数中添加：

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=your-app-name \
  -Denv=test
```

**注意**: 如果使用 SkyWalking APM，请将 DeepCover agent 参数放在 SkyWalking agent 参数**前面**，否则会冲突。

### 3. 部署 DeepCover 模块

```bash
# 方法1: 复制到 Sandbox 模块目录
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/

# 方法2: 通过 Sandbox HTTP 服务动态加载
curl -X POST \
  "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"
```

### 4. 验证安装

```bash
# 检查日志中是否出现"加载完成"字样
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

### 5. 运行测试

```bash
mvn clean test -Dmaven.javadoc.skip=true
```

当前测试覆盖 41 个用例，涵盖核心工具类和实体类。

## 配置说明

### 基础配置

从示例模板复制配置文件：

```bash
cp src/main/resources/deepcover.properties.example src/main/resources/deepcover.properties
```

### 配置项一览

| 配置项 | 说明 | 默认值/示例 |
|--------|------|-------------|
| `serviceName` | 应用名称 | - |
| `env` | 环境标识: test/pre/pro | - |
| `packageName` | 采集包名模式（正则） | `com.myapp.*` |
| `ignoreClasses` | 忽略的类名模式（分号分隔） | `*.logger;*.frame;` |
| `ignoreUrls` | 忽略的 URL（分号分隔） | `/health;/metrics` |
| `ignoreAnnos` | 忽略的注解 | - |
| `sampleRate` | 采样率（10000 = 100%） | `10000` |
| `limitCodeMethodSize` | 单请求最大采集方法数 | `500` |
| `limitCodeMethodLineSize` | 单方法最大行号采集次数 | `500` |
| `sendDataCenterType` | 发送方式: 1=HTTP, 2=Kafka | `1` |
| `dataCenterAddr` | 数据中心地址（HTTP 模式） | - |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 集群地址 | - |
| `KAFKA_TOPIC` | Kafka 主题 | - |
| `exceptionThreshold` | 异常熔断阈值 | `10` |
| `exceptionCalcTime` | 异常计算时间窗口（分钟） | `1` |
| `exceptionPauseTime` | 熔断暂停时间（秒） | `5` |

所有配置项均支持通过 `syncConfig` 命令动态热更新，无需重启应用。

### 采样率说明

```
sampleRate=10000   # 100% 采样
sampleRate=5000    # 50% 采样
sampleRate=100     # 1% 采样
```

采样率基于 traceId 哈希值计算，同一请求在不同实例上采样结果一致。

## 动态控制 API

通过 JVM Sandbox 的 HTTP 服务可以动态控制模块：

```bash
# 激活模块
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# 停用模块
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# 卸载模块（彻底卸载，需重启才能再次采集）
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=your-app-name"

# 同步配置
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20"
```

## 性能设计

DeepCover 通过以下策略最小化对应用性能的影响：

- **异步队列**: 数据发送使用独立线程池（core=4, max=8, queue=256），CallerRunsPolicy 防止任务丢失
- **批量处理**: 本地队列批量攒批发送，减少网络开销
- **采样机制**: 基于 traceId 的确定性采样，可按需调整采样率
- **智能限流**: 方法行号达到阈值后自动停止采集该方法的行号信息
- **异常熔断**: 发送异常超过阈值后自动暂停采集，防止雪崩
- **正则缓存**: Pattern 编译结果缓存，避免热路径重复编译

## 项目结构

```
deepcover/
├── src/
│   ├── main/java/io/deepcover/agent/
│   │   ├── CodeCollecter.java       # Module 入口，生命周期管理
│   │   ├── HttpCodeModule.java      # 采集核心逻辑
│   │   ├── entity/                  # 数据实体
│   │   │   ├── CodeEntity.java      # 一次请求的采集数据
│   │   │   └── LineEntity.java      # 一个方法的行号信息
│   │   ├── config/
│   │   │   ├── DeepCoverConfig.java # 全局配置
│   │   │   ├── ExecutorThreadPoolConfig.java  # 线程池配置
│   │   │   ├── queue/               # 异步队列引擎
│   │   │   └── kafka/               # Kafka 发送引擎
│   │   ├── ext/                     # JVM Sandbox 扩展
│   │   │   ├── CodeAdviceListener.java
│   │   │   ├── CodeAdviceAdapterListener.java
│   │   │   └── CodeEventWatchBuilder.java
│   │   └── util/                    # 工具类
│   │       ├── TraceContext.java     # TraceId 管理
│   │       ├── TraceUtil.java       # 采样计算
│   │       ├── ExceptionAwareUtil.java  # 异常熔断
│   │       └── http/HttpClient2.java # HTTP 客户端
│   └── test/java/                   # 单元测试（41 用例）
├── src/main/resources/
│   ├── deepcover.properties.example  # 配置模板
│   └── logback.xml
├── sandbox/                          # JVM Sandbox 二进制和配置
├── LICENSE                           # Apache 2.0
├── CONTRIBUTING.md                   # 贡献指南
├── CHANGELOG.md                      # 变更日志
└── README.md
```

## 安全说明

- `deepcover.properties` 已加入 `.gitignore`，不会意外提交敏感信息
- 配置模板使用 `YOUR_*` 占位符替代真实地址和密钥
- 所有依赖均为开源组件，无内部私有依赖

### 依赖清单

| 依赖 | 版本 | 用途 |
|------|------|------|
| Alibaba JVM Sandbox | 1.4.0 | 字节码增强框架 |
| OpenTelemetry API | 1.30.0 | 链路追踪标准 |
| Apache HttpClient | 4.5.6 | HTTP 数据发送 |
| Hutool | 5.8.9 | HTTP 工具（配置中心上报） |
| FastJSON | 2.0.25 | JSON 序列化 |
| Logback | 1.2.1 | 日志框架 |
| Lombok | 1.18.12 | 代码简化 |
| Kafka Clients | 2.4.1 | Kafka 数据发送 |
| Guava | 18.0 | 工具类 |

## 注意事项

1. **性能影响**: 采集有性能开销，生产环境建议设置合理采样率
2. **SkyWalking 兼容**: DeepCover agent 必须在 SkyWalking agent 之前加载
3. **资源占用**: 采集数据会占用内存，根据应用情况调整 `limitCodeMethodSize`
4. **测试环境先行**: 生产部署前务必在测试环境充分验证

## 文档

- [CONTRIBUTING.md](CONTRIBUTING.md) - 贡献指南
- [CHANGELOG.md](CHANGELOG.md) - 变更日志
- [LICENSE](LICENSE) - Apache 2.0 许可证

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证开源。
