package ai.intelliswarm.vulnpatcher.config;

import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
public class LangChainConfig {
    
    @ConfigProperty(name = "ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaBaseUrl;
    
    @ConfigProperty(name = "ollama.model.orchestrator", defaultValue = "llama2")
    String orchestratorModel;
    
    @ConfigProperty(name = "ollama.model.agent", defaultValue = "llama2")
    String agentModel;
    
    @ConfigProperty(name = "ollama.timeout", defaultValue = "60s")
    Duration timeout;
    
    @ConfigProperty(name = "ci.environment", defaultValue = "false")
    boolean ciEnvironment;
    
    @ConfigProperty(name = "skip.ollama.tests", defaultValue = "false")
    boolean skipOllamaTests;
    
    @Produces
    @Named("orchestratorModel")
    @ApplicationScoped
    public OllamaChatModel orchestratorModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(orchestratorModel)
                .timeout(timeout)
                .temperature(0.7)
                .build();
    }
    
    @Produces
    @Named("agentModel")
    @ApplicationScoped
    public OllamaChatModel agentModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(agentModel)
                .timeout(timeout)
                .temperature(0.5)
                .build();
    }
    
    @Produces
    @CodeGeneration
    @ApplicationScoped
    public OllamaChatModel codeGenerationModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(agentModel)
                .timeout(timeout)
                .temperature(0.3)
                .build();
    }
    
    @Produces
    @CodeReview
    @ApplicationScoped
    public OllamaChatModel codeReviewModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(agentModel)
                .timeout(timeout)
                .temperature(0.4)
                .build();
    }
    
    @Produces
    @VulnerabilityAnalysis
    @ApplicationScoped
    public OllamaChatModel vulnerabilityAnalysisModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(agentModel)
                .timeout(timeout)
                .temperature(0.2)
                .build();
    }
}