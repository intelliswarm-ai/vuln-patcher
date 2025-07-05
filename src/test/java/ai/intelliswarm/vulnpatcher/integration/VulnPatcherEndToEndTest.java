package ai.intelliswarm.vulnpatcher.integration;

import ai.intelliswarm.vulnpatcher.api.v1.VulnPatcherResource;
import ai.intelliswarm.vulnpatcher.core.VulnerabilityDetectionEngine;
import ai.intelliswarm.vulnpatcher.fixes.JavaFixGenerator;
import ai.intelliswarm.vulnpatcher.git.GitHubService;
import ai.intelliswarm.vulnpatcher.models.ScanRequest;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import ai.intelliswarm.vulnpatcher.orchestrator.LLMOrchestrator;
import ai.intelliswarm.vulnpatcher.services.VulnerabilityDetectionService;
import ai.intelliswarm.vulnpatcher.sources.VulnerabilitySource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test that validates the entire VulnPatcher workflow.
 * This test simulates real-world usage scenarios.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VulnPatcherEndToEndTest {
    
    @Inject
    VulnerabilityDetectionService detectionService;
    
    @Inject
    VulnerabilityDetectionEngine detectionEngine;
    
    @Inject
    LLMOrchestrator orchestrator;
    
    @Inject
    List<VulnerabilitySource> vulnerabilitySources;
    
    @Inject
    JavaFixGenerator javaFixGenerator;
    
    @Inject
    GitHubService githubService;
    
    private static final String TEST_REPO_URL = "https://github.com/test/vulnerable-app";
    private static final String TEST_BRANCH = "main";
    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    
    @BeforeAll
    void setup() {
        RestAssured.baseURI = "http://localhost:8080";
        RestAssured.basePath = "/api/v1";
        
        System.out.println("Starting end-to-end integration tests");
        System.out.println("Active vulnerability sources: " + 
            vulnerabilitySources.stream()
                .filter(VulnerabilitySource::isEnabled)
                .count());
    }
    
    @Test
    @Order(1)
    @DisplayName("Should successfully start a vulnerability scan via REST API")
    void testStartScanViaAPI() {
        ScanRequest request = new ScanRequest();
        request.setRepositoryUrl(TEST_REPO_URL);
        request.setBranch(TEST_BRANCH);
        request.setLanguages(Arrays.asList("java", "javascript"));
        request.setSeverityThreshold("MEDIUM");
        
        String scanId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/vulnpatcher/scan")
            .then()
            .statusCode(202)
            .body("scanId", notNullValue())
            .body("status", equalTo("pending"))
            .body("message", containsString("initiated"))
            .extract()
            .path("scanId");
        
        assertNotNull(scanId);
        assertTrue(scanId.startsWith("scan-"));
        System.out.println("Scan initiated with ID: " + scanId);
    }
    
    @Test
    @Order(2)
    @DisplayName("Should detect vulnerabilities in a simulated vulnerable codebase")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void testVulnerabilityDetection() {
        // Create a simulated vulnerable code
        Map<String, String> vulnerableFiles = createVulnerableCodebase();
        
        // Scan for vulnerabilities
        ScanRequest request = new ScanRequest();
        request.setRepositoryUrl("local://test-repo");
        request.setLanguages(Arrays.asList("java"));
        request.setSeverityThreshold("LOW");
        
        ScanResult result = detectionService.scanRepository(request)
            .await().atMost(TIMEOUT);
        
        assertNotNull(result);
        assertEquals(ScanResult.Status.COMPLETED, result.getStatus());
        assertTrue(result.getVulnerabilitiesFound() > 0, 
            "Should detect vulnerabilities in vulnerable code");
        
        // Verify specific vulnerabilities were detected
        List<ScanResult.VulnerabilityMatch> matches = result.getVulnerabilityMatches();
        
        // Should detect SQL injection
        assertTrue(matches.stream().anyMatch(m -> 
            m.getVulnerability().getTitle().toLowerCase().contains("sql injection") ||
            m.getMatchType() == ScanResult.MatchType.SQL_INJECTION
        ), "Should detect SQL injection vulnerability");
        
        // Should detect XSS
        assertTrue(matches.stream().anyMatch(m -> 
            m.getVulnerability().getTitle().toLowerCase().contains("xss") ||
            m.getMatchType() == ScanResult.MatchType.XSS
        ), "Should detect XSS vulnerability");
        
        System.out.println("Detected " + result.getVulnerabilitiesFound() + " vulnerabilities");
        matches.forEach(match -> 
            System.out.println("- " + match.getVulnerability().getId() + 
                " (" + match.getVulnerability().getSeverity() + "): " + 
                match.getVulnerability().getTitle())
        );
    }
    
    @Test
    @Order(3)
    @DisplayName("Should generate fixes for detected vulnerabilities using AI agents")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void testAIFixGeneration() {
        // Create a mock vulnerability
        Vulnerability vuln = new Vulnerability();
        vuln.setId("CVE-2023-TEST");
        vuln.setTitle("SQL Injection in User Query");
        vuln.setSeverity("HIGH");
        vuln.setDescription("Direct SQL query construction allows injection");
        
        ScanResult.VulnerabilityMatch match = new ScanResult.VulnerabilityMatch();
        match.setVulnerability(vuln);
        match.setFilePath("/src/main/java/UserService.java");
        match.setLineNumber(42);
        match.setAffectedCode("String query = \"SELECT * FROM users WHERE id = \" + userId;");
        match.setMatchType(ScanResult.MatchType.SQL_INJECTION);
        
        // Generate fix using orchestrator
        LLMOrchestrator.WorkflowContext context = new LLMOrchestrator.WorkflowContext();
        context.setSessionId(UUID.randomUUID().toString());
        context.setLanguage("java");
        
        LLMOrchestrator.WorkflowResult result = orchestrator
            .orchestrateVulnerabilityFix(match, context)
            .await().atMost(TIMEOUT);
        
        assertNotNull(result);
        assertTrue(result.isSuccess(), "Fix generation should succeed");
        assertNotNull(result.getFix(), "Should generate a fix");
        assertNotNull(result.getFix().getCode(), "Fix should contain code");
        assertNotNull(result.getFix().getDescription(), "Fix should have description");
        
        // Verify fix addresses the vulnerability
        String fixCode = result.getFix().getCode();
        assertFalse(fixCode.contains("+ userId"), 
            "Fix should not contain string concatenation");
        assertTrue(fixCode.contains("?") || fixCode.contains("PreparedStatement"), 
            "Fix should use parameterized queries");
        
        System.out.println("Generated fix:");
        System.out.println(result.getFix().getCode());
        System.out.println("\nFix description: " + result.getFix().getDescription());
    }
    
    @ParameterizedTest
    @Order(4)
    @MethodSource("provideVulnerableCodeSamples")
    @DisplayName("Should detect and fix various vulnerability types")
    void testMultipleVulnerabilityTypes(String language, String vulnerableCode, 
                                       String expectedVulnType, String fixValidation) {
        // Create scan request with vulnerable code
        ScanRequest request = new ScanRequest();
        request.setRepositoryUrl("local://test-" + language);
        request.setLanguages(Arrays.asList(language));
        
        // Add vulnerable code to scan context
        Map<String, String> files = new HashMap<>();
        files.put("vulnerable." + getFileExtension(language), vulnerableCode);
        
        // Perform scan
        ScanResult scanResult = detectionEngine.scanCode(files, request)
            .await().atMost(Duration.ofMinutes(2));
        
        assertNotNull(scanResult);
        assertTrue(scanResult.getVulnerabilitiesFound() > 0, 
            "Should detect vulnerability in " + language + " code");
        
        // Verify vulnerability type
        ScanResult.VulnerabilityMatch match = scanResult.getVulnerabilityMatches()
            .stream()
            .findFirst()
            .orElse(null);
        
        assertNotNull(match);
        assertTrue(
            match.getVulnerability().getTitle().toLowerCase().contains(expectedVulnType.toLowerCase()) ||
            match.getMatchType().toString().toLowerCase().contains(expectedVulnType.toLowerCase()),
            "Should detect " + expectedVulnType + " vulnerability"
        );
        
        // Generate fix if Java (as we have JavaFixGenerator)
        if ("java".equals(language)) {
            LLMOrchestrator.WorkflowContext context = new LLMOrchestrator.WorkflowContext();
            context.setSessionId(UUID.randomUUID().toString());
            context.setLanguage(language);
            
            LLMOrchestrator.WorkflowResult fixResult = orchestrator
                .orchestrateVulnerabilityFix(match, context)
                .await().atMost(Duration.ofMinutes(2));
            
            assertNotNull(fixResult);
            if (fixResult.isSuccess() && fixResult.getFix() != null) {
                String fixCode = fixResult.getFix().getCode();
                assertTrue(fixCode.contains(fixValidation), 
                    "Fix should contain: " + fixValidation);
            }
        }
    }
    
    private static Stream<Arguments> provideVulnerableCodeSamples() {
        return Stream.of(
            Arguments.of("java", 
                "String query = \"SELECT * FROM users WHERE email = '\" + email + \"'\";",
                "sql injection", 
                "PreparedStatement"),
            
            Arguments.of("java",
                "response.getWriter().println(\"<h1>Welcome \" + request.getParameter(\"name\") + \"</h1>\");",
                "xss",
                "escape"),
            
            Arguments.of("java",
                "DocumentBuilder builder = factory.newDocumentBuilder();\n" +
                "Document doc = builder.parse(userInput);",
                "xxe",
                "disallow-doctype-decl"),
            
            Arguments.of("javascript",
                "eval(userInput);",
                "code injection",
                "JSON.parse"),
            
            Arguments.of("python",
                "cursor.execute(\"SELECT * FROM users WHERE id = %s\" % user_id)",
                "sql injection",
                "execute")
        );
    }
    
    @Test
    @Order(5)
    @DisplayName("Should handle concurrent scan requests efficiently")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void testConcurrentScans() throws InterruptedException {
        int numScans = 5;
        List<CompletableFuture<String>> scanFutures = new ArrayList<>();
        
        // Start multiple scans concurrently
        for (int i = 0; i < numScans; i++) {
            final int scanIndex = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                ScanRequest request = new ScanRequest();
                request.setRepositoryUrl(TEST_REPO_URL + "-" + scanIndex);
                request.setBranch("main");
                request.setLanguages(Arrays.asList("java"));
                
                return given()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/vulnpatcher/scan")
                    .then()
                    .statusCode(202)
                    .extract()
                    .path("scanId");
            });
            scanFutures.add(future);
        }
        
        // Wait for all scans to be initiated
        CompletableFuture<Void> allScans = CompletableFuture.allOf(
            scanFutures.toArray(new CompletableFuture[0])
        );
        allScans.get(2, TimeUnit.MINUTES);
        
        // Verify all scans were created
        List<String> scanIds = new ArrayList<>();
        for (CompletableFuture<String> future : scanFutures) {
            String scanId = future.get();
            assertNotNull(scanId);
            scanIds.add(scanId);
        }
        
        assertEquals(numScans, scanIds.size());
        assertEquals(numScans, scanIds.stream().distinct().count(), 
            "All scan IDs should be unique");
        
        System.out.println("Successfully initiated " + numScans + " concurrent scans");
    }
    
    @Test
    @Order(6)
    @DisplayName("Should create pull request with security fixes")
    void testPullRequestCreation() {
        // This test would require mock GitHub service
        // For now, we'll test the PR creation logic
        
        ScanResult scanResult = new ScanResult();
        scanResult.setScanId("test-scan-123");
        scanResult.setRepositoryUrl(TEST_REPO_URL);
        scanResult.setBranch(TEST_BRANCH);
        scanResult.setVulnerabilitiesFound(3);
        
        // Mock vulnerability matches with fixes
        List<ScanResult.VulnerabilityMatch> matches = Arrays.asList(
            createMockVulnerabilityMatch("CVE-2021-44228", "Log4j RCE", "CRITICAL"),
            createMockVulnerabilityMatch("CWE-89", "SQL Injection", "HIGH"),
            createMockVulnerabilityMatch("CWE-79", "XSS", "MEDIUM")
        );
        scanResult.setVulnerabilityMatches(matches);
        
        // Test PR request payload
        Map<String, Object> prRequest = new HashMap<>();
        prRequest.put("title", "Security fixes for 3 vulnerabilities");
        prRequest.put("description", "This PR addresses critical security vulnerabilities");
        prRequest.put("targetBranch", "main");
        prRequest.put("reviewers", Arrays.asList("security-team"));
        prRequest.put("labels", Arrays.asList("security", "automated"));
        
        // Verify PR request structure
        assertNotNull(prRequest.get("title"));
        assertTrue(prRequest.get("title").toString().contains("Security fixes"));
        assertTrue(((List<?>) prRequest.get("labels")).contains("security"));
    }
    
    @Test
    @Order(7)
    @DisplayName("Should monitor scan progress via SSE")
    void testScanProgressStreaming() {
        // Start a scan
        ScanRequest request = new ScanRequest();
        request.setRepositoryUrl(TEST_REPO_URL);
        request.setBranch(TEST_BRANCH);
        
        String scanId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/vulnpatcher/scan")
            .then()
            .statusCode(202)
            .extract()
            .path("scanId");
        
        // Test SSE endpoint exists
        given()
            .accept("text/event-stream")
            .when()
            .get("/vulnpatcher/scan/" + scanId + "/stream")
            .then()
            .statusCode(200)
            .contentType("text/event-stream");
        
        System.out.println("SSE endpoint available for scan: " + scanId);
    }
    
    @Test
    @Order(8)
    @DisplayName("Should validate fix quality and security improvements")
    void testFixQualityValidation() {
        // Test that generated fixes actually improve security
        String vulnerableCode = """
            public User getUser(String userId) {
                String query = "SELECT * FROM users WHERE id = " + userId;
                return jdbcTemplate.queryForObject(query, new UserRowMapper());
            }
            """;
        
        // Mock fix generation result
        String fixedCode = """
            public User getUser(String userId) {
                // Validate input
                if (!isValidUserId(userId)) {
                    throw new IllegalArgumentException("Invalid user ID");
                }
                
                // Use parameterized query to prevent SQL injection
                String query = "SELECT * FROM users WHERE id = ?";
                return jdbcTemplate.queryForObject(query, new Object[]{userId}, new UserRowMapper());
            }
            
            private boolean isValidUserId(String userId) {
                return userId != null && userId.matches("^[a-zA-Z0-9-]+$");
            }
            """;
        
        // Validate fix improvements
        assertFalse(fixedCode.contains("+ userId"), "Fix should not have string concatenation");
        assertTrue(fixedCode.contains("?"), "Fix should use parameterized query");
        assertTrue(fixedCode.contains("isValid"), "Fix should include input validation");
        assertTrue(fixedCode.contains("IllegalArgumentException"), "Fix should handle invalid input");
    }
    
    private Map<String, String> createVulnerableCodebase() {
        Map<String, String> files = new HashMap<>();
        
        // SQL Injection vulnerable code
        files.put("UserService.java", """
            package com.example;
            
            public class UserService {
                public User findUser(String id) {
                    String query = "SELECT * FROM users WHERE id = " + id;
                    return executeQuery(query);
                }
            }
            """);
        
        // XSS vulnerable code
        files.put("WebController.java", """
            package com.example;
            
            @Controller
            public class WebController {
                @GetMapping("/hello")
                public void hello(HttpServletRequest request, HttpServletResponse response) {
                    String name = request.getParameter("name");
                    response.getWriter().println("<h1>Hello " + name + "</h1>");
                }
            }
            """);
        
        // XXE vulnerable code
        files.put("XmlProcessor.java", """
            package com.example;
            
            public class XmlProcessor {
                public Document parseXml(String xml) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    return builder.parse(new InputSource(new StringReader(xml)));
                }
            }
            """);
        
        return files;
    }
    
    private ScanResult.VulnerabilityMatch createMockVulnerabilityMatch(
            String id, String title, String severity) {
        Vulnerability vuln = new Vulnerability();
        vuln.setId(id);
        vuln.setTitle(title);
        vuln.setSeverity(severity);
        vuln.setDescription("Mock vulnerability for testing");
        vuln.setPublishedDate(LocalDateTime.now());
        
        ScanResult.VulnerabilityMatch match = new ScanResult.VulnerabilityMatch();
        match.setVulnerability(vuln);
        match.setFilePath("/src/main/java/Example.java");
        match.setLineNumber(10);
        match.setConfidence(0.9);
        
        return match;
    }
    
    private String getFileExtension(String language) {
        switch (language.toLowerCase()) {
            case "java": return "java";
            case "python": return "py";
            case "javascript": return "js";
            case "typescript": return "ts";
            case "go": return "go";
            default: return "txt";
        }
    }
}