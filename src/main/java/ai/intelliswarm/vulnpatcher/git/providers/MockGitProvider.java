package ai.intelliswarm.vulnpatcher.git.providers;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class MockGitProvider implements GitProvider {
    
    @Override
    public String getProviderName() {
        return "Mock";
    }
    
    @Override
    public boolean canHandle(String repositoryUrl) {
        return true; // Handle all for testing
    }
    
    @Override
    public CompletableFuture<PullRequestResult> createPullRequest(PullRequestRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            PullRequestResult result = new PullRequestResult();
            result.setUrl("https://github.com/mock/repo/pull/123");
            result.setId("123");
            result.setSuccess(true);
            return result;
        });
    }
}