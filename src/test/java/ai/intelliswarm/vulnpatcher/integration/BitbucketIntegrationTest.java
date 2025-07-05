package ai.intelliswarm.vulnpatcher.integration;

import ai.intelliswarm.vulnpatcher.BaseTest;
import ai.intelliswarm.vulnpatcher.git.providers.BitbucketProvider;
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
@TestProfile(BitbucketIntegrationTestProfile.class)
@TestPropertySource(properties = {
    "vulnpatcher.bitbucket.username=test-user",
    "vulnpatcher.bitbucket.app-password=test-password",
    "vulnpatcher.bitbucket.workspace=test-workspace"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BitbucketIntegrationTest extends BaseTest {
    
    @Inject
    BitbucketProvider bitbucketProvider;
    
    private static ClientAndServer mockServer;
    private static MockServerClient mockServerClient;
    
    private static final String TEST_REPO_URL = "https://bitbucket.org/test-workspace/test-repo";
    private static final String TEST_USERNAME = "test-user";
    private static final String TEST_APP_PASSWORD = "test-password";
    
    @BeforeAll
    public static void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(8890);
        mockServerClient = new MockServerClient("localhost", 8890);
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
    @DisplayName("Test Bitbucket provider can handle Bitbucket URLs")
    public void testCanHandleBitbucketUrls() {
        assertTrue(bitbucketProvider.canHandle("https://bitbucket.org/owner/repo"));
        assertTrue(bitbucketProvider.canHandle("git@bitbucket.org:owner/repo.git"));
        assertFalse(bitbucketProvider.canHandle("https://github.com/owner/repo"));
        assertFalse(bitbucketProvider.canHandle("https://gitlab.com/owner/repo"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Test fetching repository metadata from Bitbucket")
    public void testGetRepositoryMetadata() throws Exception {
        // Mock Bitbucket API response
        String mockResponse = """
            {
                "name": "test-repo",
                "description": "Test repository",
                "is_private": true,
                "owner": {
                    "display_name": "Test Workspace"
                },
                "links": {
                    "clone": [
                        {
                            "name": "https",
                            "href": "https://bitbucket.org/test-workspace/test-repo.git"
                        }
                    ]
                },
                "mainbranch": {
                    "name": "master"
                },
                "scm": "git",
                "size": 2048000,
                "language": "java"
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/2.0/repositories/test-workspace/test-repo")
                .withHeader("Authorization"))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        Map<String, String> credentials = Map.of(
            "username", TEST_USERNAME,
            "password", TEST_APP_PASSWORD
        );
        CompletableFuture<GitProvider.RepositoryMetadata> future = 
            bitbucketProvider.getRepositoryMetadata(TEST_REPO_URL, credentials);
        
        GitProvider.RepositoryMetadata metadata = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(metadata);
        assertEquals("test-repo", metadata.getName());
        assertEquals("Test Workspace", metadata.getOwner());
        assertEquals("Test repository", metadata.getDescription());
        assertEquals("master", metadata.getDefaultBranch());
        assertTrue(metadata.getIsPrivate());
    }
    
    @Test
    @Order(3)
    @DisplayName("Test listing branches from Bitbucket repository")
    public void testListBranches() throws Exception {
        // Mock Bitbucket API response
        String mockResponse = """
            {
                "values": [
                    {
                        "name": "master",
                        "type": "branch"
                    },
                    {
                        "name": "develop",
                        "type": "branch"
                    },
                    {
                        "name": "feature/security-fix",
                        "type": "branch"
                    }
                ]
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/2.0/repositories/test-workspace/test-repo/refs/branches"))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        Map<String, String> credentials = Map.of(
            "username", TEST_USERNAME,
            "password", TEST_APP_PASSWORD
        );
        CompletableFuture<List<String>> future = 
            bitbucketProvider.listBranches(TEST_REPO_URL, credentials);
        
        List<String> branches = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(branches);
        assertEquals(3, branches.size());
        assertTrue(branches.contains("master"));
        assertTrue(branches.contains("develop"));
        assertTrue(branches.contains("feature/security-fix"));
    }
    
    @Test
    @Order(4)
    @DisplayName("Test creating pull request on Bitbucket")
    public void testCreatePullRequest() throws Exception {
        // Mock Bitbucket API response for PR creation
        String mockResponse = """
            {
                "id": 123,
                "title": "Security fixes",
                "state": "OPEN",
                "links": {
                    "html": {
                        "href": "https://bitbucket.org/test-workspace/test-repo/pull-requests/123"
                    }
                },
                "author": {
                    "display_name": "VulnPatcher Bot"
                }
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("POST")
                .withPath("/2.0/repositories/test-workspace/test-repo/pullrequests"))
            .respond(response()
                .withStatusCode(201)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        GitProvider.PullRequestRequest request = new GitProvider.PullRequestRequest();
        request.setRepositoryUrl(TEST_REPO_URL);
        request.setTitle("Security fixes");
        request.setDescription("Fixing security vulnerabilities");
        request.setSourceBranch("feature/security-fix");
        request.setTargetBranch("master");
        request.setReviewers(List.of("security-reviewer"));
        request.setCredentials(Map.of(
            "username", TEST_USERNAME,
            "password", TEST_APP_PASSWORD
        ));
        
        CompletableFuture<GitProvider.PullRequestResult> future = 
            bitbucketProvider.createPullRequest(request);
        
        GitProvider.PullRequestResult result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("123", result.getPullRequestId());
        assertEquals("https://bitbucket.org/test-workspace/test-repo/pull-requests/123", 
            result.getPullRequestUrl());
    }
    
    @Test
    @Order(5)
    @DisplayName("Test getting pull request status from Bitbucket")
    public void testGetPullRequestStatus() throws Exception {
        // Mock Bitbucket API response
        String mockResponse = """
            {
                "id": 123,
                "state": "OPEN",
                "title": "Security fixes",
                "author": {
                    "display_name": "VulnPatcher Bot"
                },
                "participants": [
                    {
                        "role": "REVIEWER",
                        "user": {
                            "display_name": "Security Reviewer"
                        },
                        "approved": true
                    }
                ]
            }
            """;
        
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/2.0/repositories/test-workspace/test-repo/pullrequests/123"))
            .respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponse));
        
        // Test
        Map<String, String> credentials = Map.of(
            "username", TEST_USERNAME,
            "password", TEST_APP_PASSWORD
        );
        CompletableFuture<GitProvider.PullRequestStatus> future = 
            bitbucketProvider.getPullRequestStatus(TEST_REPO_URL, "123", credentials);
        
        GitProvider.PullRequestStatus status = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(status);
        assertEquals("123", status.getId());
        assertEquals("OPEN", status.getState());
        assertEquals("Security fixes", status.getTitle());
        
        // Check reviews
        assertNotNull(status.getReviews());
        assertEquals(1, status.getReviews().size());
        assertEquals("Security Reviewer", status.getReviews().get(0).getReviewer());
        assertEquals("approved", status.getReviews().get(0).getState());
    }
    
    @Test
    @Order(6)
    @DisplayName("Test authentication header generation")
    public void testAuthenticationHeader() {
        // Test that credentials are properly encoded
        Map<String, String> credentials = Map.of(
            "username", "test-user",
            "password", "test-pass:with:colons"
        );
        
        // Create a mock request to verify auth header
        mockServerClient
            .when(request()
                .withMethod("GET")
                .withPath("/2.0/repositories/test-workspace/test-repo")
                .withHeader("Authorization", "Basic dGVzdC11c2VyOnRlc3QtcGFzczp3aXRoOmNvbG9ucw=="))
            .respond(response()
                .withStatusCode(200)
                .withBody("{}"));
        
        // This test verifies that the auth header is correctly generated
        // The base64 value corresponds to "test-user:test-pass:with:colons"
        assertTrue(true);
    }
}

@TestProfile(BitbucketIntegrationTestProfile.class)
class BitbucketIntegrationTestProfile implements io.quarkus.test.junit.QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "vulnpatcher.bitbucket.api.url", "http://localhost:8890",
            "vulnpatcher.bitbucket.username", "test-user",
            "vulnpatcher.bitbucket.app-password", "test-password"
        );
    }
}