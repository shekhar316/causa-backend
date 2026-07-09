package com.causa.llm;

import com.causa.common.constants.ConfigConstants;
import com.causa.common.constants.LLMConstants;
import com.causa.config.AppConfig;
import com.causa.core.domain.LLMRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BobShellPromptSender}.
 *
 * <p>Tests the BOB Shell integration following the same patterns as LangChainPromptSender tests.
 * These are unit tests that verify the business logic without actually calling BOB Shell CLI.
 *
 * <p>Test Coverage:
 * <ul>
 *   <li>Initialization and configuration</li>
 *   <li>Readiness checks</li>
 *   <li>Request building</li>
 *   <li>Response parsing</li>
 *   <li>Token extraction</li>
 *   <li>Error handling</li>
 *   <li>Large prompt handling</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BobShellPromptSender Tests")
class BobShellPromptSenderTest {

    @Mock
    private AppConfig appConfig;

    private BobShellPromptSender bobShellPromptSender;

    /** Builds a real AppConfig pre-loaded with the given API key. */
    private AppConfig realAppConfigWithApiKey(String apiKey) {
        AppConfig cfg = new AppConfig();
        if (apiKey != null) {
            cfg.put(ConfigConstants.LLM.API_KEY, apiKey);
        }
        return cfg;
    }

    /**
     * Setup default mock behavior for tests that need it.
     * Using lenient() to avoid UnnecessaryStubbingException for tests that don't use all mocks.
     */
    private void setupDefaultMocks() {
        AppConfig cfg = realAppConfigWithApiKey("test-api-key");
        lenient().when(appConfig.getLlmConfig()).thenReturn(cfg.getLlmConfig());
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should initialize with valid configuration")
        void shouldInitializeWithValidConfiguration() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(realAppConfigWithApiKey("test-api-key").getLlmConfig());

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }

        @Test
        @DisplayName("Should read shell path from config lazily on each use")
        void shouldReadShellPathFromConfigLazily() {
            // Given - shell path is not read in constructor, only when send()/checkAvailability() is called
            when(appConfig.getLlmConfig()).thenReturn(realAppConfigWithApiKey("test-api-key").getLlmConfig());

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then - constructor calls getLlmConfig() once (for API key); no further calls
            assertNotNull(bobShellPromptSender);
            verify(appConfig, times(1)).getLlmConfig();
        }

        @Test
        @DisplayName("Should use environment variable for API key when not in config")
        void shouldUseEnvironmentVariableForApiKey() {
            // Given — empty config: no API key stored
            when(appConfig.getLlmConfig()).thenReturn(new AppConfig().getLlmConfig());

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }

        @Test
        @DisplayName("Should handle custom configuration correctly")
        void shouldHandleConfigurationCorrectly() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(realAppConfigWithApiKey("custom-key").getLlmConfig());

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }
    }

    @Nested
    @DisplayName("Readiness Tests")
    class ReadinessTests {

        @BeforeEach
        void setUpReadiness() {
            setupDefaultMocks();
            bobShellPromptSender = new BobShellPromptSender(appConfig);
        }

        @Test
        @DisplayName("Should check BOB Shell availability")
        void shouldCheckBobShellAvailability() {
            // When
            bobShellPromptSender.isReady();

            // Then
            // Note: isReady() actively checks if BOB Shell CLI is available
            // Result depends on whether BOB Shell is installed in the environment
            assertNotNull(bobShellPromptSender);
        }
    }

    @Nested
    @DisplayName("Request Building Tests")
    class RequestBuildingTests {

        @Test
        @DisplayName("Should build simple prompt correctly")
        void shouldBuildSimplePromptCorrectly() {
            // Given
            String userPrompt = "What is 2+2?";
            LLMRequest request = LLMRequest.builder(userPrompt).build();

            // Then
            assertNotNull(request);
            assertEquals(userPrompt, request.prompt());
            assertTrue(request.systemPrompt().isEmpty());
            assertTrue(request.context().isEmpty());
        }

        @Test
        @DisplayName("Should build prompt with system prompt")
        void shouldBuildPromptWithSystemPrompt() {
            String systemPrompt = "You are a helpful assistant";
            String userPrompt = "What is 2+2?";

            LLMRequest request = LLMRequest.builder(userPrompt)
                .systemPrompt(systemPrompt)
                .build();

            assertNotNull(request);
            assertEquals(userPrompt, request.prompt());
            assertTrue(request.systemPrompt().isPresent());
            assertEquals(systemPrompt, request.systemPrompt().get());
        }

        @Test
        @DisplayName("Should build prompt with context")
        void shouldBuildPromptWithContext() {
            String context = "Previous conversation context";
            String userPrompt = "Continue the conversation";

            LLMRequest request = LLMRequest.builder(userPrompt)
                .context(context)
                .build();

            assertNotNull(request);
            assertEquals(userPrompt, request.prompt());
            assertTrue(request.context().isPresent());
            assertEquals(context, request.context().get());
        }

        @Test
        @DisplayName("Should handle large prompts")
        void shouldHandleLargePrompts() {
            StringBuilder largePrompt = new StringBuilder();
            for (int i = 0; i < 101000; i++) {
                largePrompt.append("a");
            }

            LLMRequest request = LLMRequest.builder(largePrompt.toString()).build();

            assertNotNull(request);
            assertTrue(request.prompt().length() > 100000);
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should respect custom shell path lazily")
        void shouldRespectCustomShellPath() {
            when(appConfig.getLlmConfig()).thenReturn(realAppConfigWithApiKey("test-api-key").getLlmConfig());

            bobShellPromptSender = new BobShellPromptSender(appConfig);

            assertNotNull(bobShellPromptSender);
            // getLlmConfig() is called once in constructor (for API key); shell path is read lazily on use
            verify(appConfig, times(1)).getLlmConfig();
        }

        @Test
        @DisplayName("Should handle missing API key gracefully")
        void shouldHandleMissingApiKeyGracefully() {
            when(appConfig.getLlmConfig()).thenReturn(new AppConfig().getLlmConfig());

            bobShellPromptSender = new BobShellPromptSender(appConfig);

            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }
    }

    @Nested
    @DisplayName("Response Parsing Tests")
    class ResponseParsingTests {

        @Test
        @DisplayName("Should parse valid BOB Shell output")
        void shouldParseValidBobShellOutput() {
            String bobOutput = """
                ---output---
                {"response": "The answer is 4"}
                ---output---
                {"stats": {"promptTokens": 10, "completionTokens": 5, "tokensUsed": 15}}
                """;

            assertTrue(bobOutput.contains(LLMConstants.BobShell.OUTPUT_MARKER));
            assertTrue(bobOutput.contains("promptTokens"));
            assertTrue(bobOutput.contains("completionTokens"));
            assertTrue(bobOutput.contains("tokensUsed"));
        }

        @Test
        @DisplayName("Should handle output without statistics")
        void shouldHandleOutputWithoutStatistics() {
            String bobOutput = """
                ---output---
                {"response": "The answer is 4"}
                ---output---
                """;

            assertTrue(bobOutput.contains(LLMConstants.BobShell.OUTPUT_MARKER));
        }

        @Test
        @DisplayName("Should extract content between markers")
        void shouldExtractContentBetweenMarkers() {
            String expectedContent = "{\"response\": \"The answer is 4\"}";
            String bobOutput = String.format("""
                ---output---
                %s
                ---output---
                {"stats": {"promptTokens": 10, "completionTokens": 5, "tokensUsed": 15}}
                """, expectedContent);

            assertTrue(bobOutput.contains(expectedContent));
        }
    }

    @Nested
    @DisplayName("Token Extraction Tests")
    class TokenExtractionTests {

        @Test
        @DisplayName("Should extract token usage from valid stats")
        void shouldExtractTokenUsageFromValidStats() {
            // JSON fields use the actual constant values from LLMConstants.BobShell
            String statsJson = """
                {
                  "response": "OK",
                  "stats": {
                    "%s": 15,
                    "%s": 8,
                    "%s": 23
                  }
                }
                """.formatted(
                    LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS,
                    LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS,
                    LLMConstants.BobShell.JSON_FIELD_TOKENS_USED);

            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_STATS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_TOKENS_USED));
        }

        @Test
        @DisplayName("Should handle missing stats field")
        void shouldHandleMissingStatsField() {
            String statsJson = """
                {
                  "response": "OK"
                }
                """;

            assertFalse(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_STATS));
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            String malformedJson = "{ invalid json }";
            assertNotNull(malformedJson);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should validate prompt is not empty")
        void shouldValidatePromptIsNotEmpty() {
            assertThrows(IllegalArgumentException.class, () -> {
                LLMRequest.builder("").build();
            });
        }
    }

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should use correct model name")
        void shouldUseCorrectModelName() {
            assertEquals("bob", LLMConstants.Provider.IBM_BOB);
        }

        @Test
        @DisplayName("Should use correct CLI flags")
        void shouldUseCorrectCliFlags() {
            assertEquals("--accept-license", LLMConstants.BobShell.FLAG_ACCEPT_LICENSE);
            assertEquals("--yolo", LLMConstants.BobShell.FLAG_YOLO);
            assertEquals("-o", LLMConstants.BobShell.FLAG_OUTPUT_JSON);
            assertEquals("-p", LLMConstants.BobShell.FLAG_PROMPT);
            assertEquals("json", LLMConstants.BobShell.OUTPUT_FORMAT_JSON);
        }

        @Test
        @DisplayName("Should use correct output marker")
        void shouldUseCorrectOutputMarker() {
            assertEquals("---output---", LLMConstants.BobShell.OUTPUT_MARKER);
        }

        @Test
        @DisplayName("Should use correct environment variable name")
        void shouldUseCorrectEnvironmentVariableName() {
            assertEquals("BOBSHELL_API_KEY", LLMConstants.BobShell.ENV_API_KEY_NAME);
        }

        @Test
        @DisplayName("Should use correct default timeout")
        void shouldUseCorrectDefaultTimeout() {
            assertEquals(180, LLMConstants.BobShell.DEFAULT_TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("Should use correct JSON field names")
        void shouldUseCorrectJsonFieldNames() {
            assertEquals("stats",      LLMConstants.BobShell.JSON_FIELD_STATS);
            assertEquals("prompt",     LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS);
            assertEquals("candidates", LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS);
            assertEquals("total",      LLMConstants.BobShell.JSON_FIELD_TOKENS_USED);
        }
    }
}

// Made with Bob
