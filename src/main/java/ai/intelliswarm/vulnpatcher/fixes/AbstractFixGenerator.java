package ai.intelliswarm.vulnpatcher.fixes;

import ai.intelliswarm.vulnpatcher.config.CodeGeneration;
import ai.intelliswarm.vulnpatcher.config.CodeReview;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ChatMessage;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractFixGenerator implements FixGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(AbstractFixGenerator.class.getName());
    
    @Inject
    @CodeGeneration
    protected OllamaChatModel codeGenerationModel;
    
    @Inject
    @CodeReview
    protected OllamaChatModel reviewModel;
    
    @Inject
    protected ContextManager contextManager;
    
    protected abstract String getLanguageName();
    protected abstract Map<String, FixTemplate> initializeLanguageTemplates();
    protected abstract List<String> getLanguageSpecificSecurityGuidelines();
    protected abstract boolean validateLanguageSyntax(String code);
    protected abstract List<String> detectMissingDependencies(String code);
    protected abstract String addLanguageImports(String code, List<String> imports);
    
    @Override
    public CompletableFuture<FixResult> generateFix(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Analyze the vulnerability context
                VulnerabilityAnalysis analysis = analyzeVulnerabilityContext(vulnerability, context);
                
                // Step 2: Generate multiple fix candidates
                List<FixCandidate> candidates = generateFixCandidates(vulnerability, context, analysis);
                
                // Step 3: Evaluate and rank candidates
                FixCandidate bestCandidate = evaluateAndSelectBestFix(candidates, context);
                
                // Step 4: Refine the selected fix
                FixResult refinedFix = refineSelectedFix(bestCandidate, vulnerability, context);
                
                // Step 5: Validate the fix doesn't break functionality
                ValidationResult validation = validateFunctionality(refinedFix, context).get();
                
                if (!validation.isValid()) {
                    // If validation fails, try to auto-correct
                    refinedFix = autoCorrectFix(refinedFix, validation, context);
                }
                
                return refinedFix;
                
            } catch (Exception e) {
                LOGGER.severe("Error generating fix: " + e.getMessage());
                FixResult errorResult = new FixResult();
                errorResult.setSuccess(false);
                errorResult.setExplanation("Failed to generate fix: " + e.getMessage());
                return errorResult;
            }
        });
    }
    
    protected VulnerabilityAnalysis analyzeVulnerabilityContext(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context) {
        
        VulnerabilityAnalysis analysis = new VulnerabilityAnalysis();
        
        // Get surrounding code context
        String[] lines = context.getFileContent().split("\n");
        int vulnLine = vulnerability.getLineNumber() - 1; // Convert to 0-based
        
        // Extract method/function context
        analysis.setMethodContext(extractMethodContext(lines, vulnLine));
        
        // Identify data flow
        analysis.setDataFlow(analyzeDataFlow(lines, vulnLine, vulnerability.getAffectedCode()));
        
        // Detect existing security measures
        analysis.setExistingSecurityMeasures(detectExistingSecurityMeasures(lines));
        
        // Analyze dependencies and imports
        analysis.setUsedDependencies(extractUsedDependencies(context.getFileContent()));
        
        // Get related code from other files
        ContextManager.SessionContext sessionContext = 
            contextManager.getOrCreateSession(context.getSessionId());
        List<ContextManager.RelevantContext> relatedCode = 
            sessionContext.getRelevantContext(vulnerability.getAffectedCode(), 10);
        analysis.setRelatedCode(relatedCode);
        
        return analysis;
    }
    
    protected List<FixCandidate> generateFixCandidates(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            VulnerabilityAnalysis analysis) {
        
        List<FixCandidate> candidates = new ArrayList<>();
        
        // 1. Template-based fix (if available)
        FixTemplate template = getFixTemplates().get(vulnerability.getVulnerability().getId());
        if (template != null) {
            FixCandidate templateCandidate = new FixCandidate();
            templateCandidate.setType("TEMPLATE");
            templateCandidate.setCode(applyTemplate(template, vulnerability, context));
            templateCandidate.setConfidence(0.85);
            candidates.add(templateCandidate);
        }
        
        // 2. LLM-generated fixes with different strategies
        candidates.addAll(generateLLMFixCandidates(vulnerability, context, analysis));
        
        return candidates;
    }
    
    protected List<FixCandidate> generateLLMFixCandidates(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            VulnerabilityAnalysis analysis) {
        
        List<FixCandidate> candidates = new ArrayList<>();
        
        // Strategy 1: Minimal change fix
        String minimalPrompt = buildMinimalChangePrompt(vulnerability, context, analysis);
        FixCandidate minimalFix = generateLLMFix(minimalPrompt, "MINIMAL");
        if (minimalFix != null) candidates.add(minimalFix);
        
        // Strategy 2: Best practice fix
        String bestPracticePrompt = buildBestPracticePrompt(vulnerability, context, analysis);
        FixCandidate bestPracticeFix = generateLLMFix(bestPracticePrompt, "BEST_PRACTICE");
        if (bestPracticeFix != null) candidates.add(bestPracticeFix);
        
        // Strategy 3: Defensive programming fix
        String defensivePrompt = buildDefensivePrompt(vulnerability, context, analysis);
        FixCandidate defensiveFix = generateLLMFix(defensivePrompt, "DEFENSIVE");
        if (defensiveFix != null) candidates.add(defensiveFix);
        
        return candidates;
    }
    
    protected String buildMinimalChangePrompt(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            VulnerabilityAnalysis analysis) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert ").append(getLanguageName()).append(" security engineer.\n");
        prompt.append("Generate a MINIMAL fix that:\n");
        prompt.append("1. Fixes the security vulnerability with the least code changes\n");
        prompt.append("2. Preserves ALL existing functionality\n");
        prompt.append("3. Maintains the current code style and patterns\n");
        prompt.append("4. Does not introduce new dependencies unless absolutely necessary\n\n");
        
        appendVulnerabilityContext(prompt, vulnerability, analysis);
        appendCodeContext(prompt, context, analysis);
        appendSecurityGuidelines(prompt);
        
        prompt.append("\n## Critical Requirements\n");
        prompt.append("- The fix MUST be backward compatible\n");
        prompt.append("- Do NOT change method signatures\n");
        prompt.append("- Do NOT modify unrelated code\n");
        prompt.append("- Preserve all existing behavior except the security issue\n");
        
        appendOutputFormat(prompt);
        
        return prompt.toString();
    }
    
    protected String buildBestPracticePrompt(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            VulnerabilityAnalysis analysis) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert ").append(getLanguageName()).append(" security engineer.\n");
        prompt.append("Generate a fix following BEST PRACTICES that:\n");
        prompt.append("1. Implements the most secure solution using industry standards\n");
        prompt.append("2. Uses well-tested security libraries when appropriate\n");
        prompt.append("3. Follows ").append(getLanguageName()).append(" security guidelines\n");
        prompt.append("4. Includes proper error handling and logging\n\n");
        
        appendVulnerabilityContext(prompt, vulnerability, analysis);
        appendCodeContext(prompt, context, analysis);
        appendSecurityGuidelines(prompt);
        
        prompt.append("\n## Best Practice Requirements\n");
        prompt.append("- Use established security libraries (e.g., OWASP, Spring Security)\n");
        prompt.append("- Implement defense in depth\n");
        prompt.append("- Add appropriate security headers/configurations\n");
        prompt.append("- Include security-focused comments\n");
        
        appendOutputFormat(prompt);
        
        return prompt.toString();
    }
    
    protected String buildDefensivePrompt(
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context,
            VulnerabilityAnalysis analysis) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert ").append(getLanguageName()).append(" security engineer.\n");
        prompt.append("Generate a DEFENSIVE fix that:\n");
        prompt.append("1. Assumes all inputs are malicious\n");
        prompt.append("2. Implements multiple layers of validation\n");
        prompt.append("3. Fails securely with proper error handling\n");
        prompt.append("4. Includes comprehensive logging for security events\n\n");
        
        appendVulnerabilityContext(prompt, vulnerability, analysis);
        appendCodeContext(prompt, context, analysis);
        appendSecurityGuidelines(prompt);
        
        prompt.append("\n## Defensive Programming Requirements\n");
        prompt.append("- Validate ALL inputs at multiple levels\n");
        prompt.append("- Implement whitelisting over blacklisting\n");
        prompt.append("- Use principle of least privilege\n");
        prompt.append("- Add security event logging\n");
        prompt.append("- Handle all error cases explicitly\n");
        
        appendOutputFormat(prompt);
        
        return prompt.toString();
    }
    
    protected void appendVulnerabilityContext(
            StringBuilder prompt,
            ScanResult.VulnerabilityMatch vulnerability,
            VulnerabilityAnalysis analysis) {
        
        prompt.append("\n## Vulnerability Details\n");
        prompt.append("Type: ").append(vulnerability.getVulnerability().getTitle()).append("\n");
        prompt.append("Severity: ").append(vulnerability.getVulnerability().getSeverity()).append("\n");
        prompt.append("Line: ").append(vulnerability.getLineNumber()).append("\n");
        prompt.append("Affected Code:\n```").append(getLanguageName().toLowerCase()).append("\n");
        prompt.append(vulnerability.getAffectedCode()).append("\n```\n");
        
        if (analysis.getMethodContext() != null) {
            prompt.append("\n## Method Context\n```").append(getLanguageName().toLowerCase()).append("\n");
            prompt.append(analysis.getMethodContext()).append("\n```\n");
        }
    }
    
    protected void appendCodeContext(
            StringBuilder prompt,
            FixContext context,
            VulnerabilityAnalysis analysis) {
        
        prompt.append("\n## Full File Context\n```").append(getLanguageName().toLowerCase()).append("\n");
        prompt.append(context.getFileContent()).append("\n```\n");
        
        if (!analysis.getRelatedCode().isEmpty()) {
            prompt.append("\n## Related Code Usage\n");
            for (ContextManager.RelevantContext related : analysis.getRelatedCode()) {
                prompt.append("File: ").append(related.getFilePath()).append("\n");
                prompt.append("```").append(getLanguageName().toLowerCase()).append("\n");
                prompt.append(related.getContent()).append("\n```\n\n");
            }
        }
    }
    
    protected void appendSecurityGuidelines(StringBuilder prompt) {
        prompt.append("\n## Security Guidelines\n");
        for (String guideline : getLanguageSpecificSecurityGuidelines()) {
            prompt.append("- ").append(guideline).append("\n");
        }
    }
    
    protected void appendOutputFormat(StringBuilder prompt) {
        prompt.append("\n## Output Format\n");
        prompt.append("FIXED_CODE:\n```").append(getLanguageName().toLowerCase()).append("\n");
        prompt.append("[Complete fixed file content]\n```\n");
        prompt.append("CHANGES_MADE:\n[List each change and why]\n");
        prompt.append("FUNCTIONALITY_PRESERVED:\n[Explain how existing functionality is maintained]\n");
        prompt.append("NEW_IMPORTS:\n[Any new imports needed, comma-separated]\n");
        prompt.append("TESTING_NOTES:\n[How to test the fix]\n");
    }
    
    protected FixCandidate generateLLMFix(String prompt, String strategy) {
        try {
            // Create messages with system context
            SystemMessage systemMessage = SystemMessage.from(
                "You are an expert security engineer specializing in " + getLanguageName() + 
                ". You must generate secure fixes that preserve existing functionality."
            );
            UserMessage userMessage = UserMessage.from(prompt);
            
            // Generate fix
            AiMessage response = codeGenerationModel.generate(Arrays.asList(systemMessage, userMessage));
            
            // Parse response
            FixCandidate candidate = parseLLMResponse(response.text());
            candidate.setType(strategy);
            candidate.setConfidence(0.8); // Base confidence for LLM fixes
            
            return candidate;
            
        } catch (Exception e) {
            LOGGER.warning("Failed to generate " + strategy + " fix: " + e.getMessage());
            return null;
        }
    }
    
    protected FixCandidate parseLLMResponse(String response) {
        FixCandidate candidate = new FixCandidate();
        
        // Extract fixed code
        Pattern codePattern = Pattern.compile(
            "FIXED_CODE:\\s*```" + getLanguageName().toLowerCase() + "\\s*([\\s\\S]*?)```",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher codeMatcher = codePattern.matcher(response);
        if (codeMatcher.find()) {
            candidate.setCode(codeMatcher.group(1).trim());
        }
        
        // Extract changes
        Pattern changesPattern = Pattern.compile(
            "CHANGES_MADE:\\s*([\\s\\S]*?)(?=FUNCTIONALITY_PRESERVED:|$)",
            Pattern.MULTILINE
        );
        java.util.regex.Matcher changesMatcher = changesPattern.matcher(response);
        if (changesMatcher.find()) {
            candidate.setChangesDescription(changesMatcher.group(1).trim());
        }
        
        // Extract functionality preservation notes
        Pattern funcPattern = Pattern.compile(
            "FUNCTIONALITY_PRESERVED:\\s*([\\s\\S]*?)(?=NEW_IMPORTS:|$)",
            Pattern.MULTILINE
        );
        java.util.regex.Matcher funcMatcher = funcPattern.matcher(response);
        if (funcMatcher.find()) {
            candidate.setFunctionalityNotes(funcMatcher.group(1).trim());
        }
        
        // Extract imports
        Pattern importsPattern = Pattern.compile(
            "NEW_IMPORTS:\\s*([\\s\\S]*?)(?=TESTING_NOTES:|$)",
            Pattern.MULTILINE
        );
        java.util.regex.Matcher importsMatcher = importsPattern.matcher(response);
        if (importsMatcher.find()) {
            String imports = importsMatcher.group(1).trim();
            if (!imports.isEmpty() && !imports.equalsIgnoreCase("none")) {
                candidate.setNewImports(Arrays.asList(imports.split(",\\s*")));
            }
        }
        
        return candidate;
    }
    
    protected FixCandidate evaluateAndSelectBestFix(
            List<FixCandidate> candidates,
            FixContext context) {
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // Evaluate each candidate
        for (FixCandidate candidate : candidates) {
            evaluateCandidate(candidate, context);
        }
        
        // Sort by score and select best
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        return candidates.get(0);
    }
    
    protected void evaluateCandidate(FixCandidate candidate, FixContext context) {
        double score = candidate.getConfidence();
        
        // Factor 1: Code change size (prefer minimal changes)
        int changeSize = calculateChangeSize(context.getFileContent(), candidate.getCode());
        score *= (1.0 - Math.min(changeSize / 1000.0, 0.5)); // Penalize large changes
        
        // Factor 2: Syntax validity
        if (validateLanguageSyntax(candidate.getCode())) {
            score *= 1.1;
        } else {
            score *= 0.5; // Heavy penalty for syntax errors
        }
        
        // Factor 3: Security completeness
        if (candidate.getType().equals("DEFENSIVE") || candidate.getType().equals("BEST_PRACTICE")) {
            score *= 1.05;
        }
        
        // Factor 4: Functionality preservation confidence
        if (candidate.getFunctionalityNotes() != null && 
            candidate.getFunctionalityNotes().toLowerCase().contains("backward compatible")) {
            score *= 1.1;
        }
        
        candidate.setScore(score);
    }
    
    protected int calculateChangeSize(String original, String fixed) {
        // Simple line diff count
        Set<String> originalLines = new HashSet<>(Arrays.asList(original.split("\n")));
        Set<String> fixedLines = new HashSet<>(Arrays.asList(fixed.split("\n")));
        
        Set<String> added = new HashSet<>(fixedLines);
        added.removeAll(originalLines);
        
        Set<String> removed = new HashSet<>(originalLines);
        removed.removeAll(fixedLines);
        
        return added.size() + removed.size();
    }
    
    protected FixResult refineSelectedFix(
            FixCandidate candidate,
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context) {
        
        FixResult result = new FixResult();
        
        // Apply imports if needed
        String refinedCode = candidate.getCode();
        if (candidate.getNewImports() != null && !candidate.getNewImports().isEmpty()) {
            refinedCode = addLanguageImports(refinedCode, candidate.getNewImports());
        }
        
        // Use review model to polish the fix
        String polishPrompt = buildPolishPrompt(refinedCode, vulnerability, context);
        
        try {
            UserMessage userMessage = UserMessage.from(polishPrompt);
            AiMessage response = reviewModel.generate(userMessage);
            
            // Parse polished result
            String polishedCode = extractPolishedCode(response.text());
            if (polishedCode != null && !polishedCode.isEmpty()) {
                refinedCode = polishedCode;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to polish fix: " + e.getMessage());
        }
        
        result.setSuccess(true);
        result.setFixedCode(refinedCode);
        result.setExplanation(buildExplanation(candidate, vulnerability));
        result.setConfidence(candidate.getScore());
        
        // Build detailed changes
        result.setChanges(buildDetailedChanges(context.getFileContent(), refinedCode, vulnerability));
        
        return result;
    }
    
    protected String buildPolishPrompt(
            String code,
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context) {
        
        return String.format(
            "Review and polish this %s security fix:\n\n" +
            "```%s\n%s\n```\n\n" +
            "Ensure:\n" +
            "1. Code follows %s best practices and idioms\n" +
            "2. Variable names are clear and consistent\n" +
            "3. Comments explain security decisions\n" +
            "4. No unnecessary changes\n" +
            "5. Maintains existing code style\n\n" +
            "Return ONLY the polished code.",
            getLanguageName(),
            getLanguageName().toLowerCase(),
            code,
            getLanguageName()
        );
    }
    
    protected String extractPolishedCode(String response) {
        // Extract code from response
        Pattern pattern = Pattern.compile(
            "```" + getLanguageName().toLowerCase() + "\\s*([\\s\\S]*?)```",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(response);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // If no code block, assume entire response is code
        return response.trim();
    }
    
    protected String buildExplanation(FixCandidate candidate, ScanResult.VulnerabilityMatch vulnerability) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Fixed ").append(vulnerability.getVulnerability().getTitle());
        explanation.append(" using ").append(candidate.getType().toLowerCase().replace("_", " "));
        explanation.append(" approach.\n\n");
        
        if (candidate.getChangesDescription() != null) {
            explanation.append("Changes made:\n").append(candidate.getChangesDescription()).append("\n\n");
        }
        
        if (candidate.getFunctionalityNotes() != null) {
            explanation.append("Functionality preservation:\n").append(candidate.getFunctionalityNotes());
        }
        
        return explanation.toString();
    }
    
    protected List<FixResult.CodeChange> buildDetailedChanges(
            String original,
            String fixed,
            ScanResult.VulnerabilityMatch vulnerability) {
        
        List<FixResult.CodeChange> changes = new ArrayList<>();
        
        // Simple line-based diff
        String[] originalLines = original.split("\n");
        String[] fixedLines = fixed.split("\n");
        
        // Focus on the vulnerability area
        int startLine = Math.max(0, vulnerability.getLineNumber() - 10);
        int endLine = Math.min(originalLines.length, vulnerability.getLineNumber() + 10);
        
        for (int i = startLine; i < endLine; i++) {
            if (i < fixedLines.length && !originalLines[i].equals(fixedLines[i])) {
                FixResult.CodeChange change = new FixResult.CodeChange();
                change.setStartLine(i + 1);
                change.setEndLine(i + 1);
                change.setOriginalCode(originalLines[i]);
                change.setFixedCode(fixedLines[i]);
                change.setChangeType("MODIFY");
                change.setReason("Security fix for line " + (i + 1));
                changes.add(change);
            }
        }
        
        return changes;
    }
    
    protected FixResult autoCorrectFix(
            FixResult fix,
            ValidationResult validation,
            FixContext context) {
        
        // Build auto-correction prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("The following ").append(getLanguageName()).append(" security fix has validation issues:\n\n");
        prompt.append("```").append(getLanguageName().toLowerCase()).append("\n");
        prompt.append(fix.getFixedCode()).append("\n```\n\n");
        
        prompt.append("Validation Issues:\n");
        for (ValidationResult.ValidationIssue issue : validation.getIssues()) {
            prompt.append("- ").append(issue.getSeverity()).append(": ");
            prompt.append(issue.getMessage()).append("\n");
        }
        
        prompt.append("\nPlease fix these issues while maintaining the security fix.\n");
        prompt.append("Return ONLY the corrected code.");
        
        try {
            UserMessage userMessage = UserMessage.from(prompt.toString());
            AiMessage response = codeGenerationModel.generate(userMessage);
            
            String correctedCode = extractPolishedCode(response.text());
            if (correctedCode != null && !correctedCode.isEmpty()) {
                fix.setFixedCode(correctedCode);
                fix.getWarnings().add("Auto-corrected validation issues");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to auto-correct fix: " + e.getMessage());
            fix.getWarnings().add("Validation issues detected but auto-correction failed");
        }
        
        return fix;
    }
    
    @Override
    public CompletableFuture<ValidationResult> validateFix(FixResult fix, FixContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ValidationResult result = new ValidationResult();
            List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
            
            // 1. Syntax validation
            if (!validateLanguageSyntax(fix.getFixedCode())) {
                issues.add(createIssue("SYNTAX_ERROR", "ERROR", 
                    getLanguageName() + " syntax error in generated fix"));
            }
            
            // 2. Import/dependency validation
            List<String> missingDeps = detectMissingDependencies(fix.getFixedCode());
            for (String dep : missingDeps) {
                issues.add(createIssue("SYNTAX_ERROR", "WARNING", 
                    "Potentially missing dependency: " + dep));
            }
            
            // 3. Security validation
            if (containsNewVulnerabilities(fix.getFixedCode())) {
                issues.add(createIssue("SECURITY_CONCERN", "ERROR", 
                    "Fix may introduce new security vulnerabilities"));
            }
            
            // 4. Functionality validation using LLM
            ValidationResult.ValidationIssue functionalityIssue = 
                validateFunctionalityWithLLM(fix, context);
            if (functionalityIssue != null) {
                issues.add(functionalityIssue);
            }
            
            result.setValid(issues.stream().noneMatch(i -> "ERROR".equals(i.getSeverity())));
            result.setIssues(issues);
            
            return result;
        });
    }
    
    protected ValidationResult.ValidationIssue validateFunctionalityWithLLM(
            FixResult fix,
            FixContext context) {
        
        String prompt = String.format(
            "Analyze if this %s security fix preserves the original functionality:\n\n" +
            "ORIGINAL CODE:\n```%s\n%s\n```\n\n" +
            "FIXED CODE:\n```%s\n%s\n```\n\n" +
            "Check if:\n" +
            "1. All original method signatures are preserved\n" +
            "2. Return values remain the same for valid inputs\n" +
            "3. Side effects are maintained\n" +
            "4. API contracts are not broken\n\n" +
            "Respond with: PRESERVES_FUNCTIONALITY: YES/NO\n" +
            "If NO, explain what functionality is broken.",
            getLanguageName(),
            getLanguageName().toLowerCase(),
            context.getFileContent(),
            getLanguageName().toLowerCase(),
            fix.getFixedCode()
        );
        
        try {
            UserMessage userMessage = UserMessage.from(prompt);
            AiMessage response = reviewModel.generate(userMessage);
            
            String responseText = response.text();
            if (responseText.contains("PRESERVES_FUNCTIONALITY: NO")) {
                return createIssue("LOGIC_ERROR", "ERROR", 
                    "Fix may break existing functionality: " + 
                    responseText.substring(responseText.indexOf("NO") + 2).trim());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to validate functionality with LLM: " + e.getMessage());
        }
        
        return null;
    }
    
    protected boolean containsNewVulnerabilities(String code) {
        // Base implementation - should be overridden by language-specific generators
        return false;
    }
    
    protected ValidationResult.ValidationIssue createIssue(String type, String severity, String message) {
        ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue();
        issue.setType(type);
        issue.setSeverity(severity);
        issue.setMessage(message);
        return issue;
    }
    
    protected String applyTemplate(
            FixTemplate template,
            ScanResult.VulnerabilityMatch vulnerability,
            FixContext context) {
        
        String code = context.getFileContent();
        
        // Simple template application - can be enhanced
        Pattern pattern = Pattern.compile(template.getPattern());
        java.util.regex.Matcher matcher = pattern.matcher(code);
        
        if (matcher.find()) {
            return matcher.replaceAll(template.getReplacement());
        }
        
        return code;
    }
    
    protected String extractMethodContext(String[] lines, int vulnLine) {
        // Find method boundaries
        int methodStart = vulnLine;
        int methodEnd = vulnLine;
        int braceCount = 0;
        
        // Search backwards for method start
        for (int i = vulnLine; i >= 0; i--) {
            String line = lines[i];
            if (line.contains("{")) braceCount--;
            if (line.contains("}")) braceCount++;
            
            if (braceCount < 0 || isMethodDeclaration(line)) {
                methodStart = i;
                break;
            }
        }
        
        // Search forward for method end
        braceCount = 0;
        for (int i = methodStart; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("{")) braceCount++;
            if (line.contains("}")) braceCount--;
            
            if (braceCount == 0 && i > methodStart) {
                methodEnd = i;
                break;
            }
        }
        
        // Extract method
        StringBuilder method = new StringBuilder();
        for (int i = methodStart; i <= methodEnd && i < lines.length; i++) {
            method.append(lines[i]).append("\n");
        }
        
        return method.toString();
    }
    
    protected abstract boolean isMethodDeclaration(String line);
    
    protected Map<String, String> analyzeDataFlow(String[] lines, int vulnLine, String vulnCode) {
        Map<String, String> dataFlow = new HashMap<>();
        
        // Simple variable tracking - can be enhanced with proper AST analysis
        Pattern varPattern = Pattern.compile("\\b(\\w+)\\s*=");
        java.util.regex.Matcher matcher = varPattern.matcher(vulnCode);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            dataFlow.put(varName, findVariableSource(lines, vulnLine, varName));
        }
        
        return dataFlow;
    }
    
    protected String findVariableSource(String[] lines, int startLine, String varName) {
        // Search backwards for variable definition
        for (int i = startLine - 1; i >= 0; i--) {
            if (lines[i].contains(varName + " =") || lines[i].contains(varName + "=")) {
                return lines[i].trim();
            }
        }
        return "unknown";
    }
    
    protected List<String> detectExistingSecurityMeasures(String[] lines) {
        List<String> measures = new ArrayList<>();
        
        // Common security patterns
        Map<String, String> securityPatterns = Map.of(
            "validate|sanitize|escape", "Input validation",
            "authenticate|authorize", "Authentication/Authorization",
            "encrypt|decrypt|hash", "Cryptography",
            "parameterized|prepared", "Parameterized queries",
            "whitelist|allowlist", "Whitelisting"
        );
        
        for (String line : lines) {
            for (Map.Entry<String, String> entry : securityPatterns.entrySet()) {
                if (line.toLowerCase().matches(".*\\b(" + entry.getKey() + ")\\b.*")) {
                    measures.add(entry.getValue() + ": " + line.trim());
                }
            }
        }
        
        return measures;
    }
    
    protected List<String> extractUsedDependencies(String fileContent) {
        // Base implementation - should be overridden by language-specific generators
        return new ArrayList<>();
    }
    
    // Inner classes
    protected static class VulnerabilityAnalysis {
        private String methodContext;
        private Map<String, String> dataFlow;
        private List<String> existingSecurityMeasures;
        private List<String> usedDependencies;
        private List<ContextManager.RelevantContext> relatedCode;
        
        // Getters and setters
        public String getMethodContext() { return methodContext; }
        public void setMethodContext(String methodContext) { this.methodContext = methodContext; }
        public Map<String, String> getDataFlow() { return dataFlow; }
        public void setDataFlow(Map<String, String> dataFlow) { this.dataFlow = dataFlow; }
        public List<String> getExistingSecurityMeasures() { return existingSecurityMeasures; }
        public void setExistingSecurityMeasures(List<String> existingSecurityMeasures) { 
            this.existingSecurityMeasures = existingSecurityMeasures; 
        }
        public List<String> getUsedDependencies() { return usedDependencies; }
        public void setUsedDependencies(List<String> usedDependencies) { 
            this.usedDependencies = usedDependencies; 
        }
        public List<ContextManager.RelevantContext> getRelatedCode() { return relatedCode; }
        public void setRelatedCode(List<ContextManager.RelevantContext> relatedCode) { 
            this.relatedCode = relatedCode; 
        }
    }
    
    protected static class FixCandidate {
        private String type;
        private String code;
        private double confidence;
        private double score;
        private String changesDescription;
        private String functionalityNotes;
        private List<String> newImports;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getChangesDescription() { return changesDescription; }
        public void setChangesDescription(String changesDescription) { 
            this.changesDescription = changesDescription; 
        }
        public String getFunctionalityNotes() { return functionalityNotes; }
        public void setFunctionalityNotes(String functionalityNotes) { 
            this.functionalityNotes = functionalityNotes; 
        }
        public List<String> getNewImports() { return newImports; }
        public void setNewImports(List<String> newImports) { this.newImports = newImports; }
    }
}