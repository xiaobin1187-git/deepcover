# DeepCover Deployment Guide

[中文](deployment-guide.md) | **English** | [日本語](deployment-guide_ja.md) | [Francais](deployment-guide_fr.md) | [Portugues](deployment-guide_pt.md) | [Русский](deployment-guide_ru.md)

## Environment Requirements

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 8+ | Recommended: 1.8.0_202+ |
| Maven | 3.5+ | For building the agent |
| JVM Sandbox | 1.4.0 | Bytecode instrumentation framework |
| Servlet Container | 3.0+ | Tomcat, Jetty, etc. |

## Step 1: Build DeepCover Agent

```bash
git clone https://github.com/xiaobin1187-git/deepcover.git
cd deepcover
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

Output: `target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Step 2: Install JVM Sandbox

Download JVM Sandbox from the [official repository](https://github.com/alibaba/jvm-sandbox) and extract:

```
sandbox/
├── bin/
│   └── sandbox.sh
├── cfg/
│   └── sandbox.properties
├── lib/
│   ├── sandbox-agent.jar    # javaagent jar
│   └── sandbox-spy.jar
└── sandbox-module/          # DeepCover module goes here
```

## Step 3: Deploy DeepCover Module

```bash
# Copy the built agent to sandbox module directory
cp target/deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar \
   /path/to/sandbox/sandbox-module/
```

## Step 4: Configure Target Application

Add JVM parameters to your application startup script:

### Tomcat

Edit `bin/setenv.sh` (or `catalina.sh`):

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=your-app-name \
  -Denv=test \
  -Ddeepcover.env=test \
  -Dbranch=master"
```

### Spring Boot (java -jar)

```bash
java -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
     -Dapp.name=your-app-name \
     -Denv=test \
     -Ddeepcover.env=test \
     -jar your-app.jar
```

### Important: SkyWalking Compatibility

If using SkyWalking APM, DeepCover agent parameters **must come before** SkyWalking:

```bash
-javaagent:/path/to/sandbox/lib/sandbox-agent.jar \     # DeepCover first
-javaagent:/path/to/skywalking-agent.jar \               # SkyWalking second
```

## Step 5: Configure deepcover.properties

Copy and edit the configuration template:

```bash
cp src/main/resources/deepcover.properties.example \
   src/main/resources/deepcover.properties
```

See [configuration-guide_en.md](configuration-guide_en.md) for detailed parameter descriptions.

## Step 6: Verify Installation

### Check Module Loading

```bash
tail -f ~/sandbox/sandbox.log | grep "code-module"
```

You should see the module loaded successfully.

### Check Metrics Endpoint

```bash
curl "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/metrics"
```

Expected JSON response with runtime statistics.

## Step 7: Control Module Lifecycle

```bash
# Activate module
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/active?ids=deepcover"

# Deactivate module (can be reactivated)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/sandbox-module-mgr/unactive?ids=deepcover"

# Unload module completely (requires restart to re-enable)
curl -X POST "http://localhost:<sandbox-port>/sandbox/default/module/http/deepcover/unloadCodeModule" \
  -d "serviceName=your-app-name"
```

## Troubleshooting

### Module fails to load

- Verify `sandbox-agent.jar` path is correct
- Check that `app.name` and `env` JVM parameters are set
- Check `~/sandbox/sandbox.log` for error details

### No coverage data collected

- Verify `packageName` regex matches your application packages
- Check that `deepcover.properties` has correct `env` section
- Verify data center or Kafka is reachable from the application host

### Performance impact too high

- Reduce `sampleRate` (e.g., `5000` for 50%)
- Lower `limitCodeMethodSize` and `limitCodeMethodLineSize`
- Check circuit breaker is functioning via `/metrics` endpoint

### Queue full warnings

- Increase `queueSize` (default: 100)
- Increase `queueNum` (default: 1)
- Reduce `sampleRate` to decrease data volume
