package com.causa.mcp;

/**
 * Hardcoded diagnostic context for testing validation pipeline with real OOM data.
 *
 * REMOVE IN PRODUCTION - This is test data only
 */
public class TestDiagnosticContext {

    public static final String FULL_CONTEXT = """
=== KRUIZE RECOMMENDATIONS ===
Analysis for pod: heap-oom-prom-8554b846d7-v5hj2

Recommended Resource Adjustments:
resources:
  requests:
    cpu: 0.435          # +0.185 (current: 0.250)
    memory: 806 Mi      # +550 Mi (current: 256 Mi)
  limits:
    cpu: 0.435          # -0.065 (current: 0.500)
    memory: 806 Mi      # +294 Mi (current: 512 Mi)

Confidence: HIGH
Reason: Memory pressure detected, consistent OOM patterns

=== KUBERNETES EVENTS (for pod: heap-oom-prom-8554b846d7-v5hj2) ===
[Normal] 2026-06-18 06:03:32 +0000 UTC: AddedInterface - Add eth0 [10.129.2.41/23] from ovn-kubernetes
[Normal] 2026-06-18 06:03:32 +0000 UTC: Pulling - Pulling image "quay.io/causa-ai-hub/quarkus-heap-oom:heap-oom-prom"
[Normal] 2026-06-18 06:03:36 +0000 UTC: Pulled - Successfully pulled image "quay.io/causa-ai-hub/quarkus-heap-oom:heap-oom-prom"
[Normal] 2026-06-18 06:07:26 +0000 UTC: Created - Created container: heap-oom-prom
[Normal] 2026-06-18 06:07:26 +0000 UTC: Started - Started container heap-oom-prom
[Normal] 2026-06-18 06:07:26 +0000 UTC: Pulled - Container image "quay.io/causa-ai-hub/quarkus-heap-oom:heap-oom-prom" already present
[Warning] 2026-06-18 06:09:13 +0000 UTC: BackOff - Back-off restarting failed container heap-oom-prom in pod heap-oom-prom-8554b846d7-v5hj2_chaos-test(094f00cf-7b84-41c3-8977-7beb051b6689)
[Warning] 2026-06-18 06:03:31 +0000 UTC: SuccessfulCreate - Created pod: heap-oom-prom-8554b846d7-v5hj2
[Normal] 2026-06-18 06:03:31 +0000 UTC: ScalingReplicaSet - Scaled up replica set heap-oom-prom-8554b846d7 from 0 to 1

=== POD LOGS (last 15 lines) ===
Pod: heap-oom-prom-8554b846d7-v5hj2 | Container: heap-oom-prom
2026-06-18 06:09:01,405 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 159000 targets. Current registry size=159000
2026-06-18 06:09:01,411 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 160000 targets. Current registry size=160000
2026-06-18 06:09:01,417 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 161000 targets. Current registry size=161000
2026-06-18 06:09:01,507 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 162000 targets. Current registry size=162000
2026-06-18 06:09:01,513 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 163000 targets. Current registry size=163000
2026-06-18 06:09:02,615 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 164000 targets. Current registry size=164000
2026-06-18 06:09:03,745 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 165000 targets. Current registry size=165000
2026-06-18 06:09:03,807 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 166000 targets. Current registry size=166001
2026-06-18 06:09:04,839 INFO  [ai.causa.scheduler.ScrapeScheduler] (executor-thread-11) Scrape started. targets=166324
2026-06-18 06:09:04,839 INFO  [ai.causa.scheduler.ScrapeScheduler] (executor-thread-12) Writer started. targets=166350
Aborting due to java.lang.OutOfMemoryError: Java heap space
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  Internal Error (debug.cpp:271), pid=2, tid=32
#  fatal error: OutOfMemory encountered: Java heap space
#
# JRE version: OpenJDK Runtime Environment Temurin-21.0.10+7 (21.0.10+7) (build 21.0.10+7-LTS)
# Java VM: OpenJDK 64-Bit Server VM Temurin-21.0.10+7 (21.0.10+7-LTS, mixed mode, sharing, tiered, compressed oops, compressed class ptrs, serial gc, linux-amd64)
# Core dump will be written. Default location: Core dumps may be processed with "/usr/lib/systemd/systemd-coredump %P %u %g %s %t %c %h" (or dumping to /app/core.2)
#
# An error report file with more information is saved as:
# /tmp/hs_err_pid2.log
[103.004s][warning][os] Loading hsdis library failed
Aborted (core dumped)

=== PROMETHEUS METRICS ===
Pod: heap-oom-prom-8554b846d7-v5hj2
Namespace: chaos-test
Timestamp: 2026-06-18 06:09:00 UTC

CPU Usage: 0.421/0.500 cores (84%)
Memory Usage: 478/512 MiB (93%)
Memory Requests: 256 MiB
Memory Limits: 512 MiB

Trend: Memory usage increasing steadily
Pattern: Registry size growing from 159K to 166K targets

=== JFR PROFILING DATA ===
GC Configuration:
  Young Collector: DefNew (Serial)
  Old Collector: SerialOld
  Parallel GC Threads: 0

GC Events:
  06:08:30 - DefNew GC: 82.4ms pause
  06:08:45 - DefNew GC: 313ms pause (memory pressure increasing)
  06:09:00 - DefNew GC: 597ms pause (severe memory pressure)

Large Allocations:
  byte[104022528] (99.3 MB) - Large byte array allocation detected

Heap Status Before Crash:
  Young Generation: Near capacity
  Old Generation: 94% full
  Total Heap: 93% utilized
""";

    private TestDiagnosticContext() {
        // Utility class
    }
}
