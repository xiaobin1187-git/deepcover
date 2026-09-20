#!/usr/bin/env python3
import argparse
import hashlib
import json
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


def trace_id_for(case_id):
    return hashlib.sha256(case_id.encode("utf-8")).hexdigest()[:32]


def request_json(url):
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def reset_receiver(receiver_url):
    request = urllib.request.Request(
        receiver_url.rstrip("/") + "/reset",
        data=b"",
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        response.read()
        if response.status < 200 or response.status >= 300:
            raise RuntimeError("receiver reset returned HTTP %s" % response.status)


def execute_case(base_url, case):
    trace_id = trace_id_for(case["caseId"])
    request = urllib.request.Request(
        base_url.rstrip("/") + case["path"],
        headers={
            "traceparent": "00-%s-0000000000000001-01" % trace_id,
            "X-Test-Case-Id": case["caseId"],
        },
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        response.read()
        if response.status < 200 or response.status >= 300:
            raise RuntimeError("case %s returned HTTP %s" % (case["caseId"], response.status))
    return trace_id


def wait_for_records(receiver_url, trace_ids, timeout_seconds):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        records = request_json(receiver_url.rstrip("/") + "/records").get("records", [])
        observed = {record.get("traceId") for record in records}
        if trace_ids.issubset(observed):
            return records
        time.sleep(0.1)
    missing = sorted(trace_ids - observed)
    raise RuntimeError("timed out waiting for DeepCover records: %s" % ", ".join(missing))


def code_relation(record):
    methods = []
    for line in record.get("codeInfo", []):
        methods.append({
            "className": line.get("className"),
            "methodName": line.get("methodName"),
            "parameters": line.get("parameters", []),
            "lineNum": line.get("lineNum", []),
        })
    return {
        "serviceName": record.get("serviceName"),
        "url": record.get("url"),
        "methods": methods,
    }


def main():
    default_cases = Path(__file__).with_name("cases.json")
    parser = argparse.ArgumentParser(description="Generate a DeepCover test-case-to-code mapping")
    parser.add_argument("--cases", default=str(default_cases))
    parser.add_argument("--base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--receiver-url", default="http://127.0.0.1:18081")
    parser.add_argument("--timeout-seconds", type=float, default=15.0)
    parser.add_argument("--output")
    args = parser.parse_args()

    with open(args.cases, "r", encoding="utf-8") as cases_file:
        cases = json.load(cases_file)

    reset_receiver(args.receiver_url)
    trace_by_case = {}
    for case in cases:
        trace_by_case[case["caseId"]] = execute_case(args.base_url, case)

    records = wait_for_records(args.receiver_url, set(trace_by_case.values()), args.timeout_seconds)
    records_by_trace = {}
    for record in records:
        records_by_trace.setdefault(record.get("traceId"), []).append(record)

    output = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "cases": [],
    }
    for case in cases:
        trace_id = trace_by_case[case["caseId"]]
        output["cases"].append({
            "caseId": case["caseId"],
            "request": case["path"],
            "traceId": trace_id,
            "relations": [code_relation(record) for record in records_by_trace.get(trace_id, [])],
        })

    rendered = json.dumps(output, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        with open(args.output, "w", encoding="utf-8") as output_file:
            output_file.write(rendered)
    print(rendered, end="")


if __name__ == "__main__":
    main()
