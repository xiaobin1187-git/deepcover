#!/usr/bin/env python3
import argparse
import json
import re
import statistics
from collections import defaultdict
from pathlib import Path


RESULT_PATTERN = re.compile(
    r"^(baseline|sample-10|sample-100)-(max|rps-\d+)-run-(\d+)\.json$"
)


def load_json(path):
    with path.open("r", encoding="utf-8-sig") as input_file:
        return json.load(input_file)


def median(values):
    return statistics.median(values)


def value_range(values, digits=1):
    return "%.*f-%.*f" % (digits, min(values), digits, max(values))


def percent_change(current, baseline):
    if baseline == 0:
        return "n/a"
    return "%+.1f%%" % ((current / baseline - 1.0) * 100.0)


def load_label(load_name):
    return "Max" if load_name == "max" else load_name.replace("rps-", "") + " RPS"


def scenario_label(scenario):
    return {
        "baseline": "Baseline",
        "sample-10": "10% sampling",
        "sample-100": "100% sampling",
    }[scenario]


def sort_load(load_name):
    return 10 ** 9 if load_name == "max" else int(load_name.split("-")[1])


def main():
    parser = argparse.ArgumentParser(description="Summarize DeepCover benchmark JSON files")
    parser.add_argument("result_dir")
    args = parser.parse_args()

    result_dir = Path(args.result_dir)
    environment = load_json(result_dir / "environment.json")
    groups = defaultdict(list)
    validation_count = 0

    for path in sorted(result_dir.glob("*.json")):
        match = RESULT_PATTERN.match(path.name)
        if not match:
            continue
        scenario, load_name, run_number = match.groups()
        result = load_json(path)
        result["run_number"] = int(run_number)
        groups[(load_name, scenario)].append(result)

        if result["error_count"] != 0 or result["success_count"] != result["requests"]:
            raise RuntimeError("Request failures found in %s" % path.name)
        if scenario != "baseline":
            metrics = load_json(path.with_name(path.stem + "-metrics.json"))
            receiver = load_json(path.with_name(path.stem + "-receiver.json"))
            if metrics["queueDepth"] != 0:
                raise RuntimeError("Queue did not drain in %s" % path.name)
            if metrics["sendFailed"] != 0:
                raise RuntimeError("Send failures found in %s" % path.name)
            if receiver["requests"] != metrics["sendSuccess"]:
                raise RuntimeError("Receiver mismatch in %s" % path.name)
            result["metrics"] = metrics
            validation_count += 1

    if not groups:
        raise RuntimeError("No benchmark result files found in %s" % result_dir)

    print("# DeepCover Benchmark Results")
    print()
    print("Generated from `%s`." % result_dir.name)
    print()
    print("## Environment")
    print()
    print("- OS: `%s`" % environment.get("os"))
    print("- Processor: `%s`; logical processors: `%s`" % (
        environment.get("processor"), environment.get("logical_processors")
    ))
    print("- Java: `%s`" % str(environment.get("java", "")).replace("\n", " | "))
    print("- Workload: concurrency `%s`, warm-up `%s`, duration `%ss`, repeats `%s`" % (
        environment.get("concurrency"), environment.get("warmup_requests"),
        environment.get("duration_seconds"), environment.get("repeats")
    ))
    print("- Git commit: `%s`; dirty worktree: `%s`" % (
        environment.get("git_commit"), environment.get("git_worktree_dirty")
    ))
    print()
    print("Process CPU follows psutil semantics: 100% is one fully used logical CPU. RSS is the mean resident set size of the demo JVM.")
    print()
    print("## Absolute Results")
    print()
    print("| Offered load | Scenario | Achieved RPS median (range) | P99 ms median (range) | Mean CPU % median | Mean RSS MB median | Errors |")
    print("|---:|---|---:|---:|---:|---:|---:|")

    summaries = {}
    for load_name, scenario in sorted(groups, key=lambda key: (sort_load(key[0]), key[1])):
        runs = groups[(load_name, scenario)]
        rps = [run["throughput_rps"] for run in runs]
        p99 = [run["latency_ms"]["p99"] for run in runs]
        cpu = [run["process"]["cpu_percent_mean"] for run in runs]
        rss = [run["process"]["rss_mb_mean"] for run in runs]
        errors = sum(run["error_count"] for run in runs)
        summaries[(load_name, scenario)] = {
            "rps": median(rps),
            "p99": median(p99),
            "cpu": median(cpu),
            "rss": median(rss),
        }
        print("| %s | %s | %.1f (%s) | %.1f (%s) | %.1f | %.1f | %d |" % (
            load_label(load_name), scenario_label(scenario), median(rps), value_range(rps),
            median(p99), value_range(p99), median(cpu), median(rss), errors
        ))

    print()
    print("## Relative To Baseline")
    print()
    print("| Offered load | Scenario | Throughput change | P99 change | CPU change | RSS change |")
    print("|---:|---|---:|---:|---:|---:|")
    for load_name in sorted({key[0] for key in summaries}, key=sort_load):
        baseline = summaries[(load_name, "baseline")]
        for scenario in ("sample-10", "sample-100"):
            current = summaries[(load_name, scenario)]
            print("| %s | %s | %s | %s | %+.1f points | %+.1f MB |" % (
                load_label(load_name), scenario_label(scenario),
                percent_change(current["rps"], baseline["rps"]),
                percent_change(current["p99"], baseline["p99"]),
                current["cpu"] - baseline["cpu"],
                current["rss"] - baseline["rss"],
            ))

    print()
    print("## Sampling And Delivery Checks")
    print()
    print("| Scenario | Collected / total requests | Observed collection rate | Send failures |")
    print("|---|---:|---:|---:|")
    for scenario in ("sample-10", "sample-100"):
        runs = [run for (load, item), values in groups.items() if item == scenario for run in values]
        collected = sum(run["metrics"]["collectedRequests"] for run in runs)
        total = sum(run["metrics"]["totalRequests"] for run in runs)
        failures = sum(run["metrics"]["sendFailed"] for run in runs)
        print("| %s | %d / %d | %.2f%% | %d |" % (
            scenario_label(scenario), collected, total,
            (collected / total * 100.0) if total else 0.0, failures
        ))

    print()
    print("Validated `%d` agent runs: every queue drained to zero, every send completed without a reported failure, and receiver request counts matched `sendSuccess`." % validation_count)


if __name__ == "__main__":
    main()
