package ai.intelliswarm.vulnpatcher.fixes;

import ai.intelliswarm.vulnpatcher.config.CodeGeneration;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Component
public class PythonFixGenerator implements FixGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(PythonFixGenerator.class.getName());
    
    @Inject
    @CodeGeneration
    OllamaChatModel codeGenerationModel;
    
    @Inject
    ContextManager contextManager;
    
    private final Map<String, FixTemplate> fixTemplates = initializeTemplates();
    
    @Override
    public String getSupportedLanguage() {
        return "python";
    }
    
    @Override
    public boolean canHandle(ScanResult.VulnerabilityMatch vulnerability) {
        String filePath = vulnerability.getFilePath();
        return filePath != null && (filePath.endsWith(".py") || 
               filePath.endsWith("requirements.txt") || filePath.endsWith("setup.py"));
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
                LOGGER.severe("Error generating Python fix: " + e.getMessage());
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
            
            // Python syntax validation
            if (!validatePythonSyntax(fix.getFixedCode())) {
                ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue();
                issue.setType("SYNTAX_ERROR");
                issue.setSeverity("ERROR");
                issue.setMessage("Python syntax error in generated fix");
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
        sqlInjection.setLanguage("python");
        sqlInjection.setPattern("cursor\\.execute\\s*\\(.*%.*\\)");
        sqlInjection.setReplacement("cursor.execute(\"SELECT * FROM users WHERE id = %s\", (user_id,))");
        sqlInjection.setExplanation("Use parameterized queries to prevent SQL injection");
        templates.put("SQL_INJECTION", sqlInjection);
        
        // Command Injection fix template
        FixTemplate commandInjection = new FixTemplate();
        commandInjection.setVulnerabilityType("COMMAND_INJECTION");
        commandInjection.setLanguage("python");
        commandInjection.setPattern("os\\.system\\s*\\(.*\\+.*\\)");
        commandInjection.setReplacement("subprocess.run([command, arg], check=True, capture_output=True, text=True)");
        commandInjection.setExplanation("Use subprocess with list arguments to prevent command injection");
        commandInjection.setRequiredImports(Arrays.asList("subprocess"));
        templates.put("COMMAND_INJECTION", commandInjection);
        
        // Path Traversal fix template
        FixTemplate pathTraversal = new FixTemplate();
        pathTraversal.setVulnerabilityType("PATH_TRAVERSAL");
        pathTraversal.setLanguage("python");
        pathTraversal.setPattern("open\\s*\\(.*request\\..*\\)");
        pathTraversal.setReplacement("safe_path = os.path.join(base_dir, os.path.basename(user_input))\n" +
                                    "with open(safe_path, 'r') as f:");
        pathTraversal.setExplanation("Validate and sanitize file paths to prevent directory traversal");
        pathTraversal.setRequiredImports(Arrays.asList("os"));
        templates.put("PATH_TRAVERSAL", pathTraversal);
        
        // Weak Crypto fix template
        FixTemplate weakCrypto = new FixTemplate();
        weakCrypto.setVulnerabilityType("WEAK_CRYPTO");
        weakCrypto.setLanguage("python");
        weakCrypto.setPattern("hashlib\\.(md5|sha1)\\s*\\(");
        weakCrypto.setReplacement("hashlib.sha256(");
        weakCrypto.setExplanation("Use strong cryptographic algorithms");
        templates.put("WEAK_CRYPTO", weakCrypto);
        
        // Insecure Deserialization fix template
        FixTemplate insecurePickle = new FixTemplate();
        insecurePickle.setVulnerabilityType("INSECURE_DESERIALIZATION");
        insecurePickle.setLanguage("python");
        insecurePickle.setPattern("pickle\\.loads?\\s*\\(");
        insecurePickle.setReplacement("json.loads(");
        insecurePickle.setExplanation("Use JSON instead of pickle for untrusted data");
        insecurePickle.setRequiredImports(Arrays.asList("json"));
        templates.put("INSECURE_DESERIALIZATION", insecurePickle);
        
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
                    fixedCode = addPythonImports(fixedCode, template.getRequiredImports());
                }
                
                result.setSuccess(true);
                result.setFixedCode(fixedCode);
                result.setExplanation(template.getExplanation());
                result.setConfidence(0.9);
                
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
            String prompt = buildPythonFixPrompt(vulnerability, context, relevantContexts);
            
            // Generate fix using AI
            UserMessage userMessage = UserMessage.from(prompt);
            AiMessage response = codeGenerationModel.generate(userMessage);
            
            // Parse AI response
            parseAIFixResponse(response.text(), result, vulnerability);
            
            // Add Python-specific validations
            if (result.isSuccess()) {
                addPythonSpecificValidations(result, context);
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error generating AI fix: " + e.getMessage());
            result.setSuccess(false);
            result.setExplanation("AI fix generation failed: " + e.getMessage());
        }
        
        return result;
    }
    
    private String buildPythonFixPrompt(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            List<ContextManager.RelevantContext> relevantContexts) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Python security expert. Generate a secure fix for the following vulnerability:\n\n");
        
        prompt.append("## Vulnerability Details\n");
        prompt.append("- Type: ").append(vulnerability.getVulnerability().getTitle()).append("\n");
        prompt.append("- Severity: ").append(vulnerability.getVulnerability().getSeverity()).append("\n");
        prompt.append("- File: ").append(vulnerability.getFilePath()).append("\n");
        prompt.append("- Line: ").append(vulnerability.getLineNumber()).append("\n\n");
        
        prompt.append("## Vulnerable Code\n```python\n");
        prompt.append(vulnerability.getAffectedCode());
        prompt.append("\n```\n\n");
        
        prompt.append("## Full File Context\n```python\n");
        prompt.append(context.getFileContent());
        prompt.append("\n```\n\n");
        
        if (!relevantContexts.isEmpty()) {
            prompt.append("## Related Code Context\n");
            for (ContextManager.RelevantContext ctx : relevantContexts) {
                prompt.append("### ").append(ctx.getFilePath()).append("\n");
                prompt.append("```python\n").append(ctx.getContent()).append("\n```\n\n");
            }
        }
        
        prompt.append("## Python Security Requirements\n");
        prompt.append("1. Use parameterized queries for database operations\n");
        prompt.append("2. Validate and sanitize all user inputs\n");
        prompt.append("3. Use subprocess instead of os.system for command execution\n");
        prompt.append("4. Avoid eval() and exec() with user input\n");
        prompt.append("5. Use secrets module for cryptographic randomness\n");
        prompt.append("6. Follow OWASP Python security guidelines\n");
        prompt.append("7. Use type hints for better code safety\n\n");
        
        prompt.append("## Output Format\n");
        prompt.append("Provide the fix in the following format:\n");
        prompt.append("FIXED_CODE:\n```python\n[Complete fixed file content]\n```\n");
        prompt.append("CHANGES:\n- Line X: [Description of change]\n");
        prompt.append("EXPLANATION: [Security explanation]\n");
        prompt.append("IMPORTS: [Required new imports, comma-separated]\n");
        
        return prompt.toString();
    }
    
    private void parseAIFixResponse(String response, FixResult result, 
            ScanResult.VulnerabilityMatch vulnerability) {
        
        try {
            // Extract fixed code
            Pattern codePattern = Pattern.compile("FIXED_CODE:\\s*```python\\s*([\\s\\S]*?)```", Pattern.MULTILINE);
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
                    result.setFixedCode(addPythonImports(result.getFixedCode(), importList));
                }
            }
            
            result.setConfidence(0.85);
            
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
                change.setChangeType("MODIFY");
                change.setReason(line.substring(1).trim());
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    private String addPythonImports(String code, List<String> imports) {
        StringBuilder result = new StringBuilder();
        List<String> existingImports = new ArrayList<>();
        List<String> codeLines = new ArrayList<>();
        boolean inImportSection = true;
        
        // Parse existing code
        String[] lines = code.split("\n");
        for (String line : lines) {
            if (inImportSection && (line.startsWith("import ") || line.startsWith("from "))) {
                existingImports.add(line);
            } else if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                inImportSection = false;
                codeLines.add(line);
            } else if (line.trim().startsWith("#") && existingImports.isEmpty()) {
                codeLines.add(line); // Keep initial comments
            } else {
                codeLines.add(line);
            }
        }
        
        // Add existing imports
        for (String imp : existingImports) {
            result.append(imp).append("\n");
        }
        
        // Add new imports
        for (String imp : imports) {
            boolean exists = existingImports.stream()
                .anyMatch(existing -> existing.contains(imp));
            if (!exists) {
                result.append("import ").append(imp).append("\n");
            }
        }
        
        // Add blank line after imports
        if (!existingImports.isEmpty() || !imports.isEmpty()) {
            result.append("\n");
        }
        
        // Add rest of code
        for (String line : codeLines) {
            result.append(line).append("\n");
        }
        
        return result.toString();
    }
    
    private boolean validatePythonSyntax(String code) {
        // Basic syntax validation
        // Check for proper indentation
        String[] lines = code.split("\n");
        int currentIndent = 0;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            int indent = line.length() - line.trim().length();
            
            // Check for consistent indentation
            if (indent % 4 != 0) {
                return false; // Python typically uses 4-space indentation
            }
            
            // Check for colon before indent increase
            if (line.trim().endsWith(":")) {
                currentIndent = indent + 4;
            } else if (indent > currentIndent) {
                return false; // Unexpected indent
            }
        }
        
        return true;
    }
    
    private List<String> checkMissingImports(String code) {
        List<String> missing = new ArrayList<>();
        
        // Check for common modules that need imports
        Map<String, String> commonImports = Map.of(
            "subprocess\\.run", "subprocess",
            "hashlib\\.", "hashlib",
            "secrets\\.", "secrets",
            "os\\.path", "os",
            "json\\.", "json",
            "re\\.", "re"
        );
        
        for (Map.Entry<String, String> entry : commonImports.entrySet()) {
            if (code.matches(".*" + entry.getKey() + ".*") && 
                !code.contains("import " + entry.getValue())) {
                missing.add(entry.getValue());
            }
        }
        
        return missing;
    }
    
    private boolean containsNewVulnerabilities(String code) {
        // Check for common vulnerability patterns in Python
        List<String> vulnerablePatterns = Arrays.asList(
            "eval\\s*\\(",
            "exec\\s*\\(",
            "os\\.system\\s*\\(",
            "__import__\\s*\\(",
            "pickle\\.loads?\\s*\\(",
            "yaml\\.load\\s*\\([^,]+\\)\\s*(?!.*Loader=)",
            "hashlib\\.md5\\s*\\(",
            "hashlib\\.sha1\\s*\\(",
            "random\\.random\\s*\\("
        );
        
        for (String pattern : vulnerablePatterns) {
            if (code.matches(".*" + pattern + ".*")) {
                return true;
            }
        }
        
        return false;
    }
    
    private void addPythonSpecificValidations(FixResult result, FixContext context) {
        List<String> warnings = new ArrayList<>();
        
        // Check for proper exception handling
        if (result.getFixedCode().contains("except:") || 
            result.getFixedCode().contains("except Exception:")) {
            warnings.add("Consider using specific exception types instead of broad exception handling");
        }
        
        // Check for type hints
        if (!result.getFixedCode().contains("->") && !result.getFixedCode().contains(": ")) {
            warnings.add("Consider adding type hints for better code safety");
        }
        
        // Check for f-strings with user input
        if (result.getFixedCode().contains("f\"") || result.getFixedCode().contains("f'")) {
            warnings.add("Be careful with f-strings containing user input");
        }
        
        // Check for proper context managers
        if (result.getFixedCode().contains("open(") && 
            !result.getFixedCode().contains("with open(")) {
            warnings.add("Use context managers (with statement) for file operations");
        }
        
        result.setWarnings(warnings);
    }
}