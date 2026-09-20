#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import statistics
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

import psutil


def percentile(sorted_values, value):
    if not sorted_values:
        return 0.0
    index = max(0, math.ceil((value / 100.0) * len(sorted_values)) - 1)
    return sorted_values[index]


def request_url(base_url, index):
    action = index % 4
    if action == 0:
        return base_url + "?action=list"
    if action == 1:
        return base_url + "?action=get&id=" + str(index % 100)
    if action == 2:
        return base_url + "?action=create&name=user%s&email=user%s@example.com" % (index, index)
    return base_url + "?action=delete&id=" + str(index % 100)


def execute_request(base_url, index, timeout):
    trace_id = hashlib.md5(("deepcover-benchmark-%s" % index).encode("ascii")).hexdigest()
    request = urllib.request.Request(
        request_url(base_url, index),
        headers={"traceparent": "00-%s-0000000000000001-01" % trace_id},
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response.read()
            success = 200 <= response.status < 300
            error = None if success else "http-%s" % response.status
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        success = False
        error = type(exc).__name__
    latency_ms = (time.perf_counter() - started) * 1000.0
    return latency_ms, success, error


class ProcessMonitor:
    def __init__(self, pid):
        self.process = psutil.Process(pid) if pid else None
        self.stop_event = threading.Event()
        self.cpu_samples = []
        self.rss_samples = []
        self.thread = None

    def start(self):
        if self.process is None:
            return
        self.process.cpu_percent(None)
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self):
        while not self.stop_event.wait(0.2):
            try:
                self.cpu_samples.append(self.process.cpu_percent(None))
                self.rss_samples.append(self.process.memory_info().rss)
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                break

    def stop(self):
        if self.thread is None:
            return {}
        self.stop_event.set()
        self.thread.join(timeout=2)
        return {
            "cpu_percent_mean": round(statistics.mean(self.cpu_samples), 3) if self.cpu_samples else None,
            "cpu_percent_max": round(max(self.cpu_samples), 3) if self.cpu_samples else None,
            "rss_mb_mean": round(statistics.mean(self.rss_samples) / 1024 / 1024, 3) if self.rss_samples else None,
            "rss_mb_max": round(max(self.rss_samples) / 1024 / 1024, 3) if self.rss_samples else None,
        }


def run_requests(url, count, concurrency, timeout, start_index=0):
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        return list(executor.map(
            lambda index: execute_request(url, index, timeout),
            range(start_index, start_index + count),
        ))


def run_rate_limited_requests(url, count, concurrency, timeout, target_rps, start_index=0):
    started = time.perf_counter()
    futures = []
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        for offset in range(count):
            scheduled_at = started + (offset / target_rps)
            sleep_seconds = scheduled_at - time.perf_counter()
            if sleep_seconds > 0:
                time.sleep(sleep_seconds)
            futures.append(executor.submit(execute_request, url, start_index + offset, timeout))
        results = [future.result() for future in futures]
    return results, time.perf_counter() - started


def main():
    parser = argparse.ArgumentParser(description="Repeatable DeepCover HTTP benchmark")
    parser.add_argument("--url", required=True)
    parser.add_argument("--requests", type=int, default=10000)
    parser.add_argument("--concurrency", type=int, default=32)
    parser.add_argument("--warmup", type=int, default=1000)
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--target-rps", type=float)
    parser.add_argument("--duration-seconds", type=float, default=15.0)
    parser.add_argument("--process-pid", type=int)
    parser.add_argument("--scenario", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    run_requests(args.url, args.warmup, args.concurrency, args.timeout, -args.warmup)
    monitor = ProcessMonitor(args.process_pid)
    monitor.start()
    request_count = args.requests
    if args.target_rps:
        request_count = max(1, int(round(args.target_rps * args.duration_seconds)))
        results, duration = run_rate_limited_requests(
            args.url,
            request_count,
            args.concurrency,
            args.timeout,
            args.target_rps,
        )
    else:
        started = time.perf_counter()
        results = run_requests(args.url, request_count, args.concurrency, args.timeout)
        duration = time.perf_counter() - started
    process_metrics = monitor.stop()

    latencies = sorted(result[0] for result in results)
    success_count = sum(1 for result in results if result[1])
    errors = {}
    for _, success, error in results:
        if not success:
            errors[error] = errors.get(error, 0) + 1

    output = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "scenario": args.scenario,
        "url": args.url,
        "requests": request_count,
        "concurrency": args.concurrency,
        "warmup_requests": args.warmup,
        "target_rps": args.target_rps,
        "target_duration_seconds": args.duration_seconds if args.target_rps else None,
        "duration_seconds": round(duration, 6),
        "throughput_rps": round(request_count / duration, 3),
        "success_count": success_count,
        "error_count": request_count - success_count,
        "errors": errors,
        "latency_ms": {
            "mean": round(statistics.mean(latencies), 3),
            "p50": round(percentile(latencies, 50), 3),
            "p95": round(percentile(latencies, 95), 3),
            "p99": round(percentile(latencies, 99), 3),
            "max": round(max(latencies), 3),
        },
        "process": process_metrics,
    }
    with open(args.output, "w", encoding="utf-8") as output_file:
        json.dump(output, output_file, indent=2, ensure_ascii=True)
        output_file.write("\n")
    print(json.dumps(output, indent=2, ensure_ascii=True))


if __name__ == "__main__":
    main()
