package ai.intelliswarm.vulnpatcher.orchestrator;

import ai.intelliswarm.vulnpatcher.agents.SecurityEngineerAgent;
import ai.intelliswarm.vulnpatcher.agents.SecurityExpertReviewerAgent;
import ai.intelliswarm.vulnpatcher.agents.SecLeadReviewerAgent;
import ai.intelliswarm.vulnpatcher.config.MetricsConfig;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class LLMOrchestrator {
    
    @Inject
    @Named("orchestratorModel")
    OllamaChatModel orchestratorModel;
    
    @Inject
    SecurityEngineerAgent securityEngineer;
    
    @Inject
    SecLeadReviewerAgent secLeadReviewer;
    
    @Inject
    SecurityExpertReviewerAgent securityExpert;
    
    @Inject
    ContextManager contextManager;
    
    @Inject
    MetricsConfig.MetricsService metricsService;
    
    private final Map<String, WorkflowState> activeWorkflows = new ConcurrentHashMap<>();
    private final Map<String, List<WorkflowEvent>> workflowEvents = new ConcurrentHashMap<>();
    
    public Uni<WorkflowResult> orchestrateVulnerabilityFix(ScanResult.VulnerabilityMatch vulnerability, WorkflowContext context) {
        String workflowId = UUID.randomUUID().toString();
        WorkflowState state = new WorkflowState(workflowId, vulnerability, context);
        activeWorkflows.put(workflowId, state);
        
        addWorkflowEvent(workflowId, "STARTED", "Workflow started for vulnerability: " + vulnerability.getVulnerability().getId());
        
        return Uni.createFrom().item(() -> {
            try {
                // Step 1: Generate task plan
                String taskPlan = generateTaskPlan(vulnerability, context);
                addWorkflowEvent(workflowId, "PLAN_CREATED", "Task plan generated");
                
                // Step 2: Security Engineer generates fix
                String secureFix = securityEngineer.generateSecureFix(
                    vulnerability.getAffectedCode(),
                    vulnerability.getVulnerability().getDescription(),
                    context.getLanguage()
                ).join();
                addWorkflowEvent(workflowId, "FIX_GENERATED", "Security fix generated");
                state.iterations.incrementAndGet();
                
                // Step 3: Tech Lead reviews
                String reviewResult = secLeadReviewer.reviewCode(
                    secureFix,
                    vulnerability.getVulnerability().getDescription()
                ).join();
                addWorkflowEvent(workflowId, "CODE_REVIEWED", "Code reviewed by tech lead");
                
                // Step 4: Security Expert validates
                String securityValidation = securityExpert.analyzeSecurityImplications(
                    secureFix,
                    vulnerability.getVulnerability()
                ).join();
                addWorkflowEvent(workflowId, "SECURITY_VALIDATED", "Security implications analyzed");
                
                // Step 5: Build consensus
                String finalSolution = buildConsensus(secureFix, reviewResult, securityValidation);
                addWorkflowEvent(workflowId, "CONSENSUS_BUILT", "Final solution determined");
                
                // Create result
                WorkflowResult result = new WorkflowResult();
                result.setWorkflowId(workflowId);
                result.setVulnerabilityId(vulnerability.getVulnerability().getId());
                result.setSuccess(true);
                result.setFinalSolution(finalSolution);
                result.setConfidence(calculateConfidence(state));
                result.setIterations(state.iterations.get());
                result.setRecommendations(generateRecommendations(vulnerability));
                result.setCompletedAt(LocalDateTime.now());
                
                addWorkflowEvent(workflowId, "COMPLETED", "Workflow completed successfully");
                return result;
                
            } catch (Exception e) {
                Log.error("Workflow failed", e);
                addWorkflowEvent(workflowId, "FAILED", "Workflow failed: " + e.getMessage());
                
                WorkflowResult result = new WorkflowResult();
                result.setWorkflowId(workflowId);
                result.setVulnerabilityId(vulnerability.getVulnerability().getId());
                result.setSuccess(false);
                result.setError(e.getMessage());
                result.setCompletedAt(LocalDateTime.now());
                return result;
            } finally {
                activeWorkflows.remove(workflowId);
            }
        });
    }
    
    public Multi<WorkflowEvent> streamWorkflowEvents(String workflowId) {
        return Multi.createFrom().emitter(emitter -> {
            List<WorkflowEvent> events = workflowEvents.get(workflowId);
            if (events == null) {
                emitter.fail(new IllegalArgumentException("Workflow not found: " + workflowId));
                return;
            }
            
            for (WorkflowEvent event : events) {
                emitter.emit(event);
            }
            emitter.complete();
        });
    }
    
    private String generateTaskPlan(ScanResult.VulnerabilityMatch vulnerability, WorkflowContext context) {
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("You are an AI orchestrator planning vulnerability fixes."),
            UserMessage.from(String.format(
                "Create a task plan to fix this vulnerability:\n" +
                "Vulnerability: %s\n" +
                "File: %s\n" +
                "Code: %s\n" +
                "Language: %s\n" +
                "Framework: %s",
                vulnerability.getVulnerability().getTitle(),
                vulnerability.getFilePath(),
                vulnerability.getAffectedCode(),
                context.getLanguage(),
                context.getFramework()
            ))
        );
        
        return orchestratorModel.generate(messages).content().text();
    }
    
    private String buildConsensus(String fix, String review, String validation) {
        List<ChatMessage> messages = Arrays.asList(
            SystemMessage.from("You are building consensus between multiple security experts."),
            UserMessage.from(String.format(
                "Build a final solution based on:\n" +
                "Security Fix: %s\n" +
                "Review Feedback: %s\n" +
                "Security Validation: %s",
                fix, review, validation
            ))
        );
        
        return orchestratorModel.generate(messages).content().text();
    }
    
    private double calculateConfidence(WorkflowState state) {
        // Simple confidence calculation
        return Math.min(0.95, 0.7 + (state.iterations.get() * 0.05));
    }
    
    private List<String> generateRecommendations(ScanResult.VulnerabilityMatch vulnerability) {
        List<String> recommendations = new ArrayList<>();
        
        String severity = vulnerability.getVulnerability().getSeverity();
        if ("CRITICAL".equals(severity)) {
            recommendations.add("Priority fix: This is a critical vulnerability and should be fixed immediately");
        }
        
        recommendations.add("Apply the fix and thoroughly test the changes");
        recommendations.add("Review similar code patterns in the codebase");
        recommendations.add("Update security policies to prevent similar issues");
        
        return recommendations;
    }
    
    private void addWorkflowEvent(String workflowId, String type, String message) {
        WorkflowEvent event = new WorkflowEvent();
        event.setType(type);
        event.setMessage(message);
        event.setTimestamp(LocalDateTime.now());
        
        workflowEvents.computeIfAbsent(workflowId, k -> new ArrayList<>()).add(event);
    }
    
    private static class WorkflowState {
        final String workflowId;
        final ScanResult.VulnerabilityMatch vulnerability;
        final WorkflowContext context;
        final AtomicInteger iterations = new AtomicInteger(0);
        final LocalDateTime startedAt = LocalDateTime.now();
        
        WorkflowState(String workflowId, ScanResult.VulnerabilityMatch vulnerability, WorkflowContext context) {
            this.workflowId = workflowId;
            this.vulnerability = vulnerability;
            this.context = context;
        }
    }
    
    public static class WorkflowContext {
        private String repositoryUrl;
        private String language;
        private String framework;
        private Map<String, Object> additionalContext;
        
        // Getters and setters
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }
        
        public Map<String, Object> getAdditionalContext() { return additionalContext; }
        public void setAdditionalContext(Map<String, Object> additionalContext) { this.additionalContext = additionalContext; }
    }
    
    public static class WorkflowResult {
        private String workflowId;
        private String vulnerabilityId;
        private boolean success;
        private String finalSolution;
        private double confidence;
        private int iterations;
        private List<String> recommendations;
        private String error;
        private LocalDateTime completedAt;
        
        // Getters and setters
        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
        
        public String getVulnerabilityId() { return vulnerabilityId; }
        public void setVulnerabilityId(String vulnerabilityId) { this.vulnerabilityId = vulnerabilityId; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getFinalSolution() { return finalSolution; }
        public void setFinalSolution(String finalSolution) { this.finalSolution = finalSolution; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public int getIterations() { return iterations; }
        public void setIterations(int iterations) { this.iterations = iterations; }
        
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    }
    
    public static class WorkflowEvent {
        private String type;
        private String message;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}