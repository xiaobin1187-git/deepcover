# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

DeepCover 是一个基于 JVM Sandbox 的精准测试采集 Agent，用于 Java 应用的无侵入代码覆盖率采集。项目通过增强目标应用的 HTTP 请求和方法调用，采集代码执行行号、类名、方法名等信息，并通过 HTTP 或 Kafka 发送到数据中心。

## 构建和编译

### Maven 编译命令
```bash
mvn clean package -Dmaven.test.skip=true -X -Dmaven.javadoc.skip=true
```

注意：项目使用本地 Maven 配置 `D:\tools\apache-maven-3.5.2\conf\settings.xml`

### 编译产物
编译后会在 `target/` 目录下生成 `deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`，并通过 maven-antrun-plugin 自动复制到 `sandbox/sandbox-module/` 目录。

## 核心架构

### 模块结构

**核心入口类**
- `CodeCollecter` - JVM Sandbox Module 主入口，实现 `ModuleLifecycle` 接口
  - `loadCompleted()` - 模块加载完成后的初始化入口
  - `syncConfig()` - 动态同步配置中心配置
  - `reportServerInfo()` - 定期上报服务器信息

**采集模块**
- `HttpCodeModule` - 代码覆盖率采集核心逻辑
  - `createWatcher()` - 创建 Sandbox 事件监听器
  - 通过 `CodeAdviceListener` 监听 HTTP 请求和方法调用
  - `before()` - 在 `HttpServlet.service` 入口创建 `CodeEntity` 采集上下文
  - `beforeLine()` - 采集代码行号信息
  - `after()` - 在请求结束时发送采集数据

**事件监听**
- `CodeAdviceListener` - Sandbox Advice 监听器适配器
- `CodeEventWatchBuilder` - 事件监听器构建器，支持正则匹配类和方法
- `CodeEventWatcher` - 事件监视器封装

**配置管理**
- `DeepCoverConfig` - 全局配置类，所有配置项通过配置中心动态获取
  - 从配置中心 `deepcover-brain` 拉取服务配置
  - 支持 HTTP 和 Kafka 两种数据发送方式
  - 支持异常熔断机制

**数据发送**
- `LocalAsyncConfig` / `LocalAsyncEngine` - 本地异步队列，用于批量发送采集数据
- `KafkaProducerEngine` - Kafka 发送引擎
- `HttpClient2` - HTTP 异步发送客户端

### 关键配置

**JVM 启动参数**
```
-javaagent:{sandbox-path}/lib/sandbox-agent.jar
-Dapp.name={应用名}
-Denv={环境标识: test/pre/pro}
-Ddeepcover.env={采集环境}
-Dbranch={代码分支}
```

**采集范围配置**
- `packageName` - 需要采集的包名（正则匹配）
- `ignoreClasses` - 忽略的类名（正则，分号分隔）
- `ignoreUrls` - 忽略的 URL（分号分隔）
- `ignoreAnnos` - 忽略的注解

**性能限制配置**
- `limitCodeMethodSize` - 单请求采集的最大代码节点数（默认500）
- `limitCodeMethodLineSize` - 单方法最大行号采集次数（默认500）
- `sampleRate` - 采样率（10000=100%）

**异常熔断**
- `exceptionThreshold` - 异常阈值（默认1分钟10个）
- `exceptionCalcTime` - 异常计算时间窗口（默认1分钟）
- `exceptionPauseTime` - 熔断暂停时间（默认5秒）

### 数据流

1. HTTP 请求到达 `HttpServlet.service`
2. `before()` 创建 `CodeEntity` 并附加到 process top
3. 方法调用时 `beforeLine()` 采集行号信息到 `CodeEntity.codeInfo`
4. 请求结束时 `after()` 通过 `LocalAsyncConfig.sendMessage()` 发送数据
5. 数据经本地队列批量发送至数据中心或 Kafka

## 本地调试部署

1. 编译打包后，将 jar 包复制到 `sandbox/sandbox-module/` 目录
2. 或修改 `sandbox/cfg/sandbox.properties` 中的 `user_module` 指向编译产物
3. 目标应用 JVM 添加 `-javaagent` 参数启动
4. 检查日志中是否出现 "加载成功" 字样
5. 通过 Sandbox Jetty 服务控制模块启停：`http://{server.ip}:{server.port}/sandbox/default/module/http/sandbox-module-mgr/active?ids=xxx`

## 配置文件

- `src/main/resources/deepcover.properties` - 环境配置（数据中心地址、Kafka 配置）
- `sandbox/cfg/sandbox.properties` - Sandbox 服务配置
- 服务采集策略通过配置中心 `deepcover-brain` 动态配置

## 注意事项

- SkyWalking agent 必须在 DeepCover 之后加载，否则会冲突
- 采集模块包含性能开销，生产环境需谨慎使用并设置合理采样率
- 回调查询表存在性能问题，仅作为临时调试演示使用
- traceId 异常格式会被过滤以减少告警
