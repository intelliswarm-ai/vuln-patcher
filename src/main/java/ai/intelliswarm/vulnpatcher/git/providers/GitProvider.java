package ai.intelliswarm.vulnpatcher.git.providers;

import org.eclipse.jgit.transport.CredentialsProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface GitProvider {
    
    /**
     * Get the provider name (e.g., "GitHub", "GitLab", "Bitbucket")
     */
    String getProviderName();
    
    /**
     * Check if this provider can handle the given repository URL
     */
    boolean canHandle(String repositoryUrl);
    
    /**
     * Get credentials provider for authentication
     */
    CredentialsProvider getCredentialsProvider(String repositoryUrl, Map<String, String> credentials);
    
    /**
     * Get repository metadata
     */
    CompletableFuture<RepositoryMetadata> getRepositoryMetadata(String repositoryUrl, Map<String, String> credentials);
    
    /**
     * List branches in the repository
     */
    CompletableFuture<List<String>> listBranches(String repositoryUrl, Map<String, String> credentials);
    
    /**
     * Get default branch name
     */
    CompletableFuture<String> getDefaultBranch(String repositoryUrl, Map<String, String> credentials);
    
    /**
     * Create a pull request
     */
    CompletableFuture<PullRequestResult> createPullRequest(PullRequestRequest request);
    
    /**
     * Get pull request status
     */
    CompletableFuture<PullRequestStatus> getPullRequestStatus(String repositoryUrl, String prId, Map<String, String> credentials);
    
    /**
     * Repository metadata
     */
    class RepositoryMetadata {
        private String name;
        private String owner;
        private String description;
        private String defaultBranch;
        private String language;
        private Long size;
        private Boolean isPrivate;
        private String cloneUrl;
        private Map<String, Object> additionalInfo;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getDefaultBranch() { return defaultBranch; }
        public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
        public Boolean getIsPrivate() { return isPrivate; }
        public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
        public String getCloneUrl() { return cloneUrl; }
        public void setCloneUrl(String cloneUrl) { this.cloneUrl = cloneUrl; }
        public Map<String, Object> getAdditionalInfo() { return additionalInfo; }
        public void setAdditionalInfo(Map<String, Object> additionalInfo) { this.additionalInfo = additionalInfo; }
    }
    
    /**
     * Pull request creation request
     */
    class PullRequestRequest {
        private String repositoryUrl;
        private String title;
        private String description;
        private String sourceBranch;
        private String targetBranch;
        private List<String> reviewers;
        private List<String> labels;
        private Map<String, String> credentials;
        private Map<String, Object> additionalOptions;
        
        // Getters and setters
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSourceBranch() { return sourceBranch; }
        public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }
        public String getTargetBranch() { return targetBranch; }
        public void setTargetBranch(String targetBranch) { this.targetBranch = targetBranch; }
        public List<String> getReviewers() { return reviewers; }
        public void setReviewers(List<String> reviewers) { this.reviewers = reviewers; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public Map<String, String> getCredentials() { return credentials; }
        public void setCredentials(Map<String, String> credentials) { this.credentials = credentials; }
        public Map<String, Object> getAdditionalOptions() { return additionalOptions; }
        public void setAdditionalOptions(Map<String, Object> additionalOptions) { this.additionalOptions = additionalOptions; }
    }
    
    /**
     * Pull request creation result
     */
    class PullRequestResult {
        private boolean success;
        private String pullRequestId;
        private String pullRequestUrl;
        private String message;
        private Map<String, Object> metadata;
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getPullRequestId() { return pullRequestId; }
        public void setPullRequestId(String pullRequestId) { this.pullRequestId = pullRequestId; }
        public String getPullRequestUrl() { return pullRequestUrl; }
        public void setPullRequestUrl(String pullRequestUrl) { this.pullRequestUrl = pullRequestUrl; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * Pull request status
     */
    class PullRequestStatus {
        private String id;
        private String state; // open, closed, merged
        private String title;
        private String author;
        private List<ReviewStatus> reviews;
        private List<CheckStatus> checks;
        private Boolean mergeable;
        private Map<String, Object> metadata;
        
        public static class ReviewStatus {
            private String reviewer;
            private String state; // approved, changes_requested, pending
            private String comment;
            
            // Getters and setters
            public String getReviewer() { return reviewer; }
            public void setReviewer(String reviewer) { this.reviewer = reviewer; }
            public String getState() { return state; }
            public void setState(String state) { this.state = state; }
            public String getComment() { return comment; }
            public void setComment(String comment) { this.comment = comment; }
        }
        
        public static class CheckStatus {
            private String name;
            private String status; // success, failure, pending
            private String conclusion;
            private String detailsUrl;
            
            // Getters and setters
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getStatus() { return status; }
            public void setStatus(String status) { this.status = status; }
            public String getConclusion() { return conclusion; }
            public void setConclusion(String conclusion) { this.conclusion = conclusion; }
            public String getDetailsUrl() { return detailsUrl; }
            public void setDetailsUrl(String detailsUrl) { this.detailsUrl = detailsUrl; }
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public List<ReviewStatus> getReviews() { return reviews; }
        public void setReviews(List<ReviewStatus> reviews) { this.reviews = reviews; }
        public List<CheckStatus> getChecks() { return checks; }
        public void setChecks(List<CheckStatus> checks) { this.checks = checks; }
        public Boolean getMergeable() { return mergeable; }
        public void setMergeable(Boolean mergeable) { this.mergeable = mergeable; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}