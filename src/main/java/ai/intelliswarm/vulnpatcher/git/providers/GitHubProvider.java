package ai.intelliswarm.vulnpatcher.git.providers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kohsuke.github.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class GitHubProvider implements GitProvider {
    
    private static final Logger LOGGER = Logger.getLogger(GitHubProvider.class.getName());
    
    @ConfigProperty(name = "vulnpatcher.github.token", defaultValue = "")
    String githubToken;
    
    @ConfigProperty(name = "vulnpatcher.github.app-id", defaultValue = "")
    String githubAppId;
    
    @ConfigProperty(name = "vulnpatcher.github.private-key", defaultValue = "")
    String githubPrivateKey;
    
    @Override
    public String getProviderName() {
        return "GitHub";
    }
    
    @Override
    public boolean canHandle(String repositoryUrl) {
        return repositoryUrl.contains("github.com");
    }
    
    @Override
    public CredentialsProvider getCredentialsProvider(String repositoryUrl, Map<String, String> credentials) {
        String token = credentials != null ? credentials.getOrDefault("token", githubToken) : githubToken;
        
        if (token != null && !token.isEmpty()) {
            return new UsernamePasswordCredentialsProvider(token, "");
        }
        
        return null;
    }
    
    @Override
    public CompletableFuture<RepositoryMetadata> getRepositoryMetadata(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GitHub github = createGitHubClient(credentials);
                String repoPath = extractRepoPath(repositoryUrl);
                GHRepository repo = github.getRepository(repoPath);
                
                RepositoryMetadata metadata = new RepositoryMetadata();
                metadata.setName(repo.getName());
                metadata.setOwner(repo.getOwnerName());
                metadata.setDescription(repo.getDescription());
                metadata.setDefaultBranch(repo.getDefaultBranch());
                metadata.setLanguage(repo.getLanguage());
                metadata.setSize(repo.getSize());
                metadata.setIsPrivate(repo.isPrivate());
                metadata.setCloneUrl(repo.getHttpTransportUrl());
                
                Map<String, Object> additionalInfo = new HashMap<>();
                additionalInfo.put("stars", repo.getStargazersCount());
                additionalInfo.put("forks", repo.getForksCount());
                additionalInfo.put("openIssues", repo.getOpenIssueCount());
                metadata.setAdditionalInfo(additionalInfo);
                
                return metadata;
                
            } catch (IOException e) {
                LOGGER.severe("Error fetching GitHub repository metadata: " + e.getMessage());
                throw new RuntimeException("Failed to fetch repository metadata", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<String>> listBranches(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GitHub github = createGitHubClient(credentials);
                String repoPath = extractRepoPath(repositoryUrl);
                GHRepository repo = github.getRepository(repoPath);
                
                List<String> branches = new ArrayList<>();
                for (Map.Entry<String, GHBranch> entry : repo.getBranches().entrySet()) {
                    branches.add(entry.getKey());
                }
                
                return branches;
                
            } catch (IOException e) {
                LOGGER.severe("Error listing GitHub branches: " + e.getMessage());
                throw new RuntimeException("Failed to list branches", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<String> getDefaultBranch(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GitHub github = createGitHubClient(credentials);
                String repoPath = extractRepoPath(repositoryUrl);
                GHRepository repo = github.getRepository(repoPath);
                
                return repo.getDefaultBranch();
                
            } catch (IOException e) {
                LOGGER.severe("Error getting default branch: " + e.getMessage());
                return "main"; // Default fallback
            }
        });
    }
    
    @Override
    public CompletableFuture<PullRequestResult> createPullRequest(PullRequestRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            PullRequestResult result = new PullRequestResult();
            
            try {
                GitHub github = createGitHubClient(request.getCredentials());
                String repoPath = extractRepoPath(request.getRepositoryUrl());
                GHRepository repo = github.getRepository(repoPath);
                
                // Create pull request
                GHPullRequest pr = repo.createPullRequest(
                    request.getTitle(),
                    request.getSourceBranch(),
                    request.getTargetBranch(),
                    request.getDescription()
                );
                
                // Add reviewers if specified
                if (request.getReviewers() != null && !request.getReviewers().isEmpty()) {
                    List<GHUser> reviewers = new ArrayList<>();
                    for (String reviewer : request.getReviewers()) {
                        reviewers.add(github.getUser(reviewer));
                    }
                    pr.requestReviewers(reviewers);
                }
                
                // Add labels if specified
                if (request.getLabels() != null && !request.getLabels().isEmpty()) {
                    pr.addLabels(request.getLabels().toArray(new String[0]));
                }
                
                result.setSuccess(true);
                result.setPullRequestId(String.valueOf(pr.getNumber()));
                result.setPullRequestUrl(pr.getHtmlUrl().toString());
                result.setMessage("Pull request created successfully");
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("number", pr.getNumber());
                metadata.put("state", pr.getState());
                result.setMetadata(metadata);
                
            } catch (Exception e) {
                LOGGER.severe("Error creating GitHub pull request: " + e.getMessage());
                result.setSuccess(false);
                result.setMessage("Failed to create pull request: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    @Override
    public CompletableFuture<PullRequestStatus> getPullRequestStatus(String repositoryUrl, String prId, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GitHub github = createGitHubClient(credentials);
                String repoPath = extractRepoPath(repositoryUrl);
                GHRepository repo = github.getRepository(repoPath);
                GHPullRequest pr = repo.getPullRequest(Integer.parseInt(prId));
                
                PullRequestStatus status = new PullRequestStatus();
                status.setId(prId);
                status.setState(pr.getState().toString());
                status.setTitle(pr.getTitle());
                status.setAuthor(pr.getUser().getLogin());
                status.setMergeable(pr.getMergeable());
                
                // Get reviews
                List<PullRequestStatus.ReviewStatus> reviews = new ArrayList<>();
                for (GHPullRequestReview review : pr.listReviews()) {
                    PullRequestStatus.ReviewStatus reviewStatus = new PullRequestStatus.ReviewStatus();
                    reviewStatus.setReviewer(review.getUser().getLogin());
                    reviewStatus.setState(review.getState().toString());
                    reviewStatus.setComment(review.getBody());
                    reviews.add(reviewStatus);
                }
                status.setReviews(reviews);
                
                // Get checks
                List<PullRequestStatus.CheckStatus> checks = new ArrayList<>();
                GHCommitStatus commitStatus = repo.getLastCommitStatus(pr.getHead().getSha());
                if (commitStatus != null) {
                    PullRequestStatus.CheckStatus check = new PullRequestStatus.CheckStatus();
                    check.setName("CI/CD");
                    check.setStatus(commitStatus.getState().toString());
                    checks.add(check);
                }
                status.setChecks(checks);
                
                return status;
                
            } catch (Exception e) {
                LOGGER.severe("Error getting pull request status: " + e.getMessage());
                throw new RuntimeException("Failed to get pull request status", e);
            }
        });
    }
    
    private GitHub createGitHubClient(Map<String, String> credentials) throws IOException {
        String token = credentials != null ? credentials.getOrDefault("token", githubToken) : githubToken;
        
        if (token != null && !token.isEmpty()) {
            return new GitHubBuilder().withOAuthToken(token).build();
        } else if (!githubAppId.isEmpty() && !githubPrivateKey.isEmpty()) {
            // Use GitHub App authentication
            return new GitHubBuilder()
                .withAppInstallationToken(githubAppId)
                .build();
        } else {
            // Anonymous access (limited rate)
            return GitHub.connectAnonymously();
        }
    }
    
    private String extractRepoPath(String url) {
        // Extract owner/repo from various GitHub URL formats
        String path = url.replaceAll("https?://github.com/", "")
                        .replaceAll("\\.git$", "")
                        .replaceAll("/$", "");
        
        // Handle SSH URLs
        if (path.startsWith("git@github.com:")) {
            path = path.replace("git@github.com:", "");
        }
        
        return path;
    }
}