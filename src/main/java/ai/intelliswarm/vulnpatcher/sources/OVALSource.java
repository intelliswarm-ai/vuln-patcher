package ai.intelliswarm.vulnpatcher.sources;

import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class OVALSource implements VulnerabilitySource {
    
    @ConfigProperty(name = "vulnpatcher.sources.oval.enabled", defaultValue = "false")
    boolean enabled;
    
    @Override
    public Uni<List<Vulnerability>> fetchVulnerabilities() {
        if (!enabled) {
            return Uni.createFrom().item(List.of());
        }
        
        return Uni.createFrom().item(() -> {
            // Mock OVAL data for now - in real implementation this would parse OVAL XML feeds
            Vulnerability vuln = new Vulnerability();
            vuln.setId("OVAL-DEF-2024-0001");
            vuln.setTitle("Test OVAL Vulnerability");
            vuln.setSeverity("LOW");
            vuln.setDescription("A test OVAL vulnerability for demonstration");
            vuln.setPublishedDate(LocalDateTime.now());
            vuln.setCvssScore(3.0);
            vuln.setAffectedLanguages(Arrays.asList("C", "C++"));
            
            return List.of(vuln);
        });
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public String getSourceName() {
        return "OVAL";
    }
}