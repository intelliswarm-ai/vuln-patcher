package ai.intelliswarm.vulnpatcher.services;

import ai.intelliswarm.vulnpatcher.git.providers.GitProvider;
import ai.intelliswarm.vulnpatcher.orchestrator.LLMOrchestrator;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class PullRequestService {
    
    @Inject
    GitProvider gitProvider;
    
    public Uni<PullRequestResult> createPullRequest(String repositoryUrl, LLMOrchestrator.WorkflowResult workflowResult) {
        return Uni.createFrom().item(() -> {
            Log.info("Creating pull request for vulnerability fix: " + workflowResult.getVulnerabilityId());
            
            try {
                // Create branch name
                String branchName = "fix-" + workflowResult.getVulnerabilityId().toLowerCase().replace("_", "-");
                
                // Create PR title and body
                String title = "Fix " + workflowResult.getVulnerabilityId() + " vulnerability";
                String body = buildPullRequestBody(workflowResult);
                
                // Create the PR using git provider
                String prUrl = gitProvider.createPullRequest(repositoryUrl, branchName, title, body);
                
                PullRequestResult result = new PullRequestResult();
                result.setPullRequestUrl(prUrl);
                result.setBranch(branchName);
                result.setSuccess(true);
                
                return result;
                
            } catch (Exception e) {
                Log.error("Failed to create pull request", e);
                PullRequestResult result = new PullRequestResult();
                result.setSuccess(false);
                result.setError(e.getMessage());
                return result;
            }
        });
    }
    
    private String buildPullRequestBody(LLMOrchestrator.WorkflowResult workflowResult) {
        StringBuilder body = new StringBuilder();
        body.append("## Vulnerability Fix\n\n");
        body.append("**Vulnerability ID:** ").append(workflowResult.getVulnerabilityId()).append("\n\n");
        body.append("### Solution\n");
        body.append(workflowResult.getFinalSolution()).append("\n\n");
        body.append("### Recommendations\n");
        for (String recommendation : workflowResult.getRecommendations()) {
            body.append("- ").append(recommendation).append("\n");
        }
        body.append("\n**Confidence:** ").append(String.format("%.2f%%", workflowResult.getConfidence() * 100));
        return body.toString();
    }
    
    public static class PullRequestResult {
        private String pullRequestUrl;
        private String branch;
        private boolean success;
        private String error;
        
        // Getters and setters
        public String getPullRequestUrl() { return pullRequestUrl; }
        public void setPullRequestUrl(String pullRequestUrl) { this.pullRequestUrl = pullRequestUrl; }
        
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}