package ai.intelliswarm.vulnpatcher.sources;

import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class OSVSource implements VulnerabilitySource {
    
    @ConfigProperty(name = "vulnpatcher.sources.osv.enabled", defaultValue = "true")
    boolean enabled;
    
    @Override
    public Uni<List<Vulnerability>> fetchVulnerabilities() {
        if (!enabled) {
            return Uni.createFrom().item(List.of());
        }
        
        return Uni.createFrom().item(() -> {
            // Mock OSV data for now - in real implementation this would call OSV API
            Vulnerability vuln = new Vulnerability();
            vuln.setId("OSV-2024-0001");
            vuln.setTitle("Test OSV Vulnerability");
            vuln.setSeverity("MEDIUM");
            vuln.setDescription("A test OSV vulnerability for demonstration in Maven ecosystem");
            vuln.setPublishedDate(LocalDateTime.now());
            vuln.setCvssScore(5.5);
            vuln.setAffectedLanguages(Arrays.asList("Java"));
            
            return List.of(vuln);
        });
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public String getSourceName() {
        return "OSV";
    }
}