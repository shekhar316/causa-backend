package com.causa.config;

import com.causa.common.constants.ConfigConstants;

import java.util.Map;

/**
 * MCP Configuration Snapshot
 *
 * <p>Typed view of all MCP-related keys from the in-memory configuration cache.
 * Constructed by {@link AppConfig} on every call to {@link AppConfig#getMcpConfig()}
 * so callers always receive the current values from the DB-backed cache.
 *
 * <p>Keys mirror {@link ConfigConstants.MCP} and its sub-classes.
 *
 * @since 0.0.1
 */
public final class McpConfig {

    private final KubernetesConfig kubernetes;
    private final KruizeConfig     kruize;
    private final CryostatConfig   cryostat;
    private final FilesystemConfig filesystem;
    private final JavaConfig       java;

    McpConfig(Map<String, String> cache) {
        this.kubernetes = new KubernetesConfig(cache);
        this.kruize     = new KruizeConfig(cache);
        this.cryostat   = new CryostatConfig(cache);
        this.filesystem = new FilesystemConfig(cache);
        this.java       = new JavaConfig(cache);
    }

    /** Kubernetes MCP server configuration. */
    public KubernetesConfig getKubernetes() {
        return kubernetes;
    }

    /** Kruize MCP server configuration. */
    public KruizeConfig getKruize() {
        return kruize;
    }

    /** Cryostat MCP server configuration. */
    public CryostatConfig getCryostat() {
        return cryostat;
    }

    /** Filesystem MCP server configuration (VM platform). */
    public FilesystemConfig getFilesystem() {
        return filesystem;
    }

    /** Java MCP server configuration (VM platform). */
    public JavaConfig getJava() {
        return java;
    }

    // -------------------------------------------------------------------------
    // Kubernetes
    // -------------------------------------------------------------------------

    /**
     * Kubernetes MCP server configuration snapshot.
     */
    public static final class KubernetesConfig {

        private final String endpoint;
        private final String healthPath;
        private final String timeout;

        KubernetesConfig(Map<String, String> cache) {
            this.endpoint   = cache.get(ConfigConstants.MCP.Kubernetes.ENDPOINT);
            this.healthPath = cache.get(ConfigConstants.MCP.Kubernetes.HEALTH_PATH);
            this.timeout    = cache.get(ConfigConstants.MCP.Kubernetes.TIMEOUT);
        }

        /** Kubernetes MCP server endpoint URL. */
        public String getEndpoint() {
            return endpoint != null ? endpoint : "";
        }

        /** Health check path; defaults to {@code "/healthz"} if not set. */
        public String getHealthPath() {
            return healthPath != null ? healthPath : "/healthz";
        }

        /** HTTP timeout in milliseconds; defaults to {@code 5000} if not set. */
        public int getTimeoutMs() {
            return timeout != null ? Integer.parseInt(timeout) : 5000;
        }
    }

    // -------------------------------------------------------------------------
    // Kruize
    // -------------------------------------------------------------------------

    /**
     * Kruize MCP server configuration snapshot.
     */
    public static final class KruizeConfig {

        private final String endpoint;
        private final String healthPath;
        private final String timeout;

        KruizeConfig(Map<String, String> cache) {
            this.endpoint   = cache.get(ConfigConstants.MCP.Kruize.ENDPOINT);
            this.healthPath = cache.get(ConfigConstants.MCP.Kruize.HEALTH_PATH);
            this.timeout    = cache.get(ConfigConstants.MCP.Kruize.TIMEOUT);
        }

        /** Kruize MCP server endpoint URL. */
        public String getEndpoint() {
            return endpoint != null ? endpoint : "";
        }

        /** Health check path; defaults to {@code "/q/health/ready"} if not set. */
        public String getHealthPath() {
            return healthPath != null ? healthPath : "/q/health/ready";
        }

        /** HTTP timeout in milliseconds; defaults to {@code 10000} if not set. */
        public int getTimeoutMs() {
            return timeout != null ? Integer.parseInt(timeout) : 10000;
        }
    }

    // -------------------------------------------------------------------------
    // Cryostat
    // -------------------------------------------------------------------------

    /**
     * Cryostat MCP server configuration snapshot.
     */
    public static final class CryostatConfig {

        private final String endpoint;
        private final String healthEndpoint;
        private final String healthPath;
        private final String timeout;
        private final String retryDelay;
        private final String maxRetries;

        CryostatConfig(Map<String, String> cache) {
            this.endpoint       = cache.get(ConfigConstants.MCP.Cryostat.ENDPOINT);
            this.healthEndpoint = cache.get(ConfigConstants.MCP.Cryostat.HEALTH_ENDPOINT);
            this.healthPath     = cache.get(ConfigConstants.MCP.Cryostat.HEALTH_PATH);
            this.timeout        = cache.get(ConfigConstants.MCP.Cryostat.TIMEOUT);
            this.retryDelay     = cache.get(ConfigConstants.MCP.Cryostat.RETRY_DELAY);
            this.maxRetries     = cache.get(ConfigConstants.MCP.Cryostat.MAX_RETRIES);
        }

        /** Cryostat MCP server endpoint URL (port 8000). */
        public String getEndpoint() {
            return endpoint != null ? endpoint : "";
        }

        /** Cryostat health check endpoint URL (port 8080). */
        public String getHealthEndpoint() {
            return healthEndpoint != null ? healthEndpoint : "";
        }

        /** Health check path; defaults to {@code "/healthz"} if not set. */
        public String getHealthPath() {
            return healthPath != null ? healthPath : "/healthz";
        }

        /** HTTP timeout in milliseconds; defaults to {@code 15000} if not set. */
        public int getTimeoutMs() {
            return timeout != null ? Integer.parseInt(timeout) : 15000;
        }

        /** Retry delay in milliseconds; defaults to {@code 5000} if not set. */
        public int getRetryDelayMs() {
            return retryDelay != null ? Integer.parseInt(retryDelay) : 5000;
        }

        /** Maximum retry attempts; defaults to {@code 3} if not set. */
        public int getMaxRetries() {
            return maxRetries != null ? Integer.parseInt(maxRetries) : 3;
        }
    }

    // -------------------------------------------------------------------------
    // Filesystem (VM platform)
    // -------------------------------------------------------------------------

    /**
     * Filesystem MCP server configuration snapshot (VM platform).
     */
    public static final class FilesystemConfig {

        private final String endpoint;
        private final String healthPath;
        private final String timeout;

        FilesystemConfig(Map<String, String> cache) {
            this.endpoint   = cache.get(ConfigConstants.MCP.Filesystem.ENDPOINT);
            this.healthPath = cache.get(ConfigConstants.MCP.Filesystem.HEALTH_PATH);
            this.timeout    = cache.get(ConfigConstants.MCP.Filesystem.TIMEOUT);
        }

        /** Filesystem MCP server endpoint URL. */
        public String getEndpoint() {
            return endpoint != null ? endpoint : "";
        }

        /** Health check path; defaults to {@code "/healthz"} if not set. */
        public String getHealthPath() {
            return healthPath != null ? healthPath : "/healthz";
        }

        /** HTTP timeout in milliseconds; defaults to {@code 10000} if not set. */
        public int getTimeoutMs() {
            return timeout != null ? Integer.parseInt(timeout) : 10000;
        }
    }

    // -------------------------------------------------------------------------
    // Java (VM platform)
    // -------------------------------------------------------------------------

    /**
     * Java MCP server configuration snapshot (VM platform).
     */
    public static final class JavaConfig {

        private final String endpoint;
        private final String healthPath;
        private final String timeout;

        JavaConfig(Map<String, String> cache) {
            this.endpoint   = cache.get(ConfigConstants.MCP.Java.ENDPOINT);
            this.healthPath = cache.get(ConfigConstants.MCP.Java.HEALTH_PATH);
            this.timeout    = cache.get(ConfigConstants.MCP.Java.TIMEOUT);
        }

        /** Java MCP server endpoint URL. */
        public String getEndpoint() {
            return endpoint != null ? endpoint : "";
        }

        /** Health check path; defaults to {@code "/healthz"} if not set. */
        public String getHealthPath() {
            return healthPath != null ? healthPath : "/healthz";
        }

        /** HTTP timeout in milliseconds; defaults to {@code 10000} if not set. */
        public int getTimeoutMs() {
            return timeout != null ? Integer.parseInt(timeout) : 10000;
        }
    }
}
