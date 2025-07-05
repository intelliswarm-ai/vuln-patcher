package ai.intelliswarm.vulnpatcher.agents;

import ai.intelliswarm.vulnpatcher.config.CodeGeneration;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class SecurityEngineerAgent implements Agent {
    
    private static final Logger LOGGER = Logger.getLogger(SecurityEngineerAgent.class.getName());
    
    @Inject
    @CodeGeneration
    OllamaChatModel codeGenerationModel;
    
    @Inject
    ContextManager contextManager;
    
    @Override
    public String getName() {
        return "SecurityEngineer";
    }
    
    @Override
    public String getRole() {
        return "Security-focused software engineer responsible for implementing secure code fixes and patches";
    }
    
    @Override
    public CompletableFuture<AgentResult> execute(AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            AgentResult result = new AgentResult();
            
            try {
                String taskType = context.getTaskType();
                
                switch (taskType) {
                    case "GENERATE_SECURITY_PATCH":
                        return generateSecurityPatch(context);
                    case "IMPLEMENT_SECURITY_CONTROL":
                        return implementSecurityControl(context);
                    case "REFACTOR_INSECURE_CODE":
                        return refactorInsecureCode(context);
                    default:
                        result.setSuccess(false);
                        result.setMessage("Unknown task type for Security Engineer: " + taskType);
                        return result;
                }
            } catch (Exception e) {
                LOGGER.severe("Error in Security Engineer Agent: " + e.getMessage());
                result.setSuccess(false);
                result.setMessage("Security Engineer error: " + e.getMessage());
                return result;
            }
        });
    }
    
    @Override
    public boolean canHandle(String taskType) {
        return Arrays.asList(
            "GENERATE_SECURITY_PATCH",
            "IMPLEMENT_SECURITY_CONTROL",
            "REFACTOR_INSECURE_CODE"
        ).contains(taskType);
    }
    
    private AgentResult generateSecurityPatch(AgentContext context) {
        AgentResult result = new AgentResult();
        Map<String, Object> output = new HashMap<>();
        
        try {
            // Extract vulnerability information
            ScanResult.VulnerabilityMatch vulnMatch = 
                (ScanResult.VulnerabilityMatch) context.getParameters().get("vulnerability");
            String sessionId = (String) context.getParameters().get("sessionId");
            
            if (vulnMatch == null) {
                result.setSuccess(false);
                result.setMessage("No vulnerability information provided");
                return result;
            }
            
            // Get relevant context from the codebase
            ContextManager.SessionContext sessionContext = contextManager.getOrCreateSession(sessionId);
            List<ContextManager.RelevantContext> relevantContexts = 
                sessionContext.getRelevantContext(vulnMatch.getAffectedCode(), 10);
            
            // Build comprehensive prompt for the LLM
            String prompt = buildSecurityPatchPrompt(vulnMatch, relevantContexts);
            
            // Generate patch using the code generation model
            UserMessage userMessage = UserMessage.from(prompt);
            AiMessage response = codeGenerationModel.generate(userMessage);
            
            String generatedPatch = response.text();
            
            // Parse and structure the patch
            Map<String, Object> patchDetails = parsePatchResponse(generatedPatch);
            
            // Add security metadata
            patchDetails.put("securityControls", identifySecurityControls(generatedPatch));
            patchDetails.put("complianceChecks", performComplianceChecks(generatedPatch, vulnMatch));
            patchDetails.put("testCases", generateSecurityTestCases(vulnMatch, patchDetails));
            
            output.put("patch", patchDetails);
            output.put("vulnerability", vulnMatch);
            output.put("engineerNotes", generateEngineerNotes(vulnMatch, patchDetails));
            
            result.setSuccess(true);
            result.setMessage("Security patch generated successfully");
            result.setOutput(output);
            result.setNextAgent("SecLeadReviewer"); // Send to sec lead for review
            
        } catch (Exception e) {
            LOGGER.severe("Error generating security patch: " + e.getMessage());
            result.setSuccess(false);
            result.setMessage("Failed to generate security patch: " + e.getMessage());
        }
        
        return result;
    }
    
    private String buildSecurityPatchPrompt(
            ScanResult.VulnerabilityMatch vulnMatch, 
            List<ContextManager.RelevantContext> contexts) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a security-focused software engineer. Generate a secure patch for the following vulnerability:\n\n");
        
        // Vulnerability details
        prompt.append("## Vulnerability Details\n");
        prompt.append("- Type: ").append(vulnMatch.getVulnerability().getTitle()).append("\n");
        prompt.append("- Severity: ").append(vulnMatch.getVulnerability().getSeverity()).append("\n");
        prompt.append("- Description: ").append(vulnMatch.getVulnerability().getDescription()).append("\n");
        prompt.append("- File: ").append(vulnMatch.getFilePath()).append("\n");
        prompt.append("- Line: ").append(vulnMatch.getLineNumber()).append("\n\n");
        
        // Affected code
        prompt.append("## Affected Code\n```\n");
        prompt.append(vulnMatch.getAffectedCode());
        prompt.append("\n```\n\n");
        
        // Relevant context
        if (!contexts.isEmpty()) {
            prompt.append("## Related Code Context\n");
            for (ContextManager.RelevantContext ctx : contexts) {
                prompt.append("### ").append(ctx.getFilePath())
                      .append(" (lines ").append(ctx.getStartLine())
                      .append("-").append(ctx.getEndLine()).append(")\n");
                prompt.append("```").append(ctx.getFileType()).append("\n");
                prompt.append(ctx.getContent());
                prompt.append("\n```\n\n");
            }
        }
        
        // Security requirements
        prompt.append("## Security Requirements\n");
        prompt.append("1. Fix must completely address the vulnerability\n");
        prompt.append("2. Implement defense-in-depth principles\n");
        prompt.append("3. Include input validation and sanitization\n");
        prompt.append("4. Follow OWASP secure coding practices\n");
        prompt.append("5. Maintain backward compatibility when possible\n");
        prompt.append("6. Add appropriate error handling\n");
        prompt.append("7. Include security comments explaining the fix\n\n");
        
        prompt.append("## Expected Output Format\n");
        prompt.append("Provide the patch in the following format:\n");
        prompt.append("1. PATCH_CODE: The actual code fix\n");
        prompt.append("2. SECURITY_RATIONALE: Explanation of security improvements\n");
        prompt.append("3. POTENTIAL_RISKS: Any risks or trade-offs\n");
        prompt.append("4. TESTING_GUIDANCE: How to test the security fix\n");
        
        return prompt.toString();
    }
    
    private Map<String, Object> parsePatchResponse(String response) {
        Map<String, Object> parsed = new HashMap<>();
        
        // Parse sections from the response
        String[] sections = response.split("(?=\\d+\\.)");
        
        for (String section : sections) {
            if (section.contains("PATCH_CODE:")) {
                parsed.put("code", extractSection(section, "PATCH_CODE:"));
            } else if (section.contains("SECURITY_RATIONALE:")) {
                parsed.put("rationale", extractSection(section, "SECURITY_RATIONALE:"));
            } else if (section.contains("POTENTIAL_RISKS:")) {
                parsed.put("risks", extractSection(section, "POTENTIAL_RISKS:"));
            } else if (section.contains("TESTING_GUIDANCE:")) {
                parsed.put("testing", extractSection(section, "TESTING_GUIDANCE:"));
            }
        }
        
        return parsed;
    }
    
    private String extractSection(String text, String marker) {
        int start = text.indexOf(marker);
        if (start == -1) return "";
        
        start += marker.length();
        int end = text.indexOf("\n\n", start);
        if (end == -1) end = text.length();
        
        return text.substring(start, end).trim();
    }
    
    private List<String> identifySecurityControls(String patch) {
        List<String> controls = new ArrayList<>();
        
        // Pattern matching for common security controls
        Map<String, String> controlPatterns = Map.of(
            "Input Validation", "(?i)(validate|sanitize|escape|filter).*input",
            "Authentication", "(?i)(authenticate|auth|verify.*identity)",
            "Authorization", "(?i)(authorize|permission|access.*control)",
            "Encryption", "(?i)(encrypt|decrypt|cipher|crypto)",
            "Logging", "(?i)(log|audit|record).*security",
            "Rate Limiting", "(?i)(rate.*limit|throttle|quota)"
        );
        
        for (Map.Entry<String, String> entry : controlPatterns.entrySet()) {
            if (patch.matches(".*" + entry.getValue() + ".*")) {
                controls.add(entry.getKey());
            }
        }
        
        return controls;
    }
    
    private Map<String, Boolean> performComplianceChecks(String patch, ScanResult.VulnerabilityMatch vuln) {
        Map<String, Boolean> compliance = new HashMap<>();
        
        // Check for common compliance requirements
        compliance.put("OWASP_TOP_10", checkOwaspCompliance(patch, vuln));
        compliance.put("CWE_SANS_25", checkCweCompliance(patch, vuln));
        compliance.put("PCI_DSS", checkPciCompliance(patch, vuln));
        compliance.put("GDPR", checkGdprCompliance(patch));
        
        return compliance;
    }
    
    private boolean checkOwaspCompliance(String patch, ScanResult.VulnerabilityMatch vuln) {
        // Simplified OWASP compliance check
        List<String> owaspPatterns = Arrays.asList(
            "input validation",
            "output encoding",
            "parameterized queries",
            "access control",
            "cryptographic storage"
        );
        
        return owaspPatterns.stream()
            .anyMatch(pattern -> patch.toLowerCase().contains(pattern));
    }
    
    private boolean checkCweCompliance(String patch, ScanResult.VulnerabilityMatch vuln) {
        // Check if patch addresses the CWE
        List<String> cweIds = vuln.getVulnerability().getCweIds();
        return cweIds != null && !cweIds.isEmpty();
    }
    
    private boolean checkPciCompliance(String patch, ScanResult.VulnerabilityMatch vuln) {
        // PCI-DSS relevant checks
        return patch.contains("encrypt") || patch.contains("mask") || patch.contains("tokenize");
    }
    
    private boolean checkGdprCompliance(String patch) {
        // GDPR relevant checks
        return patch.contains("consent") || patch.contains("privacy") || patch.contains("data protection");
    }
    
    private List<Map<String, String>> generateSecurityTestCases(
            ScanResult.VulnerabilityMatch vuln, 
            Map<String, Object> patchDetails) {
        
        List<Map<String, String>> testCases = new ArrayList<>();
        
        // Generate test cases based on vulnerability type
        String vulnType = vuln.getMatchType().toString();
        
        Map<String, String> positiveTest = new HashMap<>();
        positiveTest.put("name", "Valid input should be processed correctly");
        positiveTest.put("type", "POSITIVE");
        positiveTest.put("description", "Ensure the fix doesn't break normal functionality");
        testCases.add(positiveTest);
        
        Map<String, String> negativeTest = new HashMap<>();
        negativeTest.put("name", "Malicious input should be rejected");
        negativeTest.put("type", "NEGATIVE");
        negativeTest.put("description", "Ensure the vulnerability is actually fixed");
        testCases.add(negativeTest);
        
        Map<String, String> boundaryTest = new HashMap<>();
        boundaryTest.put("name", "Boundary conditions should be handled");
        boundaryTest.put("type", "BOUNDARY");
        boundaryTest.put("description", "Test edge cases and limits");
        testCases.add(boundaryTest);
        
        return testCases;
    }
    
    private String generateEngineerNotes(
            ScanResult.VulnerabilityMatch vuln, 
            Map<String, Object> patchDetails) {
        
        StringBuilder notes = new StringBuilder();
        notes.append("## Security Engineer Notes\n\n");
        
        notes.append("### Vulnerability Assessment\n");
        notes.append("- Confirmed vulnerability type: ").append(vuln.getVulnerability().getTitle()).append("\n");
        notes.append("- Risk level: ").append(vuln.getVulnerability().getSeverity()).append("\n");
        notes.append("- Confidence in detection: ").append(vuln.getConfidence()).append("\n\n");
        
        notes.append("### Patch Implementation\n");
        notes.append("- Applied security controls: ").append(patchDetails.get("securityControls")).append("\n");
        notes.append("- Compliance status: ").append(patchDetails.get("complianceChecks")).append("\n\n");
        
        notes.append("### Recommendations\n");
        notes.append("- This patch should undergo thorough security testing\n");
        notes.append("- Consider adding monitoring for this security control\n");
        notes.append("- Update security documentation after merge\n");
        
        return notes.toString();
    }
    
    private AgentResult implementSecurityControl(AgentContext context) {
        // Implementation for adding new security controls
        AgentResult result = new AgentResult();
        // TODO: Implement security control addition logic
        return result;
    }
    
    private AgentResult refactorInsecureCode(AgentContext context) {
        // Implementation for refactoring insecure code patterns
        AgentResult result = new AgentResult();
        // TODO: Implement secure refactoring logic
        return result;
    }
}