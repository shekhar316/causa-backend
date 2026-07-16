package com.causa.core.domain;

import com.causa.common.constants.ContextConstants;

/**
 * Diagnostic Context
 *
 * <p>Aggregates all diagnostic context collected from MCP servers for a single alert.
 * The fields populated depend on the deployment platform:
 *
 * <ul>
 *   <li><b>cluster</b> — Kubernetes MCP (pod status, events, logs),
 *       Kruize MCP (cost/perf recommendations),
 *       Cryostat MCP (GC, memory, thread, exception, container JFR analysis)</li>
 *   <li><b>vm</b>      — Filesystem MCP (log directory listing, verboseGC log content),
 *       Java JMX MCP (heap status, GC activity, thread state, GC pressure,
 *       memory leak indicators, thread contention, JVM runtime info)</li>
 * </ul>
 *
 * <p>{@link #toString()} produces LLM-ready formatted text with section headers that
 * reflect the actual data source and are routed by {@link #platform}.
 *
 * @since 0.0.1
 */
public final class DiagnosticContext {

    /** Platform value for Kubernetes cluster deployments. */
    public static final String PLATFORM_CLUSTER = "cluster";

    /** Platform value for VM deployments. */
    public static final String PLATFORM_VM = "vm";

    // -------------------------------------------------------------------------
    // Identity (from Alert)
    // -------------------------------------------------------------------------
    private final String platform;
    private final String podName;
    private final String workloadName;
    private final String containerName;
    private final String namespace;

    // -------------------------------------------------------------------------
    // Cluster platform — Kubernetes MCP
    // -------------------------------------------------------------------------
    private final String podStatus;
    private final String podEvents;
    private final String podLogs;

    // Cluster platform — Kruize MCP
    private final String costRecommendations;
    private final String performanceRecommendations;

    // Cluster platform — Cryostat MCP (JFR)
    private final String gcAnalysis;
    private final String memoryAnalysis;
    private final String threadAnalysis;
    private final String exceptionAnalysis;
    private final String containerAnalysis;

    // -------------------------------------------------------------------------
    // VM platform — Filesystem MCP
    // -------------------------------------------------------------------------
    private final String logDirectoryListing;
    private final String gcLogContent;

    // VM platform — Java JMX MCP
    private final String heapStatus;
    private final String gcActivity;
    private final String threadState;
    private final String gcPressureAnalysis;
    private final String memoryLeakIndicators;
    private final String threadContentionAnalysis;
    private final String jvmRuntimeInfo;

    private DiagnosticContext(Builder builder) {
        this.platform               = builder.platform;
        this.podName                = builder.podName;
        this.workloadName           = builder.workloadName;
        this.containerName          = builder.containerName;
        this.namespace              = builder.namespace;
        // cluster
        this.podStatus              = builder.podStatus;
        this.podEvents              = builder.podEvents;
        this.podLogs                = builder.podLogs;
        this.costRecommendations    = builder.costRecommendations;
        this.performanceRecommendations = builder.performanceRecommendations;
        this.gcAnalysis             = builder.gcAnalysis;
        this.memoryAnalysis         = builder.memoryAnalysis;
        this.threadAnalysis         = builder.threadAnalysis;
        this.exceptionAnalysis      = builder.exceptionAnalysis;
        this.containerAnalysis      = builder.containerAnalysis;
        // vm
        this.logDirectoryListing    = builder.logDirectoryListing;
        this.gcLogContent           = builder.gcLogContent;
        this.heapStatus             = builder.heapStatus;
        this.gcActivity             = builder.gcActivity;
        this.threadState            = builder.threadState;
        this.gcPressureAnalysis     = builder.gcPressureAnalysis;
        this.memoryLeakIndicators   = builder.memoryLeakIndicators;
        this.threadContentionAnalysis = builder.threadContentionAnalysis;
        this.jvmRuntimeInfo         = builder.jvmRuntimeInfo;
    }

    // -------------------------------------------------------------------------
    // Getters — identity
    // -------------------------------------------------------------------------

    public String getPlatform()      { return platform; }
    public String getPodName()       { return podName; }
    /** Workload name — container for cluster, service/process name for VM. */
    public String getWorkloadName()  { return workloadName; }
    public String getContainerName() { return containerName; }
    public String getNamespace()     { return namespace; }

    // -------------------------------------------------------------------------
    // Getters — cluster platform
    // -------------------------------------------------------------------------

    public String getPodStatus()                  { return podStatus; }
    public String getPodEvents()                  { return podEvents; }
    public String getPodLogs()                    { return podLogs; }
    public String getCostRecommendations()        { return costRecommendations; }
    public String getPerformanceRecommendations() { return performanceRecommendations; }
    public String getGcAnalysis()                 { return gcAnalysis; }
    public String getMemoryAnalysis()             { return memoryAnalysis; }
    public String getThreadAnalysis()             { return threadAnalysis; }
    public String getExceptionAnalysis()          { return exceptionAnalysis; }
    public String getContainerAnalysis()          { return containerAnalysis; }

    // -------------------------------------------------------------------------
    // Getters — VM platform
    // -------------------------------------------------------------------------

    public String getLogDirectoryListing()      { return logDirectoryListing; }
    public String getGcLogContent()             { return gcLogContent; }
    public String getHeapStatus()               { return heapStatus; }
    public String getGcActivity()               { return gcActivity; }
    public String getThreadState()              { return threadState; }
    public String getGcPressureAnalysis()       { return gcPressureAnalysis; }
    public String getMemoryLeakIndicators()     { return memoryLeakIndicators; }
    public String getThreadContentionAnalysis() { return threadContentionAnalysis; }
    public String getJvmRuntimeInfo()           { return jvmRuntimeInfo; }

    // -------------------------------------------------------------------------
    // Context presence checks
    // -------------------------------------------------------------------------

    /** Returns {@code true} if Kubernetes MCP data (pod status, events, or logs) was collected. */
    public boolean hasKubernetesContext() {
        return isNotBlank(podStatus) || isNotBlank(podEvents) || isNotBlank(podLogs);
    }

    /** Returns {@code true} if Kruize MCP data (cost or perf recommendations) was collected. */
    public boolean hasKruizeContext() {
        return isNotBlank(costRecommendations) || isNotBlank(performanceRecommendations);
    }

    /** Returns {@code true} if Cryostat MCP data (any JFR analysis) was collected. */
    public boolean hasCryostatContext() {
        return isNotBlank(gcAnalysis)
            || isNotBlank(memoryAnalysis)
            || isNotBlank(threadAnalysis)
            || isNotBlank(exceptionAnalysis)
            || isNotBlank(containerAnalysis);
    }

    /** Returns {@code true} if Filesystem MCP data (directory listing or GC log) was collected. */
    public boolean hasFilesystemContext() {
        return isNotBlank(logDirectoryListing) || isNotBlank(gcLogContent);
    }

    /** Returns {@code true} if Java JMX MCP data (any JVM metric) was collected. */
    public boolean hasJavaMcpContext() {
        return isNotBlank(heapStatus)
            || isNotBlank(gcActivity)
            || isNotBlank(threadState)
            || isNotBlank(gcPressureAnalysis)
            || isNotBlank(memoryLeakIndicators)
            || isNotBlank(threadContentionAnalysis)
            || isNotBlank(jvmRuntimeInfo);
    }

    /** Returns {@code true} if any context was collected regardless of platform. */
    public boolean hasAnyContext() {
        return hasKubernetesContext()
            || hasKruizeContext()
            || hasCryostatContext()
            || hasFilesystemContext()
            || hasJavaMcpContext();
    }

    // -------------------------------------------------------------------------
    // LLM-ready formatted output — routed by platform
    // -------------------------------------------------------------------------

    /**
     * Produces LLM-ready formatted text with platform-appropriate section headers.
     *
     * <ul>
     *   <li>For {@code cluster}: renders Kubernetes, Kruize, and Cryostat sections.</li>
     *   <li>For {@code vm}: renders Filesystem MCP and Java JMX MCP sections.</li>
     * </ul>
     *
     * <p>Missing sections are explicitly marked as "No Data Available" to prevent
     * LLM hallucination.
     *
     * @return structured text suitable for LLM context
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Header — identity fields common to both platforms
        sb.append(ContextConstants.HEADER).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_PLATFORM).append(ContextConstants.FIELD_SEPARATOR)
            .append(platform != null ? platform : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_WORKLOAD).append(ContextConstants.FIELD_SEPARATOR)
            .append(workloadName != null ? workloadName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_POD).append(ContextConstants.FIELD_SEPARATOR)
            .append(podName != null ? podName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_CONTAINER).append(ContextConstants.FIELD_SEPARATOR)
            .append(containerName != null ? containerName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_NAMESPACE).append(ContextConstants.FIELD_SEPARATOR)
            .append(namespace != null ? namespace : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE)
            .append(ContextConstants.NEWLINE);

        if (PLATFORM_VM.equals(platform)) {
            appendVmSections(sb);
        } else {
            appendClusterSections(sb);
        }

        return sb.toString();
    }

    /** Renders all cluster-platform sections (Kubernetes / Kruize / Cryostat). */
    private void appendClusterSections(StringBuilder sb) {
        appendSection(sb, ContextConstants.SECTION_POD_STATUS,           podStatus);
        appendSection(sb, ContextConstants.SECTION_POD_EVENTS,           podEvents);
        appendSection(sb, ContextConstants.SECTION_POD_LOGS,             podLogs);
        appendSection(sb, ContextConstants.SECTION_COST_RECOMMENDATIONS, costRecommendations);
        appendSection(sb, ContextConstants.SECTION_PERF_RECOMMENDATIONS, performanceRecommendations);
        appendSection(sb, ContextConstants.SECTION_GC_ANALYSIS,          gcAnalysis);
        appendSection(sb, ContextConstants.SECTION_MEMORY_ANALYSIS,      memoryAnalysis);
        appendSection(sb, ContextConstants.SECTION_THREAD_ANALYSIS,      threadAnalysis);
        appendSection(sb, ContextConstants.SECTION_EXCEPTION_ANALYSIS,   exceptionAnalysis);
        appendSection(sb, ContextConstants.SECTION_CONTAINER_ANALYSIS,   containerAnalysis);
    }

    /** Renders all VM-platform sections (Filesystem MCP / Java JMX MCP). */
    private void appendVmSections(StringBuilder sb) {
        appendSection(sb, ContextConstants.SECTION_VM_LOG_DIR_LISTING,   logDirectoryListing);
        appendSection(sb, ContextConstants.SECTION_VM_GC_LOG_CONTENT,    gcLogContent);
        appendSection(sb, ContextConstants.SECTION_VM_HEAP_STATUS,       heapStatus);
        appendSection(sb, ContextConstants.SECTION_VM_GC_ACTIVITY,       gcActivity);
        appendSection(sb, ContextConstants.SECTION_VM_THREAD_STATE,      threadState);
        appendSection(sb, ContextConstants.SECTION_VM_GC_PRESSURE,       gcPressureAnalysis);
        appendSection(sb, ContextConstants.SECTION_VM_MEMORY_LEAK,       memoryLeakIndicators);
        appendSection(sb, ContextConstants.SECTION_VM_THREAD_CONTENTION, threadContentionAnalysis);
        appendSection(sb, ContextConstants.SECTION_VM_JVM_RUNTIME_INFO,  jvmRuntimeInfo);
    }

    private void appendSection(StringBuilder sb, String header, String content) {
        sb.append(ContextConstants.SECTION_PREFIX).append(header).append(ContextConstants.SECTION_SUFFIX)
            .append(ContextConstants.NEWLINE);
        sb.append(isNotBlank(content) ? content : ContextConstants.NOT_AVAILABLE)
            .append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.NEWLINE);
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Builder for {@link DiagnosticContext}.
     *
     * <p>Set {@link #platform(String)} first, then populate only the fields
     * relevant to that platform. Fields for the other platform remain null and
     * will be omitted from the LLM output.
     */
    public static final class Builder {

        // identity
        private String platform;
        private String podName;
        private String workloadName;
        private String containerName;
        private String namespace;

        // cluster — Kubernetes
        private String podStatus;
        private String podEvents;
        private String podLogs;

        // cluster — Kruize
        private String costRecommendations;
        private String performanceRecommendations;

        // cluster — Cryostat
        private String gcAnalysis;
        private String memoryAnalysis;
        private String threadAnalysis;
        private String exceptionAnalysis;
        private String containerAnalysis;

        // vm — Filesystem
        private String logDirectoryListing;
        private String gcLogContent;

        // vm — Java JMX
        private String heapStatus;
        private String gcActivity;
        private String threadState;
        private String gcPressureAnalysis;
        private String memoryLeakIndicators;
        private String threadContentionAnalysis;
        private String jvmRuntimeInfo;

        private Builder() {}

        // identity
        public Builder platform(String platform)             { this.platform = platform; return this; }
        public Builder podName(String podName)               { this.podName = podName; return this; }
        public Builder workloadName(String workloadName)     { this.workloadName = workloadName; return this; }
        public Builder containerName(String containerName)   { this.containerName = containerName; return this; }
        public Builder namespace(String namespace)           { this.namespace = namespace; return this; }

        // cluster — Kubernetes
        public Builder podStatus(String podStatus)           { this.podStatus = podStatus; return this; }
        public Builder podEvents(String podEvents)           { this.podEvents = podEvents; return this; }
        public Builder podLogs(String podLogs)               { this.podLogs = podLogs; return this; }

        // cluster — Kruize
        public Builder costRecommendations(String v)         { this.costRecommendations = v; return this; }
        public Builder performanceRecommendations(String v)  { this.performanceRecommendations = v; return this; }

        // cluster — Cryostat
        public Builder gcAnalysis(String v)                  { this.gcAnalysis = v; return this; }
        public Builder memoryAnalysis(String v)              { this.memoryAnalysis = v; return this; }
        public Builder threadAnalysis(String v)              { this.threadAnalysis = v; return this; }
        public Builder exceptionAnalysis(String v)           { this.exceptionAnalysis = v; return this; }
        public Builder containerAnalysis(String v)           { this.containerAnalysis = v; return this; }

        // vm — Filesystem
        public Builder logDirectoryListing(String v)         { this.logDirectoryListing = v; return this; }
        public Builder gcLogContent(String v)                { this.gcLogContent = v; return this; }

        // vm — Java JMX
        public Builder heapStatus(String v)                  { this.heapStatus = v; return this; }
        public Builder gcActivity(String v)                  { this.gcActivity = v; return this; }
        public Builder threadState(String v)                 { this.threadState = v; return this; }
        public Builder gcPressureAnalysis(String v)          { this.gcPressureAnalysis = v; return this; }
        public Builder memoryLeakIndicators(String v)        { this.memoryLeakIndicators = v; return this; }
        public Builder threadContentionAnalysis(String v)    { this.threadContentionAnalysis = v; return this; }
        public Builder jvmRuntimeInfo(String v)              { this.jvmRuntimeInfo = v; return this; }

        public DiagnosticContext build() {
            return new DiagnosticContext(this);
        }
    }
}
