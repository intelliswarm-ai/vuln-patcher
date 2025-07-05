package ai.intelliswarm.vulnpatcher.sources;

import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class OSSIndexSource implements VulnerabilitySource {
    
    @ConfigProperty(name = "vulnpatcher.sources.ossindex.enabled", defaultValue = "false")
    boolean enabled;
    
    @Override
    public Uni<List<Vulnerability>> fetchVulnerabilities() {
        if (!enabled) {
            return Uni.createFrom().item(List.of());
        }
        
        return Uni.createFrom().item(() -> {
            // Mock OSS Index data for now - in real implementation this would call Sonatype OSS Index API
            Vulnerability vuln = new Vulnerability();
            vuln.setId("OSS-INDEX-2024-0001");
            vuln.setTitle("Test OSS Index Vulnerability");
            vuln.setSeverity("MEDIUM");
            vuln.setDescription("A test OSS Index vulnerability for demonstration");
            vuln.setPublishedDate(LocalDateTime.now());
            vuln.setCvssScore(6.0);
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
        return "OSSIndex";
    }
}