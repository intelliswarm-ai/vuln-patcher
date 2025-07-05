package ai.intelliswarm.vulnpatcher.agents;

import ai.intelliswarm.vulnpatcher.config.CodeGeneration;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.data.message.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class SecurityEngineerAgent {
    
    @Inject
    @CodeGeneration
    OllamaChatModel codeGenerationModel;
    
    @Inject
    ContextManager contextManager;
    
    public CompletableFuture<String> generateSecureFix(String affectedCode, String vulnerabilityDescription, String language) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = String.format(
                    "Generate a secure fix for the following vulnerability:\n" +
                    "Language: %s\n" +
                    "Vulnerability: %s\n" +
                    "Affected Code:\n%s\n" +
                    "Provide only the fixed code.",
                    language, vulnerabilityDescription, affectedCode
                );
                
                UserMessage userMessage = UserMessage.from(prompt);
                return codeGenerationModel.generate(userMessage).content().text();
            } catch (Exception e) {
                Log.error("Error generating secure fix: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}