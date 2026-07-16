package com.causa.common.constants;

/**
 * MCP (Model Context Protocol) Constants
 *
 * <p>Centralized constants for MCP server integration.
 *
 * @since 0.0.1
 */
public final class McpConstants {

    private McpConstants() {
        // Prevent instantiation
    }

    /**
     * MCP Protocol version
     */
    public static final String PROTOCOL_VERSION = "2025-03-26";

    /**
     * Client information
     */
    public static final String CLIENT_NAME = "causa-backend";
    public static final String CLIENT_VERSION = "0.0.1";

    /**
     * MCP JSON-RPC constants
     */
    public static final class JsonRpc {
        private JsonRpc() {}

        public static final String VERSION = "2.0";
        public static final String METHOD_INITIALIZE = "initialize";
        public static final String METHOD_TOOLS_CALL = "tools/call";
        public static final String METHOD_NOTIFICATIONS_INITIALIZED = "notifications/initialized";

        public static final String PARAM_PROTOCOL_VERSION = "protocolVersion";
        public static final String PARAM_CLIENT_INFO = "clientInfo";
        public static final String PARAM_CAPABILITIES = "capabilities";
        public static final String PARAM_NAME = "name";
        public static final String PARAM_ARGUMENTS = "arguments";

        public static final String FIELD_JSONRPC = "jsonrpc";
        public static final String FIELD_ID = "id";
        public static final String FIELD_METHOD = "method";
        public static final String FIELD_PARAMS = "params";
        public static final String FIELD_RESULT = "result";
        public static final String FIELD_ERROR = "error";
        public static final String FIELD_CONTENT = "content";
        public static final String FIELD_TEXT = "text";
    }

    /**
     * HTTP Headers
     */
    public static final class Headers {
        private Headers() {}

        public static final String CONTENT_TYPE = "Content-Type";
        public static final String ACCEPT = "Accept";
        public static final String MCP_SESSION_ID = "Mcp-Session-Id";

        public static final String CONTENT_TYPE_JSON = "application/json";
        public static final String ACCEPT_VALUE = "application/json, text/event-stream";
    }

    /**
     * MCP Tool names
     */
    public static final class Tools {
        private Tools() {}

        // Kubernetes MCP tools
        public static final String PODS_GET = "pods_get";
        public static final String PODS_LOG = "pods_log";
        public static final String EVENTS_LIST = "events_list";

        // Kruize MCP tools
        public static final String KRUIZE_GET_COST_RECOMMENDATIONS = "getCostOptimizedRecommendations";
        public static final String KRUIZE_GET_PERF_RECOMMENDATIONS = "getPerformanceOptimizedRecommendations";

        // Cryostat MCP tools
        public static final String CRYOSTAT_GET_GC_ANALYSIS = "get_gc_analysis";
        public static final String CRYOSTAT_GET_MEMORY_ANALYSIS = "get_memory_analysis";
        public static final String CRYOSTAT_GET_THREAD_ANALYSIS = "get_thread_analysis";
        public static final String CRYOSTAT_GET_EXCEPTION_ANALYSIS = "get_exception_analysis";
        public static final String CRYOSTAT_GET_CONTAINER_ANALYSIS = "get_container_analysis";

        // Filesystem MCP tools (VM platform) — https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem
        public static final String FILESYSTEM_READ_TEXT_FILE           = "read_text_file";
        public static final String FILESYSTEM_READ_MEDIA_FILE          = "read_media_file";
        public static final String FILESYSTEM_READ_MULTIPLE_FILES      = "read_multiple_files";
        public static final String FILESYSTEM_LIST_DIRECTORY           = "list_directory";
        public static final String FILESYSTEM_LIST_DIRECTORY_WITH_SIZES = "list_directory_with_sizes";
        public static final String FILESYSTEM_DIRECTORY_TREE           = "directory_tree";
        public static final String FILESYSTEM_SEARCH_FILES             = "search_files";
        public static final String FILESYSTEM_GET_FILE_INFO            = "get_file_info";
        public static final String FILESYSTEM_LIST_ALLOWED_DIRECTORIES = "list_allowed_directories";
        public static final String FILESYSTEM_CREATE_DIRECTORY         = "create_directory";

        // Java JMX MCP tools (VM platform) — jvm-jmx-mcp server
        // Memory
        public static final String JAVA_GET_HEAP_STATUS              = "getHeapStatus";
        public static final String JAVA_GET_MEMORY_POOLS_BREAKDOWN   = "getMemoryPoolsBreakdown";
        public static final String JAVA_GET_MEMORY_OVER_TIME         = "getMemoryOverTime";

        // GC
        public static final String JAVA_GET_GC_ACTIVITY              = "getGcActivity";
        public static final String JAVA_GET_GC_BEHAVIOR_OVER_TIME    = "getGcBehaviorOverTime";
        public static final String JAVA_GET_GC_EFFICIENCY            = "getGcEfficiency";

        // Threads
        public static final String JAVA_GET_THREAD_STATE             = "getThreadState";
        public static final String JAVA_GET_THREAD_ACTIVITY_OVER_TIME = "getThreadActivityOverTime";

        // CPU & Resources
        public static final String JAVA_GET_CPU_USAGE                = "getCpuUsage";
        public static final String JAVA_GET_SYSTEM_RESOURCES         = "getSystemResources";
        public static final String JAVA_GET_RESOURCE_USAGE_OVER_TIME = "getResourceUsageOverTime";

        // Application Behavior
        public static final String JAVA_GET_CLASS_LOADING_STATS      = "getClassLoadingStats";
        public static final String JAVA_GET_JVM_RUNTIME_INFO         = "getJvmRuntimeInfo";

        // Comparative Analysis
        public static final String JAVA_GET_CURRENT_VS_BASELINE      = "getCurrentVsBaseline";
        public static final String JAVA_GET_METRIC_PERCENTILES       = "getMetricPercentiles";

        // Time-Window Investigation
        public static final String JAVA_GET_INCIDENT_WINDOW_DATA     = "getIncidentWindowData";
        public static final String JAVA_GET_BEFORE_AFTER_SNAPSHOT    = "getBeforeAfterSnapshot";

        // Correlation Analysis
        public static final String JAVA_GET_MEMORY_GC_CORRELATION    = "getMemoryGcCorrelation";
        public static final String JAVA_GET_CPU_GC_CORRELATION       = "getCpuGcCorrelation";

        // Comprehensive Context
        public static final String JAVA_GET_JVM_HEALTH_CONTEXT       = "getJvmHealthContext";
        public static final String JAVA_GET_INVESTIGATION_BUNDLE     = "getInvestigationBundle";

        // Advanced RCA
        public static final String JAVA_GET_MEMORY_ALLOCATION_RATE   = "getMemoryAllocationRate";
        public static final String JAVA_GET_GC_PRESSURE_ANALYSIS     = "getGcPressureAnalysis";
        public static final String JAVA_GET_MEMORY_LEAK_INDICATORS   = "getMemoryLeakIndicators";
        public static final String JAVA_GET_THREAD_CONTENTION_ANALYSIS = "getThreadContentionAnalysis";
        public static final String JAVA_GET_HEAP_FRAGMENTATION_ANALYSIS = "getHeapFragmentationAnalysis";
    }

    /**
     * MCP Tool arguments
     */
    public static final class Arguments {
        private Arguments() {}

        public static final String NAME = "name";
        public static final String NAMESPACE = "namespace";
        public static final String CONTAINER = "container";
        public static final String TAIL_LINES = "tailLines";
        public static final String FIELD_SELECTOR = "fieldSelector";

        // Kruize arguments
        public static final String CONTAINER_NAME = "containerName";

        // Cryostat arguments
        public static final String POD_NAME = "pod_name";

        // Filesystem arguments
        public static final String PATH = "path";

        // Java JMX MCP arguments
        public static final String LOOKBACK         = "lookback";
        public static final String STEP             = "step";
        public static final String WINDOW           = "window";
        public static final String BASELINE_TIME    = "baselineTime";
        public static final String METRIC_CATEGORY  = "metricCategory";
        public static final String START_TIME       = "startTime";
        public static final String END_TIME         = "endTime";
        public static final String EVENT_TIME       = "eventTime";
        public static final String FOCUS_AREA       = "focusArea";
    }

    /**
     * Output section headers
     */
    public static final class OutputHeaders {
        private OutputHeaders() {}

        public static final String POD_STATUS = "\n=== POD STATUS ===";
        public static final String KUBERNETES_EVENTS = "\n=== KUBERNETES EVENTS (for pod: %s) ===";
        public static final String POD_LOGS = "\n=== POD LOGS (last 5 lines) ===";
    }

    /**
     * Error messages
     */
    public static final class Errors {
        private Errors() {}

        public static final String UNABLE_TO_GET_POD_STATUS = "Unable to retrieve pod status: %s";
        public static final String UNABLE_TO_GET_EVENTS = "Unable to retrieve events: %s";
        public static final String UNABLE_TO_GET_LOGS = "Unable to retrieve logs: %s";
        public static final String UNABLE_TO_GET_KRUIZE_RECOMMENDATIONS = "Unable to retrieve Kruize recommendations: %s";
        public static final String UNABLE_TO_GET_CRYOSTAT_ANALYSIS = "Unable to retrieve Cryostat %s analysis: %s";
        public static final String CRYOSTAT_RECORDING_CREATED = "Cryostat recording created, retrying after delay";
        public static final String CRYOSTAT_MAX_RETRIES_EXCEEDED = "Cryostat max retries exceeded for %s";

        public static final String MCP_INITIALIZE_FAILED = "MCP initialize failed with status: %d, body: %s";
        public static final String MCP_TOOL_CALL_FAILED = "MCP tool call failed with status: %d, body: %s";
        public static final String MCP_TOOL_ERROR = "MCP tool error: %s";

        public static final String NO_RECOMMENDATIONS_AVAILABLE = "No recommendations available";
        public static final String NO_ANALYSIS_AVAILABLE = "No analysis available";
    }

    /**
     * Default values
     */
    public static final class Defaults {
        private Defaults() {}

        public static final int DEFAULT_TAIL_LINES = 25;
        public static final String NO_EVENTS_FOUND = "No events found";
        public static final String NO_LOGS_AVAILABLE = "No logs available";
        public static final String UNKNOWN_STATUS = "Unknown";
        public static final String NO_DATA_AVAILABLE = "No Data Available";
    }

    /**
     * Error detection patterns in MCP responses
     */
    public static final class ErrorMarkers {
        private ErrorMarkers() {}

        public static final String ERROR_CALLING_TOOL = "Error calling tool";
        public static final String LIST_INDEX_OUT_OF_RANGE = "list index out of range";
    }

    /**
     * SSE (Server-Sent Events) parsing
     */
    public static final class SSE {
        private SSE() {}

        public static final String DATA_PREFIX = "data: ";
        public static final String LINE_SEPARATOR = "\n";
        public static final String EVENT_MESSAGE = "event: message";
    }


    /**
     * MCP endpoint paths
     */
    public static final class Paths {
        private Paths() {}

        public static final String MCP_ENDPOINT = "/mcp";
        public static final String MCP_ENDPOINT_SLASH = "/mcp/";
    }
    
    /**
     * YAML parsing constants
     */
    public static final class Yaml {
        private Yaml() {}

        public static final String PHASE_PREFIX = "phase:";
        public static final String REASON_PREFIX = "reason:";
        public static final String TYPE_PREFIX = "Type:";
        public static final String REASON_FIELD = "Reason:";
        public static final String MESSAGE_FIELD = "Message:";
        public static final String TIMESTAMP_FIELD = "Timestamp:";
        public static final String WAITING_STATE = "waiting:";
        public static final String TERMINATED_STATE = "terminated:";
        public static final String RUNNING_STATE = "running:";
        public static final String STARTED_AT_FIELD = "startedAt:";
        public static final String RESTART_COUNT_FIELD = "restartCount:";
        public static final String CONTAINER_STATUSES_SECTION = "containerStatuses:";
        public static final String INIT_CONTAINER_STATUSES_SECTION = "initContainerStatuses:";
        public static final String STATUS_SECTION = "status:";
        public static final String SPEC_SECTION = "spec:";
        public static final String CONTAINERS_SECTION = "containers:";
        public static final String RESOURCES_SECTION = "resources:";
        public static final String LIMITS_SECTION = "limits:";
        public static final String REQUESTS_SECTION = "requests:";
        public static final String CPU_FIELD = "cpu:";
        public static final String MEMORY_FIELD = "memory:";
        public static final String STATE_FIELD = "state:";
        public static final String HOST_IP_FIELD = "hostIP:";
        public static final String ITEM_PREFIX = "- ";
        public static final String COLON_SEPARATOR = ":";
        public static final int COLON_SPLIT_LIMIT = 2;
    }

    /**
     * Logging field names
     */
    public static final class LogFields {
        private LogFields() {}

        public static final String ALERT_ID = "alertId";
        public static final String POD_NAME = "podName";
        public static final String NAMESPACE = "namespace";
        public static final String CONTAINER = "container";
        public static final String CONTAINER_NAME = "containerName";
        public static final String STATUS = "status";
        public static final String TOOL = "tool";
        public static final String ERROR = "error";
        public static final String ERROR_TEXT = "errorText";
        public static final String HAS_K8S_CONTEXT = "hasK8sContext";
        public static final String HAS_KRUIZE_CONTEXT = "hasKruizeContext";
        public static final String HAS_CRYOSTAT_CONTEXT = "hasCryostatContext";
        public static final String HAS_FILESYSTEM_CONTEXT = "hasFilesystemContext";
        public static final String HAS_JAVA_CONTEXT = "hasJavaContext";
        public static final String RETRY_ATTEMPT = "retryAttempt";
        public static final String DELAY_MS = "delayMs";
        public static final String ANALYSIS_TYPE = "analysisType";
        public static final String PLATFORM = "platform";
    }

    /**
     * String formatting constants
     */
    public static final class Format {
        private Format() {}

        public static final String INVOLVED_OBJECT_NAME_PREFIX = "involvedObject.name=";
        public static final String PARENTHESIS_FORMAT = " (%s)";
        public static final String VERSION = "version";
    }

    /**
     * Cryostat-specific constants
     */
    public static final class Cryostat {
        private Cryostat() {}

        public static final String RECORDING_CREATED_STATUS = "RECORDING_CREATED";
        public static final String STATUS_FIELD = "status";
    }
}
