#!/usr/bin/env python3
import argparse
import re
import subprocess
from collections import Counter
from pathlib import Path


EVENT_KINDS = {
    "Allocation in new TLAB": "Allocation",
    "Method Profiling Sample": "CPU",
}
INCLUSIVE_PREFIXES = (
    "io/deepcover/",
    "com/alibaba/jvm/sandbox/",
    "org/apache/http/",
    "com/alibaba/fastjson",
)
EVENT_START = re.compile(r"^([A-Za-z][^@]+)@")
CLASS_NAME = re.compile(r"^ {15}Name = (.+)$")
METHOD_NAME = re.compile(r"^ {12}Name = (.+)$")


def add_event(kind, frames, top_counts, inclusive_counts):
    if kind is None or not frames:
        return
    top_counts[kind][frames[0]] += 1
    for frame in set(frames):
        if frame.startswith(INCLUSIVE_PREFIXES):
            inclusive_counts[kind][frame] += 1


def parse_recording(java, jfr_jar, recording):
    command = [
        java,
        "-cp",
        jfr_jar,
        "oracle.jrockit.jfr.parser.Parser",
        recording,
    ]
    process = subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    top_counts = {kind: Counter() for kind in EVENT_KINDS.values()}
    inclusive_counts = {kind: Counter() for kind in EVENT_KINDS.values()}
    current_kind = None
    current_class = None
    frames = []

    for line in process.stdout:
        event_match = EVENT_START.match(line)
        if event_match:
            add_event(current_kind, frames, top_counts, inclusive_counts)
            current_kind = EVENT_KINDS.get(event_match.group(1))
            current_class = None
            frames = []
            continue
        if current_kind is None:
            continue
        class_match = CLASS_NAME.match(line.rstrip("\r\n"))
        if class_match:
            current_class = class_match.group(1)
            continue
        method_match = METHOD_NAME.match(line.rstrip("\r\n"))
        if current_class is not None and method_match:
            frames.append("%s::%s" % (current_class, method_match.group(1)))
            current_class = None

    add_event(current_kind, frames, top_counts, inclusive_counts)
    stderr = process.stderr.read()
    return_code = process.wait()
    if return_code != 0:
        raise RuntimeError("JFR parser failed: %s" % stderr.strip())
    return top_counts, inclusive_counts


def render_table(title, counts, limit):
    lines = ["## %s" % title, "", "| Samples | Frame |", "|---:|---|"]
    for frame, count in counts.most_common(limit):
        lines.append("| %s | `%s` |" % (count, frame))
    if not counts:
        lines.append("| 0 | No matching samples |")
    lines.append("")
    return lines


def main():
    parser = argparse.ArgumentParser(description="Summarize Oracle JDK 8 JFR 0.9 hotspots")
    parser.add_argument("recording")
    parser.add_argument("--java", default="java")
    parser.add_argument("--jfr-jar")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--output")
    args = parser.parse_args()

    java_path = Path(args.java).resolve()
    jfr_jar = args.jfr_jar
    if not jfr_jar:
        jfr_jar = str(java_path.parent.parent / "jre" / "lib" / "jfr.jar")
    if not Path(jfr_jar).is_file():
        raise RuntimeError("JDK 8 jfr.jar not found: %s" % jfr_jar)

    top_counts, inclusive_counts = parse_recording(
        str(java_path),
        jfr_jar,
        str(Path(args.recording).resolve()),
    )
    lines = ["# JFR 8 Hotspot Summary", ""]
    for kind in ("Allocation", "CPU"):
        lines.extend(render_table("%s Top Frames" % kind, top_counts[kind], args.limit))
        lines.extend(render_table(
            "%s Inclusive DeepCover/Sandbox/HTTP/JSON Frames" % kind,
            inclusive_counts[kind],
            args.limit,
        ))
    rendered = "\n".join(lines)
    if args.output:
        Path(args.output).write_text(rendered, encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
