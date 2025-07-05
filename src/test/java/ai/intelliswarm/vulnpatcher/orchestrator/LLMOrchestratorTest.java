package ai.intelliswarm.vulnpatcher.orchestrator;

import ai.intelliswarm.vulnpatcher.agents.SecurityEngineerAgent;
import ai.intelliswarm.vulnpatcher.agents.SecurityExpertReviewerAgent;
import ai.intelliswarm.vulnpatcher.agents.SecLeadReviewerAgent;
import ai.intelliswarm.vulnpatcher.config.MetricsConfig;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
public class LLMOrchestratorTest {
    
    @Inject
    LLMOrchestrator orchestrator;
    
    @InjectMock
    OllamaChatModel orchestratorModel;
    
    @InjectMock
    SecurityEngineerAgent securityEngineer;
    
    @InjectMock
    SecLeadReviewerAgent secLeadReviewer;
    
    @InjectMock
    SecurityExpertReviewerAgent securityExpert;
    
    @InjectMock
    ContextManager contextManager;
    
    @InjectMock
    MetricsConfig.MetricsService metricsService;
    
    private ScanResult.VulnerabilityMatch testVulnerability;
    private LLMOrchestrator.WorkflowContext testContext;
    
    @BeforeEach
    void setUp() {
        // Create test vulnerability
        Vulnerability vuln = new Vulnerability();
        vuln.setId("CVE-2023-12345");
        vuln.setTitle("SQL Injection");
        vuln.setSeverity("HIGH");
        vuln.setDescription("SQL injection vulnerability in user input");
        
        testVulnerability = new ScanResult.VulnerabilityMatch();
        testVulnerability.setVulnerability(vuln);
        testVulnerability.setFilePath("/src/main/java/UserService.java");
        testVulnerability.setLineNumber(42);
        testVulnerability.setAffectedCode("String query = \"SELECT * FROM users WHERE id = \" + userId;");
        testVulnerability.setConfidence(0.95);
        
        // Create test context
        testContext = new LLMOrchestrator.WorkflowContext();
        testContext.setRepositoryUrl("https://github.com/test/repo");
        testContext.setLanguage("Java");
        testContext.setFramework("Spring Boot");
        testContext.setAdditionalContext(Map.of("version", "2.7.0"));
    }
    
    @Test
    @DisplayName("Should successfully orchestrate vulnerability fix workflow")
    void testOrchestrateVulnerabilityFix() {
        // Mock LLM responses
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Task plan created"));
        
        // Mock agent responses
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Secure fix generated"));
        when(secLeadReviewer.reviewCode(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Code reviewed"));
        when(securityExpert.analyzeSecurityImplications(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture("Security validated"));
        
        // Execute workflow
        Uni<LLMOrchestrator.WorkflowResult> result = orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext);
        
        // Assert
        UniAssertSubscriber<LLMOrchestrator.WorkflowResult> subscriber = result
            .subscribe().withSubscriber(UniAssertSubscriber.create());
        
        LLMOrchestrator.WorkflowResult workflowResult = subscriber.awaitItem().getItem();
        
        assertNotNull(workflowResult);
        assertNotNull(workflowResult.getWorkflowId());
        assertTrue(workflowResult.isSuccess());
        assertEquals("CVE-2023-12345", workflowResult.getVulnerabilityId());
        assertNotNull(workflowResult.getFinalSolution());
        assertNotNull(workflowResult.getRecommendations());
        assertFalse(workflowResult.getRecommendations().isEmpty());
        assertNotNull(workflowResult.getCompletedAt());
        
        // Verify agent interactions
        verify(securityEngineer, atLeastOnce()).generateSecureFix(anyString(), anyString(), anyString());
        verify(secLeadReviewer, atLeastOnce()).reviewCode(anyString(), anyString());
        verify(securityExpert, atLeastOnce()).analyzeSecurityImplications(anyString(), any());
    }
    
    @Test
    @DisplayName("Should handle workflow failure gracefully")
    void testOrchestrateVulnerabilityFixFailure() {
        // Mock agent failure
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Agent error")));
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Task plan created"));
        
        // Execute workflow
        Uni<LLMOrchestrator.WorkflowResult> result = orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext);
        
        // Assert
        UniAssertSubscriber<LLMOrchestrator.WorkflowResult> subscriber = result
            .subscribe().withSubscriber(UniAssertSubscriber.create());
        
        // Should still complete but with error status
        assertDoesNotThrow(() -> subscriber.awaitItem());
    }
    
    @Test
    @DisplayName("Should stream workflow events correctly")
    void testStreamWorkflowEvents() {
        // First start a workflow to create events
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Task plan created"));
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Fix generated"));
        
        Uni<LLMOrchestrator.WorkflowResult> workflowUni = orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext);
        LLMOrchestrator.WorkflowResult result = workflowUni.await().indefinitely();
        
        // Now stream events
        Multi<LLMOrchestrator.WorkflowEvent> events = orchestrator.streamWorkflowEvents(result.getWorkflowId());
        
        // Assert
        AssertSubscriber<LLMOrchestrator.WorkflowEvent> subscriber = events
            .subscribe().withSubscriber(AssertSubscriber.create(10));
        
        List<LLMOrchestrator.WorkflowEvent> eventList = subscriber.awaitCompletion().getItems();
        
        assertFalse(eventList.isEmpty());
        assertTrue(eventList.stream().anyMatch(e -> "STARTED".equals(e.getType())));
        assertTrue(eventList.stream().anyMatch(e -> e.getMessage().contains("Workflow started")));
    }
    
    @Test
    @DisplayName("Should handle invalid workflow ID in stream")
    void testStreamWorkflowEventsInvalidId() {
        Multi<LLMOrchestrator.WorkflowEvent> events = orchestrator.streamWorkflowEvents("invalid-id");
        
        AssertSubscriber<LLMOrchestrator.WorkflowEvent> subscriber = events
            .subscribe().withSubscriber(AssertSubscriber.create());
        
        subscriber.awaitFailure();
        assertNotNull(subscriber.getFailure());
        assertTrue(subscriber.getFailure().getMessage().contains("Workflow not found"));
    }
    
    @Test
    @DisplayName("Should build consensus correctly")
    void testConsensusBuilding() {
        // Setup mocks for successful consensus
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Refined solution"));
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Fix with high confidence"));
        when(secLeadReviewer.reviewCode(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Approved"));
        when(securityExpert.analyzeSecurityImplications(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture("Secure"));
        
        Uni<LLMOrchestrator.WorkflowResult> result = orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext);
        LLMOrchestrator.WorkflowResult workflowResult = result.await().indefinitely();
        
        assertTrue(workflowResult.isSuccess());
        assertTrue(workflowResult.getConfidence() > 0);
    }
    
    @Test
    @DisplayName("Should generate appropriate recommendations based on severity")
    void testRecommendationsGeneration() {
        // Test with CRITICAL severity
        testVulnerability.getVulnerability().setSeverity("CRITICAL");
        
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Task plan"));
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Critical fix"));
        when(secLeadReviewer.reviewCode(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Approved"));
        when(securityExpert.analyzeSecurityImplications(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture("Validated"));
        
        Uni<LLMOrchestrator.WorkflowResult> result = orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext);
        LLMOrchestrator.WorkflowResult workflowResult = result.await().indefinitely();
        
        assertNotNull(workflowResult.getRecommendations());
        assertTrue(workflowResult.getRecommendations().stream()
            .anyMatch(r -> r.contains("Priority fix")));
    }
    
    @Test
    @DisplayName("Should handle multiple concurrent workflows")
    void testConcurrentWorkflows() {
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Task plan"));
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Fix"));
        when(secLeadReviewer.reviewCode(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Reviewed"));
        when(securityExpert.analyzeSecurityImplications(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture("Validated"));
        
        // Start multiple workflows
        List<Uni<LLMOrchestrator.WorkflowResult>> workflows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            workflows.add(orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext));
        }
        
        // Wait for all to complete
        List<LLMOrchestrator.WorkflowResult> results = Uni.join().all(workflows)
            .andFailFast()
            .await().indefinitely();
        
        assertEquals(5, results.size());
        assertTrue(results.stream().allMatch(LLMOrchestrator.WorkflowResult::isSuccess));
        
        // All should have unique workflow IDs
        Set<String> workflowIds = new HashSet<>();
        results.forEach(r -> workflowIds.add(r.getWorkflowId()));
        assertEquals(5, workflowIds.size());
    }
    
    @Test
    @DisplayName("Should validate workflow context properly")
    void testWorkflowContextValidation() {
        // Test with null context fields
        LLMOrchestrator.WorkflowContext invalidContext = new LLMOrchestrator.WorkflowContext();
        
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Task plan"));
        
        // Should still handle gracefully
        Uni<LLMOrchestrator.WorkflowResult> result = orchestrator.orchestrateVulnerabilityFix(testVulnerability, invalidContext);
        
        assertDoesNotThrow(() -> result.await().indefinitely());
    }
    
    @Test
    @DisplayName("Should track workflow iterations correctly")
    void testWorkflowIterations() {
        when(orchestratorModel.generate(anyList()))
            .thenReturn(AiMessage.from("Task plan"));
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Fix"));
        when(secLeadReviewer.reviewCode(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Reviewed"));
        when(securityExpert.analyzeSecurityImplications(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture("Validated"));
        
        Uni<LLMOrchestrator.WorkflowResult> result = orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext);
        LLMOrchestrator.WorkflowResult workflowResult = result.await().indefinitely();
        
        assertTrue(workflowResult.getIterations() >= 0);
    }
    
    @Test
    @DisplayName("Should capture LLM prompts correctly")
    void testLLMPromptGeneration() {
        ArgumentCaptor<List<dev.langchain4j.data.message.ChatMessage>> promptCaptor = 
            ArgumentCaptor.forClass(List.class);
        
        when(orchestratorModel.generate(promptCaptor.capture()))
            .thenReturn(AiMessage.from("Task plan"));
        when(securityEngineer.generateSecureFix(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture("Fix"));
        
        orchestrator.orchestrateVulnerabilityFix(testVulnerability, testContext).await().indefinitely();
        
        List<List<dev.langchain4j.data.message.ChatMessage>> capturedPrompts = promptCaptor.getAllValues();
        assertFalse(capturedPrompts.isEmpty());
        
        // Verify prompt contains vulnerability info
        String promptContent = capturedPrompts.get(0).stream()
            .map(msg -> msg.text())
            .reduce("", (a, b) -> a + " " + b);
        
        assertTrue(promptContent.contains("SQL Injection"));
        assertTrue(promptContent.contains("UserService.java"));
    }
}