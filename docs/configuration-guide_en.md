# DeepCover Configuration Guide

[中文](configuration-guide.md) | **English** | [日本語](configuration-guide_ja.md) | [Francais](configuration-guide_fr.md) | [Portugues](configuration-guide_pt.md) | [Русский](configuration-guide_ru.md)

## Configuration Methods

DeepCover supports three configuration methods:

1. **Configuration file** - `deepcover.properties` (static, loaded at startup)
2. **Configuration center** - `deepcover-brain` (dynamic, pulled periodically)
3. **HTTP command** - `syncConfig` (on-demand, via Sandbox HTTP API)

## Configuration File

### Location

```
src/main/resources/deepcover.properties
```

### Format

The file is organized by environment:

```properties
# Test environment
test.dataCenterAddr=http://127.0.0.1:8080/api/collect
test.configCenterAddr=http://127.0.0.1:8080
test.KAFKA_BOOTSTRAP_SERVERS=localhost:9092
test.KAFKA_TOPIC=deepcover-collection-code-info

# Pre-production environment
pre.dataCenterAddr=http://pre-dc.example.com:8080/api/collect
pre.configCenterAddr=http://pre-dc.example.com:8080
pre.KAFKA_BOOTSTRAP_SERVERS=pre-kafka.example.com:9092
pre.KAFKA_TOPIC=deepcover-collection-code-info

# Production environment
pro.dataCenterAddr=http://dc.example.com:8080/api/collect
pro.configCenterAddr=http://dc.example.com:8080
pro.KAFKA_BOOTSTRAP_SERVERS=kafka.example.com:9092
pro.KAFKA_TOPIC=deepcover-collection-code-info

# Common settings
getDeepCoverInfo=/deepcover-brain/deepcover/info
reportServerInfo=/deepcover-brain/deepcover/report
```

The active environment is determined by the JVM parameter `-Ddeepcover.env=test` or `-Denv=test`.

## Parameter Reference

### Core Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `serviceName` | String | - | Application name (set via `-Dapp.name`) |
| `env` | String | - | Environment: test/pre/pro (set via `-Denv` or `-Ddeepcover.env`) |
| `branch` | String | `master` | Git branch name (set via `-Dbranch`) |

### Collection Scope

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `packageName` | Regex | - | Package pattern to instrument. Only classes matching this regex will be monitored. |
| `ignoreClasses` | Regex(s) | - | Class patterns to exclude (semicolon-separated). Supports regex. |
| `ignoreMethods` | Regex(s) | - | Method patterns to exclude (semicolon-separated). |
| `ignoreUrls` | String(s) | - | URL paths to exclude (semicolon-separated). E.g., `/health;/metrics` |
| `ignoreAnnos` | String(s) | - | Annotations to exclude. |

### Sampling

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `sampleRate` | Integer | `10000` | Sampling rate. `10000` = 100%, `5000` = 50%, `100` = 1%. Based on traceId hash for deterministic sampling. |

### Performance Limits

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `limitCodeMethodSize` | Integer | `500` | Maximum number of method nodes collected per HTTP request. Exceeding this discards the request data. |
| `limitCodeMethodLineSize` | Integer | `500` | Maximum line collection count per method. Exceeding this stops line collection for that method within the request. |

### Data Export

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `sendDataCenterType` | Integer | `1` | Export method: `1` = HTTP, `2` = Kafka |
| `dataCenterAddr` | URL | - | Data center HTTP endpoint for coverage data |
| `KAFKA_BOOTSTRAP_SERVERS` | String | - | Kafka cluster bootstrap servers |
| `KAFKA_TOPIC` | String | - | Kafka topic for coverage data |

### Queue Configuration

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `queueNum` | Integer | `1` | Number of internal async queues |
| `queueSize` | Integer | `100` | Maximum elements per queue (ArrayBlockingQueue capacity) |
| `queueMsgSize` | Integer | `50` | Maximum batch size for queue drain operations |
| `queueRecycleTime` | Integer | `10` | Consumer thread sleep time (seconds) between batches |

### Circuit Breaker

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `exceptionThreshold` | Integer | `10` | Number of exceptions to trigger circuit breaker |
| `exceptionCalcTime` | Integer | `1` | Time window for exception counting (minutes) |
| `exceptionPauseTime` | Integer | `5` | Pause duration after circuit breaker triggers (seconds) |

### Reporting

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `reportPeriod` | Integer | `10` | Interval for server info reporting and config polling (seconds) |

## Dynamic Configuration

### Via syncConfig Command

All parameters can be updated at runtime without restart:

```bash
curl -X POST "http://sandbox-host:port/sandbox/default/module/http/deepcover/syncConfig" \
  -d "sampleRate=5000&exceptionThreshold=20&limitCodeMethodSize=300"
```

### Via Configuration Center

The agent periodically polls the configuration center (`reportPeriod` interval). If the config version increases, all parameters are automatically reloaded.

## Monitoring

Use the metrics endpoint to check agent status:

```bash
curl "http://sandbox-host:port/sandbox/default/module/http/deepcover/metrics"
```

Response includes:

```json
{
  "serviceName": "my-app",
  "env": "test",
  "uptimeSeconds": 3600,
  "configVersion": 5,
  "sampleRate": 5000,
  "sendType": "Kafka",
  "totalRequests": 15000,
  "collectedRequests": 7500,
  "droppedRequests": 50,
  "totalLinesCollected": 120000,
  "methodThresholdReached": 10,
  "sendSuccess": 7400,
  "sendFailed": 50,
  "queueOfferFailed": 30,
  "circuitBreakerTripped": 0,
  "circuitBreakerPaused": false
}
```

## Recommended Production Settings

```properties
sampleRate=1000              # 10% sampling
limitCodeMethodSize=300      # Moderate method limit
limitCodeMethodLineSize=300  # Moderate line limit
exceptionThreshold=5         # Conservative circuit breaker
exceptionCalcTime=1          # 1-minute window
exceptionPauseTime=10        # 10-second pause
queueNum=2                   # Multiple queues
queueSize=200                # Larger queue capacity
reportPeriod=30              # Less frequent reporting
```
