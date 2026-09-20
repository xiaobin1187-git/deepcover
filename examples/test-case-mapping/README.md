# Test Case to Code Mapping Demo

This example demonstrates the smallest external link needed to turn DeepCover request evidence into a test-case-to-code mapping:

```text
Test case ID -> deterministic W3C traceId -> HTTP request -> DeepCover payload -> code relation
```

DeepCover does not manage test cases. The runner keeps the `caseId -> traceId` relation, while DeepCover records the `traceId -> service/request/code` relation.

## Run

Build and start the demo with 100% sampling as described in [the Servlet demo](../demo-servlet/README.md). Start the receiver with record capture explicitly enabled:

```bash
python benchmarks/mock_receiver.py --port 18081 --capture-records
```

Then run:

```bash
python examples/test-case-mapping/run_mapping.py
```

The runner resets captured records before execution. Its JSON output contains each test case, its deterministic traceId, the request path, and the observed class/method/line relation. Line numbers depend on the current source and compiler, so the repository does not hard-code a generated output file.

`--capture-records` keeps payloads only in receiver memory and should be used with local test data. The receiver binds to `127.0.0.1` by default.
