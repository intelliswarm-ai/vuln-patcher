package ai.intelliswarm.vulnpatcher.agents;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Agent {
    /**
     * Get the agent's name
     */
    String getName();
    
    /**
     * Get the agent's role description
     */
    String getRole();
    
    /**
     * Execute the agent's task
     * @param context Task context containing necessary information
     * @return Result of the agent's execution
     */
    CompletableFuture<AgentResult> execute(AgentContext context);
    
    /**
     * Check if the agent can handle a specific task
     * @param taskType Type of task
     * @return true if the agent can handle this task
     */
    boolean canHandle(String taskType);
    
    class AgentContext {
        private String taskId;
        private String taskType;
        private Map<String, Object> parameters;
        private Map<String, Object> sharedMemory;
        
        public String getTaskId() {
            return taskId;
        }
        
        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }
        
        public String getTaskType() {
            return taskType;
        }
        
        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }
        
        public Map<String, Object> getParameters() {
            return parameters;
        }
        
        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
        
        public Map<String, Object> getSharedMemory() {
            return sharedMemory;
        }
        
        public void setSharedMemory(Map<String, Object> sharedMemory) {
            this.sharedMemory = sharedMemory;
        }
    }
    
    class AgentResult {
        private boolean success;
        private String message;
        private Map<String, Object> output;
        private String nextAgent;
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Map<String, Object> getOutput() {
            return output;
        }
        
        public void setOutput(Map<String, Object> output) {
            this.output = output;
        }
        
        public String getNextAgent() {
            return nextAgent;
        }
        
        public void setNextAgent(String nextAgent) {
            this.nextAgent = nextAgent;
        }
    }
}