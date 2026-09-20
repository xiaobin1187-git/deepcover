# DeepCover 部署指南

[中文](deployment-guide.md) | [English](deployment-guide_en.md) | [日本語](deployment-guide_ja.md) | [Francais](deployment-guide_fr.md) | [Portugues](deployment-guide_pt.md) | [Русский](deployment-guide_ru.md)

## 环境要求

| 组件 | 要求 | 说明 |
|---|---|---|
| Java | 8+ | 项目编译目标为 Java 8，基准环境为 1.8.0_202 |
| Maven | 3.5+ | 用于构建 Agent 和 demo |
| JVM Sandbox | 1.4.0 | 仓库 `sandbox/` 已包含运行所需文件 |
| Web 入口 | Servlet API | 当前从 `HttpServlet.service()` 建立请求上下文 |
| 数据接收端 | HTTP 或 Kafka | 必须与 `sendDataCenterType` 配置匹配 |

## 1. 构建

```bash
mvn clean package -Dmaven.javadoc.skip=true
```

构建会运行根工程测试，并生成：

```text
target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar
```

该 JAR 还会自动复制到：

```text
sandbox/sandbox-module/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar
```

只需要编译、不运行测试时可使用项目约定命令：

```bash
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

## 2. 准备 Sandbox

### 使用仓库内 Sandbox

仓库已包含以下结构：

```text
sandbox/
├── cfg/sandbox.properties
├── lib/sandbox-agent.jar
├── lib/sandbox-core.jar
├── lib/sandbox-spy.jar
├── module/
└── sandbox-module/
```

`sandbox/cfg/sandbox.properties` 中的 `user_module=../sandbox-module` 会加载构建后复制的 DeepCover JAR。

### 接入已有 Sandbox

将 Agent JAR 复制到现有 Sandbox 配置的 user module 目录：

```bash
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /opt/sandbox/sandbox-module/
```

不要把 DeepCover 的依赖 JAR 单独散放到应用 classpath。推荐使用包含依赖的 assembly JAR，并让 Sandbox 模块 classloader 加载。

## 3. 配置目标 JVM

以下示例使用 HTTP 发送和本地配置，不依赖 `deepcover-brain`：

```bash
java \
  "-javaagent:/opt/sandbox/lib/sandbox-agent.jar=home=/opt/sandbox;server.ip=127.0.0.1;server.port=4769;namespace=default" \
  -Dapp.name=my-service \
  -Denv=test \
  -Ddeepcover.env=test \
  -Ddeepcover.configCenterEnabled=false \
  '-Ddeepcover.packageName=com\.example\.myservice\..*' \
  -Ddeepcover.sampleRate=1000 \
  -Ddeepcover.sendDataCenterType=1 \
  -Ddeepcover.dataCenterAddr=http://collector.example/collect \
  -jar my-service.jar
```

对于 Tomcat、Jetty 或其他启动脚本，将相同参数加入 `JAVA_OPTS` 或容器 JVM 参数即可。必须保证 `-javaagent` 位于 `-jar` 或主类之前。

Kafka 模式替换为：

```text
-Ddeepcover.sendDataCenterType=2
-Ddeepcover.kafkaBootstrapServers=kafka-1:9092,kafka-2:9092
-Ddeepcover.kafkaTopic=deepcover-code-info
```

所有配置项和动态更新边界见 [配置指南](configuration-guide.md)。

## 4. 启动验收

### 检查应用与 Sandbox 端口

启动后应同时满足：

- 业务应用可以正常处理请求。
- Sandbox 管理端口可访问。
- DeepCover `metrics` 命令返回 JSON。
- 数据接收端能够收到采集消息。

```bash
curl "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/metrics"
```

重点检查：

```text
queueRunning=true
queueOfferFailed=0
sendFailed=0
circuitBreakerDroppedRequests=0
circuitBreakerPaused=false
```

产生一批业务请求后，`totalRequests` 应增加。采样命中的请求会增加 `collectedRequests`，发送完成后 `sendSuccess` 应增加，队列最终应回落到 0。

### 检查日志

DeepCover 默认日志位置：

```text
${user.home}/logs/sandbox/code-coverage.log
${user.home}/logs/sandbox/code-coverage-error.log
```

还应检查应用标准错误输出和 Sandbox 自身日志，确认没有类增强失败、端口冲突或模块加载异常。

## 5. 配置中心模式

需要使用 `deepcover-brain` 时，配置：

```text
-Ddeepcover.configCenterEnabled=true
-Ddeepcover.configCenterAddr=http://deepcover-brain.example
```

模块初始化时会读取服务配置。配置中心不可用、返回非 200、服务不存在或配置校验失败时，DeepCover 不会启动采集。

配置中心后续轮询可以更新采样率、阈值、URL 过滤等动态字段。包范围、类/方法/注解过滤、队列数量、队列容量和上报周期需要重启后生效。

## 6. 生命周期命令

```bash
# 重新创建采集监听器
curl -X POST "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/startCodeModule"

# 动态配置
curl -X POST "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=1000&exceptionThreshold=20"

# 完整卸载 DeepCover 模块资源
curl -X POST "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=my-service"
```

`unloadCodeModule` 会停止监听器、线程池、本地队列、HTTP 客户端和 Kafka producer。当前实现卸载后不保证在同一进程中完整重建所有已关闭的全局线程池，重新启用应通过进程重启完成。

## 7. 生产前容量校准

不要直接沿用 demo 的采样率或队列参数。至少在目标服务上验证：

1. 无 Agent、候选采样率、100% 采样三个场景。
2. 正常 QPS、峰值 QPS 和超过峰值的保护场景。
3. P50/P95/P99、CPU、RSS、GC、业务错误率。
4. `queueDepth`、`queueOfferFailed`、`sendFailed`、熔断次数。
5. 接收端不可用、延迟升高、Kafka broker 不可用时的行为。
6. 长时间运行和模块卸载时是否存在资源泄漏。

仓库基准脚本：

```powershell
.\benchmarks\run-benchmark.ps1 `
  -TargetRps '50,100,200' `
  -DurationSeconds 15 `
  -Concurrency 64 `
  -Warmup 200 `
  -Repeats 3
```

当前本机结果与限制见 [Benchmark 结果](../benchmarks/RESULTS.md)。

## 8. 安全要求

Sandbox HTTP 管理端口可以执行模块命令，不应直接暴露到公网。建议：

- 默认绑定 `127.0.0.1`，通过受控运维通道访问。
- 必须远程访问时，通过防火墙、服务网格或反向代理限制来源并增加认证。
- 不在启动参数、日志或仓库中提交 Kafka 凭据、令牌和私有地址。
- 数据接收端按 traceId 和服务维度实施容量、幂等和保留策略。

## 9. 兼容性注意事项

- 当前工程经验要求 DeepCover/Sandbox javaagent 位于 SkyWalking javaagent 之前。不同版本组合仍需单独验证。
- 多个字节码 Agent 同时增强同一类时，启动顺序会影响结果。
- WebFlux、Netty、RPC 消费端、消息监听器和异步线程切换不属于当前 Servlet 请求关联范围。
- 应用 class 文件需要保留行号表，否则只能获得不完整的行级信息。

## 故障排查

### 模块没有启动

- 检查 `app.name`、`env` 和 `deepcover.packageName`。
- HTTP 模式检查 `deepcover.dataCenterAddr`；Kafka 模式检查 broker 和 topic。
- 不使用配置中心时确认 `deepcover.configCenterEnabled=false`。
- 检查 Sandbox home、user module 路径和端口占用。

### 没有采集数据

- 确认请求经过 `HttpServlet.service()`。
- 确认 `packageName` 正则匹配业务类。
- 查看 `sampledOutRequests`、`ignoredRequests`、`emptyRequests`、`thresholdDroppedRequests` 和 `circuitBreakerDroppedRequests`。
- 确认业务 class 文件包含行号信息。

### 队列积压或丢弃

- 比较 `queueDepth` 与 `queueCapacity`。
- 检查接收端延迟和 `sendFailed`。
- 优先降低采样率或缩小包范围，再根据压测结果调整队列。
- 队列变大只会延后丢弃，也会增加内存占用，不能替代接收端扩容。

### 性能影响过高

- 降低 `sampleRate`。
- 缩小 `packageName`，补充类、方法、注解和 URL 过滤。
- 降低单请求和单方法采集阈值。
- 在目标机器上重新执行基准，不直接套用仓库 demo 数据。
