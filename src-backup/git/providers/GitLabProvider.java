package ai.intelliswarm.vulnpatcher.git.providers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class GitLabProvider implements GitProvider {
    
    private static final Logger LOGGER = Logger.getLogger(GitLabProvider.class.getName());
    
    @ConfigProperty(name = "vulnpatcher.gitlab.url", defaultValue = "https://gitlab.com")
    String gitlabUrl;
    
    @ConfigProperty(name = "vulnpatcher.gitlab.token", defaultValue = "")
    String gitlabToken;
    
    @Override
    public String getProviderName() {
        return "GitLab";
    }
    
    @Override
    public boolean canHandle(String repositoryUrl) {
        return repositoryUrl.contains("gitlab.com") || repositoryUrl.contains(gitlabUrl);
    }
    
    @Override
    public CredentialsProvider getCredentialsProvider(String repositoryUrl, Map<String, String> credentials) {
        String token = credentials != null ? credentials.getOrDefault("token", gitlabToken) : gitlabToken;
        
        if (token != null && !token.isEmpty()) {
            return new UsernamePasswordCredentialsProvider("oauth2", token);
        }
        
        // Username/password authentication
        if (credentials != null && credentials.containsKey("username") && credentials.containsKey("password")) {
            return new UsernamePasswordCredentialsProvider(
                credentials.get("username"),
                credentials.get("password")
            );
        }
        
        return null;
    }
    
    @Override
    public CompletableFuture<RepositoryMetadata> getRepositoryMetadata(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try (GitLabApi gitLabApi = createGitLabApi(credentials)) {
                String projectPath = extractProjectPath(repositoryUrl);
                Project project = gitLabApi.getProjectApi().getProject(projectPath);
                
                RepositoryMetadata metadata = new RepositoryMetadata();
                metadata.setName(project.getName());
                metadata.setOwner(project.getNamespace().getName());
                metadata.setDescription(project.getDescription());
                metadata.setDefaultBranch(project.getDefaultBranch());
                metadata.setIsPrivate(project.getVisibility() == Visibility.PRIVATE);
                metadata.setCloneUrl(project.getHttpUrlToRepo());
                
                Map<String, Object> additionalInfo = new HashMap<>();
                additionalInfo.put("stars", project.getStarCount());
                additionalInfo.put("forks", project.getForksCount());
                additionalInfo.put("openIssues", project.getOpenIssuesCount());
                additionalInfo.put("visibility", project.getVisibility().toString());
                metadata.setAdditionalInfo(additionalInfo);
                
                return metadata;
                
            } catch (GitLabApiException e) {
                LOGGER.severe("Error fetching GitLab repository metadata: " + e.getMessage());
                throw new RuntimeException("Failed to fetch repository metadata", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<String>> listBranches(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try (GitLabApi gitLabApi = createGitLabApi(credentials)) {
                String projectPath = extractProjectPath(repositoryUrl);
                List<Branch> branches = gitLabApi.getRepositoryApi().getBranches(projectPath);
                
                return branches.stream()
                    .map(Branch::getName)
                    .collect(Collectors.toList());
                
            } catch (GitLabApiException e) {
                LOGGER.severe("Error listing GitLab branches: " + e.getMessage());
                throw new RuntimeException("Failed to list branches", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<String> getDefaultBranch(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try (GitLabApi gitLabApi = createGitLabApi(credentials)) {
                String projectPath = extractProjectPath(repositoryUrl);
                Project project = gitLabApi.getProjectApi().getProject(projectPath);
                
                return project.getDefaultBranch();
                
            } catch (GitLabApiException e) {
                LOGGER.severe("Error getting default branch: " + e.getMessage());
                return "main"; // Default fallback
            }
        });
    }
    
    @Override
    public CompletableFuture<PullRequestResult> createPullRequest(PullRequestRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            PullRequestResult result = new PullRequestResult();
            
            try (GitLabApi gitLabApi = createGitLabApi(request.getCredentials())) {
                String projectPath = extractProjectPath(request.getRepositoryUrl());
                
                // Create merge request (GitLab's term for pull request)
                MergeRequestParams params = new MergeRequestParams()
                    .withSourceBranch(request.getSourceBranch())
                    .withTargetBranch(request.getTargetBranch())
                    .withTitle(request.getTitle())
                    .withDescription(request.getDescription())
                    .withRemoveSourceBranch(false);
                
                // Add reviewers if specified
                if (request.getReviewers() != null && !request.getReviewers().isEmpty()) {
                    List<Long> reviewerIds = new ArrayList<>();
                    for (String reviewer : request.getReviewers()) {
                        try {
                            User user = gitLabApi.getUserApi().getUser(reviewer);
                            reviewerIds.add(user.getId());
                        } catch (GitLabApiException e) {
                            LOGGER.warning("Could not find reviewer: " + reviewer);
                        }
                    }
                    params.withReviewerIds(reviewerIds);
                }
                
                // Add labels if specified
                if (request.getLabels() != null && !request.getLabels().isEmpty()) {
                    params.withLabels(request.getLabels());
                }
                
                MergeRequest mr = gitLabApi.getMergeRequestApi().createMergeRequest(
                    projectPath, params
                );
                
                result.setSuccess(true);
                result.setPullRequestId(String.valueOf(mr.getIid()));
                result.setPullRequestUrl(mr.getWebUrl());
                result.setMessage("Merge request created successfully");
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("iid", mr.getIid());
                metadata.put("id", mr.getId());
                metadata.put("state", mr.getState());
                result.setMetadata(metadata);
                
            } catch (Exception e) {
                LOGGER.severe("Error creating GitLab merge request: " + e.getMessage());
                result.setSuccess(false);
                result.setMessage("Failed to create merge request: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    @Override
    public CompletableFuture<PullRequestStatus> getPullRequestStatus(String repositoryUrl, String prId, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try (GitLabApi gitLabApi = createGitLabApi(credentials)) {
                String projectPath = extractProjectPath(repositoryUrl);
                MergeRequest mr = gitLabApi.getMergeRequestApi().getMergeRequest(
                    projectPath, Long.parseLong(prId)
                );
                
                PullRequestStatus status = new PullRequestStatus();
                status.setId(prId);
                status.setState(mr.getState());
                status.setTitle(mr.getTitle());
                status.setAuthor(mr.getAuthor().getUsername());
                status.setMergeable(mr.getMergeStatus().equals("can_be_merged"));
                
                // Get approvals
                List<PullRequestStatus.ReviewStatus> reviews = new ArrayList<>();
                try {
                    MergeRequestApprovals approvals = gitLabApi.getMergeRequestApi()
                        .getApprovals(projectPath, mr.getIid());
                    
                    for (User approver : approvals.getApprovedBy()) {
                        PullRequestStatus.ReviewStatus reviewStatus = new PullRequestStatus.ReviewStatus();
                        reviewStatus.setReviewer(approver.getUsername());
                        reviewStatus.setState("approved");
                        reviews.add(reviewStatus);
                    }
                } catch (GitLabApiException e) {
                    LOGGER.warning("Could not fetch approvals: " + e.getMessage());
                }
                status.setReviews(reviews);
                
                // Get pipeline status (CI/CD checks)
                List<PullRequestStatus.CheckStatus> checks = new ArrayList<>();
                if (mr.getPipeline() != null) {
                    PullRequestStatus.CheckStatus check = new PullRequestStatus.CheckStatus();
                    check.setName("Pipeline");
                    check.setStatus(mr.getPipeline().getStatus().toString());
                    check.setDetailsUrl(mr.getPipeline().getWebUrl());
                    checks.add(check);
                }
                status.setChecks(checks);
                
                return status;
                
            } catch (Exception e) {
                LOGGER.severe("Error getting merge request status: " + e.getMessage());
                throw new RuntimeException("Failed to get merge request status", e);
            }
        });
    }
    
    private GitLabApi createGitLabApi(Map<String, String> credentials) {
        String token = credentials != null ? credentials.getOrDefault("token", gitlabToken) : gitlabToken;
        String url = credentials != null ? credentials.getOrDefault("url", gitlabUrl) : gitlabUrl;
        
        if (token != null && !token.isEmpty()) {
            return new GitLabApi(url, token);
        }
        
        // Username/password authentication
        if (credentials != null && credentials.containsKey("username") && credentials.containsKey("password")) {
            GitLabApi api = new GitLabApi(url, (String) null);
            try {
                api.oauth2Login(credentials.get("username"), credentials.get("password"));
                return api;
            } catch (GitLabApiException e) {
                LOGGER.warning("OAuth2 login failed, trying basic auth");
                return new GitLabApi(url, credentials.get("username"), credentials.get("password"));
            }
        }
        
        throw new RuntimeException("No valid GitLab credentials provided");
    }
    
    private String extractProjectPath(String url) {
        // Extract namespace/project from various GitLab URL formats
        String path = url.replaceAll("https?://[^/]+/", "")
                        .replaceAll("\\.git$", "")
                        .replaceAll("/$", "");
        
        // Handle SSH URLs
        if (path.contains("git@")) {
            path = path.substring(path.indexOf(':') + 1);
        }
        
        // Handle /-/ in path (GitLab specific)
        path = path.replace("/-/", "/");
        
        return path;
    }
}