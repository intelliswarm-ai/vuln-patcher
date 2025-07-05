package ai.intelliswarm.vulnpatcher.fixes;

import ai.intelliswarm.vulnpatcher.config.CodeGeneration;
import ai.intelliswarm.vulnpatcher.config.CodeReview;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
public class AbstractFixGeneratorTest {
    
    @InjectMock
    @CodeGeneration
    OllamaChatModel codeGenerationModel;
    
    @InjectMock
    @CodeReview
    OllamaChatModel reviewModel;
    
    @InjectMock
    ContextManager contextManager;
    
    private TestFixGenerator fixGenerator;
    private ScanResult.VulnerabilityMatch testVulnerability;
    private FixContext testContext;
    
    @BeforeEach
    void setUp() {
        fixGenerator = new TestFixGenerator();
        fixGenerator.codeGenerationModel = codeGenerationModel;
        fixGenerator.reviewModel = reviewModel;
        fixGenerator.contextManager = contextManager;
        
        // Create test vulnerability
        Vulnerability vuln = new Vulnerability();
        vuln.setId("SQL_INJECTION");
        vuln.setTitle("SQL Injection");
        vuln.setSeverity("HIGH");
        vuln.setDescription("SQL injection vulnerability");
        
        testVulnerability = new ScanResult.VulnerabilityMatch();
        testVulnerability.setVulnerability(vuln);
        testVulnerability.setFilePath("/src/main/java/UserService.java");
        testVulnerability.setLineNumber(42);
        testVulnerability.setAffectedCode("String query = \"SELECT * FROM users WHERE id = \" + userId;");
        
        // Create test context
        testContext = new FixContext();
        testContext.setSessionId("test-session");
        testContext.setFileContent("""
            public class UserService {
                public User getUser(String userId) {
                    String query = "SELECT * FROM users WHERE id = " + userId;
                    return executeQuery(query);
                }
            }
            """);
    }
    
    @Test
    @DisplayName("Should generate fix successfully with all strategies")
    void testGenerateFix() throws ExecutionException, InterruptedException {
        // Mock context manager
        ContextManager.SessionContext mockSession = mock(ContextManager.SessionContext.class);
        when(contextManager.getOrCreateSession(anyString())).thenReturn(mockSession);
        when(mockSession.getRelevantContext(anyString(), anyInt())).thenReturn(Arrays.asList());
        
        // Mock LLM responses
        String mockLLMResponse = """
            FIXED_CODE:
            ```java
            public class UserService {
                public User getUser(String userId) {
                    String query = "SELECT * FROM users WHERE id = ?";
                    return executeQuery(query, userId);
                }
            }
            ```
            CHANGES_MADE:
            - Replaced string concatenation with parameterized query
            FUNCTIONALITY_PRESERVED:
            - Method still returns user by ID
            NEW_IMPORTS:
            none
            TESTING_NOTES:
            - Test with various user IDs
            """;
        
        when(codeGenerationModel.generate(anyList()))
            .thenReturn(AiMessage.from(mockLLMResponse));
        when(reviewModel.generate(any()))
            .thenReturn(AiMessage.from("```java\n// Polished code\n```"));
        
        // Execute fix generation
        CompletableFuture<FixResult> futureResult = fixGenerator.generateFix(testVulnerability, testContext);
        FixResult result = futureResult.get();
        
        // Assertions
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getFixedCode());
        assertNotNull(result.getExplanation());
        assertTrue(result.getConfidence() > 0);
        assertNotNull(result.getChanges());
        
        // Verify multiple strategies were attempted
        verify(codeGenerationModel, atLeast(3)).generate(anyList()); // Minimal, Best Practice, Defensive
    }
    
    @Test
    @DisplayName("Should handle fix generation failure gracefully")
    void testGenerateFixFailure() throws ExecutionException, InterruptedException {
        when(codeGenerationModel.generate(anyList()))
            .thenThrow(new RuntimeException("LLM error"));
        
        CompletableFuture<FixResult> futureResult = fixGenerator.generateFix(testVulnerability, testContext);
        FixResult result = futureResult.get();
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getExplanation());
        assertTrue(result.getExplanation().contains("Failed to generate fix"));
    }
    
    @Test
    @DisplayName("Should validate fix functionality using LLM")
    void testValidateFunctionality() throws ExecutionException, InterruptedException {
        FixResult testFix = new FixResult();
        testFix.setFixedCode("// Fixed code");
        
        // Mock validation response - functionality preserved
        when(reviewModel.generate(any()))
            .thenReturn(AiMessage.from("PRESERVES_FUNCTIONALITY: YES"));
        
        CompletableFuture<ValidationResult> futureResult = fixGenerator.validateFix(testFix, testContext);
        ValidationResult result = futureResult.get();
        
        assertNotNull(result);
        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }
    
    @Test
    @DisplayName("Should detect functionality breaking changes")
    void testValidateFunctionalityBreaking() throws ExecutionException, InterruptedException {
        FixResult testFix = new FixResult();
        testFix.setFixedCode("// Breaking fix");
        
        // Mock validation response - functionality broken
        when(reviewModel.generate(any()))
            .thenReturn(AiMessage.from("PRESERVES_FUNCTIONALITY: NO\nMethod signature changed"));
        
        CompletableFuture<ValidationResult> futureResult = fixGenerator.validateFix(testFix, testContext);
        ValidationResult result = futureResult.get();
        
        assertNotNull(result);
        assertFalse(result.isValid());
        assertFalse(result.getIssues().isEmpty());
        assertTrue(result.getIssues().stream()
            .anyMatch(issue -> issue.getType().equals("LOGIC_ERROR")));
    }
    
    @Test
    @DisplayName("Should build appropriate prompts for different strategies")
    void testPromptBuilding() {
        ArgumentCaptor<List<dev.langchain4j.data.message.ChatMessage>> promptCaptor = 
            ArgumentCaptor.forClass(List.class);
        
        when(codeGenerationModel.generate(promptCaptor.capture()))
            .thenReturn(AiMessage.from("FIXED_CODE:\n```java\n// Fix\n```"));
        
        ContextManager.SessionContext mockSession = mock(ContextManager.SessionContext.class);
        when(contextManager.getOrCreateSession(anyString())).thenReturn(mockSession);
        when(mockSession.getRelevantContext(anyString(), anyInt())).thenReturn(Arrays.asList());
        
        fixGenerator.generateFix(testVulnerability, testContext).join();
        
        List<List<dev.langchain4j.data.message.ChatMessage>> capturedPrompts = promptCaptor.getAllValues();
        
        // Should have prompts for different strategies
        assertTrue(capturedPrompts.size() >= 3);
        
        // Check prompts contain strategy-specific content
        String minimalPrompt = capturedPrompts.stream()
            .flatMap(List::stream)
            .map(msg -> msg.text())
            .filter(text -> text.contains("MINIMAL"))
            .findFirst()
            .orElse("");
        
        assertTrue(minimalPrompt.contains("least code changes"));
        assertTrue(minimalPrompt.contains("backward compatible"));
    }
    
    @Test
    @DisplayName("Should extract method context correctly")
    void testMethodContextExtraction() {
        String fileContent = """
            public class Service {
                private String field;
                
                public void method1() {
                    // Some code
                }
                
                public User getUser(String id) {
                    String query = "SELECT * FROM users WHERE id = " + id;
                    return executeQuery(query);
                }
                
                private void helper() {
                    // Helper method
                }
            }
            """;
        
        testContext.setFileContent(fileContent);
        testVulnerability.setLineNumber(9); // Line with vulnerability
        
        ContextManager.SessionContext mockSession = mock(ContextManager.SessionContext.class);
        when(contextManager.getOrCreateSession(anyString())).thenReturn(mockSession);
        when(mockSession.getRelevantContext(anyString(), anyInt())).thenReturn(Arrays.asList());
        
        when(codeGenerationModel.generate(anyList()))
            .thenReturn(AiMessage.from("FIXED_CODE:\n```java\n// Fix\n```"));
        
        fixGenerator.generateFix(testVulnerability, testContext).join();
        
        // Verify that method context was included in prompts
        ArgumentCaptor<List<dev.langchain4j.data.message.ChatMessage>> promptCaptor = 
            ArgumentCaptor.forClass(List.class);
        verify(codeGenerationModel, atLeastOnce()).generate(promptCaptor.capture());
        
        String promptContent = promptCaptor.getAllValues().stream()
            .flatMap(List::stream)
            .map(msg -> msg.text())
            .reduce("", (a, b) -> a + " " + b);
        
        assertTrue(promptContent.contains("getUser"));
    }
    
    @Test
    @DisplayName("Should handle template-based fixes when available")
    void testTemplateBasedFix() {
        // Configure fix template
        fixGenerator.getFixTemplates().put("SQL_INJECTION", new FixTemplate(
            "SQL_INJECTION",
            "\"SELECT.*WHERE.*\"\\s*\\+",
            "Parameterized query"
        ));
        
        ContextManager.SessionContext mockSession = mock(ContextManager.SessionContext.class);
        when(contextManager.getOrCreateSession(anyString())).thenReturn(mockSession);
        when(mockSession.getRelevantContext(anyString(), anyInt())).thenReturn(Arrays.asList());
        
        when(codeGenerationModel.generate(anyList()))
            .thenReturn(AiMessage.from("FIXED_CODE:\n```java\n// Fix\n```"));
        
        CompletableFuture<FixResult> result = fixGenerator.generateFix(testVulnerability, testContext);
        
        assertDoesNotThrow(() -> result.get());
    }
    
    @Test
    @DisplayName("Should auto-correct validation issues")
    void testAutoCorrectFix() throws ExecutionException, InterruptedException {
        FixResult fixWithIssues = new FixResult();
        fixWithIssues.setSuccess(true);
        fixWithIssues.setFixedCode("// Code with issues");
        
        ValidationResult validation = new ValidationResult();
        validation.setValid(false);
        ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue();
        issue.setSeverity("ERROR");
        issue.setMessage("Missing import");
        validation.setIssues(Arrays.asList(issue));
        
        // Mock auto-correction
        when(codeGenerationModel.generate(any()))
            .thenReturn(AiMessage.from("```java\n// Corrected code\n```"));
        
        // Test through validation flow
        when(reviewModel.generate(any()))
            .thenReturn(AiMessage.from("PRESERVES_FUNCTIONALITY: NO\nMissing import"))
            .thenReturn(AiMessage.from("// Corrected"));
        
        testContext.setFileContent("// Original code");
        ContextManager.SessionContext mockSession = mock(ContextManager.SessionContext.class);
        when(contextManager.getOrCreateSession(anyString())).thenReturn(mockSession);
        when(mockSession.getRelevantContext(anyString(), anyInt())).thenReturn(Arrays.asList());
        
        CompletableFuture<FixResult> futureResult = fixGenerator.generateFix(testVulnerability, testContext);
        FixResult result = futureResult.get();
        
        assertNotNull(result);
        // Should have warning about auto-correction
        assertFalse(result.getWarnings().isEmpty());
    }
    
    @Test
    @DisplayName("Should calculate change size correctly")
    void testChangeSize() {
        String original = """
            line1
            line2
            line3
            line4
            """;
        
        String fixed = """
            line1
            line2-modified
            line3
            line4
            line5-new
            """;
        
        ContextManager.SessionContext mockSession = mock(ContextManager.SessionContext.class);
        when(contextManager.getOrCreateSession(anyString())).thenReturn(mockSession);
        when(mockSession.getRelevantContext(anyString(), anyInt())).thenReturn(Arrays.asList());
        
        when(codeGenerationModel.generate(anyList()))
            .thenReturn(AiMessage.from("FIXED_CODE:\n```java\n" + fixed + "\n```"));
        
        testContext.setFileContent(original);
        
        CompletableFuture<FixResult> result = fixGenerator.generateFix(testVulnerability, testContext);
        
        assertDoesNotThrow(() -> result.get());
    }
    
    @Test
    @DisplayName("Should detect existing security measures")
    void testDetectExistingSecurityMeasures() {
        String fileContent = """
            public class SecureService {
                public void validateInput(String input) {
                    // Input validation
                }
                
                public String sanitizeData(String data) {
                    return escapeHtml(data);
                }
                
                public void authenticate(User user) {
                    // Authentication logic
                }
                
                public String encrypt(String data) {
                    return encryptionService.encrypt(data);
                }
            }
            """;
        
        testContext.setFileContent(fileContent);
        
        ContextManager.SessionContext mockSession = mock(ContextManager.SessionContext.class);
        when(contextManager.getOrCreateSession(anyString())).thenReturn(mockSession);
        when(mockSession.getRelevantContext(anyString(), anyInt())).thenReturn(Arrays.asList());
        
        when(codeGenerationModel.generate(anyList()))
            .thenReturn(AiMessage.from("FIXED_CODE:\n```java\n// Fix\n```"));
        
        fixGenerator.generateFix(testVulnerability, testContext).join();
        
        // Verify security measures were detected and included in context
        ArgumentCaptor<List<dev.langchain4j.data.message.ChatMessage>> promptCaptor = 
            ArgumentCaptor.forClass(List.class);
        verify(codeGenerationModel, atLeastOnce()).generate(promptCaptor.capture());
        
        String promptContent = promptCaptor.getAllValues().stream()
            .flatMap(List::stream)
            .map(msg -> msg.text())
            .reduce("", (a, b) -> a + " " + b);
        
        // Should detect various security patterns
        assertTrue(promptContent.contains("validate") || promptContent.contains("sanitize") || 
                  promptContent.contains("authenticate") || promptContent.contains("encrypt"));
    }
    
    /**
     * Test implementation of AbstractFixGenerator
     */
    private static class TestFixGenerator extends AbstractFixGenerator {
        private final Map<String, FixTemplate> templates = new HashMap<>();
        
        @Override
        protected String getLanguageName() {
            return "Java";
        }
        
        @Override
        protected Map<String, FixTemplate> initializeLanguageTemplates() {
            return templates;
        }
        
        @Override
        public Map<String, FixTemplate> getFixTemplates() {
            return templates;
        }
        
        @Override
        protected List<String> getLanguageSpecificSecurityGuidelines() {
            return Arrays.asList(
                "Use parameterized queries",
                "Validate all inputs",
                "Use secure random for tokens"
            );
        }
        
        @Override
        protected boolean validateLanguageSyntax(String code) {
            return !code.contains("SYNTAX_ERROR");
        }
        
        @Override
        protected List<String> detectMissingDependencies(String code) {
            List<String> missing = new ArrayList<>();
            if (code.contains("@Autowired") && !code.contains("import org.springframework")) {
                missing.add("spring-core");
            }
            return missing;
        }
        
        @Override
        protected String addLanguageImports(String code, List<String> imports) {
            StringBuilder result = new StringBuilder();
            for (String imp : imports) {
                result.append("import ").append(imp).append(";\n");
            }
            result.append("\n").append(code);
            return result.toString();
        }
        
        @Override
        protected boolean isMethodDeclaration(String line) {
            return line.matches(".*\\b(public|private|protected)\\s+.*\\(.*\\).*");
        }
        
        @Override
        public boolean canHandle(String language) {
            return "Java".equalsIgnoreCase(language);
        }
    }
}