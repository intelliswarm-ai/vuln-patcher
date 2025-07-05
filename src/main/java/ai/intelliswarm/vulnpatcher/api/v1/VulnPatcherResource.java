package ai.intelliswarm.vulnpatcher.api.v1;

import ai.intelliswarm.vulnpatcher.models.ScanResult;
import ai.intelliswarm.vulnpatcher.orchestrator.LLMOrchestrator;
import ai.intelliswarm.vulnpatcher.services.PullRequestService;
import ai.intelliswarm.vulnpatcher.services.VulnerabilityDetectionService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/v1/vulnpatcher")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VulnPatcherResource {
    
    @Inject
    VulnerabilityDetectionService detectionService;
    
    @Inject
    LLMOrchestrator orchestrator;
    
    @Inject
    PullRequestService pullRequestService;
    
    @POST
    @Path("/scan")
    public Uni<Response> scanRepository(ScanRequest request) {
        return detectionService.scanRepository(request.getRepositoryUrl(), request.getBranch())
                .map(result -> Response.ok(result).build())
                .onFailure().recoverWithItem(error -> 
                    Response.serverError().entity(Map.of("error", error.getMessage())).build()
                );
    }
    
    @GET
    @Path("/scan/{scanId}")
    public Uni<Response> getScanResult(@PathParam("scanId") String scanId) {
        return detectionService.getScanResult(scanId)
                .map(result -> Response.ok(result).build())
                .onFailure().recoverWithItem(error ->
                    Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "Scan not found")).build()
                );
    }
    
    @POST
    @Path("/fix/{scanId}")
    public Uni<Response> fixVulnerabilities(@PathParam("scanId") String scanId, FixRequest request) {
        return detectionService.getScanResult(scanId)
                .flatMap(scanResult -> {
                    if (scanResult.getVulnerabilities().isEmpty()) {
                        return Uni.createFrom().item(
                            Response.ok(Map.of("message", "No vulnerabilities to fix")).build()
                        );
                    }
                    
                    // For demo purposes, fix the first vulnerability
                    ScanResult.VulnerabilityMatch vuln = scanResult.getVulnerabilities().get(0);
                    
                    LLMOrchestrator.WorkflowContext context = new LLMOrchestrator.WorkflowContext();
                    context.setRepositoryUrl(scanResult.getRepositoryUrl());
                    context.setLanguage(request.getLanguage());
                    context.setFramework(request.getFramework());
                    
                    return orchestrator.orchestrateVulnerabilityFix(vuln, context);
                })
                .map(workflowResult -> Response.ok(workflowResult).build())
                .onFailure().recoverWithItem(error ->
                    Response.serverError().entity(Map.of("error", error.getMessage())).build()
                );
    }
    
    @GET
    @Path("/workflow/{workflowId}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<LLMOrchestrator.WorkflowEvent> streamWorkflowEvents(@PathParam("workflowId") String workflowId) {
        return orchestrator.streamWorkflowEvents(workflowId);
    }
    
    public static class ScanRequest {
        @NotBlank
        private String repositoryUrl;
        private String branch = "main";
        
        // Getters and setters
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
        
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
    }
    
    public static class FixRequest {
        private String language;
        private String framework;
        private boolean createPullRequest = true;
        
        // Getters and setters
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }
        
        public boolean isCreatePullRequest() { return createPullRequest; }
        public void setCreatePullRequest(boolean createPullRequest) { this.createPullRequest = createPullRequest; }
    }
}