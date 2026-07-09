package com.causa.config;

import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.services.ConfigService;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Config Startup Handler
 *
 * <p>Observes {@link StartupEvent} at priority
 * {@link AppConstants.StartupConstants#CONFIG_PRIORITY} (after the DB pool
 * is verified at priority {@link AppConstants.StartupConstants#DATABASE_PRIORITY})
 * to bootstrap the in-memory configuration cache.
 *
 * <p>Startup sequence:
 * <ol>
 *   <li>Priority 5  — {@code DatabaseConnectionService} verifies the pool is live.</li>
 *   <li>Priority 10 — <b>This class</b> loads configs from DB, falls back to ENV vars.</li>
 *   <li>Priority 20 — {@code LLMStartup} initialises the LLM using the loaded config.</li>
 * </ol>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigStartup {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigStartup.class);

    private final ConfigService configService;

    @Inject
    public ConfigStartup(ConfigService configService) {
        this.configService = configService;
    }

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.CONFIG_PRIORITY) StartupEvent event) {
        try {
            configService.loadFromDbAndEnv();
        } catch (Exception e) {
            // Non-fatal: application starts but configs may be missing.
            // Individual consumers handle null/empty values gracefully.
            log.warn(LogMessages.Config.LOAD_FAILED)
                    .exception(e)
                    .log();
        }
    }
}
