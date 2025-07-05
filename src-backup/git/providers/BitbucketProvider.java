package ai.intelliswarm.vulnpatcher.git.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class BitbucketProvider implements GitProvider {
    
    private static final Logger LOGGER = Logger.getLogger(BitbucketProvider.class.getName());
    private static final String BITBUCKET_API_URL = "https://api.bitbucket.org/2.0";
    
    @ConfigProperty(name = "vulnpatcher.bitbucket.username", defaultValue = "")
    String bitbucketUsername;
    
    @ConfigProperty(name = "vulnpatcher.bitbucket.app-password", defaultValue = "")
    String bitbucketAppPassword;
    
    @ConfigProperty(name = "vulnpatcher.bitbucket.workspace", defaultValue = "")
    String defaultWorkspace;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    
    @Override
    public String getProviderName() {
        return "Bitbucket";
    }
    
    @Override
    public boolean canHandle(String repositoryUrl) {
        return repositoryUrl.contains("bitbucket.org");
    }
    
    @Override
    public CredentialsProvider getCredentialsProvider(String repositoryUrl, Map<String, String> credentials) {
        String username = credentials != null ? credentials.getOrDefault("username", bitbucketUsername) : bitbucketUsername;
        String password = credentials != null ? credentials.getOrDefault("password", bitbucketAppPassword) : bitbucketAppPassword;
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            return new UsernamePasswordCredentialsProvider(username, password);
        }
        
        return null;
    }
    
    @Override
    public CompletableFuture<RepositoryMetadata> getRepositoryMetadata(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] parts = extractWorkspaceAndRepo(repositoryUrl);
                String workspace = parts[0];
                String repoSlug = parts[1];
                
                String apiUrl = String.format("%s/repositories/%s/%s", BITBUCKET_API_URL, workspace, repoSlug);
                HttpGet request = new HttpGet(apiUrl);
                addAuthHeader(request, credentials);
                
                return httpClient.execute(request, response -> {
                    if (response.getCode() == 200) {
                        Map<String, Object> repoData = objectMapper.readValue(
                            response.getEntity().getContent(), Map.class
                        );
                        
                        RepositoryMetadata metadata = new RepositoryMetadata();
                        metadata.setName((String) repoData.get("name"));
                        metadata.setDescription((String) repoData.get("description"));
                        metadata.setIsPrivate((Boolean) repoData.get("is_private"));
                        
                        // Get owner info
                        Map<String, Object> owner = (Map<String, Object>) repoData.get("owner");
                        if (owner != null) {
                            metadata.setOwner((String) owner.get("display_name"));
                        }
                        
                        // Get clone URLs
                        List<Map<String, Object>> cloneLinks = (List<Map<String, Object>>) repoData.get("links");
                        if (cloneLinks != null) {
                            for (Map<String, Object> link : cloneLinks) {
                                if ("https".equals(link.get("name"))) {
                                    metadata.setCloneUrl((String) link.get("href"));
                                    break;
                                }
                            }
                        }
                        
                        // Get main branch
                        Map<String, Object> mainBranch = (Map<String, Object>) repoData.get("mainbranch");
                        if (mainBranch != null) {
                            metadata.setDefaultBranch((String) mainBranch.get("name"));
                        } else {
                            metadata.setDefaultBranch("master"); // Bitbucket default
                        }
                        
                        // Additional info
                        Map<String, Object> additionalInfo = new HashMap<>();
                        additionalInfo.put("scm", repoData.get("scm"));
                        additionalInfo.put("size", repoData.get("size"));
                        additionalInfo.put("language", repoData.get("language"));
                        metadata.setAdditionalInfo(additionalInfo);
                        
                        return metadata;
                    } else {
                        throw new RuntimeException("Failed to fetch repository metadata: HTTP " + response.getCode());
                    }
                });
                
            } catch (Exception e) {
                LOGGER.severe("Error fetching Bitbucket repository metadata: " + e.getMessage());
                throw new RuntimeException("Failed to fetch repository metadata", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<String>> listBranches(String repositoryUrl, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] parts = extractWorkspaceAndRepo(repositoryUrl);
                String workspace = parts[0];
                String repoSlug = parts[1];
                
                String apiUrl = String.format("%s/repositories/%s/%s/refs/branches", 
                    BITBUCKET_API_URL, workspace, repoSlug);
                HttpGet request = new HttpGet(apiUrl);
                addAuthHeader(request, credentials);
                
                return httpClient.execute(request, response -> {
                    if (response.getCode() == 200) {
                        Map<String, Object> result = objectMapper.readValue(
                            response.getEntity().getContent(), Map.class
                        );
                        
                        List<String> branches = new ArrayList<>();
                        List<Map<String, Object>> values = (List<Map<String, Object>>) result.get("values");
                        
                        if (values != null) {
                            for (Map<String, Object> branch : values) {
                                branches.add((String) branch.get("name"));
                            }
                        }
                        
                        return branches;
                    } else {
                        throw new RuntimeException("Failed to list branches: HTTP " + response.getCode());
                    }
                });
                
            } catch (Exception e) {
                LOGGER.severe("Error listing Bitbucket branches: " + e.getMessage());
                throw new RuntimeException("Failed to list branches", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<String> getDefaultBranch(String repositoryUrl, Map<String, String> credentials) {
        return getRepositoryMetadata(repositoryUrl, credentials)
            .thenApply(metadata -> metadata.getDefaultBranch());
    }
    
    @Override
    public CompletableFuture<PullRequestResult> createPullRequest(PullRequestRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            PullRequestResult result = new PullRequestResult();
            
            try {
                String[] parts = extractWorkspaceAndRepo(request.getRepositoryUrl());
                String workspace = parts[0];
                String repoSlug = parts[1];
                
                String apiUrl = String.format("%s/repositories/%s/%s/pullrequests", 
                    BITBUCKET_API_URL, workspace, repoSlug);
                
                // Build pull request payload
                Map<String, Object> payload = new HashMap<>();
                payload.put("title", request.getTitle());
                payload.put("description", request.getDescription());
                
                Map<String, Object> source = new HashMap<>();
                source.put("branch", Map.of("name", request.getSourceBranch()));
                payload.put("source", source);
                
                Map<String, Object> destination = new HashMap<>();
                destination.put("branch", Map.of("name", request.getTargetBranch()));
                payload.put("destination", destination);
                
                // Add reviewers if specified
                if (request.getReviewers() != null && !request.getReviewers().isEmpty()) {
                    List<Map<String, String>> reviewers = new ArrayList<>();
                    for (String reviewer : request.getReviewers()) {
                        reviewers.add(Map.of("username", reviewer));
                    }
                    payload.put("reviewers", reviewers);
                }
                
                HttpPost httpPost = new HttpPost(apiUrl);
                httpPost.setHeader("Content-Type", "application/json");
                addAuthHeader(httpPost, request.getCredentials());
                httpPost.setEntity(new StringEntity(
                    objectMapper.writeValueAsString(payload),
                    StandardCharsets.UTF_8
                ));
                
                return httpClient.execute(httpPost, response -> {
                    if (response.getCode() == 201) {
                        Map<String, Object> prData = objectMapper.readValue(
                            response.getEntity().getContent(), Map.class
                        );
                        
                        result.setSuccess(true);
                        result.setPullRequestId(String.valueOf(prData.get("id")));
                        
                        Map<String, Object> links = (Map<String, Object>) prData.get("links");
                        if (links != null) {
                            Map<String, Object> html = (Map<String, Object>) links.get("html");
                            if (html != null) {
                                result.setPullRequestUrl((String) html.get("href"));
                            }
                        }
                        
                        result.setMessage("Pull request created successfully");
                        
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("id", prData.get("id"));
                        metadata.put("state", prData.get("state"));
                        result.setMetadata(metadata);
                        
                        return result;
                    } else {
                        result.setSuccess(false);
                        result.setMessage("Failed to create pull request: HTTP " + response.getCode());
                        return result;
                    }
                });
                
            } catch (Exception e) {
                LOGGER.severe("Error creating Bitbucket pull request: " + e.getMessage());
                result.setSuccess(false);
                result.setMessage("Failed to create pull request: " + e.getMessage());
                return result;
            }
        });
    }
    
    @Override
    public CompletableFuture<PullRequestStatus> getPullRequestStatus(String repositoryUrl, String prId, Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] parts = extractWorkspaceAndRepo(repositoryUrl);
                String workspace = parts[0];
                String repoSlug = parts[1];
                
                String apiUrl = String.format("%s/repositories/%s/%s/pullrequests/%s", 
                    BITBUCKET_API_URL, workspace, repoSlug, prId);
                HttpGet request = new HttpGet(apiUrl);
                addAuthHeader(request, credentials);
                
                return httpClient.execute(request, response -> {
                    if (response.getCode() == 200) {
                        Map<String, Object> prData = objectMapper.readValue(
                            response.getEntity().getContent(), Map.class
                        );
                        
                        PullRequestStatus status = new PullRequestStatus();
                        status.setId(prId);
                        status.setState((String) prData.get("state"));
                        status.setTitle((String) prData.get("title"));
                        
                        Map<String, Object> author = (Map<String, Object>) prData.get("author");
                        if (author != null) {
                            status.setAuthor((String) author.get("display_name"));
                        }
                        
                        // Get reviewers and their status
                        List<PullRequestStatus.ReviewStatus> reviews = new ArrayList<>();
                        List<Map<String, Object>> participants = 
                            (List<Map<String, Object>>) prData.get("participants");
                        
                        if (participants != null) {
                            for (Map<String, Object> participant : participants) {
                                if ("REVIEWER".equals(participant.get("role"))) {
                                    PullRequestStatus.ReviewStatus reviewStatus = new PullRequestStatus.ReviewStatus();
                                    Map<String, Object> user = (Map<String, Object>) participant.get("user");
                                    if (user != null) {
                                        reviewStatus.setReviewer((String) user.get("display_name"));
                                    }
                                    
                                    Boolean approved = (Boolean) participant.get("approved");
                                    reviewStatus.setState(approved != null && approved ? "approved" : "pending");
                                    reviews.add(reviewStatus);
                                }
                            }
                        }
                        status.setReviews(reviews);
                        
                        // Build status (CI/CD)
                        // Note: Bitbucket uses a separate API for build status
                        List<PullRequestStatus.CheckStatus> checks = new ArrayList<>();
                        // Simplified - in production, fetch from statuses API
                        status.setChecks(checks);
                        
                        return status;
                    } else {
                        throw new RuntimeException("Failed to get pull request status: HTTP " + response.getCode());
                    }
                });
                
            } catch (Exception e) {
                LOGGER.severe("Error getting pull request status: " + e.getMessage());
                throw new RuntimeException("Failed to get pull request status", e);
            }
        });
    }
    
    private void addAuthHeader(org.apache.hc.core5.http.ClassicHttpRequest request, Map<String, String> credentials) {
        String username = credentials != null ? credentials.getOrDefault("username", bitbucketUsername) : bitbucketUsername;
        String password = credentials != null ? credentials.getOrDefault("password", bitbucketAppPassword) : bitbucketAppPassword;
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            String auth = username + ":" + password;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);
            request.setHeader("Authorization", authHeader);
        }
    }
    
    private String[] extractWorkspaceAndRepo(String url) {
        // Extract workspace/repo from various Bitbucket URL formats
        String path = url.replaceAll("https?://bitbucket.org/", "")
                        .replaceAll("\\.git$", "")
                        .replaceAll("/$", "");
        
        // Handle SSH URLs
        if (path.contains("git@bitbucket.org:")) {
            path = path.replace("git@bitbucket.org:", "");
        }
        
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return new String[]{parts[0], parts[1]};
        } else {
            throw new IllegalArgumentException("Invalid Bitbucket repository URL: " + url);
        }
    }
}