package ai.intelliswarm.vulnpatcher.fixes;

import ai.intelliswarm.vulnpatcher.config.CodeGeneration;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class JavaFixGenerator implements FixGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(JavaFixGenerator.class.getName());
    
    @Inject
    @CodeGeneration
    OllamaChatModel codeGenerationModel;
    
    @Inject
    ContextManager contextManager;
    
    private final Map<String, FixTemplate> fixTemplates = initializeTemplates();
    
    @Override
    public String getSupportedLanguage() {
        return "java";
    }
    
    @Override
    public boolean canHandle(ScanResult.VulnerabilityMatch vulnerability) {
        String filePath = vulnerability.getFilePath();
        return filePath != null && (filePath.endsWith(".java") || 
               filePath.endsWith("pom.xml") || filePath.endsWith("build.gradle"));
    }
    
    @Override
    public CompletableFuture<FixResult> generateFix(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context) {
        
        return CompletableFuture.supplyAsync(() -> {
            FixResult result = new FixResult();
            
            try {
                // Check if we have a template fix
                String vulnType = vulnerability.getVulnerability().getId();
                FixTemplate template = fixTemplates.get(vulnType);
                
                if (template != null && vulnerability.getMatchType() == 
                        ScanResult.VulnerabilityMatch.MatchType.CODE_PATTERN) {
                    return applyTemplateFix(vulnerability, context, template);
                }
                
                // Use AI for complex fixes
                return generateAIFix(vulnerability, context);
                
            } catch (Exception e) {
                LOGGER.severe("Error generating Java fix: " + e.getMessage());
                result.setSuccess(false);
                result.setExplanation("Failed to generate fix: " + e.getMessage());
                return result;
            }
        });
    }
    
    @Override
    public CompletableFuture<ValidationResult> validateFix(FixResult fix, FixContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ValidationResult result = new ValidationResult();
            List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
            
            // Syntax validation
            if (!validateJavaSyntax(fix.getFixedCode())) {
                ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue();
                issue.setType("SYNTAX_ERROR");
                issue.setSeverity("ERROR");
                issue.setMessage("Java syntax error in generated fix");
                issues.add(issue);
            }
            
            // Import validation
            List<String> missingImports = checkMissingImports(fix.getFixedCode());
            for (String imp : missingImports) {
                ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue();
                issue.setType("SYNTAX_ERROR");
                issue.setSeverity("WARNING");
                issue.setMessage("Potentially missing import: " + imp);
                issues.add(issue);
            }
            
            // Security validation
            if (containsNewVulnerabilities(fix.getFixedCode())) {
                ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue();
                issue.setType("SECURITY_CONCERN");
                issue.setSeverity("ERROR");
                issue.setMessage("Fix may introduce new security vulnerabilities");
                issues.add(issue);
            }
            
            result.setValid(issues.isEmpty() || 
                issues.stream().noneMatch(i -> "ERROR".equals(i.getSeverity())));
            result.setIssues(issues);
            
            return result;
        });
    }
    
    @Override
    public Map<String, FixTemplate> getFixTemplates() {
        return fixTemplates;
    }
    
    private Map<String, FixTemplate> initializeTemplates() {
        Map<String, FixTemplate> templates = new HashMap<>();
        
        // SQL Injection fix template
        FixTemplate sqlInjection = new FixTemplate();
        sqlInjection.setVulnerabilityType("SQL_INJECTION");
        sqlInjection.setLanguage("java");
        sqlInjection.setPattern("Statement.*executeQuery\\s*\\(.*\\+.*\\)");
        sqlInjection.setReplacement("PreparedStatement pstmt = connection.prepareStatement(?);\n" +
                                   "pstmt.setString(1, userInput);\n" +
                                   "ResultSet rs = pstmt.executeQuery();");
        sqlInjection.setExplanation("Use PreparedStatement to prevent SQL injection");
        sqlInjection.setRequiredImports(Arrays.asList("java.sql.PreparedStatement"));
        templates.put("SQL_INJECTION", sqlInjection);
        
        // XSS fix template
        FixTemplate xss = new FixTemplate();
        xss.setVulnerabilityType("XSS");
        xss.setLanguage("java");
        xss.setPattern("response\\.getWriter\\(\\)\\.write\\(.*request\\.getParameter.*\\)");
        xss.setReplacement("response.getWriter().write(StringEscapeUtils.escapeHtml4(request.getParameter(?)))");
        xss.setExplanation("Escape HTML to prevent XSS attacks");
        xss.setRequiredImports(Arrays.asList("org.apache.commons.text.StringEscapeUtils"));
        templates.put("XSS", xss);
        
        // Path Traversal fix template
        FixTemplate pathTraversal = new FixTemplate();
        pathTraversal.setVulnerabilityType("PATH_TRAVERSAL");
        pathTraversal.setLanguage("java");
        pathTraversal.setPattern("new File\\s*\\(.*request\\.getParameter.*\\)");
        pathTraversal.setReplacement("String filename = Paths.get(request.getParameter(?)).getFileName().toString();\n" +
                                    "File file = new File(safeDirectory, filename);");
        pathTraversal.setExplanation("Validate and sanitize file paths to prevent directory traversal");
        pathTraversal.setRequiredImports(Arrays.asList("java.nio.file.Paths"));
        templates.put("PATH_TRAVERSAL", pathTraversal);
        
        // Weak Crypto fix template
        FixTemplate weakCrypto = new FixTemplate();
        weakCrypto.setVulnerabilityType("WEAK_CRYPTO");
        weakCrypto.setLanguage("java");
        weakCrypto.setPattern("MessageDigest\\.getInstance\\s*\\(\\s*[\"'](?:MD5|SHA1)[\"']\\s*\\)");
        weakCrypto.setReplacement("MessageDigest.getInstance(\"SHA-256\")");
        weakCrypto.setExplanation("Use strong cryptographic algorithms");
        templates.put("WEAK_CRYPTO", weakCrypto);
        
        return templates;
    }
    
    private FixResult applyTemplateFix(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            FixTemplate template) {
        
        FixResult result = new FixResult();
        String originalCode = context.getFileContent();
        
        try {
            // Apply template replacement
            Pattern pattern = Pattern.compile(template.getPattern());
            Matcher matcher = pattern.matcher(originalCode);
            
            if (matcher.find()) {
                String fixedCode = matcher.replaceAll(template.getReplacement());
                
                // Add required imports
                if (template.getRequiredImports() != null && !template.getRequiredImports().isEmpty()) {
                    fixedCode = addImports(fixedCode, template.getRequiredImports());
                }
                
                result.setSuccess(true);
                result.setFixedCode(fixedCode);
                result.setExplanation(template.getExplanation());
                result.setConfidence(0.9); // High confidence for template fixes
                
                // Record changes
                List<FixResult.CodeChange> changes = new ArrayList<>();
                FixResult.CodeChange change = new FixResult.CodeChange();
                change.setStartLine(vulnerability.getLineNumber());
                change.setEndLine(vulnerability.getLineNumber());
                change.setOriginalCode(vulnerability.getAffectedCode());
                change.setFixedCode(template.getReplacement());
                change.setChangeType("MODIFY");
                change.setReason(template.getExplanation());
                changes.add(change);
                result.setChanges(changes);
            } else {
                // Template didn't match, fall back to AI
                return generateAIFix(vulnerability, context);
            }
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setExplanation("Template application failed: " + e.getMessage());
        }
        
        return result;
    }
    
    private FixResult generateAIFix(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context) {
        
        FixResult result = new FixResult();
        
        try {
            // Get relevant context
            ContextManager.SessionContext sessionContext = 
                contextManager.getOrCreateSession(context.getSessionId());
            List<ContextManager.RelevantContext> relevantContexts = 
                sessionContext.getRelevantContext(vulnerability.getAffectedCode(), 5);
            
            // Build prompt
            String prompt = buildJavaFixPrompt(vulnerability, context, relevantContexts);
            
            // Generate fix using AI
            UserMessage userMessage = UserMessage.from(prompt);
            AiMessage response = codeGenerationModel.generate(userMessage);
            
            // Parse AI response
            parseAIFixResponse(response.text(), result, vulnerability);
            
            // Add Java-specific validations
            if (result.isSuccess()) {
                addJavaSpecificValidations(result, context);
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error generating AI fix: " + e.getMessage());
            result.setSuccess(false);
            result.setExplanation("AI fix generation failed: " + e.getMessage());
        }
        
        return result;
    }
    
    private String buildJavaFixPrompt(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            List<ContextManager.RelevantContext> relevantContexts) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Java security expert. Generate a secure fix for the following vulnerability:\n\n");
        
        prompt.append("## Vulnerability Details\n");
        prompt.append("- Type: ").append(vulnerability.getVulnerability().getTitle()).append("\n");
        prompt.append("- Severity: ").append(vulnerability.getVulnerability().getSeverity()).append("\n");
        prompt.append("- File: ").append(vulnerability.getFilePath()).append("\n");
        prompt.append("- Line: ").append(vulnerability.getLineNumber()).append("\n\n");
        
        prompt.append("## Vulnerable Code\n```java\n");
        prompt.append(vulnerability.getAffectedCode());
        prompt.append("\n```\n\n");
        
        prompt.append("## Full File Context\n```java\n");
        prompt.append(context.getFileContent());
        prompt.append("\n```\n\n");
        
        if (!relevantContexts.isEmpty()) {
            prompt.append("## Related Code Context\n");
            for (ContextManager.RelevantContext ctx : relevantContexts) {
                prompt.append("### ").append(ctx.getFilePath()).append("\n");
                prompt.append("```java\n").append(ctx.getContent()).append("\n```\n\n");
            }
        }
        
        prompt.append("## Java Security Requirements\n");
        prompt.append("1. Use parameterized queries for database operations\n");
        prompt.append("2. Validate and sanitize all user inputs\n");
        prompt.append("3. Use strong encryption algorithms (AES-256, RSA-2048+)\n");
        prompt.append("4. Follow OWASP Java security guidelines\n");
        prompt.append("5. Use security libraries from Spring Security or Apache Shiro when appropriate\n");
        prompt.append("6. Implement proper exception handling without information leakage\n\n");
        
        prompt.append("## Output Format\n");
        prompt.append("Provide the fix in the following format:\n");
        prompt.append("FIXED_CODE:\n```java\n[Complete fixed file content]\n```\n");
        prompt.append("CHANGES:\n- Line X: [Description of change]\n");
        prompt.append("EXPLANATION: [Security explanation]\n");
        prompt.append("IMPORTS: [Required new imports, comma-separated]\n");
        
        return prompt.toString();
    }
    
    private void parseAIFixResponse(String response, FixResult result, 
            ScanResult.VulnerabilityMatch vulnerability) {
        
        try {
            // Extract fixed code
            Pattern codePattern = Pattern.compile("FIXED_CODE:\\s*```java\\s*([\\s\\S]*?)```", Pattern.MULTILINE);
            Matcher codeMatcher = codePattern.matcher(response);
            
            if (codeMatcher.find()) {
                result.setFixedCode(codeMatcher.group(1).trim());
                result.setSuccess(true);
            }
            
            // Extract changes
            Pattern changesPattern = Pattern.compile("CHANGES:\\s*([\\s\\S]*?)(?=EXPLANATION:|$)", Pattern.MULTILINE);
            Matcher changesMatcher = changesPattern.matcher(response);
            
            if (changesMatcher.find()) {
                List<FixResult.CodeChange> changes = parseChanges(changesMatcher.group(1));
                result.setChanges(changes);
            }
            
            // Extract explanation
            Pattern explanationPattern = Pattern.compile("EXPLANATION:\\s*([\\s\\S]*?)(?=IMPORTS:|$)", Pattern.MULTILINE);
            Matcher explanationMatcher = explanationPattern.matcher(response);
            
            if (explanationMatcher.find()) {
                result.setExplanation(explanationMatcher.group(1).trim());
            }
            
            // Extract imports
            Pattern importsPattern = Pattern.compile("IMPORTS:\\s*([\\s\\S]*?)$", Pattern.MULTILINE);
            Matcher importsMatcher = importsPattern.matcher(response);
            
            if (importsMatcher.find()) {
                String imports = importsMatcher.group(1).trim();
                if (!imports.isEmpty() && !imports.equals("None")) {
                    List<String> importList = Arrays.asList(imports.split(",\\s*"));
                    result.setFixedCode(addImports(result.getFixedCode(), importList));
                }
            }
            
            result.setConfidence(0.85); // Good confidence for AI fixes
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setExplanation("Failed to parse AI response: " + e.getMessage());
        }
    }
    
    private List<FixResult.CodeChange> parseChanges(String changesText) {
        List<FixResult.CodeChange> changes = new ArrayList<>();
        String[] lines = changesText.split("\n");
        
        for (String line : lines) {
            if (line.trim().startsWith("-")) {
                FixResult.CodeChange change = new FixResult.CodeChange();
                // Simple parsing - can be improved
                change.setChangeType("MODIFY");
                change.setReason(line.substring(1).trim());
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    private String addImports(String code, List<String> imports) {
        StringBuilder result = new StringBuilder();
        
        // Find package declaration
        Pattern packagePattern = Pattern.compile("package\\s+[\\w.]+;");
        Matcher packageMatcher = packagePattern.matcher(code);
        
        if (packageMatcher.find()) {
            // Add imports after package declaration
            int insertPos = packageMatcher.end();
            result.append(code.substring(0, insertPos));
            result.append("\n\n");
            
            for (String imp : imports) {
                if (!code.contains("import " + imp)) {
                    result.append("import ").append(imp).append(";\n");
                }
            }
            
            result.append(code.substring(insertPos));
        } else {
            // No package declaration, add at beginning
            for (String imp : imports) {
                if (!code.contains("import " + imp)) {
                    result.append("import ").append(imp).append(";\n");
                }
            }
            result.append("\n").append(code);
        }
        
        return result.toString();
    }
    
    private boolean validateJavaSyntax(String code) {
        // Basic syntax validation
        // In production, use Java parser like JavaParser
        return code.contains("class") || code.contains("interface") || code.contains("enum");
    }
    
    private List<String> checkMissingImports(String code) {
        List<String> missing = new ArrayList<>();
        
        // Check for common classes that need imports
        Map<String, String> commonImports = Map.of(
            "PreparedStatement", "java.sql.PreparedStatement",
            "StringEscapeUtils", "org.apache.commons.text.StringEscapeUtils",
            "Paths", "java.nio.file.Paths",
            "SecureRandom", "java.security.SecureRandom",
            "MessageDigest", "java.security.MessageDigest"
        );
        
        for (Map.Entry<String, String> entry : commonImports.entrySet()) {
            if (code.contains(entry.getKey()) && !code.contains("import " + entry.getValue())) {
                missing.add(entry.getValue());
            }
        }
        
        return missing;
    }
    
    private boolean containsNewVulnerabilities(String code) {
        // Check for common vulnerability patterns
        List<String> vulnerablePatterns = Arrays.asList(
            "Statement.*executeQuery.*\\+",
            "Runtime\\.getRuntime\\(\\)\\.exec",
            "new File\\(request\\.getParameter",
            "MessageDigest\\.getInstance\\(\"MD5\"\\)",
            "MessageDigest\\.getInstance\\(\"SHA1\"\\)"
        );
        
        for (String pattern : vulnerablePatterns) {
            if (code.matches(".*" + pattern + ".*")) {
                return true;
            }
        }
        
        return false;
    }
    
    private void addJavaSpecificValidations(FixResult result, FixContext context) {
        List<String> warnings = new ArrayList<>();
        
        // Check for proper resource management
        if (result.getFixedCode().contains("Connection") && 
            !result.getFixedCode().contains("try-with-resources") &&
            !result.getFixedCode().contains(".close()")) {
            warnings.add("Consider using try-with-resources for proper resource management");
        }
        
        // Check for null checks
        if (result.getFixedCode().contains(".equals(") && 
            !result.getFixedCode().contains("!= null")) {
            warnings.add("Consider adding null checks before method calls");
        }
        
        // Check for thread safety
        if (result.getFixedCode().contains("static") && 
            result.getFixedCode().contains("HashMap") &&
            !result.getFixedCode().contains("ConcurrentHashMap")) {
            warnings.add("Consider using ConcurrentHashMap for thread-safe static maps");
        }
        
        result.setWarnings(warnings);
    }
}