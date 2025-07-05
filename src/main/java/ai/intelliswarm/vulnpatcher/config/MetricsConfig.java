package ai.intelliswarm.vulnpatcher.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Startup
public class MetricsConfig {
    
    @Inject
    MeterRegistry registry;
    
    @ApplicationScoped
    public static class MetricsService {
        
        @Inject
        MeterRegistry registry;
        
        public void recordScanDuration(long duration) {
            registry.timer("vulnpatcher.scan.duration")
                    .record(duration, TimeUnit.MILLISECONDS);
        }
        
        public void incrementVulnerabilitiesFound(int count) {
            registry.counter("vulnpatcher.vulnerabilities.found").increment(count);
        }
        
        public void incrementVulnerabilitiesDetected() {
            registry.counter("vulnpatcher.vulnerabilities.detected").increment();
        }
        
        public void incrementApiCallErrors(String sourceName) {
            registry.counter("vulnpatcher.api.errors", "source", sourceName).increment();
        }
        
        public void recordApiCallDuration(String sourceName, long durationMs) {
            registry.timer("vulnpatcher.api.duration", "source", sourceName)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }
        
        public void incrementPullRequestsCreated() {
            registry.counter("vulnpatcher.pullrequests.created").increment();
        }
        
        public void recordWorkflowDuration(String workflowType, long duration) {
            registry.timer("vulnpatcher.workflow.duration", "type", workflowType)
                    .record(duration, TimeUnit.MILLISECONDS);
        }
        
        public void incrementWorkflowSuccess(String workflowType) {
            registry.counter("vulnpatcher.workflow.success", "type", workflowType).increment();
        }
        
        public void incrementWorkflowFailure(String workflowType) {
            registry.counter("vulnpatcher.workflow.failure", "type", workflowType).increment();
        }
        
        public Timer.Sample startTimer() {
            return Timer.start(registry);
        }
        
        public void stopTimer(Timer.Sample sample, String metricName, String... tags) {
            sample.stop(registry.timer(metricName, tags));
        }
    }
}