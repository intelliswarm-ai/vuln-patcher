package ai.intelliswarm.vulnpatcher.agents;

import ai.intelliswarm.vulnpatcher.config.VulnerabilityAnalysis;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class CoordinatorAgent implements Agent {
    
    private static final Logger LOGGER = Logger.getLogger(CoordinatorAgent.class.getName());
    
    @Inject
    @VulnerabilityAnalysis
    OllamaChatModel analysisModel;
    
    @Inject
    List<Agent> availableAgents;
    
    private final Map<String, Agent> agentRegistry = new ConcurrentHashMap<>();
    
    @Override
    public String getName() {
        return "Coordinator";
    }
    
    @Override
    public String getRole() {
        return "Orchestrates vulnerability scanning and patching workflow across multiple specialized agents";
    }
    
    @Override
    public CompletableFuture<AgentResult> execute(AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Coordinator Agent starting workflow for task: " + context.getTaskId());
                
                String taskType = context.getTaskType();
                AgentResult result = new AgentResult();
                
                switch (taskType) {
                    case "FULL_SCAN":
                        return orchestrateFullScan(context);
                    case "PATCH_GENERATION":
                        return orchestratePatchGeneration(context);
                    case "PR_CREATION":
                        return orchestratePullRequestCreation(context);
                    default:
                        result.setSuccess(false);
                        result.setMessage("Unknown task type: " + taskType);
                        return result;
                }
            } catch (Exception e) {
                LOGGER.severe("Error in Coordinator Agent: " + e.getMessage());
                AgentResult errorResult = new AgentResult();
                errorResult.setSuccess(false);
                errorResult.setMessage("Coordinator error: " + e.getMessage());
                return errorResult;
            }
        });
    }
    
    @Override
    public boolean canHandle(String taskType) {
        return Arrays.asList("FULL_SCAN", "PATCH_GENERATION", "PR_CREATION").contains(taskType);
    }
    
    private AgentResult orchestrateFullScan(AgentContext context) {
        AgentResult result = new AgentResult();
        Map<String, Object> workflowState = new HashMap<>();
        
        try {
            // Step 1: Repository Analysis
            Agent repoAnalyzer = findAgent("RepositoryAnalyzer");
            if (repoAnalyzer != null) {
                AgentResult analysisResult = repoAnalyzer.execute(context).join();
                if (!analysisResult.isSuccess()) {
                    result.setSuccess(false);
                    result.setMessage("Repository analysis failed: " + analysisResult.getMessage());
                    return result;
                }
                workflowState.putAll(analysisResult.getOutput());
            }
            
            // Step 2: Vulnerability Detection
            Agent detector = findAgent("VulnerabilityDetector");
            if (detector != null) {
                context.getSharedMemory().putAll(workflowState);
                AgentResult detectionResult = detector.execute(context).join();
                if (!detectionResult.isSuccess()) {
                    result.setSuccess(false);
                    result.setMessage("Vulnerability detection failed: " + detectionResult.getMessage());
                    return result;
                }
                workflowState.putAll(detectionResult.getOutput());
            }
            
            // Step 3: Generate patches for detected vulnerabilities
            List<ScanResult.VulnerabilityMatch> vulnerabilities = 
                (List<ScanResult.VulnerabilityMatch>) workflowState.get("vulnerabilities");
            
            if (vulnerabilities != null && !vulnerabilities.isEmpty()) {
                Agent patchGenerator = findAgent("PatchGenerator");
                if (patchGenerator != null) {
                    context.getSharedMemory().put("vulnerabilities", vulnerabilities);
                    AgentResult patchResult = patchGenerator.execute(context).join();
                    workflowState.putAll(patchResult.getOutput());
                }
            }
            
            result.setSuccess(true);
            result.setMessage("Full scan completed successfully");
            result.setOutput(workflowState);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Orchestration error: " + e.getMessage());
        }
        
        return result;
    }
    
    private AgentResult orchestratePatchGeneration(AgentContext context) {
        AgentResult result = new AgentResult();
        
        try {
            // Determine the programming language
            String language = (String) context.getParameters().get("language");
            String agentName = getLanguageSpecificAgent(language);
            
            Agent languageAgent = findAgent(agentName);
            if (languageAgent == null) {
                result.setSuccess(false);
                result.setMessage("No agent available for language: " + language);
                return result;
            }
            
            // Generate patch
            AgentResult patchResult = languageAgent.execute(context).join();
            
            // Review the patch
            if (patchResult.isSuccess()) {
                Agent reviewer = findAgent("CodeReviewer");
                if (reviewer != null) {
                    context.getSharedMemory().put("proposedPatch", patchResult.getOutput());
                    AgentResult reviewResult = reviewer.execute(context).join();
                    
                    if (reviewResult.isSuccess()) {
                        result.setSuccess(true);
                        result.setMessage("Patch generated and reviewed successfully");
                        result.setOutput(patchResult.getOutput());
                    } else {
                        result.setSuccess(false);
                        result.setMessage("Patch review failed: " + reviewResult.getMessage());
                    }
                }
            } else {
                result = patchResult;
            }
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Patch generation error: " + e.getMessage());
        }
        
        return result;
    }
    
    private AgentResult orchestratePullRequestCreation(AgentContext context) {
        AgentResult result = new AgentResult();
        
        try {
            Agent prAgent = findAgent("PullRequestAgent");
            if (prAgent == null) {
                result.setSuccess(false);
                result.setMessage("Pull Request Agent not available");
                return result;
            }
            
            result = prAgent.execute(context).join();
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("PR creation error: " + e.getMessage());
        }
        
        return result;
    }
    
    private Agent findAgent(String agentName) {
        return availableAgents.stream()
            .filter(agent -> agent.getName().equals(agentName))
            .findFirst()
            .orElse(null);
    }
    
    private String getLanguageSpecificAgent(String language) {
        Map<String, String> languageAgentMap = Map.of(
            "java", "JavaPatchAgent",
            "python", "PythonPatchAgent",
            "javascript", "JavaScriptPatchAgent",
            "typescript", "JavaScriptPatchAgent",
            "cpp", "CppPatchAgent",
            "kotlin", "KotlinPatchAgent",
            "sql", "SqlPatchAgent"
        );
        
        return languageAgentMap.getOrDefault(language.toLowerCase(), "GenericPatchAgent");
    }
}