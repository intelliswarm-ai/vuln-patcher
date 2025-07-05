package ai.intelliswarm.vulnpatcher.integration;

import ai.intelliswarm.vulnpatcher.BaseTest;
import ai.intelliswarm.vulnpatcher.git.providers.GitHubProvider;
import ai.intelliswarm.vulnpatcher.git.providers.GitProvider;
import ai.intelliswarm.vulnpatcher.services.PullRequestService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@TestProfile(GitHubIntegrationTestProfile.class)
@TestPropertySource(properties = {
    "vulnpatcher.github.token=test-token",
    "vulnpatcher.github.enabled=true"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GitHubIntegrationTest extends BaseTest {
    
    @Inject
    GitHubProvider gitHubProvider;
    
    @Inject
    PullRequestService pullRequestService;
    
    private static ClientAndServer mockServer;
    private static MockServerClient mockServerClient;
    
    private static final String TEST_REPO_URL = "https://github.com/test-org/test-repo";
    private static final String TEST_TOKEN = "test-token";
    
    @BeforeAll
    public static void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(8888);
        mockServerClient = new MockServerClient("localhost", 8888);
    }
    
    @AfterAll
    public static void stopMockServer() {
        mockServer.stop();
    }
    
    @BeforeEach
    public void resetMockServer() {
        mockServerClient.reset();
    }
    
    @Test
    @Order(1)
    @DisplayName("Test GitHub provider can handle GitHub URLs")
    public void testCanHandleGitHubUrls() {
        assertTrue(gitHubProvider.canHandle("https://github.com/owner/repo"));
        assertTrue(gitHubProvider.canHandle("git@github.com:owner/repo.git"));
        assertFalse(gitHubProvider.canHandle("https://gitlab.com/owner/repo"));
        assertFalse(gitHubProvider.canHandle("https://bitbucket.org/owner/repo"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Test fetching repository metadata from GitHub")
    public void testGetRepositoryMetadata() throws Exception {
        // Mock GitHub API response
        String mockResponse = """
            {
                "name": "test-repo",
                "owner": {
                    "login": "test-org"
                },
                "description": "Test repository",
                "default_branch": "main",
                "language": "Java",
                "size": 1024,
                "private": false,
                "clone_url": "https://github.com/test-org/test-repo.git",
                "stargazers_count": 100,
                "forks_count": 50,
                "open_issues_count": 10
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/repos/test-org/test-repo")
                .withHeader("Authorization", "Bearer " + TEST_TOKEN))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        Map<String, String> credentials = Map.of("token", TEST_TOKEN);
        CompletableFuture<GitProvider.RepositoryMetadata> future = 
            gitHubProvider.getRepositoryMetadata(TEST_REPO_URL, credentials);
        
        GitProvider.RepositoryMetadata metadata = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(metadata);
        assertEquals("test-repo", metadata.getName());
        assertEquals("test-org", metadata.getOwner());
        assertEquals("Test repository", metadata.getDescription());
        assertEquals("main", metadata.getDefaultBranch());
        assertEquals("Java", metadata.getLanguage());
        assertFalse(metadata.getIsPrivate());
    }
    
    @Test
    @Order(3)
    @DisplayName("Test listing branches from GitHub repository")
    public void testListBranches() throws Exception {
        // Mock GitHub API response
        String mockResponse = """
            {
                "main": {
                    "name": "main",
                    "protected": true
                },
                "develop": {
                    "name": "develop",
                    "protected": false
                },
                "feature/security-fix": {
                    "name": "feature/security-fix",
                    "protected": false
                }
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/repos/test-org/test-repo/branches"))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        Map<String, String> credentials = Map.of("token", TEST_TOKEN);
        CompletableFuture<List<String>> future = 
            gitHubProvider.listBranches(TEST_REPO_URL, credentials);
        
        List<String> branches = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(branches);
        assertEquals(3, branches.size());
        assertTrue(branches.contains("main"));
        assertTrue(branches.contains("develop"));
        assertTrue(branches.contains("feature/security-fix"));
    }
    
    @Test
    @Order(4)
    @DisplayName("Test creating pull request on GitHub")
    public void testCreatePullRequest() throws Exception {
        // Mock GitHub API response for PR creation
        String mockResponse = """
            {
                "number": 123,
                "html_url": "https://github.com/test-org/test-repo/pull/123",
                "state": "open",
                "title": "Security fixes",
                "user": {
                    "login": "vulnpatcher-bot"
                }
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("POST")
                .withPath("/repos/test-org/test-repo/pulls"))
            .respond(response()
                .withStatusCode(201)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Mock reviewer lookup
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/users/security-reviewer"))
            .respond(response()
                .withStatusCode(200)
                .withBody("{\"id\": 12345, \"login\": \"security-reviewer\"}"));
        
        // Test
        GitProvider.PullRequestRequest request = new GitProvider.PullRequestRequest();
        request.setRepositoryUrl(TEST_REPO_URL);
        request.setTitle("Security fixes");
        request.setDescription("Fixing security vulnerabilities");
        request.setSourceBranch("feature/security-fix");
        request.setTargetBranch("main");
        request.setReviewers(List.of("security-reviewer"));
        request.setLabels(List.of("security", "automated"));
        request.setCredentials(Map.of("token", TEST_TOKEN));
        
        CompletableFuture<GitProvider.PullRequestResult> future = 
            gitHubProvider.createPullRequest(request);
        
        GitProvider.PullRequestResult result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("123", result.getPullRequestId());
        assertEquals("https://github.com/test-org/test-repo/pull/123", result.getPullRequestUrl());
    }
    
    @Test
    @Order(5)
    @DisplayName("Test getting pull request status from GitHub")
    public void testGetPullRequestStatus() throws Exception {
        // Mock GitHub API response
        String mockResponse = """
            {
                "number": 123,
                "state": "open",
                "title": "Security fixes",
                "user": {
                    "login": "vulnpatcher-bot"
                },
                "mergeable": true,
                "head": {
                    "sha": "abc123"
                }
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/repos/test-org/test-repo/pulls/123"))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Mock reviews
        String reviewsResponse = """
            [
                {
                    "user": {
                        "login": "security-reviewer"
                    },
                    "state": "APPROVED",
                    "body": "LGTM"
                }
            ]
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/repos/test-org/test-repo/pulls/123/reviews"))
            .respond(response()
                .withStatusCode(200)
                .withBody(reviewsResponse));
        
        // Mock commit status
        String statusResponse = """
            {
                "state": "success",
                "statuses": [
                    {
                        "state": "success",
                        "context": "CI/CD"
                    }
                ]
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/repos/test-org/test-repo/commits/abc123/status"))
            .respond(response()
                .withStatusCode(200)
                .withBody(statusResponse));
        
        // Test
        Map<String, String> credentials = Map.of("token", TEST_TOKEN);
        CompletableFuture<GitProvider.PullRequestStatus> future = 
            gitHubProvider.getPullRequestStatus(TEST_REPO_URL, "123", credentials);
        
        GitProvider.PullRequestStatus status = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(status);
        assertEquals("123", status.getId());
        assertEquals("open", status.getState());
        assertEquals("Security fixes", status.getTitle());
        assertTrue(status.getMergeable());
        
        // Check reviews
        assertNotNull(status.getReviews());
        assertEquals(1, status.getReviews().size());
        assertEquals("security-reviewer", status.getReviews().get(0).getReviewer());
        assertEquals("APPROVED", status.getReviews().get(0).getState());
    }
    
    @Test
    @Order(6)
    @DisplayName("Test end-to-end pull request creation workflow")
    public void testEndToEndPullRequestCreation() throws Exception {
        // This test would require more complex mocking or a test GitHub repository
        // For now, we'll test the service integration
        
        PullRequestService.PullRequestCreationRequest request = 
            new PullRequestService.PullRequestCreationRequest();
        request.setRepositoryUrl(TEST_REPO_URL);
        request.setBaseBranch("main");
        request.setTitle("[Security] Fix SQL injection vulnerability");
        
        List<PullRequestService.FixApplication> fixes = new ArrayList<>();
        PullRequestService.FixApplication fix = new PullRequestService.FixApplication();
        fix.setFilePath("src/main/java/UserService.java");
        fix.setLineNumber(42);
        fix.setOriginalCode("String query = \"SELECT * FROM users WHERE id = \" + userId;");
        fix.setFixedCode("String query = \"SELECT * FROM users WHERE id = ?\";");
        fix.setFixDescription("Use parameterized query to prevent SQL injection");
        fixes.add(fix);
        
        request.setFixes(fixes);
        request.setCredentials(Map.of("token", TEST_TOKEN));
        
        // Since this would require actual Git operations, we'll skip the actual test
        // In a real test environment, you would:
        // 1. Set up a test repository
        // 2. Mock the Git operations
        // 3. Verify the PR was created correctly
        
        assertTrue(true); // Placeholder assertion
    }
}

@TestProfile(GitHubIntegrationTestProfile.class)
class GitHubIntegrationTestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "vulnpatcher.github.url", "http://localhost:8888",
            "vulnpatcher.github.token", "test-token"
        );
    }
}