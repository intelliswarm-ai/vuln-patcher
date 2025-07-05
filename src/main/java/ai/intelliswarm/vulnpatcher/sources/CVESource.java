package ai.intelliswarm.vulnpatcher.sources;

import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class CVESource implements VulnerabilitySource {
    
    @ConfigProperty(name = "vulnpatcher.sources.cve.enabled", defaultValue = "true")
    boolean enabled;
    
    @Override
    public Uni<List<Vulnerability>> fetchVulnerabilities() {
        if (!enabled) {
            return Uni.createFrom().item(List.of());
        }
        
        return Uni.createFrom().item(() -> {
            // Mock CVE data for now - in real implementation this would call NVD API
            Vulnerability vuln = new Vulnerability();
            vuln.setId("CVE-2024-0001");
            vuln.setTitle("Test CVE Vulnerability");
            vuln.setSeverity("HIGH");
            vuln.setDescription("A test CVE vulnerability for demonstration");
            vuln.setPublishedDate(LocalDateTime.now());
            vuln.setCvssScore(7.5);
            vuln.setAffectedLanguages(Arrays.asList("Java", "Python"));
            
            return List.of(vuln);
        });
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public String getSourceName() {
        return "CVE";
    }
}