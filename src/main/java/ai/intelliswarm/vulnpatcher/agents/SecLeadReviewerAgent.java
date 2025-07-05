package ai.intelliswarm.vulnpatcher.agents;

import ai.intelliswarm.vulnpatcher.config.CodeReview;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class SecLeadReviewerAgent {
    
    @Inject
    @CodeReview
    OllamaChatModel reviewModel;
    
    @Inject
    ContextManager contextManager;
    
    public CompletableFuture<String> reviewCode(String code, String vulnerabilityDescription) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(
                    "Review the following security fix for code quality and maintainability:\n" +
                    "Vulnerability: %s\n" +
                    "Proposed Fix:\n%s\n" +
                    "Provide feedback on: code quality, maintainability, best practices, and potential improvements.",
                    vulnerabilityDescription, code
                );
                
                UserMessage userMessage = UserMessage.from(prompt);
                return reviewModel.generate(userMessage).content().text();
            } catch (Exception e) {
                Log.error("Error reviewing code: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}