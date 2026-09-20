<p align="center"><img src="docs/assets/logo.svg" alt="DeepCover" width="96" height="96"></p>

# DeepCover - JVM 运行时请求与代码关系采集 Agent

**[中文](README.md)** | [English](README_EN.md) | [日本語](README_JA.md) | [Francais](README_FR.md) | [Portugues](README_PT.md) | [Русский](README_RU.md)

> 其他语言版本尚未完全同步本次能力边界与 Benchmark，当前事实以本中文 README 和源码为准。

<div align="center">

![CI](https://img.shields.io/github/actions/workflow/status/xiaobin1187-git/deepcover/ci.yml?branch=main)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-8-orange)
![Maven](https://img.shields.io/badge/Maven-3.5+-blue)
![Tests](https://img.shields.io/badge/Tests-66_passed-brightgreen)

</div>

DeepCover 基于 Alibaba JVM Sandbox，在不修改业务源码的情况下，记录一次 Java Servlet HTTP 请求实际执行过的类、方法和代码行，并通过 HTTP 或 Kafka 输出这些运行时关系。

它是精准测试体系中的**运行时证据采集层**，不是完整的覆盖率平台或测试选择平台。

## Why DeepCover

随着服务规模、发布频率和测试资产增长，全量回归通常面临三个问题：

1. 全量执行时间与资源成本持续增长，发布反馈变慢。
2. 仅依赖静态调用分析容易包含理论可达但运行时并未发生的路径。
3. 传统聚合覆盖率能说明“哪些代码被执行”，但通常不能直接回答“哪一个请求或测试执行了这些代码”。

DeepCover 补充的是第三类数据：把请求 traceId、HTTP 上下文与本次执行的类、方法、行号关联起来。上层系统可以将历史运行时关系与代码差异、静态调用图和测试资产结合，用于影响分析、测试选择和覆盖验证。

运行时关系只代表**已经观察到的执行路径**。没有被观察到不等于不可达，因此它不应单独替代静态分析或风险判断。

## 精准测试闭环中的位置

下面是一个完整闭环的目标架构。实线框中的 DeepCover Agent 属于本仓库；标记为“外部”的节点需要由代码平台、测试平台、数据服务或 CI 系统提供。

```mermaid
flowchart LR
    DIFF["Git Diff<br/>外部"] --> STATIC["静态调用关系<br/>外部"]
    STATIC --> IMPACT["Impact Analysis<br/>外部"]
    STORE["历史请求/测试-代码关系库<br/>外部"] --> IMPACT
    IMPACT --> SELECT["Test Selection<br/>外部"]
    SELECT --> EXEC["测试执行或流量回放<br/>外部"]
    EXEC --> APP[被测 Java Servlet 应用]
    APP --> DC["DeepCover Agent<br/>本仓库"]
    DC --> STORE
    DC --> VERIFY["Coverage Verification<br/>外部"]
    DIFF --> VERIFY
    VERIFY --> GATE["CI Gate<br/>外部"]
```

DeepCover 当前负责：

- 在请求执行期间采集运行时代码关系。
- 生成或复用 traceId，作为外部测试执行与采集数据关联的键。
- 将采集结果投递给外部数据服务。
- 暴露采样、丢弃、队列、发送和熔断指标。

本仓库当前不负责：

- Git Diff 解析、静态调用图构建和变更影响计算。
- 测试用例资产管理、自动选例、测试调度和结果判定。
- 全量/增量覆盖率百分比计算、报告存储和可视化。
- CI 准入规则和发布阻断。
- 基于 AI 的用例生成、风险预测或自主决策。

## 已实现能力

| 能力 | 当前实现 |
|---|---|
| 接入方式 | JVM Sandbox 1.4.0 模块，Java 8 字节码增强，无需修改业务源码 |
| 请求入口 | `javax.servlet.http.HttpServlet.service()` |
| 采集粒度 | 请求上下文、类、方法、方法参数类型、调用序号、执行行号 |
| 采集范围 | `packageName` 正则匹配的类，可配置类、方法、注解和 URL 过滤 |
| TraceId | 优先读取 W3C `traceparent`，其次读取 B3、`X-Trace-Id`、`traceId`；缺失时生成 32 位 ID |
| 采样 | 基于 traceId 哈希的确定性采样，`0-10000` 对应 `0%-100%` |
| 保护机制 | 单请求方法节点上限、单方法行事件上限、有限队列、发送异常熔断 |
| 数据出口 | HTTP 或 Kafka |
| 异步处理 | 请求线程只投递本地有界队列；消费者批量出队。HTTP 模式当前仍按请求逐条发送 |
| 配置 | JVM 系统属性、本地 properties、可选 `deepcover-brain` 配置中心 |
| 运行指标 | 请求原因计数、采集行数、队列状态、发送成功/失败、熔断状态 |
| 生命周期 | 支持模块加载、启动、停止、卸载，以及队列和客户端资源关闭 |

## Agent 架构

```mermaid
flowchart TD
    REQ[Servlet HTTP Request] --> SANDBOX[JVM Sandbox 事件]
    SANDBOX --> ENTRY[Servlet 入口识别]
    ENTRY --> TRACE[提取或生成 traceId]
    TRACE --> GUARD{采样 / URL 过滤 / 熔断}
    GUARD -->|拒绝| METRICS[原因与丢弃指标]
    GUARD -->|通过| EVENTS[BEFORE / LINE / RETURN / THROWS]
    EVENTS --> ENTITY[CodeEntity + LineEntity]
    ENTITY --> LIMIT{节点与行事件阈值}
    LIMIT -->|超限| METRICS
    LIMIT -->|完成| QUEUE["LocalAsyncEngine<br/>有界队列"]
    QUEUE --> HTTP[HTTP Sender]
    QUEUE --> KAFKA[Kafka Producer]
    HTTP --> DATACENTER[外部数据接收服务]
    KAFKA --> DATACENTER
    QUEUE --> METRICS
```

一次成功发送的 `CodeEntity` 主要包含：

- HTTP 类型、来源地址、端口、方法、URL 和开始时间。
- 环境、服务名、分支、traceId 和 Sandbox processId。
- `codeInfo`：按执行过程记录的 `LineEntity` 列表。
- 每个 `LineEntity` 的类名、方法名、参数类型、调用序号、开始时间和去重后的行号集合。

## 适用边界

DeepCover 目前最适合测试环境、预发布环境或受控采样的 Java Servlet 服务。使用前需要了解以下边界：

- 当前入口是 Servlet API，不直接覆盖 WebFlux、Netty、Dubbo 或非 HTTP 入口。
- 请求上下文依赖线程本地状态；脱离请求线程执行的异步任务不会自动继承本次关联。
- 代码行采集依赖 class 文件中的行号表；缺少调试行号信息时无法得到完整行级数据。
- trace header 兼容不等于完整分布式追踪实现；本仓库不传播跨服务上下文，也不生成完整 span。
- DeepCover 记录执行事实，不判断业务断言是否正确，也不证明未执行路径不可达。
- 字节码行事件具有可观测开销，生产环境必须先做服务级压测并配置采样率和资源上限。

## 快速开始

### 1. 构建 Agent 与 Demo

```bash
mvn clean package -Dmaven.javadoc.skip=true
mvn -f examples/demo-servlet/pom.xml clean package -Dmaven.javadoc.skip=true
```

构建结果：

- `target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`
- `sandbox/sandbox-module/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`
- `examples/demo-servlet/target/demo-servlet.war`
- `examples/demo-servlet/target/demo-servlet-1.0-SNAPSHOT-standalone.jar`

### 2. 启动本地接收端

```bash
python benchmarks/mock_receiver.py --port 18081
```

### 3. 启动带 DeepCover 的 Demo

PowerShell 示例：

```powershell
$agent = "-javaagent:$PWD\sandbox\lib\sandbox-agent.jar=home=$PWD\sandbox;server.ip=127.0.0.1;server.port=4769;namespace=default"

java $agent `
  -Dapp.name=demo-servlet `
  -Denv=test `
  -Ddeepcover.env=test `
  -Ddeepcover.configCenterEnabled=false `
  '-Ddeepcover.packageName=io\.deepcover\.examples\.demo\..*' `
  -Ddeepcover.sampleRate=10000 `
  -Ddeepcover.sendDataCenterType=1 `
  -Ddeepcover.dataCenterAddr=http://127.0.0.1:18081/collect `
  -jar examples/demo-servlet/target/demo-servlet-1.0-SNAPSHOT-standalone.jar
```

### 4. 产生请求并检查结果

```bash
curl "http://127.0.0.1:18080/demo-servlet/user?action=list"
curl "http://127.0.0.1:18080/demo-servlet/user?action=get&id=1"
curl "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/metrics"
curl "http://127.0.0.1:18081/stats"
```

## 配置

独立运行最直接的方式是使用 JVM 系统属性。`deepcover.properties` 主要用于按环境配置数据中心、配置中心和 Kafka 地址；配置中心可以通过 `deepcover.configCenterEnabled=false` 关闭。

| JVM 属性 | 默认值 | 说明 |
|---|---:|---|
| `app.name` | `unknown` | 服务名，必填 |
| `env` / `deepcover.env` | `unknown` | 采集环境，必填 |
| `branch` | `master` | 代码分支标识 |
| `deepcover.packageName` | 空 | 需要增强的业务类正则，必填 |
| `deepcover.sampleRate` | `10000` | `10000=100%`、`1000=10%`、`100=1%` |
| `deepcover.ignoreClasses` | 空 | 忽略类正则，分号分隔 |
| `deepcover.ignoreMethods` | 空 | 忽略方法模式，分号分隔 |
| `deepcover.ignoreAnnos` | 空 | 忽略注解，分号分隔 |
| `deepcover.ignoreUrls` | 空 | 忽略 URL 正则，分号分隔 |
| `deepcover.limitCodeMethodSize` | `500` | 单请求最大方法节点数；超限后整次请求不发送 |
| `deepcover.limitCodeMethodLineSize` | `500` | 单方法最大行事件数；超限后停止该方法的行采集 |
| `deepcover.sendDataCenterType` | `1` | `1=HTTP`，`2=Kafka` |
| `deepcover.dataCenterAddr` | 空 | HTTP 接收地址 |
| `deepcover.kafkaBootstrapServers` | 空 | Kafka broker 列表 |
| `deepcover.kafkaTopic` | 空 | Kafka topic |
| `deepcover.queueNum` | `1` | 本地队列和消费线程数量 |
| `deepcover.queueSize` | `100` | 每个队列容量 |
| `deepcover.queueMsgSize` | `50` | 每次出队的最大消息数 |
| `deepcover.queueRecycleTime` | `10` | 空队列轮询休眠时间，毫秒 |
| `deepcover.exceptionThreshold` | `10` | 时间窗口内触发熔断的发送异常数 |
| `deepcover.exceptionCalcTime` | `1` | 异常统计窗口，分钟 |
| `deepcover.exceptionPauseTime` | `5` | 熔断暂停时间，秒 |

`syncConfig` 只会立即应用不需要重建监听器或队列的配置。响应中的 `restartRequired` 会列出已收到但未生效的结构性配置。

立即生效：`sampleRate`、异常阈值、采集阈值、`queueMsgSize`、`queueRecycleTime`、`ignoreUrls`、发送类型和配置版本。

需要重启并通过启动配置提供：`packageName`、`ignoreClasses`、`ignoreMethods`、`ignoreAnnos`、`queueNum`、`queueSize`、`reportPeriod`。

完整说明见 [配置指南](docs/configuration-guide.md)。

## 运行控制与指标

```bash
# 重新注册采集监听器
curl -X POST "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/startCodeModule"

# 同步可动态生效的配置
curl -X POST "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=1000&exceptionThreshold=20"

# 查看指标
curl "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/metrics"

# 卸载模块
curl -X POST "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=demo-servlet"
```

指标包括：

- `totalRequests`、`collectedRequests`、`sampledOutRequests`、`ignoredRequests`。
- `emptyRequests`、`thresholdDroppedRequests`、`droppedRequests`。
- `totalLinesCollected`、`methodThresholdReached`。
- `sendSuccess`、`sendFailed`、`queueOfferFailed`。
- `queueDepth`、`queueCapacity`、`queueCount`、`queueRunning`。
- `circuitBreakerTripped`、`circuitBreakerDroppedRequests`、`circuitBreakerPaused`。

## Benchmark

仓库提供固定目标 QPS 的基准工具，并在写入结果前校验队列排空、发送结果和接收端计数。

2026-09-20 的本机实验使用 50、100、200 RPS，每组运行 3 次。以下是中位数；Baseline 未加载 Sandbox，采样场景包含 Sandbox 与 DeepCover 的整体成本。

| 目标负载 | 场景 | 实际 RPS | P99 ms | 平均 CPU % | 平均 RSS MB |
|---:|---|---:|---:|---:|---:|
| 50 | Baseline | 49.9 | 31.4 | 38.7 | 94.4 |
| 50 | 10% 采样 | 50.0 | 35.6 | 168.2 | 231.5 |
| 50 | 100% 采样 | 50.0 | 73.8 | 299.6 | 234.6 |
| 100 | Baseline | 99.9 | 36.2 | 54.6 | 110.8 |
| 100 | 10% 采样 | 99.7 | 112.1 | 348.4 | 253.9 |
| 100 | 100% 采样 | 99.8 | 83.0 | 270.5 | 236.3 |
| 200 | Baseline | 199.2 | 190.5 | 149.2 | 132.7 |
| 200 | 10% 采样 | 181.8 | 1117.4 | 346.4 | 258.8 |
| 200 | 100% 采样 | 161.3 | 1152.5 | 361.2 | 275.8 |

这组数据表明：50 和 100 RPS 下可以维持目标吞吐，但资源占用和尾延迟增加；200 RPS 已接近本机 demo 的饱和区，10% 与 100% 采样的中位吞吐分别下降约 8.7% 和 19.0%，P99 中位数均超过 1.1 秒。三轮尾延迟波动较大，因此这些数据适合判断成本量级，不适合作为生产容量结论。

完整方法、范围、采样命中率和限制见 [Benchmark 结果](benchmarks/RESULTS.md)。

## 构建与测试

```bash
mvn clean test -Dmaven.javadoc.skip=true
mvn clean package -Dmaven.javadoc.skip=true
```

当前根工程包含 66 个单元测试，最近一次验证结果为 66 passed、0 failed、0 errors、0 skipped。

## 兼容性与部署注意事项

- Java 8+，构建目标为 Java 8。
- JVM Sandbox 版本为 1.4.0。
- 项目已有部署经验要求 DeepCover/Sandbox javaagent 位于 SkyWalking javaagent 之前；仍应在目标应用和目标 agent 版本上验证。
- 建议先在测试或预发布环境校准采样率、包范围、队列容量和熔断阈值。
- HTTP 和 Kafka 接收端应具备幂等、容量保护和数据保留策略，这些能力不由本仓库提供。

## 文档

- [配置指南](docs/configuration-guide.md)
- [部署指南](docs/deployment-guide.md)
- [Demo](examples/demo-servlet/README.md)
- [Benchmark 结果](benchmarks/RESULTS.md)
- [贡献指南](CONTRIBUTING.md)
- [变更记录](CHANGELOG.md)
- [许可证](LICENSE)

## License

DeepCover 使用 [Apache License 2.0](LICENSE)。
