# DeepCover Servlet Demo

**[中文](README.md)** | [English](README_EN.md) | [日本語](README_ja.md) | [Francais](README_fr.md) | [Portugues](README_pt.md) | [Русский](README_ru.md)

这个小型 Servlet 应用用于验证 DeepCover 的请求级代码关系采集。它提供列表、查询、创建和删除四条执行路径，不包含覆盖率计算或报告服务。

## 构建

先在仓库根目录构建 DeepCover，再构建 demo：

```bash
mvn clean package -Dmaven.javadoc.skip=true
mvn -f examples/demo-servlet/pom.xml clean package -Dmaven.javadoc.skip=true
```

产物：

- `target/demo-servlet.war`：部署到 Servlet 容器。
- `target/demo-servlet-1.0-SNAPSHOT-standalone.jar`：内嵌 Jetty，可直接运行。

## 独立运行

### 1. 启动接收端

在仓库根目录运行：

```bash
python benchmarks/mock_receiver.py --port 18081
```

### 2. 启动 demo

PowerShell：

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

不加载 Agent 的基线启动：

```bash
java -jar examples/demo-servlet/target/demo-servlet-1.0-SNAPSHOT-standalone.jar
```

默认业务端口为 `18080`，可通过 `-Ddemo.port=<port>` 修改。

## 请求路径

```bash
curl "http://127.0.0.1:18080/demo-servlet/user?action=list"
curl "http://127.0.0.1:18080/demo-servlet/user?action=get&id=1"
curl "http://127.0.0.1:18080/demo-servlet/user?action=create&name=Bob&email=bob@example.com"
curl "http://127.0.0.1:18080/demo-servlet/user?action=delete&id=1"
```

查看 Agent 指标和接收端计数：

```bash
curl "http://127.0.0.1:4769/sandbox/default/module/http/deepcover/metrics"
curl "http://127.0.0.1:18081/stats"
```

## DeepCover 在这个 Demo 中记录什么

请求命中 `UserServlet.service()` 后，DeepCover 会：

1. 读取或生成 traceId，并记录 HTTP 方法、URL、端口和开始时间。
2. 对匹配 `io.deepcover.examples.demo.*` 的类接收方法和行事件。
3. 将本次请求实际经过的 `UserServlet`、`UserService` 方法与行号写入 `CodeEntity`。
4. 在请求结束时把完整实体放入本地有界队列。
5. 由消费线程发送到本地 mock receiver，并更新队列与发送指标。

行号会随源码和编译器变化，因此不在文档中固化具体数字。判断是否采集成功应以接收 payload 和 `/metrics` 为准。

## WAR 部署

将 `target/demo-servlet.war` 部署到 Tomcat、Jetty 等 Servlet 容器，并把与独立运行相同的 `-javaagent` 和 `-Ddeepcover.*` 参数加入容器 JVM 参数。

详细部署要求见 [部署指南](../../docs/deployment-guide.md)。

## Benchmark

仓库根目录提供自动化基准：

```powershell
.\benchmarks\run-benchmark.ps1 -TargetRps '50,100,200' -Repeats 3
```

脚本会分别运行无 Agent、10% 采样和 100% 采样场景，并校验队列、发送计数和接收端计数。已记录结果见 [Benchmark 结果](../../benchmarks/RESULTS.md)。

要把外部测试用例 ID 与本 Demo 实际执行的类、方法和行号关联起来，可运行 [Test Case Mapping Demo](../test-case-mapping/README.md)。
