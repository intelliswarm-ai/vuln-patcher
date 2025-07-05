package ai.intelliswarm.vulnpatcher.fixes;

import ai.intelliswarm.vulnpatcher.models.ScanResult;
import ai.intelliswarm.vulnpatcher.models.Vulnerability;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface FixGenerator {
    
    /**
     * Get the language this generator supports
     */
    String getSupportedLanguage();
    
    /**
     * Check if this generator can handle the vulnerability
     */
    boolean canHandle(ScanResult.VulnerabilityMatch vulnerability);
    
    /**
     * Generate a fix for the vulnerability
     */
    CompletableFuture<FixResult> generateFix(
        ScanResult.VulnerabilityMatch vulnerability,
        FixContext context
    );
    
    /**
     * Validate that a fix is safe and correct
     */
    CompletableFuture<ValidationResult> validateFix(
        FixResult fix,
        FixContext context
    );
    
    /**
     * Get fix templates for common vulnerabilities
     */
    Map<String, FixTemplate> getFixTemplates();
    
    /**
     * Context for fix generation
     */
    class FixContext {
        private String sessionId;
        private String filePath;
        private String fileContent;
        private String language;
        private Map<String, Object> projectContext;
        private List<String> dependencies;
        private Map<String, String> environmentVariables;
        
        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getFileContent() { return fileContent; }
        public void setFileContent(String fileContent) { this.fileContent = fileContent; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public Map<String, Object> getProjectContext() { return projectContext; }
        public void setProjectContext(Map<String, Object> projectContext) { this.projectContext = projectContext; }
        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
        public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
        public void setEnvironmentVariables(Map<String, String> environmentVariables) { 
            this.environmentVariables = environmentVariables; 
        }
    }
    
    /**
     * Result of fix generation
     */
    class FixResult {
        private boolean success;
        private String fixedCode;
        private String explanation;
        private List<CodeChange> changes;
        private Map<String, Object> metadata;
        private Double confidence;
        private List<String> warnings;
        
        public static class CodeChange {
            private int startLine;
            private int endLine;
            private String originalCode;
            private String fixedCode;
            private String changeType; // ADD, REMOVE, MODIFY
            private String reason;
            
            // Getters and setters
            public int getStartLine() { return startLine; }
            public void setStartLine(int startLine) { this.startLine = startLine; }
            public int getEndLine() { return endLine; }
            public void setEndLine(int endLine) { this.endLine = endLine; }
            public String getOriginalCode() { return originalCode; }
            public void setOriginalCode(String originalCode) { this.originalCode = originalCode; }
            public String getFixedCode() { return fixedCode; }
            public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode; }
            public String getChangeType() { return changeType; }
            public void setChangeType(String changeType) { this.changeType = changeType; }
            public String getReason() { return reason; }
            public void setReason(String reason) { this.reason = reason; }
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getFixedCode() { return fixedCode; }
        public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public List<CodeChange> getChanges() { return changes; }
        public void setChanges(List<CodeChange> changes) { this.changes = changes; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }
    
    /**
     * Validation result
     */
    class ValidationResult {
        private boolean valid;
        private List<ValidationIssue> issues;
        private Map<String, Object> metrics;
        
        public static class ValidationIssue {
            private String type; // SYNTAX_ERROR, LOGIC_ERROR, SECURITY_CONCERN, PERFORMANCE_IMPACT
            private String severity; // ERROR, WARNING, INFO
            private String message;
            private Integer line;
            
            // Getters and setters
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
            public String getSeverity() { return severity; }
            public void setSeverity(String severity) { this.severity = severity; }
            public String getMessage() { return message; }
            public void setMessage(String message) { this.message = message; }
            public Integer getLine() { return line; }
            public void setLine(Integer line) { this.line = line; }
        }
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<ValidationIssue> getIssues() { return issues; }
        public void setIssues(List<ValidationIssue> issues) { this.issues = issues; }
        public Map<String, Object> getMetrics() { return metrics; }
        public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    }
    
    /**
     * Fix template for common patterns
     */
    class FixTemplate {
        private String vulnerabilityType;
        private String language;
        private String pattern;
        private String replacement;
        private String explanation;
        private List<String> requiredImports;
        private Map<String, String> variables;
        
        // Getters and setters
        public String getVulnerabilityType() { return vulnerabilityType; }
        public void setVulnerabilityType(String vulnerabilityType) { this.vulnerabilityType = vulnerabilityType; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public List<String> getRequiredImports() { return requiredImports; }
        public void setRequiredImports(List<String> requiredImports) { this.requiredImports = requiredImports; }
        public Map<String, String> getVariables() { return variables; }
        public void setVariables(Map<String, String> variables) { this.variables = variables; }
    }
}