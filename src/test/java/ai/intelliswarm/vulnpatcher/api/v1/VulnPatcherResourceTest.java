package ai.intelliswarm.vulnpatcher.api.v1;

import ai.intelliswarm.vulnpatcher.config.MetricsConfig;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import ai.intelliswarm.vulnpatcher.services.PullRequestService;
import ai.intelliswarm.vulnpatcher.services.VulnerabilityDetectionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestHTTPEndpoint(VulnPatcherResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VulnPatcherResourceTest {
    
    @InjectMock
    VulnerabilityDetectionService detectionService;
    
    @InjectMock
    PullRequestService pullRequestService;
    
    @InjectMock
    MetricsConfig.MetricsService metricsService;
    
    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    @Test
    @Order(1)
    @DisplayName("Should scan repository successfully")
    void testScanRepository() throws Exception {
        // Mock scan result
        ScanResult mockResult = new ScanResult();
        mockResult.setScanId(UUID.randomUUID().toString());
        mockResult.setRepositoryUrl("https://github.com/test/repo");
        mockResult.setBranch("main");
        mockResult.setScanDuration(5000L);
        mockResult.setVulnerabilities(Arrays.asList());
        
        when(detectionService.scanRepository(any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = "https://github.com/test/repo";
        request.branch = "main";
        request.credentials = Map.of("token", "test-token");
        request.languages = Arrays.asList("java", "python");
        request.severityThreshold = "MEDIUM";
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan")
            .then()
            .statusCode(200)
            .body("scanId", notNullValue())
            .body("repositoryUrl", equalTo("https://github.com/test/repo"))
            .body("branch", equalTo("main"))
            .body("status", equalTo("completed"))
            .body("vulnerabilitiesFound", equalTo(0))
            .body("scanDuration", greaterThan(0));
        
        verify(metricsService).startScanTimer();
        verify(metricsService).endScanTimer(any());
        verify(metricsService).incrementVulnerabilitiesDetected();
    }
    
    @Test
    @Order(2)
    @DisplayName("Should handle scan failure gracefully")
    void testScanRepositoryFailure() {
        when(detectionService.scanRepository(any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Scan failed")));
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = "https://github.com/test/repo";
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan")
            .then()
            .statusCode(500)
            .body("error", containsString("Scan failed"));
        
        verify(metricsService).endScanTimer(any());
    }
    
    @ParameterizedTest
    @Order(3)
    @ValueSource(strings = {"", " ", "not-a-url", "ftp://invalid.com"})
    @DisplayName("Should validate repository URL")
    void testInvalidRepositoryUrl(String invalidUrl) {
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = invalidUrl;
        
        Response response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan");
        
        // Should either return 400 for validation error or process with error
        assertTrue(response.getStatusCode() == 400 || response.getStatusCode() == 500);
    }
    
    @Test
    @Order(4)
    @DisplayName("Should stream scan progress via SSE")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStreamScanProgress() throws InterruptedException {
        String scanId = UUID.randomUUID().toString();
        
        CountDownLatch eventsReceived = new CountDownLatch(5);
        AtomicInteger eventCount = new AtomicInteger(0);
        List<String> receivedPhases = Collections.synchronizedList(new ArrayList<>());
        
        // Start SSE client in separate thread
        Thread sseThread = new Thread(() -> {
            given()
                .accept(MediaType.SERVER_SENT_EVENTS)
                .when()
                .get("/scan/{scanId}/stream", scanId)
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract()
                .body()
                .asString()
                .lines()
                .forEach(line -> {
                    if (line.startsWith("data:")) {
                        eventCount.incrementAndGet();
                        eventsReceived.countDown();
                        
                        // Extract phase from event
                        if (line.contains("phase")) {
                            receivedPhases.add(line);
                        }
                    }
                });
        });
        
        sseThread.start();
        
        // Wait for events
        assertTrue(eventsReceived.await(5, TimeUnit.SECONDS));
        
        // Verify events were received
        assertTrue(eventCount.get() >= 5);
        assertFalse(receivedPhases.isEmpty());
    }
    
    @Test
    @Order(5)
    @DisplayName("Should create pull request successfully")
    void testCreatePullRequest() {
        String scanId = UUID.randomUUID().toString();
        
        VulnPatcherResource.PullRequestRequest request = new VulnPatcherResource.PullRequestRequest();
        request.title = "Security fixes for vulnerabilities";
        request.description = "This PR fixes detected security vulnerabilities";
        request.targetBranch = "main";
        request.reviewers = Arrays.asList("security-team");
        request.labels = Arrays.asList("security", "automated");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan/{scanId}/create-pr", scanId)
            .then()
            .statusCode(201)
            .body("pullRequestId", equalTo("123"))
            .body("pullRequestUrl", containsString("github.com"))
            .body("status", equalTo("created"))
            .body("fixesApplied", equalTo(5));
        
        verify(metricsService).incrementPullRequestsCreated();
    }
    
    @Test
    @Order(6)
    @DisplayName("Should validate pull request request")
    void testCreatePullRequestValidation() {
        String scanId = UUID.randomUUID().toString();
        
        // Missing required field
        VulnPatcherResource.PullRequestRequest request = new VulnPatcherResource.PullRequestRequest();
        request.description = "Description only";
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan/{scanId}/create-pr", scanId)
            .then()
            .statusCode(400);
    }
    
    @Test
    @Order(7)
    @DisplayName("Should get scan results")
    void testGetScanResults() {
        String scanId = UUID.randomUUID().toString();
        
        given()
            .when()
            .get("/scan/{scanId}", scanId)
            .then()
            .statusCode(200)
            .body("scanId", equalTo(scanId))
            .body("status", equalTo("completed"))
            .body("vulnerabilities", notNullValue());
    }
    
    @Test
    @Order(8)
    @DisplayName("Should return health status")
    void testHealthEndpoint() {
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
            .body("version", notNullValue())
            .body("timestamp", notNullValue());
    }
    
    @ParameterizedTest
    @Order(9)
    @CsvSource({
        "LOW, 1",
        "MEDIUM, 2", 
        "HIGH, 3",
        "CRITICAL, 4"
    })
    @DisplayName("Should respect severity threshold")
    void testSeverityThreshold(String severity, int expectedPriority) {
        ScanResult mockResult = new ScanResult();
        mockResult.setScanId(UUID.randomUUID().toString());
        mockResult.setVulnerabilities(Arrays.asList());
        
        when(detectionService.scanRepository(any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = "https://github.com/test/repo";
        request.severityThreshold = severity;
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan")
            .then()
            .statusCode(200);
        
        ArgumentCaptor<VulnerabilityDetectionService.ScanRequest> captor = 
            ArgumentCaptor.forClass(VulnerabilityDetectionService.ScanRequest.class);
        verify(detectionService).scanRepository(captor.capture());
        
        assertEquals(severity, captor.getValue().getSeverityThreshold());
    }
    
    @Test
    @Order(10)
    @DisplayName("Should handle concurrent scan requests")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentScans() throws InterruptedException {
        int concurrentRequests = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        
        // Mock successful scans
        ScanResult mockResult = new ScanResult();
        mockResult.setScanId(UUID.randomUUID().toString());
        mockResult.setVulnerabilities(Arrays.asList());
        
        when(detectionService.scanRepository(any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        // Launch concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
                    request.repositoryUrl = "https://github.com/test/repo" + requestId;
                    
                    Response response = given()
                        .contentType(ContentType.JSON)
                        .body(request)
                        .when()
                        .post("/scan");
                    
                    statusCodes.add(response.getStatusCode());
                } catch (Exception e) {
                    fail("Request failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));
        
        // All requests should succeed
        assertEquals(concurrentRequests, statusCodes.size());
        assertTrue(statusCodes.stream().allMatch(code -> code == 200));
        
        // Verify metrics were tracked for all requests
        verify(metricsService, times(concurrentRequests)).startScanTimer();
        verify(metricsService, times(concurrentRequests)).endScanTimer(any());
    }
    
    @Test
    @Order(11)
    @DisplayName("Should handle missing credentials gracefully")
    void testMissingCredentials() {
        ScanResult mockResult = new ScanResult();
        mockResult.setScanId(UUID.randomUUID().toString());
        mockResult.setVulnerabilities(Arrays.asList());
        
        when(detectionService.scanRepository(any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = "https://github.com/private/repo";
        // No credentials provided
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan")
            .then()
            .statusCode(200); // Should still work, service will handle auth
    }
    
    @Test
    @Order(12)
    @DisplayName("Should timeout long-running scans")
    void testScanTimeout() {
        // Mock a scan that never completes
        CompletableFuture<ScanResult> neverCompletingFuture = new CompletableFuture<>();
        
        when(detectionService.scanRepository(any()))
            .thenReturn(neverCompletingFuture);
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = "https://github.com/test/repo";
        
        // Configure RestAssured with shorter timeout for this test
        given()
            .config(RestAssured.config()
                .httpClient(org.apache.http.client.config.RequestConfig.custom()
                    .setSocketTimeout(5000)
                    .build()))
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan")
            .then()
            .statusCode(anyOf(equalTo(408), equalTo(500))); // Timeout or error
    }
    
    @Test
    @Order(13)
    @DisplayName("Should validate language parameter")
    void testLanguageValidation() {
        ScanResult mockResult = new ScanResult();
        mockResult.setScanId(UUID.randomUUID().toString());
        mockResult.setVulnerabilities(Arrays.asList());
        
        when(detectionService.scanRepository(any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = "https://github.com/test/repo";
        request.languages = Arrays.asList("java", "python", "invalid-language", "c++");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan")
            .then()
            .statusCode(200);
        
        // Verify all languages were passed to service
        ArgumentCaptor<VulnerabilityDetectionService.ScanRequest> captor = 
            ArgumentCaptor.forClass(VulnerabilityDetectionService.ScanRequest.class);
        verify(detectionService).scanRepository(captor.capture());
        
        assertEquals(4, captor.getValue().getLanguages().size());
    }
    
    @Test
    @Order(14)
    @DisplayName("Should handle large scan results")
    void testLargeScanResults() {
        // Create large scan result
        ScanResult mockResult = new ScanResult();
        mockResult.setScanId(UUID.randomUUID().toString());
        mockResult.setRepositoryUrl("https://github.com/test/repo");
        
        List<ScanResult.VulnerabilityMatch> vulnerabilities = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            ScanResult.VulnerabilityMatch match = new ScanResult.VulnerabilityMatch();
            match.setFilePath("/file" + i + ".java");
            match.setLineNumber(i);
            vulnerabilities.add(match);
        }
        mockResult.setVulnerabilities(vulnerabilities);
        
        when(detectionService.scanRepository(any()))
            .thenReturn(CompletableFuture.completedFuture(mockResult));
        when(metricsService.startScanTimer()).thenReturn(mock(Timer.Sample.class));
        
        VulnPatcherResource.ScanRequest request = new VulnPatcherResource.ScanRequest();
        request.repositoryUrl = "https://github.com/test/repo";
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/scan")
            .then()
            .statusCode(200)
            .body("vulnerabilitiesFound", equalTo(1000));
    }
    
    @Test
    @Order(15)
    @DisplayName("Should return appropriate content types")
    void testContentTypes() {
        // Test JSON response
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
        
        // Test SSE response
        given()
            .accept(MediaType.SERVER_SENT_EVENTS)
            .when()
            .get("/scan/test-id/stream")
            .then()
            .statusCode(200)
            .contentType(containsString("text/event-stream"));
    }
}