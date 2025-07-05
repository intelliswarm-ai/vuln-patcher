package ai.intelliswarm.vulnpatcher.sources;

import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class SnykSource implements VulnerabilitySource {
    
    @ConfigProperty(name = "vulnpatcher.sources.snyk.enabled", defaultValue = "false")
    boolean enabled;
    
    @Override
    public Uni<List<Vulnerability>> fetchVulnerabilities() {
        if (!enabled) {
            return Uni.createFrom().item(List.of());
        }
        
        return Uni.createFrom().item(() -> {
            // Mock Snyk data for now - in real implementation this would call Snyk API
            Vulnerability vuln = new Vulnerability();
            vuln.setId("SNYK-JS-LODASH-1234567");
            vuln.setTitle("Test Snyk Vulnerability");
            vuln.setSeverity("HIGH");
            vuln.setDescription("A test Snyk vulnerability for demonstration");
            vuln.setPublishedDate(LocalDateTime.now());
            vuln.setCvssScore(8.0);
            vuln.setAffectedLanguages(Arrays.asList("JavaScript"));
            
            return List.of(vuln);
        });
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public String getSourceName() {
        return "Snyk";
    }
}