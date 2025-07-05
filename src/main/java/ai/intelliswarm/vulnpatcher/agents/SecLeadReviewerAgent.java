package ai.intelliswarm.vulnpatcher.agents;

import ai.intelliswarm.vulnpatcher.config.CodeReview;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class SecLeadReviewerAgent implements Agent {
    
    private static final Logger LOGGER = Logger.getLogger(SecLeadReviewerAgent.class.getName());
    
    @Inject
    @CodeReview
    OllamaChatModel reviewModel;
    
    @Inject
    ContextManager contextManager;
    
    @Override
    public String getName() {
        return "SecLeadReviewer";
    }
    
    @Override
    public String getRole() {
        return "Security lead responsible for reviewing code quality, architecture, and maintainability of security patches";
    }
    
    @Override
    public CompletableFuture<AgentResult> execute(AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            AgentResult result = new AgentResult();
            
            try {
                // Extract patch information from previous agent
                Map<String, Object> engineerOutput = 
                    (Map<String, Object>) context.getSharedMemory().get("engineerOutput");
                
                if (engineerOutput == null) {
                    result.setSuccess(false);
                    result.setMessage("No patch information provided for review");
                    return result;
                }
                
                // Perform comprehensive code review
                CodeReviewResult reviewResult = performCodeReview(engineerOutput, context);
                
                // Prepare output
                Map<String, Object> output = new HashMap<>();
                output.put("reviewResult", reviewResult);
                output.put("approved", reviewResult.isApproved());
                output.put("requiresChanges", reviewResult.getRequiredChanges());
                output.put("suggestions", reviewResult.getSuggestions());
                
                result.setSuccess(true);
                result.setMessage(reviewResult.isApproved() ? 
                    "Code review passed" : "Code review requires changes");
                result.setOutput(output);
                
                // Determine next agent based on review outcome
                if (reviewResult.isApproved()) {
                    result.setNextAgent("SecurityExpertReviewer");
                } else {
                    result.setNextAgent("SecurityEngineer"); // Send back for revisions
                }
                
            } catch (Exception e) {
                LOGGER.severe("Error in Sec Lead Review: " + e.getMessage());
                result.setSuccess(false);
                result.setMessage("Sec Lead review error: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    @Override
    public boolean canHandle(String taskType) {
        return "REVIEW_SECURITY_PATCH".equals(taskType);
    }
    
    private CodeReviewResult performCodeReview(Map<String, Object> engineerOutput, AgentContext context) {
        CodeReviewResult review = new CodeReviewResult();
        
        try {
            Map<String, Object> patch = (Map<String, Object>) engineerOutput.get("patch");
            String patchCode = (String) patch.get("code");
            String sessionId = (String) context.getParameters().get("sessionId");
            
            // Get architectural context
            ContextManager.SessionContext sessionContext = contextManager.getOrCreateSession(sessionId);
            List<ContextManager.RelevantContext> architecturalContext = 
                sessionContext.getRelevantContext("architecture patterns design", 5);
            
            // Build review prompt
            String reviewPrompt = buildTechLeadReviewPrompt(engineerOutput, architecturalContext);
            
            // Get AI review
            UserMessage userMessage = UserMessage.from(reviewPrompt);
            AiMessage response = reviewModel.generate(userMessage);
            
            // Parse review response
            parseReviewResponse(response.text(), review);
            
            // Perform additional automated checks
            performArchitecturalChecks(patchCode, review);
            performCodeQualityChecks(patchCode, review);
            performMaintainabilityChecks(patchCode, review);
            performPerformanceChecks(patchCode, review);
            
            // Calculate overall approval
            review.calculateApproval();
            
        } catch (Exception e) {
            LOGGER.severe("Error performing code review: " + e.getMessage());
            review.setApproved(false);
            review.addIssue("CRITICAL", "Failed to complete code review: " + e.getMessage());
        }
        
        return review;
    }
    
    private String buildTechLeadReviewPrompt(
            Map<String, Object> engineerOutput, 
            List<ContextManager.RelevantContext> architecturalContext) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a security lead reviewing a security patch. ");
        prompt.append("Evaluate the code for quality, maintainability, and architectural fit.\n\n");
        
        Map<String, Object> patch = (Map<String, Object>) engineerOutput.get("patch");
        
        prompt.append("## Patch Details\n");
        prompt.append("### Code:\n```\n");
        prompt.append(patch.get("code"));
        prompt.append("\n```\n\n");
        
        prompt.append("### Security Rationale:\n");
        prompt.append(patch.get("rationale"));
        prompt.append("\n\n");
        
        prompt.append("### Identified Risks:\n");
        prompt.append(patch.get("risks"));
        prompt.append("\n\n");
        
        if (!architecturalContext.isEmpty()) {
            prompt.append("## Architectural Context\n");
            for (ContextManager.RelevantContext ctx : architecturalContext) {
                prompt.append("- ").append(ctx.getFilePath()).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("## Review Criteria\n");
        prompt.append("1. **Code Quality**: Clean, readable, follows coding standards\n");
        prompt.append("2. **Architecture**: Fits with existing patterns and design\n");
        prompt.append("3. **Maintainability**: Easy to understand and modify\n");
        prompt.append("4. **Performance**: No unnecessary overhead or bottlenecks\n");
        prompt.append("5. **Testing**: Testable design, includes test guidance\n");
        prompt.append("6. **Documentation**: Well-commented and documented\n");
        prompt.append("7. **Best Practices**: Follows industry best practices\n\n");
        
        prompt.append("## Required Output Format\n");
        prompt.append("Provide your review in the following format:\n");
        prompt.append("OVERALL_ASSESSMENT: [APPROVED/NEEDS_CHANGES/REJECTED]\n");
        prompt.append("CODE_QUALITY_SCORE: [1-10]\n");
        prompt.append("ARCHITECTURE_SCORE: [1-10]\n");
        prompt.append("MAINTAINABILITY_SCORE: [1-10]\n");
        prompt.append("ISSUES:\n");
        prompt.append("- [CRITICAL/HIGH/MEDIUM/LOW]: Description\n");
        prompt.append("SUGGESTIONS:\n");
        prompt.append("- Description of improvement\n");
        prompt.append("POSITIVE_ASPECTS:\n");
        prompt.append("- What was done well\n");
        
        return prompt.toString();
    }
    
    private void parseReviewResponse(String response, CodeReviewResult review) {
        String[] lines = response.split("\n");
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.startsWith("OVERALL_ASSESSMENT:")) {
                String assessment = line.substring("OVERALL_ASSESSMENT:".length()).trim();
                review.setOverallAssessment(assessment);
            } else if (line.startsWith("CODE_QUALITY_SCORE:")) {
                review.setCodeQualityScore(parseScore(line));
            } else if (line.startsWith("ARCHITECTURE_SCORE:")) {
                review.setArchitectureScore(parseScore(line));
            } else if (line.startsWith("MAINTAINABILITY_SCORE:")) {
                review.setMaintainabilityScore(parseScore(line));
            } else if (line.equals("ISSUES:")) {
                currentSection = "ISSUES";
            } else if (line.equals("SUGGESTIONS:")) {
                currentSection = "SUGGESTIONS";
            } else if (line.equals("POSITIVE_ASPECTS:")) {
                currentSection = "POSITIVE";
            } else if (line.startsWith("- ")) {
                String content = line.substring(2);
                
                switch (currentSection) {
                    case "ISSUES":
                        parseIssue(content, review);
                        break;
                    case "SUGGESTIONS":
                        review.addSuggestion(content);
                        break;
                    case "POSITIVE":
                        review.addPositiveAspect(content);
                        break;
                }
            }
        }
    }
    
    private int parseScore(String line) {
        try {
            String scoreStr = line.split(":")[1].trim();
            return Integer.parseInt(scoreStr);
        } catch (Exception e) {
            return 5; // Default middle score
        }
    }
    
    private void parseIssue(String issueText, CodeReviewResult review) {
        if (issueText.contains(":")) {
            String[] parts = issueText.split(":", 2);
            String severity = parts[0].trim();
            String description = parts[1].trim();
            review.addIssue(severity, description);
        }
    }
    
    private void performArchitecturalChecks(String patchCode, CodeReviewResult review) {
        // Check for architectural patterns
        Map<String, String> antiPatterns = Map.of(
            "God Object", "class\\s+\\w+\\s*\\{[^}]{5000,}",
            "Spaghetti Code", "if\\s*\\([^)]*\\)\\s*\\{[^}]*if\\s*\\([^)]*\\)\\s*\\{[^}]*if",
            "Copy-Paste", "(\\b\\w+\\s*\\([^)]*\\)\\s*\\{[^}]+\\})\\s*\\1",
            "Magic Numbers", "\\b(?<!\\.)\\d{2,}(?!\\.\\d)\\b(?!\\s*[;,)])"
        );
        
        for (Map.Entry<String, String> entry : antiPatterns.entrySet()) {
            if (patchCode.matches(".*" + entry.getValue() + ".*")) {
                review.addIssue("MEDIUM", "Potential " + entry.getKey() + " anti-pattern detected");
            }
        }
    }
    
    private void performCodeQualityChecks(String patchCode, CodeReviewResult review) {
        // Method length check
        String[] methods = patchCode.split("\\b(public|private|protected)\\s+");
        for (String method : methods) {
            if (method.split("\n").length > 50) {
                review.addIssue("MEDIUM", "Method exceeds recommended length (50 lines)");
            }
        }
        
        // Complexity check (simplified)
        int cyclomaticComplexity = countOccurrences(patchCode, "\\b(if|while|for|case|catch)\\b");
        if (cyclomaticComplexity > 10) {
            review.addIssue("HIGH", "High cyclomatic complexity detected (" + cyclomaticComplexity + ")");
        }
        
        // Naming conventions
        if (patchCode.matches(".*\\b[a-z]\\w*[A-Z]\\w*\\s*=.*")) {
            review.addSuggestion("Consider using consistent camelCase naming convention");
        }
    }
    
    private void performMaintainabilityChecks(String patchCode, CodeReviewResult review) {
        // Comment ratio
        int codeLines = patchCode.split("\n").length;
        int commentLines = countOccurrences(patchCode, "//|/\\*|\\*/");
        double commentRatio = (double) commentLines / codeLines;
        
        if (commentRatio < 0.1) {
            review.addIssue("LOW", "Insufficient code documentation (comment ratio: " + 
                String.format("%.2f", commentRatio) + ")");
        }
        
        // TODO comments
        if (patchCode.contains("TODO") || patchCode.contains("FIXME")) {
            review.addIssue("MEDIUM", "Unresolved TODO/FIXME comments found");
        }
    }
    
    private void performPerformanceChecks(String patchCode, CodeReviewResult review) {
        // Nested loops
        if (patchCode.matches(".*for\\s*\\([^)]*\\)\\s*\\{[^}]*for\\s*\\([^)]*\\).*")) {
            review.addIssue("MEDIUM", "Nested loops detected - potential performance impact");
        }
        
        // String concatenation in loops
        if (patchCode.matches(".*(for|while)\\s*\\([^)]*\\)\\s*\\{[^}]*\\+\\s*=\\s*\".*")) {
            review.addSuggestion("Use StringBuilder for string concatenation in loops");
        }
        
        // Database queries in loops
        if (patchCode.matches(".*(for|while)\\s*\\([^)]*\\)\\s*\\{[^}]*(query|execute|select).*")) {
            review.addIssue("HIGH", "Database operations inside loop - consider batch processing");
        }
    }
    
    private int countOccurrences(String text, String pattern) {
        return text.split(pattern, -1).length - 1;
    }
    
    public static class CodeReviewResult {
        private String overallAssessment;
        private boolean approved;
        private int codeQualityScore;
        private int architectureScore;
        private int maintainabilityScore;
        private List<Issue> issues = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private List<String> positiveAspects = new ArrayList<>();
        private List<String> requiredChanges = new ArrayList<>();
        
        public static class Issue {
            private String severity;
            private String description;
            
            public Issue(String severity, String description) {
                this.severity = severity;
                this.description = description;
            }
            
            public String getSeverity() { return severity; }
            public String getDescription() { return description; }
        }
        
        public void addIssue(String severity, String description) {
            issues.add(new Issue(severity, description));
            if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                requiredChanges.add(description);
            }
        }
        
        public void addSuggestion(String suggestion) {
            suggestions.add(suggestion);
        }
        
        public void addPositiveAspect(String aspect) {
            positiveAspects.add(aspect);
        }
        
        public void calculateApproval() {
            // Check for critical issues
            boolean hasCriticalIssues = issues.stream()
                .anyMatch(issue -> "CRITICAL".equals(issue.getSeverity()));
            
            // Check scores
            double avgScore = (codeQualityScore + architectureScore + maintainabilityScore) / 3.0;
            
            // Approval logic
            if (hasCriticalIssues || avgScore < 6) {
                approved = false;
                overallAssessment = "NEEDS_CHANGES";
            } else if (avgScore >= 8 && issues.size() < 3) {
                approved = true;
                overallAssessment = "APPROVED";
            } else {
                approved = true;
                overallAssessment = "APPROVED_WITH_SUGGESTIONS";
            }
        }
        
        // Getters and setters
        public String getOverallAssessment() { return overallAssessment; }
        public void setOverallAssessment(String overallAssessment) { 
            this.overallAssessment = overallAssessment; 
        }
        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        public int getCodeQualityScore() { return codeQualityScore; }
        public void setCodeQualityScore(int codeQualityScore) { 
            this.codeQualityScore = codeQualityScore; 
        }
        public int getArchitectureScore() { return architectureScore; }
        public void setArchitectureScore(int architectureScore) { 
            this.architectureScore = architectureScore; 
        }
        public int getMaintainabilityScore() { return maintainabilityScore; }
        public void setMaintainabilityScore(int maintainabilityScore) { 
            this.maintainabilityScore = maintainabilityScore; 
        }
        public List<Issue> getIssues() { return issues; }
        public List<String> getSuggestions() { return suggestions; }
        public List<String> getPositiveAspects() { return positiveAspects; }
        public List<String> getRequiredChanges() { return requiredChanges; }
    }
}