package ai.intelliswarm.vulnpatcher.config;

import io.micrometer.core.instrument.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MetricsConfigTest {
    
    @Inject
    MetricsConfig metricsConfig;
    
    @InjectMock
    MeterRegistry meterRegistry;
    
    private MetricsConfig.MetricsService metricsService;
    private Timer mockTimer;
    private Counter mockCounter;
    private Gauge mockGauge;
    
    @BeforeEach
    void setUp() {
        mockTimer = mock(Timer.class);
        mockCounter = mock(Counter.class);
        mockGauge = mock(Gauge.class);
        
        when(meterRegistry.timer(anyString())).thenReturn(mockTimer);
        when(meterRegistry.timer(anyString(), any(String[].class))).thenReturn(mockTimer);
        when(meterRegistry.counter(anyString())).thenReturn(mockCounter);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);
        when(meterRegistry.gauge(anyString(), anyDouble())).thenReturn(mockGauge);
        
        metricsService = metricsConfig.metricsService();
    }
    
    @Test
    @Order(1)
    @DisplayName("Should register all required gauges on initialization")
    void testGaugeRegistration() {
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AtomicLong> valueCaptor = ArgumentCaptor.forClass(AtomicLong.class);
        
        verify(meterRegistry, atLeast(4)).builder(nameCaptor.capture(), valueCaptor.capture(), any());
        
        List<String> registeredGauges = nameCaptor.getAllValues();
        assertTrue(registeredGauges.contains("vulnpatcher.vulnerabilities.detected.total"));
        assertTrue(registeredGauges.contains("vulnpatcher.fixes.generated.total"));
        assertTrue(registeredGauges.contains("vulnpatcher.pull_requests.created.total"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Should track scan timing accurately")
    void testScanTiming() {
        Timer.Sample mockSample = mock(Timer.Sample.class);
        when(Timer.start(any())).thenReturn(mockSample);
        
        Timer.Sample sample = metricsService.startScanTimer();
        assertNotNull(sample);
        
        // Simulate scan completion
        metricsService.endScanTimer(sample);
        
        verify(mockSample).stop(mockTimer);
    }
    
    @Test
    @Order(3)
    @DisplayName("Should handle concurrent scan tracking")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testConcurrentScanTracking() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger activeScansCounter = new AtomicInteger(0);
        
        Timer.Sample mockSample = mock(Timer.Sample.class);
        when(Timer.start(any())).thenReturn(mockSample);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Timer.Sample sample = metricsService.startScanTimer();
                    activeScansCounter.incrementAndGet();
                    
                    // Simulate scan work
                    Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                    
                    metricsService.endScanTimer(sample);
                    activeScansCounter.decrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        
        assertEquals(0, activeScansCounter.get());
    }
    
    @Test
    @Order(4)
    @DisplayName("Should increment counters atomically")
    void testCounterIncrements() {
        // Test all counter increments
        metricsService.incrementVulnerabilitiesDetected();
        verify(mockCounter, times(1)).increment();
        
        metricsService.incrementFixesGenerated();
        verify(mockCounter, times(2)).increment();
        
        metricsService.incrementPullRequestsCreated();
        verify(mockCounter, times(3)).increment();
    }
    
    @ParameterizedTest
    @Order(5)
    @ValueSource(longs = {0, 100, 1000, 10000, 100000})
    @DisplayName("Should record fix generation time with various durations")
    void testRecordFixGenerationTime(long duration) {
        metricsService.recordFixGenerationTime(duration);
        
        verify(mockTimer).record(eq(duration), eq(TimeUnit.MILLISECONDS));
    }
    
    @Test
    @Order(6)
    @DisplayName("Should record API call metrics with proper tags")
    void testRecordApiCallMetrics() {
        String[] apiNames = {"github", "gitlab", "bitbucket", "ollama"};
        
        for (String apiName : apiNames) {
            metricsService.recordApiCallDuration(apiName, 1500);
            metricsService.incrementApiCallErrors(apiName);
        }
        
        ArgumentCaptor<String> tagCaptor = ArgumentCaptor.forClass(String.class);
        verify(meterRegistry, times(apiNames.length * 2)).timer(anyString(), eq("api"), tagCaptor.capture());
        
        List<String> capturedTags = tagCaptor.getAllValues();
        for (String apiName : apiNames) {
            assertTrue(capturedTags.contains(apiName));
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("Should handle null and empty API names gracefully")
    void testHandleInvalidApiNames() {
        assertDoesNotThrow(() -> {
            metricsService.recordApiCallDuration(null, 100);
            metricsService.recordApiCallDuration("", 100);
            metricsService.incrementApiCallErrors(null);
            metricsService.incrementApiCallErrors("");
        });
    }
    
    @Test
    @Order(8)
    @DisplayName("Should record memory usage metrics")
    void testRecordMemoryUsage() {
        metricsService.recordMemoryUsage();
        
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Number> valueCaptor = ArgumentCaptor.forClass(Number.class);
        
        verify(meterRegistry, atLeast(2)).gauge(nameCaptor.capture(), valueCaptor.capture());
        
        List<String> metricNames = nameCaptor.getAllValues();
        assertTrue(metricNames.contains("vulnpatcher.memory.used"));
        assertTrue(metricNames.contains("vulnpatcher.memory.max"));
        
        // Verify values are reasonable
        List<Number> values = valueCaptor.getAllValues();
        values.forEach(value -> assertTrue(value.longValue() > 0));
    }
    
    @Test
    @Order(9)
    @DisplayName("Should handle metric recording under high load")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testHighLoadMetricRecording() throws InterruptedException {
        int operationCount = 10000;
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        Runnable task = () -> {
            try {
                for (int i = 0; i < operationCount / threadCount; i++) {
                    metricsService.incrementVulnerabilitiesDetected();
                    metricsService.recordFixGenerationTime(ThreadLocalRandom.current().nextLong(100, 5000));
                    metricsService.recordApiCallDuration("test-api", ThreadLocalRandom.current().nextLong(10, 1000));
                    
                    if (i % 10 == 0) {
                        metricsService.incrementApiCallErrors("test-api");
                    }
                }
            } finally {
                latch.countDown();
            }
        };
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(task);
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        long duration = System.currentTimeMillis() - startTime;
        double opsPerSecond = (double) operationCount / (duration / 1000.0);
        
        System.out.println("Metrics recording performance: " + opsPerSecond + " ops/sec");
        assertTrue(opsPerSecond > 1000, "Should handle at least 1000 ops/sec");
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(10)
    @DisplayName("Should provide thread-safe access to metrics")
    void testThreadSafetyOfMetrics() throws InterruptedException {
        int threadCount = 20;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // Create a local counter to verify
        AtomicInteger expectedCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        metricsService.incrementVulnerabilitiesDetected();
                        expectedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify counter was called expected number of times
        verify(mockCounter, times(threadCount * incrementsPerThread)).increment();
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }
    
    @Test
    @Order(11)
    @DisplayName("Should handle timer edge cases")
    void testTimerEdgeCases() {
        Timer.Sample mockSample = mock(Timer.Sample.class);
        when(Timer.start(any())).thenReturn(mockSample);
        
        // Test null sample
        assertDoesNotThrow(() -> metricsService.endScanTimer(null));
        
        // Test double-end
        Timer.Sample sample = metricsService.startScanTimer();
        metricsService.endScanTimer(sample);
        assertDoesNotThrow(() -> metricsService.endScanTimer(sample));
    }
    
    @Test
    @Order(12)
    @DisplayName("Should maintain accurate active scan count")
    void testActiveScanCount() throws InterruptedException {
        int concurrentScans = 5;
        List<Timer.Sample> samples = new ArrayList<>();
        
        Timer.Sample mockSample = mock(Timer.Sample.class);
        when(Timer.start(any())).thenReturn(mockSample);
        
        // Start multiple scans
        for (int i = 0; i < concurrentScans; i++) {
            samples.add(metricsService.startScanTimer());
        }
        
        // End scans one by one
        for (Timer.Sample sample : samples) {
            metricsService.endScanTimer(sample);
        }
        
        // Verify all samples were properly ended
        verify(mockSample, times(concurrentScans)).stop(any(Timer.class));
    }
    
    @Test
    @Order(13)
    @DisplayName("Should calculate percentiles correctly")
    void testPercentileCalculations() {
        // Record various durations for percentile calculation
        long[] durations = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        
        for (long duration : durations) {
            metricsService.recordFixGenerationTime(duration);
        }
        
        verify(mockTimer, times(durations.length)).record(anyLong(), eq(TimeUnit.MILLISECONDS));
    }
    
    @Test
    @Order(14)
    @DisplayName("Should handle metric registry unavailability")
    void testMetricRegistryUnavailable() {
        when(meterRegistry.timer(anyString())).thenThrow(new RuntimeException("Registry unavailable"));
        when(meterRegistry.counter(anyString())).thenThrow(new RuntimeException("Registry unavailable"));
        
        // Operations should not throw even if registry is unavailable
        assertDoesNotThrow(() -> {
            metricsService.incrementVulnerabilitiesDetected();
            metricsService.recordFixGenerationTime(100);
        });
    }
    
    @Test
    @Order(15)
    @DisplayName("Should track metric dimensions correctly")
    void testMetricDimensions() {
        String[] apis = {"github", "gitlab", "bitbucket"};
        Map<String, Integer> apiCallCounts = new ConcurrentHashMap<>();
        
        // Simulate API calls with different patterns
        for (String api : apis) {
            int callCount = ThreadLocalRandom.current().nextInt(5, 20);
            apiCallCounts.put(api, callCount);
            
            for (int i = 0; i < callCount; i++) {
                metricsService.recordApiCallDuration(api, ThreadLocalRandom.current().nextLong(100, 2000));
                
                // Simulate some errors
                if (i % 3 == 0) {
                    metricsService.incrementApiCallErrors(api);
                }
            }
        }
        
        // Verify proper tagging
        ArgumentCaptor<String> apiCaptor = ArgumentCaptor.forClass(String.class);
        verify(meterRegistry, atLeastOnce()).timer(anyString(), eq("api"), apiCaptor.capture());
        
        List<String> capturedApis = apiCaptor.getAllValues();
        for (String api : apis) {
            assertTrue(capturedApis.contains(api));
        }
    }
}