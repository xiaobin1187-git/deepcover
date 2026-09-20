# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/lang/zh-CN/).

## [1.0.0] - 2026-02-26

### 首次开源发布

#### 新增

- 完全开源发布，采用 Apache 2.0 许可证
- 完整文档: README.md、CONTRIBUTING.md、CHANGELOG.md、LICENSE
- GitHub Issue 和 PR 模板 (.github/)
- 配置模板 deepcover.properties.example

#### 代码优化

- 移除内部依赖 cat-toolkit-trace
- 使用 OpenTelemetry API 替代内部 trace 依赖
- 移除所有内部域名、IP 地址和密钥
- 更新 .gitignore 排除敏感文件和编译产物

## [1.0.1] - 2026-04-02

### 稳定性修复

#### P0 修复 (5项)

- 修复 ThreadLocal 内存泄漏: opStackRef/traceIdRef/Spy.traceIdThreadLocal 在 finally 块中完整清理
- 修复 Attachment NPE 崩溃: beforeLine() 添加 codeEntity==null 三层判空保护
- 修复队列满误触发熔断: LocalAsyncEngine.offerMsg() 移除 exceptionOverflow() 调用
- 修复 HttpClient 未正确关闭: 添加 HttpClient2.shutdown() 方法
- 修复 KafkaProducer 未正确关闭: 添加 KafkaProducerEngine.shutdown() 方法

#### P1 修复 (4项)

- 优化线程池配置: core=4, max=8, queue=256, CallerRunsPolicy
- 线程池优雅关闭: awaitTermination(5s) + shutdownNow() 兜底
- 正则表达式 Pattern 缓存: ConcurrentHashMap 缓存编译结果
- 行号去重优化: ArrayList -> LinkedHashSet, O(n) -> O(1)

#### P2 修复 (3项)

- JSON 序列化性能优化: fluentPut + toJSONString 链式调用, JSONArray 预分配容量
- 配置热更新补全: syncConfig 从 6 项扩展到 18 项全覆盖
- 日志级别修正: 异常场景 info -> warn/error, 调试信息 info -> debug

### 性能优化

- split("\\$") 替换为 indexOf + substring, 避免热路径正则编译
- ArrayList 预分配容量, 消除热路径无用对象创建
- 移除未使用 import

### 测试

- 从零建立单元测试体系, 41 个用例全部通过
- 测试依赖: JUnit 4.13.2 + Mockito 3.12.4
- 覆盖: TraceContext(6), LineEntity(8), CodeEntity(6), TraceUtil(9), ExceptionAwareUtil(7), LocalAsyncEngine(5)

---

## [1.1.0] - 2026-04-08

### 新增

- 运行时监控指标端点 `@Command("metrics")`，暴露请求计数、队列深度、熔断状态等 JSON 指标
- GitHub Actions CI 自动构建测试，支持 Java 8/11/17 矩阵
- 代码示例项目 `examples/demo-servlet`，包含完整 Servlet 应用示例
- 多语言用户文档: 部署指南和配置指南各 6 种语言 (中文/EN/JA/FR/PT/RU)
- 多语言 README: 6 种语言版本
- 社区规范文件: CODE_OF_CONDUCT.md, SECURITY.md, Issue/PR 模板
- Maven Wrapper (`mvnw`) 支持无预装 Maven 构建
- Apache 2.0 License 头部添加到所有 Java 源文件

### 改进

- MetricsCollector: 线程安全 AtomicLong 计数器，覆盖请求/行/发送/队列/熔断全链路
- 测试用例从 41 增加到 52 (新增 MetricsCollectorTest 11 个用例)
- README 增加 CI 构建状态徽章

---

## [Unreleased]

### 新增

- 可复现 Benchmark 工具，支持固定目标 RPS、CPU/RSS 采样、队列排空和发送一致性校验
- Oracle JDK 8 JFR 采集与热点汇总脚本，用于记录 Profile -> Optimize -> Benchmark 过程
- 独立运行的嵌入式 Jetty demo JAR
- Test Case ID -> traceId -> HTTP Request -> Code Relation 的最小映射示例
- 请求丢弃原因、采集行数和队列运行状态指标

### 修复

- 修正 traceId 采样语义，`10000=100%`、`1000=10%`
- 支持 W3C `traceparent`、B3 和自定义 trace header，并在请求结束时清理上下文
- 修正本地消费线程启动/关闭竞态和中断告警循环，关闭时有界排空队列
- 修正 HTTP/Kafka 发送成功与失败计数，HTTP 客户端改为按需初始化并可关闭重建
- 配置中心改为可选，补充 JVM 系统属性、启动校验和结构配置重启提示
- 移除当前实现未使用的 OpenTelemetry 和本地 `sandbox-spy.jar` Maven 依赖

### 性能

- Servlet 请求访问方法按 request 实现类缓存，移除请求入口的动态代理创建与重复方法解析
- trace header 命中后短路，`traceparent` 使用索引解析，并停止读取未使用的请求字段
- 移除 `LineEntity` JSON 序列化再解析、集合临时字符串和同步 `Stack` 热路径
- Benchmark 场景按 repeat 轮换，并增加同轮 Baseline 配对增量
- 200 RPS 配对吞吐变化从首轮的 `-8.7% / -17.9%` 改善为 `-0.1% / -4.3%`（10% / 100% 采样）；CPU、RSS 和 P99 仍记录为已知瓶颈

### 文档

- 增加 Why DeepCover、精准测试闭环架构、能力边界和非目标
- 增加 Performance Optimization Journey、JFR 前后热点和 100/200 RPS 优化后结果
- 增加 Test Case 到请求与代码关系的可运行示例
- 配置与部署文档按当前源码行为重写

### 测试

- 根工程单元测试增加到 69 个

### 计划中

- 支持更多采集策略配置
- 支持更多 JVM 版本

### 已知问题

- 回调查询表存在性能问题，建议生产环境设置合理采样率
- HTTP 模式当前仍逐条同步发送；200 RPS 下 CPU、RSS 和 P99 开销仍然显著
- 需要与 SkyWalking agent 配合使用 (SkyWalking 必须在 DeepCover 之后加载)

---

贡献方式请查看 [CONTRIBUTING.md](CONTRIBUTING.md)。
