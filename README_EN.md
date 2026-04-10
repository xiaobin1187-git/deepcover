<img src="docs/assets/logo.svg" alt="DeepCover Logo" width="128" height="128" align="right">
# DeepCover - Full-Chain Precision Analysis Collection Agent

[Chinese](README.md) | **English** | [日本語](README_JA.md) | [Francais](README_FR.md) | [Portugues](README_PT.md) | [Русский](README_RU.md)

<div align="center">

![CI](https://img.shields.io/github/actions/workflow/status/xiaobin1187-git/deepcover/ci.yml?branch=main)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-1.8-orange)
![Maven](https://img.shields.io/badge/Maven-3.5-blue)
![Tests](https://img.shields.io/badge/Tests-52_passed-brightgreen)

</div>

> A JVM Sandbox-based Java precision analysis collection tool for non-intrusive line-level precision analysis monitoring

## Introduction

DeepCover is a **non-intrusive Java precision analysis collection Agent** based on Alibaba JVM Sandbox. It collects real-time code line execution data without modifying application source code.

### Key Features

- **Non-intrusive Collection** -- Based on JVM Sandbox bytecode enhancement, no source code changes required
- **Line-Level Analysis** -- Precise execution records down to each line of code
- **HTTP Request Tracing** -- Automatic identification and tracing of HTTP Servlet requests
- **High-Performance Design** -- Async queues + batch sending to minimize application impact
- **Flexible Configuration** -- Fine-grained control over class names, method names, sampling rates, with dynamic hot-reload via config center
- **Multiple Export Methods** -- Supports both HTTP and Kafka data export
- **OpenTelemetry Integration** -- Compatible with OpenTelemetry standard for distributed tracing

### System Architecture

The complete pipeline from code collection to data processing and storage:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Target Application JVM                       │
│                                                                     │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────────┐  │
│  │ HTTP Request │───>│  JVM Sandbox     │───>│  DeepCover Agent  │  │
│  │              │    │  (javaagent)      │    │  (Collection)     │  │
│  └─────────────┘    └──────────────────┘    └────────┬──────────┘  │
│                                                        │            │
│                              ┌──────────────────────────┘            │
│                              │                                       │
│                     ┌────────┴────────┐                              │
│                     │  Local Async     │  Batch Aggregation           │
│                     │  Queue           │  (queue x N)                 │
│                     └────────┬────────┘                              │
│                              │                                       │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                  │
     ┌────────┴────────┐              ┌─────────┴─────────┐
     │  Data Center     │              │  Kafka Cluster     │
     │  (HTTP)          │              │  precision-analysis     │
     │  /api/collect    │              │                    │
     └────────┬────────┘              └─────────┬─────────┘
              │                                  │
              └────────────┬─────────────────────┘
                           │
              ┌────────────┴────────────┐
              │  Data Processing /      │
              │  Storage Service        │
              │                         │
              │  - Precision analysis calculation │
              │  - Diff precision analysis        │
              │  - Data persistence     │
              │  - Report generation    │
              └─────────────────────────┘
```

### Agent Internal Architecture

The collection flow within a single JVM:

```
                        ┌─────────────────────────────────────┐
                        │          HTTP Request               │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │       HttpServlet.service()          │
                        │       (Bytecode enhancement point)   │
                        └──────────────┬──────────────────────┘
                                       │
 Event Flow:                           │
 ┌─────────────────────────────────────┼──────────────────────────┐
 │                                     │                          │
 │  [before]                                                      │
 │  ├─ Create CodeEntity, bind to ProcessTop (ThreadLocal)        │
 │  ├─ Sampling rate check (based on traceId hash)                │
 │  ├─ URL filtering (ignoreUrls regex matching)                  │
 │  │                                                             │
 │  [beforeLine]  <── triggered on each monitored method call     │
 │  ├─ Retrieve CodeEntity from ProcessTop                        │
 │  ├─ NPE guard: codeEntity==null / isSend==1 / codeInfo==null   │
 │  ├─ Build LineEntity (className, methodName, line number)      │
 │  │   └─ Auto-dedup line numbers (LinkedHashSet, O(1))          │
 │  ├─ Line execution count threshold check                       │
 │  │   └─ Exceeded: mark REFUSE, stop collecting for this method │
 │  │                                                             │
 │  [after]                                                       │
 │  ├─ Collected node count threshold check                       │
 │  ├─ Mark isSend=1, prevent duplicate sends                     │
 │  ├─ Enqueue to LocalAsyncQueue                                 │
 │  └─ finally: cleanup ThreadLocal, prevent memory leaks         │
 │                                                                │
 └────────────────────────────────────────────────────────────────┘
                                       │
                        ┌──────────────┴──────────────────────┐
                        │        LocalAsyncEngine              │
                        │                                     │
                        │  ┌─────────┐  ┌─────────┐           │
                        │  │ Queue 0 │  │ Queue 1 │  ...       │
                        │  │ (batch) │  │         │           │
                        │  └────┬────┘  └────┬────┘           │
                        │       │             │                │
                        │  ┌────┴─────────────┴────┐           │
                        │  │   Consumer Thread      │           │
                        │  │   HTTP / Kafka Send    │           │
                        │  └───────────────────────┘           │
                        │                                     │
                        │  Circuit Breaker: exceptionOverflow()│
                        │  └─ Exceeded threshold -> pause      │
                        └─────────────────────────────────────┘
```

### Configuration Hot-Reload

Runtime configuration can be dynamically updated without restart:

```
┌────────────────┐     Periodic polling (reportPeriod)  ┌──────────────────┐
│  Config Center │ ───────────────────────────────────> │  DeepCover Agent │
│  (deepcover-   │                                      │                  │
│   brain)       │ <─────────────────────────────────── │  reportServerInfo│
└────────────────┘     Return latest config + version   │                  │
                                                       │  Version compare: │
┌────────────────┐     syncConfig command              │  info.version >   │
│  Sandbox HTTP  │ ───────────────────────────────────> │  configVersion   │
│  /deepcover/   │                                      │  -> hot-reload    │
│  syncCfg       │                                      │  18 config items  │
└────────────────┘                                      └──────────────────┘
```

## Prerequisites

- Java 8+ (recommended Java 1.8.0_202+)
- Maven 3.5+
- Alibaba JVM Sandbox 1.4.0
- Application running on JVM

## Quick Start

### 1. Build the Project

```bash
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

After a successful build, `deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar` is generated in the `target/` directory (a full JAR with all dependencies).

### 2. Configure JVM Parameters

Add the following to your target application's JVM parameters:

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=your-app-name \
  -Denv=test
```

**Note**: If using SkyWalking APM, place the DeepCover agent parameter **before** the SkyWalking agent parameter to avoid conflicts.

### 3. Deploy DeepCover Module

```bash
# Method 1: Copy to Sandbox module directory
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/

# Method 2: Dynamic loading via Sandbox HTTP service
curl -X POST \
  "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"
```

### 4. Verify Installation

```bash
# Check logs for "loaded" message
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

### 5. Run Tests

```bash
mvn clean test -Dmaven.javadoc.skip=true
```

41 unit tests covering core utility and entity classes.

## Configuration

### Basic Setup

Copy the configuration template:

```bash
cp src/main/resources/deepcover.properties.example src/main/resources/deepcover.properties
```

### Configuration Reference

| Parameter | Description | Default/Example |
|-----------|-------------|-----------------|
| `serviceName` | Application name | - |
| `env` | Environment: test/pre/pro | - |
| `packageName` | Package name pattern (regex) | `com.myapp.*` |
| `ignoreClasses` | Ignored class patterns (semicolon-separated) | `*.logger;*.frame;` |
| `ignoreUrls` | Ignored URLs (semicolon-separated) | `/health;/metrics` |
| `ignoreAnnos` | Ignored annotations | - |
| `sampleRate` | Sampling rate (10000 = 100%) | `10000` |
| `limitCodeMethodSize` | Max collected methods per request | `500` |
| `limitCodeMethodLineSize` | Max line collections per method | `500` |
| `sendDataCenterType` | Send method: 1=HTTP, 2=Kafka | `1` |
| `dataCenterAddr` | Data center address (HTTP mode) | - |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka cluster address | - |
| `KAFKA_TOPIC` | Kafka topic | - |
| `exceptionThreshold` | Circuit breaker exception threshold | `10` |
| `exceptionCalcTime` | Exception calculation window (minutes) | `1` |
| `exceptionPauseTime` | Circuit breaker pause time (seconds) | `5` |

All configuration items support dynamic hot-reload via `syncConfig` command without restart.

### Sampling Rate

```
sampleRate=10000   # 100% sampling
sampleRate=5000    # 50% sampling
sampleRate=100     # 1% sampling
```

The sampling rate is calculated based on traceId hash, ensuring consistent results across instances.

## Dynamic Control API

Control the module dynamically via JVM Sandbox HTTP service:

```bash
# Activate module
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# Deactivate module
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# Unload module (requires restart to re-enable collection)
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=your-app-name"

# Sync configuration
curl -X POST "http://sandbox-server:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20"
```

## Performance Design

DeepCover minimizes application performance impact through:

- **Async Queues**: Independent thread pool (core=4, max=8, queue=256) with CallerRunsPolicy to prevent task loss
- **Batch Processing**: Local queues aggregate data for batch sending, reducing network overhead
- **Sampling**: Deterministic sampling based on traceId hash, adjustable on demand
- **Smart Throttling**: Automatically stops collecting line numbers when method threshold is reached
- **Circuit Breaker**: Pauses collection when send exceptions exceed threshold, preventing cascading failures
- **Regex Caching**: Pattern compilation results cached to avoid repeated compilation on hot paths

## Project Structure

```
deepcover/
├── src/
│   ├── main/java/io/deepcover/agent/
│   │   ├── CodeCollecter.java       # Module entry, lifecycle management
│   │   ├── HttpCodeModule.java      # Core collection logic
│   │   ├── entity/                  # Data entities
│   │   │   ├── CodeEntity.java      # Per-request collection data
│   │   │   └── LineEntity.java      # Per-method line info
│   │   ├── config/
│   │   │   ├── DeepCoverConfig.java # Global configuration
│   │   │   ├── ExecutorThreadPoolConfig.java  # Thread pool config
│   │   │   ├── queue/               # Async queue engine
│   │   │   └── kafka/               # Kafka send engine
│   │   ├── ext/                     # JVM Sandbox extensions
│   │   │   ├── CodeAdviceListener.java
│   │   │   ├── CodeAdviceAdapterListener.java
│   │   │   └── CodeEventWatchBuilder.java
│   │   └── util/                    # Utility classes
│   │       ├── TraceContext.java     # TraceId management
│   │       ├── TraceUtil.java       # Sampling calculation
│   │       ├── ExceptionAwareUtil.java  # Circuit breaker
│   │       └── http/HttpClient2.java # HTTP client
│   └── test/java/                   # Unit tests (41 cases)
├── src/main/resources/
│   ├── deepcover.properties.example  # Config template
│   └── logback.xml
├── sandbox/                          # JVM Sandbox binaries and config
├── LICENSE                           # Apache 2.0
├── CONTRIBUTING.md                   # Contribution guide
├── CHANGELOG.md                      # Changelog
└── README.md
```

## Security

- `deepcover.properties` is in `.gitignore` to prevent accidental commits of sensitive data
- Config templates use `YOUR_*` placeholders instead of real addresses and keys
- All dependencies are open-source components with no internal private dependencies

### Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Alibaba JVM Sandbox | 1.4.0 | Bytecode enhancement framework |
| OpenTelemetry API | 1.30.0 | Distributed tracing standard |
| Apache HttpClient | 4.5.6 | HTTP data sending |
| Hutool | 5.8.9 | HTTP utilities (config center reporting) |
| FastJSON | 2.0.25 | JSON serialization |
| Logback | 1.2.1 | Logging framework |
| Lombok | 1.18.12 | Code simplification |
| Kafka Clients | 2.4.1 | Kafka data sending |
| Guava | 18.0 | Utility classes |

## Notes

1. **Performance Impact**: Collection has performance overhead; set appropriate sampling rates in production
2. **SkyWalking Compatibility**: DeepCover agent must be loaded before SkyWalking agent
3. **Resource Usage**: Collection data consumes memory; adjust `limitCodeMethodSize` as needed
4. **Test First**: Thoroughly verify in test environments before production deployment

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guide
- [CHANGELOG.md](CHANGELOG.md) - Changelog
- [LICENSE](LICENSE) - Apache 2.0 License

## License

This project is licensed under the [Apache License 2.0](LICENSE).
