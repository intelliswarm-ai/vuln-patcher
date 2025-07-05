package ai.intelliswarm.vulnpatcher.integration;

import ai.intelliswarm.vulnpatcher.BaseTest;
import ai.intelliswarm.vulnpatcher.git.providers.GitLabProvider;
import ai.intelliswarm.vulnpatcher.git.providers.GitProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@TestProfile(GitLabIntegrationTestProfile.class)
@TestPropertySource(properties = {
    "vulnpatcher.gitlab.token=test-token",
    "vulnpatcher.gitlab.enabled=true"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GitLabIntegrationTest extends BaseTest {
    
    @Inject
    GitLabProvider gitLabProvider;
    
    private static ClientAndServer mockServer;
    private static MockServerClient mockServerClient;
    
    private static final String TEST_REPO_URL = "https://gitlab.com/test-group/test-project";
    private static final String TEST_TOKEN = "test-token";
    
    @BeforeAll
    public static void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(8889);
        mockServerClient = new MockServerClient("localhost", 8889);
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
    @DisplayName("Test GitLab provider can handle GitLab URLs")
    public void testCanHandleGitLabUrls() {
        assertTrue(gitLabProvider.canHandle("https://gitlab.com/owner/repo"));
        assertTrue(gitLabProvider.canHandle("git@gitlab.com:owner/repo.git"));
        assertTrue(gitLabProvider.canHandle("https://gitlab.company.com/owner/repo"));
        assertFalse(gitLabProvider.canHandle("https://github.com/owner/repo"));
        assertFalse(gitLabProvider.canHandle("https://bitbucket.org/owner/repo"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Test fetching repository metadata from GitLab")
    public void testGetRepositoryMetadata() throws Exception {
        // Mock GitLab API response
        String mockResponse = """
            {
                "id": 12345,
                "name": "test-project",
                "namespace": {
                    "name": "test-group"
                },
                "description": "Test project",
                "default_branch": "main",
                "visibility": "private",
                "http_url_to_repo": "https://gitlab.com/test-group/test-project.git",
                "star_count": 50,
                "forks_count": 25,
                "open_issues_count": 5
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/api/v4/projects/test-group%2Ftest-project")
                .withHeader("PRIVATE-TOKEN", TEST_TOKEN))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        Map<String, String> credentials = Map.of("token", TEST_TOKEN);
        CompletableFuture<GitProvider.RepositoryMetadata> future = 
            gitLabProvider.getRepositoryMetadata(TEST_REPO_URL, credentials);
        
        GitProvider.RepositoryMetadata metadata = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(metadata);
        assertEquals("test-project", metadata.getName());
        assertEquals("test-group", metadata.getOwner());
        assertEquals("Test project", metadata.getDescription());
        assertEquals("main", metadata.getDefaultBranch());
        assertTrue(metadata.getIsPrivate());
    }
    
    @Test
    @Order(3)
    @DisplayName("Test listing branches from GitLab repository")
    public void testListBranches() throws Exception {
        // Mock GitLab API response
        String mockResponse = """
            [
                {
                    "name": "main",
                    "protected": true,
                    "default": true
                },
                {
                    "name": "develop",
                    "protected": false,
                    "default": false
                },
                {
                    "name": "feature/security-fix",
                    "protected": false,
                    "default": false
                }
            ]
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/api/v4/projects/test-group%2Ftest-project/repository/branches"))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        Map<String, String> credentials = Map.of("token", TEST_TOKEN);
        CompletableFuture<List<String>> future = 
            gitLabProvider.listBranches(TEST_REPO_URL, credentials);
        
        List<String> branches = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(branches);
        assertEquals(3, branches.size());
        assertTrue(branches.contains("main"));
        assertTrue(branches.contains("develop"));
        assertTrue(branches.contains("feature/security-fix"));
    }
    
    @Test
    @Order(4)
    @DisplayName("Test creating merge request on GitLab")
    public void testCreateMergeRequest() throws Exception {
        // Mock GitLab API response for MR creation
        String mockResponse = """
            {
                "id": 1,
                "iid": 123,
                "project_id": 12345,
                "title": "Security fixes",
                "state": "opened",
                "web_url": "https://gitlab.com/test-group/test-project/-/merge_requests/123",
                "author": {
                    "username": "vulnpatcher-bot"
                }
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("POST")
                .withPath("/api/v4/projects/test-group%2Ftest-project/merge_requests"))
            .respond(response()
                .withStatusCode(201)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Mock user lookup for reviewers
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/api/v4/users")
                .withQueryStringParameter("username", "security-reviewer"))
            .respond(response()
                .withStatusCode(200)
                .withBody("[{\"id\": 67890, \"username\": \"security-reviewer\"}]"));
        
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
            gitLabProvider.createPullRequest(request);
        
        GitProvider.PullRequestResult result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("123", result.getPullRequestId());
        assertEquals("https://gitlab.com/test-group/test-project/-/merge_requests/123", 
            result.getPullRequestUrl());
    }
    
    @Test
    @Order(5)
    @DisplayName("Test getting merge request status from GitLab")
    public void testGetMergeRequestStatus() throws Exception {
        // Mock GitLab API response
        String mockResponse = """
            {
                "id": 1,
                "iid": 123,
                "state": "opened",
                "title": "Security fixes",
                "author": {
                    "username": "vulnpatcher-bot"
                },
                "merge_status": "can_be_merged",
                "pipeline": {
                    "status": "success",
                    "web_url": "https://gitlab.com/test-group/test-project/-/pipelines/456"
                }
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/api/v4/projects/test-group%2Ftest-project/merge_requests/123"))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Mock approvals
        String approvalsResponse = """
            {
                "approved": true,
                "approved_by": [
                    {
                        "user": {
                            "username": "security-reviewer"
                        }
                    }
                ]
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/api/v4/projects/test-group%2Ftest-project/merge_requests/123/approvals"))
            .respond(response()
                .withStatusCode(200)
                .withBody(approvalsResponse));
        
        // Test
        Map<String, String> credentials = Map.of("token", TEST_TOKEN);
        CompletableFuture<GitProvider.PullRequestStatus> future = 
            gitLabProvider.getPullRequestStatus(TEST_REPO_URL, "123", credentials);
        
        GitProvider.PullRequestStatus status = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(status);
        assertEquals("123", status.getId());
        assertEquals("opened", status.getState());
        assertEquals("Security fixes", status.getTitle());
        assertTrue(status.getMergeable());
        
        // Check reviews/approvals
        assertNotNull(status.getReviews());
        assertEquals(1, status.getReviews().size());
        assertEquals("security-reviewer", status.getReviews().get(0).getReviewer());
        assertEquals("approved", status.getReviews().get(0).getState());
        
        // Check pipeline status
        assertNotNull(status.getChecks());
        assertEquals(1, status.getChecks().size());
        assertEquals("Pipeline", status.getChecks().get(0).getName());
        assertEquals("success", status.getChecks().get(0).getStatus());
    }
}

@TestProfile(GitLabIntegrationTestProfile.class)
class GitLabIntegrationTestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "vulnpatcher.gitlab.url", "http://localhost:8889",
            "vulnpatcher.gitlab.token", "test-token"
        );
    }
}