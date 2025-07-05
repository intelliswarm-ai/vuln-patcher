package ai.intelliswarm.vulnpatcher.git.providers;

import java.util.concurrent.CompletableFuture;

public interface GitProvider {
    
    String getProviderName();
    boolean canHandle(String repositoryUrl);
    
    CompletableFuture<PullRequestResult> createPullRequest(PullRequestRequest request);
    
    class PullRequestRequest {
        private String repositoryUrl;
        private String title;
        private String description;
        private String sourceBranch;
        private String targetBranch;
        
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
    }
    
    class PullRequestResult {
        private String url;
        private String id;
        private boolean success;
        private String error;
        
        // Getters and setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}