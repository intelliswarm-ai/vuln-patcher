package ai.intelliswarm.vulnpatcher.sources;

import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class GHSASource implements VulnerabilitySource {
    
    @ConfigProperty(name = "vulnpatcher.sources.ghsa.enabled", defaultValue = "true")
    boolean enabled;
    
    @Override
    public Uni<List<Vulnerability>> fetchVulnerabilities() {
        if (!enabled) {
            return Uni.createFrom().item(List.of());
        }
        
        return Uni.createFrom().item(() -> {
            // Mock GHSA data for now - in real implementation this would call GitHub API
            Vulnerability vuln = new Vulnerability();
            vuln.setId("GHSA-xxxx-yyyy-zzzz");
            vuln.setTitle("Test GHSA Vulnerability");
            vuln.setSeverity("CRITICAL");
            vuln.setDescription("A test GHSA vulnerability for demonstration");
            vuln.setPublishedDate(LocalDateTime.now());
            vuln.setCvssScore(9.0);
            vuln.setAffectedLanguages(Arrays.asList("JavaScript", "TypeScript"));
            
            return List.of(vuln);
        });
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public String getSourceName() {
        return "GHSA";
    }
}