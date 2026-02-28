# FXDB Benchmarking Plan

## Overview

FXDB, DBeaver, and DbVisualizer are all JVM-based database clients, so the same measurement categories apply to all three. The goal is to benchmark them on identical hardware, against the same database, using the same datasets.

---

## Benchmark Categories

### 1. Startup Time
- **Cold start** — first launch after OS boot (no JVM cache, no file system cache)
- **Warm start** — subsequent launches (JVM class data sharing active)

### 2. Memory Footprint
- Baseline heap after startup (idle state)
- Peak heap during heavy query execution
- Off-heap / native memory
- JVM overhead (metaspace, code cache)

### 3. Query Execution & Result Rendering
- Simple `SELECT` on small dataset (100 rows)
- `SELECT` on medium dataset (10,000 rows)
- `SELECT` on large dataset (100,000 rows)
- Complex multi-table `JOIN` query
- Time-to-first-row vs. time-to-full-render

### 4. Database Tree Loading
- Time to fully expand the schema/table browser for a database with 50+ tables

### 5. CPU Usage
- Idle (steady state after startup)
- During active query execution
- During result set scroll/render

### 6. UI Responsiveness (JavaFX Thread Jank)
- Frame drop / freeze duration during background operations

---

## Test Environment Setup

All three tools must be tested under **identical conditions**:

```
OS:         Same machine, freshly booted for cold-start tests
JDK:        Record which JDK each app bundles or uses
Database:   Single shared PostgreSQL or MySQL instance (local, to eliminate network variance)
Dataset:    Pre-populated tables of controlled sizes (100 / 10K / 100K rows)
Iterations: Run each test 5 times, discard outliers, report median
```

---

## Tools

### A. For FXDB (direct JVM instrumentation)

| Tool | Purpose | How to Use |
|------|----------|------------|
| **JDK Mission Control (JMC) + Java Flight Recorder (JFR)** | Gold standard JVM profiler — heap, CPU, GC, thread activity, method hotspots | Launch FXDB with `-XX:StartFlightRecording`, open `.jfr` in JMC |
| **VisualVM** (free, ships with JDK) | Real-time heap/CPU/thread monitoring, heap dumps, sampler | Attach to the running FXDB process via PID |
| **Eclipse MAT (Memory Analyzer Tool)** | Deep heap dump analysis — object retention, leak detection | Trigger heap dump from VisualVM/JFR, open `.hprof` in MAT |
| **`time` command** | Measure cold/warm startup wall-clock time | `time java -jar fxdb-ui-1.0.0-shaded.jar` |

JFR launch flags for FXDB:
```bash
java -XX:StartFlightRecording=filename=fxdb.jfr,duration=120s,settings=profile \
     -jar fxdb-ui/target/fxdb-ui-1.0.0-shaded.jar
```

### B. For DBeaver and DbVisualizer (black-box OS-level measurement)

Since you cannot instrument their internals directly, use OS-level and process-level tools:

| Tool | Purpose | How to Use |
|------|----------|------------|
| **`/usr/bin/time -v`** | Wall time + peak RSS memory | `/usr/bin/time -v dbeaver` — reports "Maximum resident set size" |
| **`psrecord`** (pip install psrecord) | Records CPU% and memory usage of a PID over time, outputs a plot | `psrecord $(pgrep dbeaver) --plot dbeaver_profile.png --duration 120` |
| **`pidstat`** (sysstat package) | Per-process CPU, memory, I/O from the command line | `pidstat -p $(pgrep dbeaver) 1` |
| **`smem`** | Accurate proportional set size (PSS) memory — avoids double-counting shared libs | `smem -p dbeaver` |
| **Async-profiler** | Can attach to any JVM process without startup flags (uses `perf_events` + `AsyncGetCallTrace`) | `./profiler.sh -d 60 -f dbeaver.jfr $(pgrep java)` then open in JMC |

> **Async-profiler** is the key tool for DBeaver/DbVisualizer — it attaches to an already-running JVM with no restart needed, and produces the same JFR output format as JMC.

### C. Startup Time (all three tools)

```bash
# Cold start: drop file system caches first
sudo sync && sudo sh -c 'echo 3 > /proc/sys/vm/drop_caches'
time dbeaver &   # or dbvisualizer, or fxdb

# Automate with a script that kills the process after the main window appears
# Use xdotool or wmctrl to detect window creation as the "ready" signal
```

### D. UI Responsiveness

| Tool | Purpose |
|------|----------|
| **Scenic View** (for FXDB) | JavaFX scene graph inspector — identifies slow rendering passes |
| **Screen recording + frame analysis** | Record at 60fps with OBS, use `ffprobe` to detect dropped frames during scrolling |
| **`xdotool`** | Script simulated mouse/keyboard actions to automate UI interactions |

---

## Benchmark Execution Script (all three apps)

```bash
#!/bin/bash
# Run for each app: APP="dbeaver" | "dbvisualizer" | "fxdb"
APP=$1
PID_FILE=/tmp/${APP}.pid

# 1. Drop caches for cold start
sudo sync && sudo sh -c 'echo 3 > /proc/sys/vm/drop_caches'

# 2. Start app, record PID, measure startup wall time
START=$(date +%s%3N)
$APP &
echo $! > $PID_FILE

# 3. Wait for window to appear (requires xdotool)
xdotool search --sync --onlyvisible --name "$APP" >/dev/null
END=$(date +%s%3N)
echo "Startup time: $((END - START)) ms"

# 4. Start psrecord for the session
psrecord $(cat $PID_FILE) --plot ${APP}_memory.png --duration 300 &

# 5. Attach async-profiler
./profiler.sh -d 300 -f ${APP}_cpu.jfr $(cat $PID_FILE)
```

---

## Metrics to Record Per Test Run

| Metric | Unit | Tool |
|--------|------|------|
| Cold startup time | ms | `time` / `xdotool` |
| Warm startup time | ms | `time` / `xdotool` |
| Idle heap usage | MB | VisualVM / `smem` |
| Peak heap (100K query) | MB | JFR / `psrecord` |
| Query execution time (100K rows) | ms | Manual stopwatch / log timestamps |
| Time-to-first-row rendered | ms | Screen recording frame analysis |
| CPU % during query | % | `psrecord` / `pidstat` |
| CPU % at idle | % | `pidstat` |
| GC pause total (FXDB) | ms | JFR GC events |
| Schema tree load time (50 tables) | ms | Screen recording |

---

## Recommended Order of Tests

1. **Setup** — provision the test database with pre-built datasets at each size
2. **Startup benchmarks** — 5 cold + 5 warm starts per app
3. **Idle memory** — launch, wait 60s, snapshot
4. **Query benchmarks** — execute each query 5x, record wall time and memory peak
5. **Large result set render** — 100K row result, measure render time via screen recording
6. **Schema browser** — connect to a 50-table DB, time full tree expansion
7. **Report** — compare medians in a spreadsheet (FXDB vs DBeaver vs DbVisualizer)

---

## Key Packages to Install (Linux)

```bash
# OS-level tools
sudo apt install sysstat smem linux-tools-common linux-tools-$(uname -r)

# Python process recorder
pip install psrecord

# Async-profiler (download)
wget https://github.com/async-profiler/async-profiler/releases/latest/download/async-profiler-linux-x64.tar.gz

# Window detection for automation
sudo apt install xdotool wmctrl
```
