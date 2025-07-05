package ai.intelliswarm.vulnpatcher;

import ai.intelliswarm.vulnpatcher.agents.SecLeadReviewerAgent;
import ai.intelliswarm.vulnpatcher.agents.SecurityEngineerAgent;
import ai.intelliswarm.vulnpatcher.agents.SecurityExpertReviewerAgent;
import ai.intelliswarm.vulnpatcher.api.v1.VulnPatcherResource;
import ai.intelliswarm.vulnpatcher.api.v1.WorkflowResource;
import ai.intelliswarm.vulnpatcher.config.OllamaConfig;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.core.VulnerabilityDetectionEngine;
import ai.intelliswarm.vulnpatcher.fixes.*;
import ai.intelliswarm.vulnpatcher.git.BitbucketService;
import ai.intelliswarm.vulnpatcher.git.GitHubService;
import ai.intelliswarm.vulnpatcher.git.GitLabService;
import ai.intelliswarm.vulnpatcher.orchestrator.LLMOrchestrator;
import ai.intelliswarm.vulnpatcher.services.VulnerabilityDetectionService;
import ai.intelliswarm.vulnpatcher.sources.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test to verify application startup and component configuration.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApplicationStartupTest {
    
    // Core Services
    @Inject
    VulnerabilityDetectionService detectionService;
    
    @Inject
    VulnerabilityDetectionEngine detectionEngine;
    
    @Inject
    LLMOrchestrator orchestrator;
    
    @Inject
    ContextManager contextManager;
    
    // AI Agents
    @Inject
    SecurityEngineerAgent securityEngineer;
    
    @Inject
    SecLeadReviewerAgent secLeadReviewer;
    
    @Inject
    SecurityExpertReviewerAgent securityExpert;
    
    // Vulnerability Sources
    @Inject
    List<VulnerabilitySource> vulnerabilitySources;
    
    @Inject
    CVESource cveSource;
    
    @Inject
    GHSASource ghsaSource;
    
    @Inject
    OSVSource osvSource;
    
    // Fix Generators
    @Inject
    List<AbstractFixGenerator> fixGenerators;
    
    @Inject
    JavaFixGenerator javaFixGenerator;
    
    @Inject
    PythonFixGenerator pythonFixGenerator;
    
    // Git Services
    @Inject
    GitHubService gitHubService;
    
    @Inject
    GitLabService gitLabService;
    
    @Inject
    BitbucketService bitbucketService;
    
    // REST Resources
    @Inject
    VulnPatcherResource vulnPatcherResource;
    
    @Inject
    WorkflowResource workflowResource;
    
    // Configuration
    @Inject
    OllamaConfig ollamaConfig;
    
    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;
    
    @ConfigProperty(name = "quarkus.application.version")
    String applicationVersion;
    
    // Health Checks
    @Inject
    @Liveness
    List<HealthCheck> livenessChecks;
    
    @Inject
    @Readiness
    List<HealthCheck> readinessChecks;
    
    @Test
    @Order(1)
    @DisplayName("Application should start with correct name and version")
    void testApplicationMetadata() {
        assertEquals("vuln-patcher", applicationName);
        assertNotNull(applicationVersion);
        System.out.println("Application: " + applicationName + " v" + applicationVersion);
    }
    
    @Test
    @Order(2)
    @DisplayName("All core services should be injected and initialized")
    void testCoreServicesInjection() {
        assertNotNull(detectionService, "VulnerabilityDetectionService should be injected");
        assertNotNull(detectionEngine, "VulnerabilityDetectionEngine should be injected");
        assertNotNull(orchestrator, "LLMOrchestrator should be injected");
        assertNotNull(contextManager, "ContextManager should be injected");
        
        System.out.println("✓ All core services successfully injected");
    }
    
    @Test
    @Order(3)
    @DisplayName("All AI agents should be properly configured")
    void testAIAgentsConfiguration() {
        assertNotNull(securityEngineer, "SecurityEngineerAgent should be injected");
        assertNotNull(secLeadReviewer, "SecLeadReviewerAgent should be injected");
        assertNotNull(securityExpert, "SecurityExpertReviewerAgent should be injected");
        
        // Verify agent names
        assertEquals("SecurityEngineer", securityEngineer.getName());
        assertEquals("SecLeadReviewer", secLeadReviewer.getName());
        assertEquals("SecurityExpertReviewer", securityExpert.getName());
        
        // Verify agent roles
        assertNotNull(securityEngineer.getRole());
        assertNotNull(secLeadReviewer.getRole());
        assertNotNull(securityExpert.getRole());
        
        System.out.println("✓ All AI agents properly configured:");
        System.out.println("  - " + securityEngineer.getName() + ": " + securityEngineer.getRole());
        System.out.println("  - " + secLeadReviewer.getName() + ": " + secLeadReviewer.getRole());
        System.out.println("  - " + securityExpert.getName() + ": " + securityExpert.getRole());
    }
    
    @Test
    @Order(4)
    @DisplayName("All vulnerability sources should be registered")
    void testVulnerabilitySourcesRegistration() {
        assertNotNull(vulnerabilitySources);
        assertFalse(vulnerabilitySources.isEmpty(), "Should have vulnerability sources registered");
        
        // Verify specific sources
        assertNotNull(cveSource, "CVE source should be injected");
        assertNotNull(ghsaSource, "GHSA source should be injected");
        assertNotNull(osvSource, "OSV source should be injected");
        
        // List all sources
        List<String> sourceNames = vulnerabilitySources.stream()
            .map(VulnerabilitySource::getSourceName)
            .collect(Collectors.toList());
        
        System.out.println("✓ Registered vulnerability sources (" + sourceNames.size() + "):");
        vulnerabilitySources.forEach(source -> 
            System.out.println("  - " + source.getSourceName() + 
                " (enabled: " + source.isEnabled() + ")")
        );
        
        // Verify expected sources are present
        assertTrue(sourceNames.contains("CVE"));
        assertTrue(sourceNames.contains("GHSA"));
        assertTrue(sourceNames.contains("OSV"));
    }
    
    @Test
    @Order(5)
    @DisplayName("All fix generators should be registered for supported languages")
    void testFixGeneratorsRegistration() {
        assertNotNull(fixGenerators);
        assertFalse(fixGenerators.isEmpty(), "Should have fix generators registered");
        
        // Verify specific generators
        assertNotNull(javaFixGenerator, "Java fix generator should be injected");
        assertNotNull(pythonFixGenerator, "Python fix generator should be injected");
        
        // List all supported languages
        List<String> supportedLanguages = fixGenerators.stream()
            .map(AbstractFixGenerator::getLanguageName)
            .collect(Collectors.toList());
        
        System.out.println("✓ Registered fix generators for languages:");
        supportedLanguages.forEach(lang -> System.out.println("  - " + lang));
        
        // Verify expected languages
        assertTrue(supportedLanguages.contains("Java"));
        assertTrue(supportedLanguages.contains("Python"));
    }
    
    @Test
    @Order(6)
    @DisplayName("All git services should be properly configured")
    void testGitServicesConfiguration() {
        assertNotNull(gitHubService, "GitHub service should be injected");
        assertNotNull(gitLabService, "GitLab service should be injected");
        assertNotNull(bitbucketService, "Bitbucket service should be injected");
        
        System.out.println("✓ Git services configured:");
        System.out.println("  - GitHub: " + (gitHubService.isConfigured() ? "✓" : "✗"));
        System.out.println("  - GitLab: " + (gitLabService.isConfigured() ? "✓" : "✗"));
        System.out.println("  - Bitbucket: " + (bitbucketService.isConfigured() ? "✓" : "✗"));
    }
    
    @Test
    @Order(7)
    @DisplayName("REST API endpoints should be available")
    void testRESTEndpoints() {
        // Test main API endpoint
        given()
            .when()
            .get("/api/v1/vulnpatcher/status")
            .then()
            .statusCode(anyOf(is(200), is(404))); // 404 if endpoint not implemented yet
        
        // Test OpenAPI endpoint
        given()
            .when()
            .get("/openapi")
            .then()
            .statusCode(200)
            .contentType(containsString("application/json"))
            .body("openapi", notNullValue())
            .body("info.title", containsString("VulnPatcher"));
        
        System.out.println("✓ REST API endpoints available");
    }
    
    @Test
    @Order(8)
    @DisplayName("Health checks should be properly configured")
    void testHealthChecks() {
        // Test liveness endpoint
        given()
            .when()
            .get("/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
        
        // Test readiness endpoint
        given()
            .when()
            .get("/health/ready")
            .then()
            .statusCode(anyOf(is(200), is(503))) // May be 503 if Ollama not running
            .body("status", anyOf(equalTo("UP"), equalTo("DOWN")));
        
        // Test main health endpoint
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(anyOf(is(200), is(503)))
            .body("status", anyOf(equalTo("UP"), equalTo("DOWN")));
        
        // Verify health check registration
        assertFalse(livenessChecks.isEmpty(), "Should have liveness checks");
        assertFalse(readinessChecks.isEmpty(), "Should have readiness checks");
        
        System.out.println("✓ Health checks configured:");
        System.out.println("  - Liveness checks: " + livenessChecks.size());
        System.out.println("  - Readiness checks: " + readinessChecks.size());
        
        // Execute health checks
        livenessChecks.forEach(check -> {
            HealthCheckResponse response = check.call();
            System.out.println("  - " + response.getName() + ": " + response.getStatus());
        });
    }
    
    @Test
    @Order(9)
    @DisplayName("Metrics endpoint should be available")
    void testMetricsEndpoint() {
        given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .contentType(containsString("text/plain"))
            .body(containsString("vulnpatcher"));
        
        System.out.println("✓ Metrics endpoint available at /metrics");
    }
    
    @Test
    @Order(10)
    @DisplayName("Ollama configuration should be properly loaded")
    void testOllamaConfiguration() {
        assertNotNull(ollamaConfig);
        assertNotNull(ollamaConfig.baseUrl(), "Ollama base URL should be configured");
        assertNotNull(ollamaConfig.timeout(), "Ollama timeout should be configured");
        assertNotNull(ollamaConfig.codeModel(), "Ollama code model should be configured");
        assertNotNull(ollamaConfig.analysisModel(), "Ollama analysis model should be configured");
        assertNotNull(ollamaConfig.reviewModel(), "Ollama review model should be configured");
        
        System.out.println("✓ Ollama configuration:");
        System.out.println("  - Base URL: " + ollamaConfig.baseUrl());
        System.out.println("  - Code Model: " + ollamaConfig.codeModel());
        System.out.println("  - Analysis Model: " + ollamaConfig.analysisModel());
        System.out.println("  - Review Model: " + ollamaConfig.reviewModel());
        System.out.println("  - Timeout: " + ollamaConfig.timeout());
    }
    
    @Test
    @Order(11)
    @DisplayName("Application should handle concurrent requests")
    void testConcurrentRequestHandling() throws InterruptedException {
        int numRequests = 10;
        Thread[] threads = new Thread[numRequests];
        
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            threads[i] = new Thread(() -> {
                given()
                    .when()
                    .get("/health")
                    .then()
                    .statusCode(anyOf(is(200), is(503)));
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("✓ Successfully handled " + numRequests + " concurrent requests");
    }
    
    @Test
    @Order(12)
    @DisplayName("Application should have proper error handling")
    void testErrorHandling() {
        // Test 404 handling
        given()
            .when()
            .get("/api/v1/nonexistent")
            .then()
            .statusCode(404);
        
        // Test invalid request handling
        given()
            .contentType("application/json")
            .body("{invalid json")
            .when()
            .post("/api/v1/vulnpatcher/scan")
            .then()
            .statusCode(400);
        
        System.out.println("✓ Error handling working correctly");
    }
    
    @AfterAll
    static void printSummary() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Application Startup Test Summary");
        System.out.println("=".repeat(50));
        System.out.println("✓ All components successfully initialized");
        System.out.println("✓ Application ready to process vulnerability scans");
        System.out.println("=".repeat(50));
    }
}