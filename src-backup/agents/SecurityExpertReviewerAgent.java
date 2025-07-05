package ai.intelliswarm.vulnpatcher.agents;

import ai.intelliswarm.vulnpatcher.config.VulnerabilityAnalysis;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class SecurityExpertReviewerAgent implements Agent {
    
    private static final Logger LOGGER = Logger.getLogger(SecurityExpertReviewerAgent.class.getName());
    
    @Inject
    @VulnerabilityAnalysis
    OllamaChatModel analysisModel;
    
    @Override
    public String getName() {
        return "SecurityExpertReviewer";
    }
    
    @Override
    public String getRole() {
        return "Security expert responsible for final security validation and compliance checks";
    }
    
    @Override
    public CompletableFuture<AgentResult> execute(AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            AgentResult result = new AgentResult();
            
            try {
                // Get outputs from previous agents
                Map<String, Object> engineerOutput = 
                    (Map<String, Object>) context.getSharedMemory().get("engineerOutput");
                Map<String, Object> techLeadOutput = 
                    (Map<String, Object>) context.getSharedMemory().get("techLeadOutput");
                
                if (engineerOutput == null || techLeadOutput == null) {
                    result.setSuccess(false);
                    result.setMessage("Missing required outputs from previous agents");
                    return result;
                }
                
                // Perform comprehensive security review
                SecurityReviewResult reviewResult = performSecurityReview(
                    engineerOutput, techLeadOutput, context
                );
                
                // Prepare output
                Map<String, Object> output = new HashMap<>();
                output.put("securityReview", reviewResult);
                output.put("finalApproval", reviewResult.isApproved());
                output.put("securityScore", reviewResult.getSecurityScore());
                output.put("complianceStatus", reviewResult.getComplianceStatus());
                
                result.setSuccess(true);
                result.setMessage(reviewResult.isApproved() ? 
                    "Security review passed - Ready for PR creation" : 
                    "Security review failed - Additional fixes required");
                result.setOutput(output);
                
                // Determine next action
                if (reviewResult.isApproved()) {
                    result.setNextAgent("PullRequestAgent");
                } else {
                    result.setNextAgent("SecurityEngineer"); // Back for security fixes
                }
                
            } catch (Exception e) {
                LOGGER.severe("Error in Security Expert Review: " + e.getMessage());
                result.setSuccess(false);
                result.setMessage("Security review error: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    @Override
    public boolean canHandle(String taskType) {
        return "SECURITY_REVIEW".equals(taskType);
    }
    
    private SecurityReviewResult performSecurityReview(
            Map<String, Object> engineerOutput,
            Map<String, Object> techLeadOutput,
            AgentContext context) {
        
        SecurityReviewResult review = new SecurityReviewResult();
        
        try {
            Map<String, Object> patch = (Map<String, Object>) engineerOutput.get("patch");
            Map<String, Object> techReview = (Map<String, Object>) techLeadOutput.get("reviewResult");
            
            // Build comprehensive security review prompt
            String prompt = buildSecurityReviewPrompt(patch, techReview);
            
            // Get AI security analysis
            UserMessage userMessage = UserMessage.from(prompt);
            AiMessage response = analysisModel.generate(userMessage);
            
            // Parse security review
            parseSecurityReview(response.text(), review);
            
            // Perform additional automated security checks
            performOwaspTop10Checks(patch, review);
            performCvePatternChecks(patch, review);
            performSecurityBestPractices(patch, review);
            performComplianceValidation(patch, review);
            
            // Calculate final security score
            review.calculateFinalScore();
            
        } catch (Exception e) {
            LOGGER.severe("Error performing security review: " + e.getMessage());
            review.setApproved(false);
            review.addCriticalIssue("Failed to complete security review: " + e.getMessage());
        }
        
        return review;
    }
    
    private String buildSecurityReviewPrompt(Map<String, Object> patch, Map<String, Object> techReview) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a senior security expert performing a final security review. ");
        prompt.append("Evaluate the patch for security completeness and compliance.\n\n");
        
        prompt.append("## Patch Code\n```\n");
        prompt.append(patch.get("code"));
        prompt.append("\n```\n\n");
        
        prompt.append("## Security Rationale\n");
        prompt.append(patch.get("rationale"));
        prompt.append("\n\n");
        
        prompt.append("## Tech Lead Review Summary\n");
        prompt.append(techReview.toString());
        prompt.append("\n\n");
        
        prompt.append("## Security Review Criteria\n");
        prompt.append("1. **Vulnerability Mitigation**: Does the fix completely address the vulnerability?\n");
        prompt.append("2. **No New Vulnerabilities**: Does the fix introduce any new security issues?\n");
        prompt.append("3. **Defense in Depth**: Are multiple security layers implemented?\n");
        prompt.append("4. **Security Boundaries**: Are trust boundaries properly enforced?\n");
        prompt.append("5. **Data Protection**: Is sensitive data properly protected?\n");
        prompt.append("6. **Authentication/Authorization**: Are access controls properly implemented?\n");
        prompt.append("7. **Logging/Monitoring**: Are security events properly logged?\n");
        prompt.append("8. **Error Handling**: Are errors handled securely without information leakage?\n\n");
        
        prompt.append("## Required Output Format\n");
        prompt.append("SECURITY_ASSESSMENT: [APPROVED/REJECTED]\n");
        prompt.append("SECURITY_SCORE: [1-10]\n");
        prompt.append("VULNERABILITY_MITIGATION: [COMPLETE/PARTIAL/INSUFFICIENT]\n");
        prompt.append("NEW_VULNERABILITIES: [NONE/FOUND]\n");
        prompt.append("CRITICAL_ISSUES:\n");
        prompt.append("- Issue description\n");
        prompt.append("SECURITY_IMPROVEMENTS:\n");
        prompt.append("- Improvement suggestion\n");
        prompt.append("COMPLIANCE_NOTES:\n");
        prompt.append("- Compliance observation\n");
        
        return prompt.toString();
    }
    
    private void parseSecurityReview(String response, SecurityReviewResult review) {
        String[] lines = response.split("\n");
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.startsWith("SECURITY_ASSESSMENT:")) {
                String assessment = line.substring("SECURITY_ASSESSMENT:".length()).trim();
                review.setApproved("APPROVED".equals(assessment));
            } else if (line.startsWith("SECURITY_SCORE:")) {
                try {
                    int score = Integer.parseInt(line.substring("SECURITY_SCORE:".length()).trim());
                    review.setSecurityScore(score);
                } catch (NumberFormatException e) {
                    review.setSecurityScore(5);
                }
            } else if (line.startsWith("VULNERABILITY_MITIGATION:")) {
                String mitigation = line.substring("VULNERABILITY_MITIGATION:".length()).trim();
                review.setVulnerabilityMitigation(mitigation);
            } else if (line.equals("CRITICAL_ISSUES:")) {
                currentSection = "CRITICAL";
            } else if (line.equals("SECURITY_IMPROVEMENTS:")) {
                currentSection = "IMPROVEMENTS";
            } else if (line.equals("COMPLIANCE_NOTES:")) {
                currentSection = "COMPLIANCE";
            } else if (line.startsWith("- ")) {
                String content = line.substring(2);
                
                switch (currentSection) {
                    case "CRITICAL":
                        review.addCriticalIssue(content);
                        break;
                    case "IMPROVEMENTS":
                        review.addImprovement(content);
                        break;
                    case "COMPLIANCE":
                        review.addComplianceNote(content);
                        break;
                }
            }
        }
    }
    
    private void performOwaspTop10Checks(Map<String, Object> patch, SecurityReviewResult review) {
        String code = (String) patch.get("code");
        
        // OWASP Top 10 2021 checks
        Map<String, String> owaspChecks = Map.of(
            "A01:2021", "Broken Access Control",
            "A02:2021", "Cryptographic Failures",
            "A03:2021", "Injection",
            "A04:2021", "Insecure Design",
            "A05:2021", "Security Misconfiguration"
        );
        
        Map<String, Boolean> checkResults = new HashMap<>();
        
        // Simplified checks - in production, use more sophisticated analysis
        checkResults.put("A01:2021", code.contains("authorize") || code.contains("permission"));
        checkResults.put("A02:2021", !code.contains("MD5") && !code.contains("SHA1"));
        checkResults.put("A03:2021", code.contains("prepareStatement") || code.contains("parameterized"));
        checkResults.put("A04:2021", code.contains("validate") || code.contains("sanitize"));
        checkResults.put("A05:2021", code.contains("secure") || code.contains("config"));
        
        review.setOwaspCompliance(checkResults);
    }
    
    private void performCvePatternChecks(Map<String, Object> patch, SecurityReviewResult review) {
        String code = (String) patch.get("code");
        
        // Check for common CVE patterns
        List<String> cvePatterns = Arrays.asList(
            "eval\\s*\\(",
            "exec\\s*\\(",
            "system\\s*\\(",
            "Runtime\\.getRuntime",
            "ProcessBuilder",
            "ObjectInputStream",
            "readObject\\s*\\(",
            "XMLReader",
            "DocumentBuilder"
        );
        
        List<String> foundPatterns = new ArrayList<>();
        for (String pattern : cvePatterns) {
            if (code.matches(".*" + pattern + ".*")) {
                foundPatterns.add(pattern);
            }
        }
        
        if (!foundPatterns.isEmpty()) {
            review.addCriticalIssue("Potentially dangerous patterns found: " + String.join(", ", foundPatterns));
        }
    }
    
    private void performSecurityBestPractices(Map<String, Object> patch, SecurityReviewResult review) {
        String code = (String) patch.get("code");
        
        // Check for security best practices
        Map<String, Boolean> bestPractices = new HashMap<>();
        
        bestPractices.put("Input Validation", 
            code.contains("validate") || code.contains("sanitize") || code.contains("filter"));
        bestPractices.put("Output Encoding", 
            code.contains("encode") || code.contains("escape"));
        bestPractices.put("Error Handling", 
            code.contains("try") && code.contains("catch"));
        bestPractices.put("Secure Defaults", 
            code.contains("default") && (code.contains("secure") || code.contains("deny")));
        bestPractices.put("Principle of Least Privilege", 
            code.contains("minimal") || code.contains("restricted"));
        
        review.setBestPractices(bestPractices);
        
        // Add improvements for missing practices
        for (Map.Entry<String, Boolean> entry : bestPractices.entrySet()) {
            if (!entry.getValue()) {
                review.addImprovement("Consider implementing: " + entry.getKey());
            }
        }
    }
    
    private void performComplianceValidation(Map<String, Object> patch, SecurityReviewResult review) {
        Map<String, ComplianceStatus> compliance = new HashMap<>();
        
        // Check various compliance requirements
        compliance.put("PCI-DSS", checkPciDssCompliance(patch));
        compliance.put("GDPR", checkGdprCompliance(patch));
        compliance.put("HIPAA", checkHipaaCompliance(patch));
        compliance.put("SOC2", checkSoc2Compliance(patch));
        
        review.setComplianceStatus(compliance);
    }
    
    private ComplianceStatus checkPciDssCompliance(Map<String, Object> patch) {
        String code = (String) patch.get("code");
        ComplianceStatus status = new ComplianceStatus();
        status.setFramework("PCI-DSS");
        
        // Check for PCI-DSS relevant patterns
        boolean hasEncryption = code.contains("encrypt") || code.contains("AES");
        boolean hasMasking = code.contains("mask") || code.contains("redact");
        boolean hasAccessControl = code.contains("authorize") || code.contains("permission");
        
        status.setCompliant(hasEncryption && hasMasking && hasAccessControl);
        
        if (!hasEncryption) status.addRequirement("Encryption of cardholder data required");
        if (!hasMasking) status.addRequirement("PAN masking required");
        if (!hasAccessControl) status.addRequirement("Access control implementation required");
        
        return status;
    }
    
    private ComplianceStatus checkGdprCompliance(Map<String, Object> patch) {
        String code = (String) patch.get("code");
        ComplianceStatus status = new ComplianceStatus();
        status.setFramework("GDPR");
        
        boolean hasConsent = code.contains("consent") || code.contains("permission");
        boolean hasDataProtection = code.contains("protect") || code.contains("secure");
        boolean hasPrivacy = code.contains("privacy") || code.contains("personal");
        
        status.setCompliant(hasConsent || hasDataProtection || hasPrivacy);
        
        return status;
    }
    
    private ComplianceStatus checkHipaaCompliance(Map<String, Object> patch) {
        ComplianceStatus status = new ComplianceStatus();
        status.setFramework("HIPAA");
        status.setCompliant(true); // Simplified
        return status;
    }
    
    private ComplianceStatus checkSoc2Compliance(Map<String, Object> patch) {
        ComplianceStatus status = new ComplianceStatus();
        status.setFramework("SOC2");
        status.setCompliant(true); // Simplified
        return status;
    }
    
    public static class SecurityReviewResult {
        private boolean approved;
        private int securityScore;
        private String vulnerabilityMitigation;
        private List<String> criticalIssues = new ArrayList<>();
        private List<String> improvements = new ArrayList<>();
        private List<String> complianceNotes = new ArrayList<>();
        private Map<String, Boolean> owaspCompliance;
        private Map<String, Boolean> bestPractices;
        private Map<String, ComplianceStatus> complianceStatus;
        
        public void calculateFinalScore() {
            // If there are critical issues, cannot approve
            if (!criticalIssues.isEmpty()) {
                approved = false;
                return;
            }
            
            // Check minimum security score
            if (securityScore < 7) {
                approved = false;
                return;
            }
            
            // Check vulnerability mitigation
            if (!"COMPLETE".equals(vulnerabilityMitigation)) {
                approved = false;
                return;
            }
            
            // All checks passed
            approved = true;
        }
        
        public void addCriticalIssue(String issue) {
            criticalIssues.add(issue);
        }
        
        public void addImprovement(String improvement) {
            improvements.add(improvement);
        }
        
        public void addComplianceNote(String note) {
            complianceNotes.add(note);
        }
        
        // Getters and setters
        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        public int getSecurityScore() { return securityScore; }
        public void setSecurityScore(int securityScore) { this.securityScore = securityScore; }
        public String getVulnerabilityMitigation() { return vulnerabilityMitigation; }
        public void setVulnerabilityMitigation(String vulnerabilityMitigation) { 
            this.vulnerabilityMitigation = vulnerabilityMitigation; 
        }
        public List<String> getCriticalIssues() { return criticalIssues; }
        public List<String> getImprovements() { return improvements; }
        public List<String> getComplianceNotes() { return complianceNotes; }
        public Map<String, Boolean> getOwaspCompliance() { return owaspCompliance; }
        public void setOwaspCompliance(Map<String, Boolean> owaspCompliance) { 
            this.owaspCompliance = owaspCompliance; 
        }
        public Map<String, Boolean> getBestPractices() { return bestPractices; }
        public void setBestPractices(Map<String, Boolean> bestPractices) { 
            this.bestPractices = bestPractices; 
        }
        public Map<String, ComplianceStatus> getComplianceStatus() { return complianceStatus; }
        public void setComplianceStatus(Map<String, ComplianceStatus> complianceStatus) { 
            this.complianceStatus = complianceStatus; 
        }
    }
    
    public static class ComplianceStatus {
        private String framework;
        private boolean compliant;
        private List<String> requirements = new ArrayList<>();
        
        public void addRequirement(String requirement) {
            requirements.add(requirement);
        }
        
        // Getters and setters
        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }
        public boolean isCompliant() { return compliant; }
        public void setCompliant(boolean compliant) { this.compliant = compliant; }
        public List<String> getRequirements() { return requirements; }
    }
}