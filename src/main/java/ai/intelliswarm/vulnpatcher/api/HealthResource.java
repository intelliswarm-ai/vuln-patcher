package ai.intelliswarm.vulnpatcher.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    
    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok(Map.of(
            "status", "running",
            "application", "VulnPatcher",
            "version", "1.0.0",
            "timestamp", System.currentTimeMillis()
        )).build();
    }
    
    @GET
    @Path("/test")
    public Response test() {
        return Response.ok(Map.of(
            "message", "VulnPatcher is working!",
            "ready", true
        )).build();
    }
}