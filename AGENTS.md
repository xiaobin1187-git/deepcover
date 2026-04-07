# DeepCover Agent 开发指南

本文档为 AI 代理提供 DeepCover 代码库的开发指南。

## 构建和测试

### 编译命令
```bash
mvn clean package -Dmaven.test.skip=true -X -Dmaven.javadoc.skip=true
```

**注意**：项目使用本地 Maven 配置 `D:\tools\apache-maven-3.5.2\conf\settings.xml`

### 编译产物
- 目标位置：`target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`
- 自动复制到：`sandbox/sandbox-module/`

### 测试
```bash
mvn clean test -Dmaven.javadoc.skip=true
```

## 代码风格指南

### 命名约定
- **类名**：PascalCase（如 `CodeCollecter`, `HttpCodeModule`, `CodeEntity`）
- **方法名**：camelCase（如 `loadCompleted`, `syncConfig`, `reportServerInfo`）
- **字段名**：camelCase（如 `moduleName`, `serviceName`）
- **常量**：UPPER_SNAKE_CASE（配置类）或 camelCase（静态字段）
- **包名**：全小写，遵循 `io.deepcover.agent.*` 结构

### 包结构
```
io.deepcover.agent/
├── entity/        # 实体类（CodeEntity, LineEntity）
├── config/        # 配置和线程池（DeepCoverConfig, ExecutorThreadPoolConfig）
├── ext/           # Sandbox 扩展（CodeAdviceListener, CodeEventWatcher）
└── util/          # 工具类（IpUtil, FilterUtil）
```

### 导入顺序
1. 第三方库（hutool, fastjson）
2. Alibaba JVM Sandbox API
3. 内部项目包
4. Java 标准库
5. Lombok 注解

### Lombok 使用
- **实体类**：使用 `@Data` 自动生成 getter/setter/toString/equals/hashCode
- **灵活控制**：部分类使用 `@Getter` + `@Setter` 分离控制
- **日志**：所有类使用 `@Slf4j` 替代显式 Logger 声明
- **序列化**：实现 `Serializable` 时保留 `serialVersionUID`

### 注释规范
- **类注释**：Javadoc 格式，包含 `@description`, `@author`, `@time`
- **方法注释**：Javadoc 格式，说明参数和返回值
- **行内注释**：使用 `//` 标注关键逻辑

### 异常处理
- 所有 catch 块必须记录异常：`log.error("message", e)`
- 禁止空 catch 块
- 使用 `ExceptionAwareUtil.exceptionOverflow(e)` 进行异常熔断

### 日志规范
- **框架**：Slf4j + Logback
- **日志级别**：
  - `error`：错误和异常
  - `warn`：警告（如配置变更、限流）
  - `info`：关键业务流程（初始化、上报）
  - `debug`：调试信息
- **日志文件**：
  - `${user.home}/logs/sandbox/code-coverage.log`
  - `${user.home}/logs/sandbox/code-coverage-error.log`

### JVM Sandbox 特定模式
- **模块生命周期**：实现 `ModuleLifecycle` 接口（onLoad, onActive, onFrozen, onUnload, loadCompleted）
- **事件监听**：继承 `CodeAdviceListener`，重写 `before()`, `after()`, `beforeLine()` 方法
- **对外命令**：使用 `@Command("commandName")` 注解暴露命令
- **资源注入**：使用 `@Resource` 注入 `ModuleEventWatcher`

### 配置管理
- **配置类**：`DeepCoverConfig` 存储所有配置，通过 `PropertyUtil` 读取系统属性
- **动态配置**：通过配置中心 `deepcover-brain` 拉取服务配置，支持运行时更新
- **配置文件**：`src/main/resources/deepcover.properties` 按环境存储配置

### 线程管理
- **调度线程池**：`ExecutorThreadPoolConfig.scheduleEx`（单线程，命名模式 `code-coverage-schedule-%d`）
- **发送线程池**：`ExecutorThreadPoolConfig.executorSendMsg`（2核心线程，队列16，命名 `code-coverage-send-msg-%d`）
- **异步队列**：`LocalAsyncConfig` 使用 `LocalAsyncEngine` 批量处理消息

### 数据结构
- **序列化**：使用 `fastjson` 进行 JSON 序列化
- **实体设计**：
  - `CodeEntity`：HTTP 请求上下文（包含 `ArrayList<LineEntity> codeInfo`）
  - `LineEntity`：代码行信息（类名、方法名、参数、行号列表）

### 编码规范
- **Java 版本**：1.8
- **字符编码**：UTF-8
- **缩进**：4 空格
- **大括号**：K&R 风格（左大括号同行）

### 禁止事项
- 不留空 catch 块
- 不使用空 try-catch 吞没异常
- 不删除测试来通过

### 常见操作
**启动采集模块**：
```java
HttpCodeModule module = new HttpCodeModule(moduleEventWatcher);
module.run();
```

**卸载采集模块**：
```java
if(CodeCollecter.codeEventWatcher!=null){
    CodeCollecter.codeEventWatcher.onUnWatched();
}
ExecutorThreadPoolConfig.scheduleEx.shutdown();
```

**发送消息**：
```java
LocalAsyncConfig.sendMessage(codeEntity);
// 或直接发送
HttpClient2.doAsyncPost(DeepCoverConfig.dataCenterAddr, codeEntity);
KafkaProducerEngine.sendMessage(codeEntity);
```

### 项目注意事项
- SkyWalking agent 必须在 DeepCover 之后加载，否则会冲突
- 回调查询表存在性能问题，仅作为临时调试使用
- traceId 异常格式会被过滤以减少告警
- 采集包含性能开销，生产环境需设置合理采样率
