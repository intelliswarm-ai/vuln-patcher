package ai.intelliswarm.vulnpatcher.agents;

import ai.intelliswarm.vulnpatcher.config.VulnerabilityAnalysis;
import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class SecurityExpertReviewerAgent {
    
    @Inject
    @VulnerabilityAnalysis
    OllamaChatModel analysisModel;
    
    public CompletableFuture<String> analyzeSecurityImplications(String code, Vulnerability vulnerability) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(
                    "Analyze the security implications of the following fix:\n" +
                    "Vulnerability: %s (%s)\n" +
                    "Severity: %s\n" +
                    "Proposed Fix:\n%s\n" +
                    "Evaluate: security completeness, potential bypasses, compliance, and remaining risks.",
                    vulnerability.getTitle(), vulnerability.getId(), vulnerability.getSeverity(), code
                );
                
                UserMessage userMessage = UserMessage.from(prompt);
                return analysisModel.generate(userMessage).content().text();
            } catch (Exception e) {
                Log.error("Error analyzing security implications: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}