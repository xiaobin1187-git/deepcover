# DeepCover 性能基准与优化记录

本文记录 DeepCover 的本机性能实验、JFR 热点分析和一次针对运行时热路径的优化。数据用于说明当前实现的成本与瓶颈，不代表生产环境容量承诺。

## 测试方法

- 日期：2026-09-20
- 操作系统：Windows 10.0.19045
- CPU：Intel Core i5-10210U，4 核 8 逻辑处理器
- 内存：23.8 GB
- Java：Oracle JDK 1.8.0_202
- 应用：仓库内 `examples/demo-servlet`，嵌入式 Jetty
- 发送端：HTTP 模式；接收端为同机 Python HTTP Server
- 负载：100、200 目标 RPS；并发上限 64；每轮预热 200 请求；每个场景持续 15 秒
- 重复次数：每个负载和采样组合运行 3 次；场景顺序按 repeat 轮换
- Baseline：仅运行 demo，不加载 JVM Sandbox 或 DeepCover
- 10% / 100%：加载 JVM Sandbox 和 DeepCover，分别配置 `sampleRate=1000` 和 `sampleRate=10000`
- 最终结果目录：`benchmarks/results/20260920-164043`，该目录按 `.gitignore` 不提交

运行时工作区包含本文对应的待提交优化，环境记录中的 Git 基线为 `5600ea1a51bfe1d86fa8ca8bbe56fff217fb741b`。

执行命令：

```powershell
.\benchmarks\run-benchmark.ps1 `
  -TargetRps '100,200' `
  -DurationSeconds 15 `
  -Concurrency 64 `
  -Warmup 200 `
  -Repeats 3 `
  -DrainTimeoutSeconds 120
```

结果汇总：

```bash
python benchmarks/summarize_results.py benchmarks/results/<timestamp>
```

进程 CPU 使用 psutil 口径，100% 表示占满一个逻辑处理器。RSS 是 demo JVM 在测量阶段的平均常驻内存。

## 优化后绝对结果

| 目标负载 | 场景 | 实际 RPS 中位数（范围） | P99 ms 中位数（范围） | 平均 CPU % 中位数 | 平均 RSS MB 中位数 | 请求错误 |
|---:|---|---:|---:|---:|---:|---:|
| 100 RPS | Baseline | 99.8（99.8-99.9） | 35.0（34.4-36.9） | 52.0 | 108.6 | 0 |
| 100 RPS | 10% 采样 | 99.8（99.6-99.9） | 122.4（41.1-252.4） | 250.4 | 222.4 | 0 |
| 100 RPS | 100% 采样 | 99.8（99.8-99.8） | 36.6（36.6-61.4） | 283.0 | 233.4 | 0 |
| 200 RPS | Baseline | 199.4（196.5-199.6） | 38.4（37.7-675.2） | 96.9 | 132.9 | 0 |
| 200 RPS | 10% 采样 | 199.4（184.5-199.5） | 849.0（786.5-897.1） | 316.2 | 215.6 | 0 |
| 200 RPS | 100% 采样 | 188.0（181.9-195.9） | 1048.5（1028.5-1170.2） | 330.4 | 221.1 | 0 |

## 同轮 Baseline 配对增量

每个 Agent 场景先与相同 repeat 的 Baseline 比较，再取三次增量的中位数，以减少不同轮次机器状态对相对结果的影响。

| 目标负载 | 场景 | 吞吐变化 | P99 变化 | CPU 变化 | RSS 变化 |
|---:|---|---:|---:|---:|---:|
| 100 RPS | 10% 采样 | 0.0% | +256.1% | +198.4 个百分点 | +113.8 MB |
| 100 RPS | 100% 采样 | +0.0% | +4.5% | +225.6 个百分点 | +124.2 MB |
| 200 RPS | 10% 采样 | -0.1% | +1947.8% | +184.3 个百分点 | +82.7 MB |
| 200 RPS | 100% 采样 | -4.3% | +2629.9% | +233.4 个百分点 | +92.5 MB |

百分比尾延迟增量较大，部分原因是最终 Baseline P99 较低。应同时阅读绝对 P99 和范围，不能只看百分比。

## Performance Optimization Journey

### 1. Profile

使用 `benchmarks/run-profile.ps1` 在 200 RPS、100% 采样下生成 Oracle JDK 8 JFR，再由 `benchmarks/summarize_jfr8.py` 汇总分配与 CPU 栈。

优化前主要分配证据：

| JFR 帧 | 样本数 | 含义 |
|---|---:|---|
| `java/lang/reflect/Method::copy` | 987 顶层分配样本 | 每请求反射方法枚举与复制成本明显 |
| `HttpAccessUtil::wrapperHttpAccess` | 1079 包含式分配样本 | Servlet 请求元数据读取位于热路径 |
| `InterfaceProxyUtils$1::invoke` | 1070 包含式分配样本 | 每请求动态代理调用 |
| `WrapInvocationHandler::getTargetMethod` | 1057 包含式分配样本 | 目标方法解析与缓存粒度不合适 |
| `LineEntity::equals` | 167 包含式分配样本 | 集合通过临时字符串比较 |

### 2. Optimize

- 使用 `ClassValue` 按 Servlet request 实现类缓存四个访问方法，入口不再创建动态代理。
- trace header 按优先级读取，找到有效值后立即停止；W3C `traceparent` 改为索引解析。
- 不再读取采集结果未使用的 parameter map、remote address 和 User-Agent。
- `CodeEntity` 直接加入 `LineEntity` 的 JSON 对象，移除序列化后再次解析的往返过程。
- `LineEntity.equals/hashCode` 直接比较字段和有序行集合，不再创建集合字符串。
- 请求调用栈从同步 `Stack` 改为线程内使用的 `ArrayDeque`。
- 调试日志改为参数化对象，关闭 debug 时不提前执行 `CodeEntity.toString()`。

### 3. Profile Again

优化后主要热点列表中不再出现 `Method.copy`、请求动态代理调用和代理方法查找。相同 profile 场景的一次顺序观测如下：

| 指标 | 优化前 profile | 优化后 profile |
|---|---:|---:|
| 实际 RPS | 184.5 | 198.2 |
| P99 | 1031.0 ms | 771.7 ms |
| 平均 CPU | 345.6% | 322.7% |
| 平均 RSS | 266.4 MB | 241.3 MB |

这两次 profile 用于定位热点，不是重复 Benchmark，不能据此计算统计置信度。

### 4. Benchmark Before/After

使用同一汇总算法重新计算首轮结果 `20260920-122510` 与优化后结果 `20260920-164043`。以下数据均为 200 RPS 下、与相同 repeat Baseline 配对后的中位数：

| 场景 | 首轮吞吐变化 | 优化后吞吐变化 | 首轮 CPU 增量 | 优化后 CPU 增量 | 首轮 RSS 增量 | 优化后 RSS 增量 |
|---|---:|---:|---:|---:|---:|---:|
| 10% 采样 | -8.7% | -0.1% | +206.6 个百分点 | +184.3 个百分点 | +126.1 MB | +82.7 MB |
| 100% 采样 | -17.9% | -4.3% | +212.0 个百分点 | +233.4 个百分点 | +144.5 MB | +92.5 MB |

吞吐与 RSS 观察值改善，10% 场景 CPU 增量降低；100% 场景 CPU 增量没有改善。两组 Benchmark 是同机顺序执行而非交错 A/B，且 Baseline P99 存在波动，因此这些变化只能作为工程证据，不能视为严格因果证明。

### 5. Remaining Bottlenecks

- 优化后 JFR 中，`HttpClient2::doPost` 仍有 198 个包含式 CPU 样本，HTTP 模式当前批量出队后仍逐条同步发送。
- JSON 序列化仍位于主要 CPU 路径。
- LINE 事件分发在分配路径中仍可见；调用深度、业务行数和增强范围会直接影响成本。
- `ThreadLocalMap::set` 出现在优化后分配热点中，请求状态结构仍值得继续收敛。
- 200 RPS 下 P99 仍为 849.0-1048.5 ms，CPU 与 RSS 增量仍然显著。
- 队列竞争在本次单队列 demo 中不是首要热点，但多生产线程、多队列和远程发送场景仍需单独验证。

下一阶段更有价值的方向是：HTTP 真批量协议或异步客户端、序列化复用、LINE 事件按风险分级、缩小增强范围，以及在容器资源限制下做长时间稳定性测试。

## 采样与发送校验

| 场景 | 已采集 / 总请求 | 实际采集比例 | 发送失败 |
|---|---:|---:|---:|
| 10% 采样 | 1294 / 14708 | 8.80% | 0 |
| 100% 采样 | 14706 / 14706 | 100.00% | 0 |

12 个 Agent 运行场景均满足：队列最终深度为 0、`sendFailed=0`，接收端请求数与 `sendSuccess` 完全一致。10% 场景采用确定性 traceId 哈希；这组有限 trace 集合上的 8.80% 是实际命中率，不是随机采样器的统计置信区间。

## 结论边界

1. 100 RPS 下三组都能维持目标吞吐，但 DeepCover 增加了明显的 CPU、常驻内存和部分场景尾延迟成本。
2. 200 RPS 下，优化后 10% 采样维持了目标吞吐，100% 采样仍下降 4.3%；两种采样的 P99 都明显升高。
3. 10% 采样不等于 10% 总开销。Servlet 入口判断、Sandbox 事件分发、类增强和固定运行时资源仍然存在。
4. Baseline 未加载 Sandbox，因此增量是“Sandbox + DeepCover”的整体部署成本，不是对 DeepCover 模块自身的隔离测量。
5. 前后实验未交错执行，机器状态、JIT、GC 和同机接收端都可能影响结果。
6. 本实验没有覆盖真实网络、远程 Kafka、容器资源限制、GC 日志分析、长时间稳定性或复杂业务调用栈。

生产使用前，应在目标服务、目标 JDK、真实流量模型和资源限制下重新执行，并将 P99、CPU、RSS、队列深度和发送失败率纳入准入标准。
