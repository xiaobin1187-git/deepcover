# DeepCover Demo - Servlet Application

A simple web application demonstrating how DeepCover collects code coverage data.

## Prerequisites

- Java 8+
- Maven 3.5+
- Tomcat 8+ (or any Servlet container)
- [DeepCover Agent](../../) built and ready

## Quick Start

### 1. Build the demo

```bash
cd examples/demo-servlet
mvn clean package
```

This generates `target/demo-servlet.war`.

### 2. Configure JVM with DeepCover agent

Add to Tomcat's `catalina.sh` (or `setenv.sh`):

```bash
export JAVA_OPTS="$JAVA_OPTS \
  -javaagent:/path/to/sandbox/lib/sandbox-agent.jar \
  -Dapp.name=demo-servlet \
  -Denv=test \
  -Ddeepcover.env=test"
```

### 3. Deploy and test

```bash
# Copy WAR to Tomcat
cp target/demo-servlet.war /path/to/tomcat/webapps/

# Start Tomcat
/path/to/tomcat/bin/startup.sh

# Test the endpoints
curl "http://localhost:8080/demo-servlet/user?action=list"
curl "http://localhost:8080/demo-servlet/user?action=get&id=1"
curl "http://localhost:8080/demo-servlet/user?action=create&name=Bob&email=bob@example.com"
curl "http://localhost:8080/demo-servlet/user?action=delete&id=1"
```

### 4. Check metrics

```bash
# View DeepCover runtime metrics
curl "http://localhost:port/sandbox/default/module/http/deepcover/metrics"
```

## What DeepCover Collects

When the HTTP requests hit `UserServlet.service()`, DeepCover will:

1. **Trace the request** - Record the HTTP method, URL, and traceId
2. **Instrument method calls** - Track all method calls within the `io.deepcover.examples.demo.*` package
3. **Collect line numbers** - Record which lines of code were executed in `UserService`
4. **Send data** - Transmit coverage data to the configured data center or Kafka topic

## Coverage Data Example

For a request to `GET /user?action=get&id=1`, DeepCover collects:

- Class: `io.deepcover.examples.demo.controller.UserServlet`
- Method: `service` - lines executed: 24, 25, 26, 29, 34
- Class: `io.deepcover.examples.demo.service.UserService`
- Method: `getUser` - lines executed: 22, 23, 26, 27

This data can be used to calculate line-level code coverage per HTTP request.

## Configuration

Ensure `deepcover.properties` has the correct settings:

```properties
# Package pattern to collect
test.packageName=io\\.deepcover\\.examples\\.demo\\..*
```

Or configure via the config center with `packageName=io\.deepcover\.examples\.demo\..*`.
