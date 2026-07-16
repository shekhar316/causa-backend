package com.causa.mcp;

import com.causa.common.constants.McpConstants;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * MCP Simulator
 *
 * <p>Returns hardcoded demo responses for all MCP tools used in the VM platform path.
 * This is the single source-of-truth for simulated MCP data during demos and development.
 * Replace individual methods with real MCP calls once each server is available.
 *
 * <p>Simulated servers:
 * <ul>
 *   <li><b>Filesystem MCP</b> — {@link McpConstants.Tools#FILESYSTEM_LIST_DIRECTORY_WITH_SIZES}
 *       and {@link McpConstants.Tools#FILESYSTEM_READ_TEXT_FILE}</li>
 *   <li><b>Java JMX MCP</b> — key diagnostic tools from the jvm-jmx-mcp server:
 *       {@link McpConstants.Tools#JAVA_GET_HEAP_STATUS},
 *       {@link McpConstants.Tools#JAVA_GET_GC_ACTIVITY},
 *       {@link McpConstants.Tools#JAVA_GET_THREAD_STATE},
 *       {@link McpConstants.Tools#JAVA_GET_GC_PRESSURE_ANALYSIS},
 *       {@link McpConstants.Tools#JAVA_GET_MEMORY_LEAK_INDICATORS},
 *       {@link McpConstants.Tools#JAVA_GET_THREAD_CONTENTION_ANALYSIS},
 *       {@link McpConstants.Tools#JAVA_GET_JVM_RUNTIME_INFO}</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpSimulator {

    // =========================================================================
    // Filesystem MCP — list_directory_with_sizes
    // =========================================================================

    /**
     * Simulates a {@code list_directory_with_sizes} call against the Liberty
     * server log directory ({@code /logs}).
     *
     * <p>Tool: {@link McpConstants.Tools#FILESYSTEM_LIST_DIRECTORY_WITH_SIZES}
     *
     * @return formatted directory listing with file sizes
     */
    public String getFilesystemDirectoryListing() {
        return """
                [DIR] ffdc
                [FILE] jit.log.20260715.103959.1           165 B
                [FILE] jit.log.20260715.104352.1           165 B
                [FILE] jit.log.20260715.104622.1           165 B
                [FILE] jit.log.20260715.185507.1           165 B
                [FILE] messages_26.07.15_10.43.56.0.log  140.43 KB
                [FILE] messages_26.07.15_10.46.26.0.log   97.33 KB
                [FILE] trace_26.07.15_10.46.26.0.log   669.50 KB
                [FILE] trace_26.07.15_14.40.23.0.log   100.00 MB
                [FILE] trace_26.07.15_18.36.03.0.log   100.00 MB
                [FILE] trace_26.07.15_18.55.11.0.log     3.54 MB
                [FILE] verbosegc.001.log                 2.32 MB
                [FILE] verbosegc.002.log                 2.42 MB
                [FILE] verbosegc.003.log                 5.25 MB
                [FILE] verbosegc.004.log                 2.64 MB
                Total: 14 files, 1 directories
                Combined size: 217.06 MB""";
    }

    // =========================================================================
    // Filesystem MCP — read_text_file
    // =========================================================================

    /**
     * Simulates a {@code read_text_file} call against a Liberty verboseGC log
     * ({@code /logs/verbosegc.001.log}).
     *
     * <p>Tool: {@link McpConstants.Tools#FILESYSTEM_READ_TEXT_FILE}
     *
     * @return raw verboseGC XML content excerpt
     */
    public String getFilesystemFileContent() {
        return """
                <gc-op id="9312" type="scavenge" timems="2.963" contextid="9309" timestamp="2026-07-15T10:43:47.773">
                  <scavenger-info tenureage="8" tenuremask="7f00" tiltratio="72" />
                  <memory-copied type="nursery" objects="25274" bytes="1327496" bytesdiscarded="123880" />
                  <finalization candidates="660" enqueued="13" />
                  <ownableSynchronizers candidates="193" cleared="45" />
                  <references type="soft" candidates="700" cleared="0" enqueued="0" dynamicThreshold="9" maxThreshold="32" />
                  <references type="weak" candidates="462" cleared="8" enqueued="7" />
                  <references type="phantom" candidates="72" cleared="12" enqueued="12" />
                  <object-monitors candidates="241" cleared="0" />
                </gc-op>
                <gc-end id="9313" type="scavenge" contextid="9309" durationms="3.109" usertimems="5.870" systemtimems="0.000" stalltimems="0.215" timestamp="2026-07-15T10:43:47.773" activeThreads="2">
                  <mem-info id="9314" free="19941992" total="77594624" percent="25">
                    <mem type="nursery" free="6345728" total="10354688" percent="61">
                      <mem type="allocate" free="6345728" total="7798784" percent="81" />
                      <mem type="survivor" free="0" total="2555904" percent="0" />
                    </mem>
                    <mem type="tenure" free="13596264" total="67239936" percent="20" macro-fragmented="0">
                      <mem type="soa" free="10233448" total="63877120" percent="16" />
                      <mem type="loa" free="3362816" total="3362816" percent="100" />
                    </mem>
                    <pending-finalizers system="13" default="0" reference="19" classloader="0" />
                    <remembered-set count="2547" />
                  </mem-info>
                </gc-end>
                <cycle-end id="9315" type="scavenge" contextid="9309" timestamp="2026-07-15T10:43:47.773" />
                <allocation-satisfied id="9316" threadId="00000000006EFD00" bytesRequested="232" />
                <af-end id="9317" timestamp="2026-07-15T10:43:47.773" threadId="00000000006F07B8" success="true" from="nursery"/>
                <exclusive-end id="9318" timestamp="2026-07-15T10:43:47.773" durationms="3.664" />""";
    }

    // =========================================================================
    // Java JMX MCP — getHeapStatus
    // =========================================================================

    /**
     * Simulates a {@code getHeapStatus} call.
     *
     * <p>Tool: {@link McpConstants.Tools#JAVA_GET_HEAP_STATUS}
     *
     * @return JSON heap status with utilization and 5-minute trend
     */
    public String getJavaHeapStatus() {
        return """
                {
                  "timestamp": "2026-07-15T10:43:47Z",
                  "heap": {
                    "used_bytes": 57652224,
                    "max_bytes": 77594624,
                    "committed_bytes": 77594624,
                    "utilization_percent": 74.3,
                    "available_bytes": 19941992
                  },
                  "recent_trend": {
                    "window": "5m",
                    "min_used_bytes": 48234496,
                    "max_used_bytes": 67108864,
                    "avg_used_bytes": 55574528,
                    "growth_rate_bytes_per_min": 1887437
                  }
                }""";
    }

    // =========================================================================
    // Java JMX MCP — getGcActivity
    // =========================================================================

    /**
     * Simulates a {@code getGcActivity} call.
     *
     * <p>Tool: {@link McpConstants.Tools#JAVA_GET_GC_ACTIVITY}
     *
     * @return JSON GC activity with collection counts and pause times
     */
    public String getJavaGcActivity() {
        return """
                {
                  "timestamp": "2026-07-15T10:43:47Z",
                  "collectors": [
                    {
                      "name": "scavenge",
                      "total_collections": 9312,
                      "total_time_seconds": 27.35,
                      "avg_pause_ms": 2.94
                    },
                    {
                      "name": "global",
                      "total_collections": 14,
                      "total_time_seconds": 1.42,
                      "avg_pause_ms": 101.4
                    }
                  ],
                  "summary": {
                    "total_collections": 9326,
                    "total_time_seconds": 28.77,
                    "overall_avg_pause_ms": 3.08
                  }
                }""";
    }

    // =========================================================================
    // Java JMX MCP — getThreadState
    // =========================================================================

    /**
     * Simulates a {@code getThreadState} call.
     *
     * <p>Tool: {@link McpConstants.Tools#JAVA_GET_THREAD_STATE}
     *
     * @return JSON thread counts and health metrics
     */
    public String getJavaThreadState() {
        return """
                {
                  "timestamp": "2026-07-15T10:43:47Z",
                  "current_threads": 52,
                  "daemon_threads": 44,
                  "non_daemon_threads": 8,
                  "peak_threads": 61,
                  "peak_utilization_percent": 85.2
                }""";
    }

    // =========================================================================
    // Java JMX MCP — getGcPressureAnalysis
    // =========================================================================

    /**
     * Simulates a {@code getGcPressureAnalysis} call.
     *
     * <p>Tool: {@link McpConstants.Tools#JAVA_GET_GC_PRESSURE_ANALYSIS}
     *
     * @return JSON GC pressure score and recommendations
     */
    public String getJavaGcPressureAnalysis() {
        return """
                {
                  "lookback": "5m",
                  "timestamp": "2026-07-15T10:43:47Z",
                  "avg_gc_frequency_per_min": 18.6,
                  "avg_gc_time_percent": 7.2,
                  "max_gc_time_percent": 12.4,
                  "heap_growth_percent": 11.3,
                  "current_heap_utilization_percent": 74.3,
                  "gc_pressure_score": 71.8,
                  "interpretation": "HIGH: GC pressure is elevated. Heap is filling faster than GC can reclaim. Investigate allocation rate and memory retention.",
                  "recommendations": [
                    "Heap utilization at 74.3% — consider increasing -Xmx if workload is sustained",
                    "Scavenge frequency is high (18.6/min) — nursery may be undersized, tune -Xmns",
                    "Global GC avg pause 101ms — review long-lived object promotion patterns"
                  ]
                }""";
    }

    // =========================================================================
    // Java JMX MCP — getMemoryLeakIndicators
    // =========================================================================

    /**
     * Simulates a {@code getMemoryLeakIndicators} call.
     *
     * <p>Tool: {@link McpConstants.Tools#JAVA_GET_MEMORY_LEAK_INDICATORS}
     *
     * @return JSON memory leak likelihood score and indicators
     */
    public String getJavaMemoryLeakIndicators() {
        return """
                {
                  "lookback": "30m",
                  "timestamp": "2026-07-15T10:43:47Z",
                  "post_gc_heap_trend_bytes_per_sample": 1048576,
                  "post_gc_heap_growth_rate_percent": 1.42,
                  "avg_post_gc_heap_bytes": 52428800,
                  "full_gc_count_increase": 14,
                  "leak_likelihood_score": 68,
                  "leak_indicators": [
                    "Post-GC heap usage is steadily increasing (+1.42% per sample)",
                    "Global GC frequency increased 14 times in 30 minutes",
                    "Tenure space occupancy growing between collections"
                  ],
                  "assessment": "MEDIUM-HIGH: Consistent post-GC heap growth pattern detected. Likely heap retention or classloader leak. Recommend heap dump analysis."
                }""";
    }

    // =========================================================================
    // Java JMX MCP — getThreadContentionAnalysis
    // =========================================================================

    /**
     * Simulates a {@code getThreadContentionAnalysis} call.
     *
     * <p>Tool: {@link McpConstants.Tools#JAVA_GET_THREAD_CONTENTION_ANALYSIS}
     *
     * @return JSON thread contention score and breakdown
     */
    public String getJavaThreadContentionAnalysis() {
        return """
                {
                  "lookback": "5m",
                  "timestamp": "2026-07-15T10:43:47Z",
                  "avg_blocked_threads": 2.1,
                  "max_blocked_threads": 5,
                  "avg_waiting_threads": 11.3,
                  "avg_timed_waiting_threads": 18.7,
                  "avg_runnable_threads": 19.9,
                  "blocked_thread_percent": 4.0,
                  "waiting_thread_percent": 21.7,
                  "total_contention_percent": 25.7,
                  "contention_score": 42,
                  "interpretation": "MODERATE: Noticeable thread contention. Some threads are blocked or waiting longer than expected. Review synchronization patterns and connection pool sizing."
                }""";
    }

    // =========================================================================
    // Java JMX MCP — getJvmRuntimeInfo
    // =========================================================================

    /**
     * Simulates a {@code getJvmRuntimeInfo} call.
     *
     * <p>Tool: {@link McpConstants.Tools#JAVA_GET_JVM_RUNTIME_INFO}
     *
     * @return JSON JVM vendor, version, and heap configuration
     */
    public String getJavaJvmRuntimeInfo() {
        return """
                {
                  "timestamp": "2026-07-15T10:43:47Z",
                  "heap_config": {
                    "initial_heap_bytes": 16777216,
                    "max_heap_bytes": 77594624,
                    "initial_heap_mb": 16,
                    "max_heap_mb": 74
                  },
                  "jvm_runtime_info": {
                    "runtime": "Eclipse OpenJ9 VM",
                    "vendor": "Eclipse OpenJ9",
                    "version": "21.0.3",
                    "jvm_type": "OpenJ9"
                  },
                  "target_info": {
                    "job": "liberty-jmx",
                    "instance": "localhost:9080"
                  },
                  "data_source": {
                    "type": "prometheus",
                    "url": "http://localhost:9091"
                  }
                }""";
    }
}
