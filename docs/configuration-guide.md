# DeepCover 配置指南

[中文](configuration-guide.md) | [English](configuration-guide_en.md) | [日本語](configuration-guide_ja.md) | [Francais](configuration-guide_fr.md) | [Portugues](configuration-guide_pt.md) | [Русский](configuration-guide_ru.md)

本文以当前源码行为为准，说明配置来源、启动校验、动态更新边界和运行指标。

## 配置来源与优先级

DeepCover 有三类配置来源：

1. 内置默认值。
2. classpath 中的 `deepcover.properties` 和 JVM 系统属性。
3. 可选的 `deepcover-brain` 配置中心。

初始化顺序是：读取本地 properties，应用 JVM 系统属性，然后在启用配置中心时读取远程服务配置。因此，同一个采集字段通常遵循“配置中心 > JVM 属性 > 默认值”。地址类字段不由远程服务配置覆盖。

`deepcover.properties` 当前只读取以下内容：

- `<env>.dataCenterAddr`
- `<env>.configCenterAddr`
- `<env>.KAFKA_BOOTSTRAP_SERVERS`
- `<env>.KAFKA_TOPIC`
- `getDeepCoverInfo`
- `reportServerInfo`

采集范围、采样率、阈值和队列参数应使用 `-Ddeepcover.*` JVM 属性，或由配置中心返回。

## 最小启动配置

HTTP 发送模式最少需要：

```text
-Dapp.name=my-service
-Denv=test
-Ddeepcover.configCenterEnabled=false
-Ddeepcover.packageName=com\.example\.myservice\..*
-Ddeepcover.sendDataCenterType=1
-Ddeepcover.dataCenterAddr=http://127.0.0.1:8081/collect
```

Kafka 发送模式最少需要：

```text
-Dapp.name=my-service
-Denv=test
-Ddeepcover.configCenterEnabled=false
-Ddeepcover.packageName=com\.example\.myservice\..*
-Ddeepcover.sendDataCenterType=2
-Ddeepcover.kafkaBootstrapServers=127.0.0.1:9092
-Ddeepcover.kafkaTopic=deepcover-code-info
```

缺少服务名、环境、采集包正则或当前发送模式所需地址时，模块会记录错误并停止初始化。

## 基础标识

| JVM 属性 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `app.name` | String | `unknown` | 服务名；必填。也可由环境变量 `APP_NAME` 提供 |
| `env` | String | `unknown` | 环境名；`deepcover.env` 优先于该值 |
| `deepcover.env` | String | `unknown` | DeepCover 环境名，用于选择本地 properties 的环境前缀 |
| `env.code` | String | `unknown` | 上报配置中心时使用的部署环境编码 |
| `branch` | String | `master` | 写入采集数据的分支标识 |

## 采集范围

| JVM 属性 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `deepcover.packageName` | Regex | 空 | 需要增强的业务类正则；必填 |
| `deepcover.ignoreClasses` | Regex 列表 | 空 | 忽略类，分号分隔，内部合并为正则 |
| `deepcover.ignoreMethods` | String 列表 | 空 | 忽略方法模式，分号分隔 |
| `deepcover.ignoreAnnos` | String 列表 | 空 | 忽略注解名称，分号分隔 |
| `deepcover.ignoreUrls` | Regex 列表 | 空 | 忽略 URL，分号分隔；使用完整正则匹配 |

`packageName`、类、方法和注解过滤会影响 Sandbox 监听器的构建，运行期间修改不会自动重建监听器。

## 采样与阈值

| JVM 属性 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `deepcover.sampleRate` | Integer | `10000` | 范围 `0-10000`；`10000=100%`、`1000=10%`、`100=1%` |
| `deepcover.limitCodeMethodSize` | Integer | `500` | 单请求最大方法节点数；超过后该请求数据不发送 |
| `deepcover.limitCodeMethodLineSize` | Integer | `500` | 单方法最大行事件数；超过后停止该方法后续行采集 |

采样基于 traceId 的 Java 哈希值，同一个 traceId 在相同算法和配置下会得到相同结果。对有限的 traceId 集合，实际命中比例可能与配置百分比存在统计偏差。

## 数据发送

| JVM 属性 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `deepcover.sendDataCenterType` | Integer | `1` | `1=HTTP`，`2=Kafka` |
| `deepcover.dataCenterAddr` | URL | 空 | HTTP 接收端地址 |
| `deepcover.kafkaBootstrapServers` | String | 空 | Kafka bootstrap servers |
| `deepcover.kafkaTopic` | String | 空 | Kafka topic |

HTTP 模式由本地消费线程批量取出队列元素，再逐条发送 HTTP 请求。Kafka 模式使用异步 producer 回调统计成功和失败。

## 本地队列

| JVM 属性 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `deepcover.queueNum` | Integer | `1` | 有界队列数量，同时也是消费线程数量 |
| `deepcover.queueSize` | Integer | `100` | 每个 `ArrayBlockingQueue` 的元素容量 |
| `deepcover.queueMsgSize` | Integer | `50` | 单次 `drainTo` 的最大元素数 |
| `deepcover.queueRecycleTime` | Integer | `10` | 队列为空时消费线程休眠时间，单位为毫秒 |

队列满时，新消息会被拒绝，并增加 `queueOfferFailed` 和 `droppedRequests`。调整队列容量前应同时观察内存、发送速度和数据接收端容量。

## 异常熔断

| JVM 属性 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `deepcover.exceptionThreshold` | Integer | `10` | 在统计窗口内触发熔断的异常数 |
| `deepcover.exceptionCalcTime` | Integer | `1` | 异常统计窗口，分钟 |
| `deepcover.exceptionPauseTime` | Integer | `5` | 熔断后的暂停时间，秒 |

发送和采集异常达到阈值后，入口采集会暂停。暂停时间结束后，计数器在下一次请求判断时清理并恢复采集。

## 配置中心

| JVM 属性 | 默认值 | 说明 |
|---|---:|---|
| `deepcover.configCenterEnabled` | `true` | 是否启用配置中心；地址为空时不会访问 |
| `deepcover.configCenterAddr` | 空 | `deepcover-brain` 地址 |
| `deepcover.getDeepCoverInfo` | `/deepcover-brain/deepcover/info` | 初次读取服务配置路径 |
| `deepcover.reportServerInfo` | `/deepcover-brain/deepcover/report/server/info` | 节点上报和轮询路径 |
| `deepcover.reportPeriod` | `10` | 上报周期，秒 |

独立运行时建议显式设置：

```text
-Ddeepcover.configCenterEnabled=false
```

配置中心初次加载发生在监听器创建前，可提供完整采集结构配置。后续轮询只允许动态字段生效；结构字段发生变化时会记录 `restartRequired`，并保留当前运行值。远端配置会先整体校验再一次性应用，任一字段非法时保留上一版本配置。

## 动态配置

### `syncConfig` 命令

```bash
curl -X POST "http://sandbox-host:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=1000&exceptionThreshold=20&queueRecycleTime=20"
```

当前可立即生效的字段：

- `sampleRate`
- `exceptionThreshold`
- `exceptionCalcTime`
- `exceptionPauseTime`
- `limitCodeMethodSize`
- `limitCodeMethodLineSize`
- `queueMsgSize`
- `queueRecycleTime`
- `ignoreUrls`
- `sendDataCenterType`
- `configVersion`

收到但不会立即应用、响应中列入 `restartRequired` 的字段：

- `packageName`
- `ignoreClasses`
- `ignoreMethods`
- `ignoreAnnos`
- `queueNum`
- `queueSize`
- `reportPeriod`

成功响应示例：

```json
{
  "success": true,
  "serviceName": "my-service",
  "configVersion": 3,
  "applied": ["sampleRate", "exceptionThreshold"],
  "restartRequired": ["packageName"]
}
```

## 运行指标

```bash
curl "http://sandbox-host:port/sandbox/default/module/http/deepcover/metrics"
```

响应示例：

```json
{
  "serviceName": "my-service",
  "env": "test",
  "uptimeSeconds": 3600,
  "configVersion": 3,
  "sampleRate": 1000,
  "sendType": "HTTP",
  "totalRequests": 10000,
  "collectedRequests": 980,
  "droppedRequests": 9020,
  "sampledOutRequests": 9000,
  "ignoredRequests": 20,
  "emptyRequests": 0,
  "thresholdDroppedRequests": 0,
  "totalLinesCollected": 150000,
  "methodThresholdReached": 0,
  "sendSuccess": 980,
  "sendFailed": 0,
  "queueOfferFailed": 0,
  "queueDepth": 0,
  "queueCapacity": 100,
  "queueCount": 1,
  "queueRunning": true,
  "circuitBreakerTripped": 0,
  "circuitBreakerDroppedRequests": 0,
  "circuitBreakerPaused": false
}
```

建议至少为 `queueDepth / queueCapacity`、`queueOfferFailed`、`sendFailed`、`circuitBreakerDroppedRequests` 和 `circuitBreakerPaused` 建立监控。采样率调整应结合服务 P99、CPU、RSS 和接收端积压共同判断，而不是只观察 Agent 内部指标。
