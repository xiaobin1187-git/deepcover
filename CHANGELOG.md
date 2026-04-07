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

## [Unreleased]

### 计划中

- 支持更多采集策略配置
- 添加监控指标暴露 (P3)
- 支持更多 JVM 版本

### 已知问题

- 回调查询表存在性能问题，建议生产环境设置合理采样率
- 需要与 SkyWalking agent 配合使用 (SkyWalking 必须在 DeepCover 之后加载)

---

贡献方式请查看 [CONTRIBUTING.md](CONTRIBUTING.md)。
