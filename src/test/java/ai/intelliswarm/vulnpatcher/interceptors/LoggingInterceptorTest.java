package ai.intelliswarm.vulnpatcher.interceptors;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoggingInterceptorTest {
    
    @Inject
    LoggingInterceptor loggingInterceptor;
    
    @Inject
    TestServiceWithLogging testService;
    
    private TestLogHandler testLogHandler;
    private Logger logger;
    
    @BeforeEach
    void setUp() {
        // Set up test log handler to capture log messages
        testLogHandler = new TestLogHandler();
        logger = Logger.getLogger(LoggingInterceptor.class.getName());
        logger.addHandler(testLogHandler);
        logger.setUseParentHandlers(false);
    }
    
    @AfterEach
    void tearDown() {
        logger.removeHandler(testLogHandler);
        logger.setUseParentHandlers(true);
    }
    
    @Test
    @Order(1)
    @DisplayName("Should log method invocation with basic logging")
    void testBasicMethodLogging() throws Exception {
        InvocationContext context = createMockContext("testMethod", new Object[]{});
        
        Object result = loggingInterceptor.logMethodInvocation(context);
        
        verify(context).proceed();
        
        // Check log entries
        assertTrue(testLogHandler.hasLogEntry("Entering TestService.testMethod"));
        assertTrue(testLogHandler.hasLogEntry("Completed TestService.testMethod"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Should log method parameters when detailed logging is enabled")
    void testDetailedParameterLogging() throws Exception {
        // Enable detailed logging
        loggingInterceptor.detailedLogging = true;
        
        Object[] params = {"param1", 42, true};
        InvocationContext context = createMockContext("testMethod", params);
        
        loggingInterceptor.logMethodInvocation(context);
        
        assertTrue(testLogHandler.hasLogEntry("with parameters"));
        assertTrue(testLogHandler.hasLogEntry("param1"));
        assertTrue(testLogHandler.hasLogEntry("42"));
        assertTrue(testLogHandler.hasLogEntry("true"));
    }
    
    @Test
    @Order(3)
    @DisplayName("Should truncate long parameters")
    void testLongParameterTruncation() throws Exception {
        loggingInterceptor.detailedLogging = true;
        loggingInterceptor.maxParamLength = 10;
        
        String longParam = "This is a very long parameter that should be truncated";
        InvocationContext context = createMockContext("testMethod", new Object[]{longParam});
        
        loggingInterceptor.logMethodInvocation(context);
        
        assertTrue(testLogHandler.hasLogEntry("This is a ..."));
        assertFalse(testLogHandler.hasLogEntry(longParam)); // Full string should not appear
    }
    
    @Test
    @Order(4)
    @DisplayName("Should handle null parameters gracefully")
    void testNullParameterHandling() throws Exception {
        loggingInterceptor.detailedLogging = true;
        
        Object[] params = {"value1", null, "value3"};
        InvocationContext context = createMockContext("testMethod", params);
        
        loggingInterceptor.logMethodInvocation(context);
        
        assertTrue(testLogHandler.hasLogEntry("null"));
    }
    
    @Test
    @Order(5)
    @DisplayName("Should log execution time")
    void testExecutionTimeLogging() throws Exception {
        InvocationContext context = createMockContext("slowMethod", new Object[]{});
        when(context.proceed()).thenAnswer(invocation -> {
            Thread.sleep(100); // Simulate slow method
            return "result";
        });
        
        loggingInterceptor.logMethodInvocation(context);
        
        // Should log completion with duration
        LogRecord durationLog = testLogHandler.getLogRecords().stream()
            .filter(record -> record.getMessage().contains("Completed") && 
                            record.getMessage().contains("ms"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(durationLog);
        assertTrue(durationLog.getMessage().matches(".*\\d+ms.*"));
    }
    
    @Test
    @Order(6)
    @DisplayName("Should log exceptions with details")
    void testExceptionLogging() throws Exception {
        RuntimeException testException = new RuntimeException("Test error");
        InvocationContext context = createMockContext("failingMethod", new Object[]{});
        when(context.proceed()).thenThrow(testException);
        
        assertThrows(RuntimeException.class, () -> 
            loggingInterceptor.logMethodInvocation(context)
        );
        
        // Should log the exception
        LogRecord errorLog = testLogHandler.getLogRecords().stream()
            .filter(record -> record.getMessage().contains("Failed"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(errorLog);
        assertTrue(errorLog.getMessage().contains("Test error"));
        assertEquals(testException, errorLog.getThrown());
    }
    
    @ParameterizedTest
    @Order(7)
    @MethodSource("provideVariousParameterTypes")
    @DisplayName("Should format various parameter types correctly")
    void testParameterFormatting(Object parameter, String expectedFormat) throws Exception {
        loggingInterceptor.detailedLogging = true;
        loggingInterceptor.maxParamLength = 100;
        
        InvocationContext context = createMockContext("testMethod", new Object[]{parameter});
        
        loggingInterceptor.logMethodInvocation(context);
        
        assertTrue(testLogHandler.hasLogEntry(expectedFormat));
    }
    
    @Test
    @Order(8)
    @DisplayName("Should work with actual annotated service")
    void testWithAnnotatedService() {
        // This tests the actual interceptor binding
        String result = testService.performLoggableOperation("test", 123);
        
        assertEquals("Result: test-123", result);
        
        // Verify logging occurred
        assertTrue(testLogHandler.hasLogEntry("performLoggableOperation"));
    }
    
    @Test
    @Order(9)
    @DisplayName("Should handle methods with no parameters")
    void testNoParameterMethod() throws Exception {
        InvocationContext context = createMockContext("noParamMethod", new Object[0]);
        
        loggingInterceptor.logMethodInvocation(context);
        
        assertTrue(testLogHandler.hasLogEntry("Entering TestService.noParamMethod"));
        // Should not log "with parameters" when there are none
        assertFalse(testLogHandler.hasLogEntry("with parameters"));
    }
    
    @Test
    @Order(10)
    @DisplayName("Should maintain method return value")
    void testReturnValuePreservation() throws Exception {
        Object expectedResult = "Expected Result";
        InvocationContext context = createMockContext("testMethod", new Object[]{});
        when(context.proceed()).thenReturn(expectedResult);
        
        Object actualResult = loggingInterceptor.logMethodInvocation(context);
        
        assertEquals(expectedResult, actualResult);
    }
    
    @Test
    @Order(11)
    @DisplayName("Should handle concurrent method invocations")
    void testConcurrentLogging() throws Exception {
        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    InvocationContext context = createMockContext(
                        "concurrentMethod" + threadId, 
                        new Object[]{"thread" + threadId}
                    );
                    loggingInterceptor.logMethodInvocation(context);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Thread " + threadId + " failed: " + e.getMessage());
                }
            });
        }
        
        // Start all threads
        Arrays.stream(threads).forEach(Thread::start);
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join(5000);
        }
        
        assertEquals(threadCount, successCount.get());
        
        // Verify all methods were logged
        for (int i = 0; i < threadCount; i++) {
            assertTrue(testLogHandler.hasLogEntry("concurrentMethod" + i));
        }
    }
    
    @ParameterizedTest
    @Order(12)
    @ValueSource(booleans = {true, false})
    @DisplayName("Should respect detailed logging configuration")
    void testDetailedLoggingConfiguration(boolean detailedLogging) throws Exception {
        loggingInterceptor.detailedLogging = detailedLogging;
        
        Object[] params = {"param1", "param2"};
        InvocationContext context = createMockContext("testMethod", params);
        
        loggingInterceptor.logMethodInvocation(context);
        
        if (detailedLogging) {
            assertTrue(testLogHandler.hasLogEntry("with parameters"));
        } else {
            assertFalse(testLogHandler.hasLogEntry("with parameters"));
        }
    }
    
    // Helper methods
    
    private InvocationContext createMockContext(String methodName, Object[] parameters) throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        
        Object target = new TestService();
        Method method = TestService.class.getMethod(methodName, getParameterTypes(parameters));
        
        when(context.getTarget()).thenReturn(target);
        when(context.getMethod()).thenReturn(method);
        when(context.getParameters()).thenReturn(parameters);
        when(context.proceed()).thenReturn("result");
        
        return context;
    }
    
    private Class<?>[] getParameterTypes(Object[] parameters) {
        return Arrays.stream(parameters)
            .map(param -> param == null ? Object.class : param.getClass())
            .toArray(Class<?>[]::new);
    }
    
    private static Stream<Arguments> provideVariousParameterTypes() {
        return Stream.of(
            Arguments.of("SimpleString", "SimpleString"),
            Arguments.of(123, "123"),
            Arguments.of(45.67, "45.67"),
            Arguments.of(true, "true"),
            Arguments.of(Arrays.asList("a", "b", "c"), "[a, b, c]"),
            Arguments.of(new CustomObject("test"), "CustomObject[test]")
        );
    }
    
    // Test classes
    
    static class TestService {
        public String testMethod() { return "result"; }
        public String testMethod(Object... params) { return "result"; }
        public String slowMethod() { return "result"; }
        public String failingMethod() { throw new RuntimeException("Test error"); }
        public String noParamMethod() { return "result"; }
        public String concurrentMethod0(Object param) { return "result"; }
        public String concurrentMethod1(Object param) { return "result"; }
        public String concurrentMethod2(Object param) { return "result"; }
        public String concurrentMethod3(Object param) { return "result"; }
        public String concurrentMethod4(Object param) { return "result"; }
        public String concurrentMethod5(Object param) { return "result"; }
        public String concurrentMethod6(Object param) { return "result"; }
        public String concurrentMethod7(Object param) { return "result"; }
        public String concurrentMethod8(Object param) { return "result"; }
        public String concurrentMethod9(Object param) { return "result"; }
    }
    
    @Loggable
    static class TestServiceWithLogging {
        public String performLoggableOperation(String input, int number) {
            return "Result: " + input + "-" + number;
        }
    }
    
    static class CustomObject {
        private final String value;
        
        CustomObject(String value) {
            this.value = value;
        }
        
        @Override
        public String toString() {
            return "CustomObject[" + value + "]";
        }
    }
    
    static class TestLogHandler extends Handler {
        private final java.util.List<LogRecord> records = new java.util.ArrayList<>();
        
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }
        
        @Override
        public void flush() {}
        
        @Override
        public void close() throws SecurityException {}
        
        public boolean hasLogEntry(String message) {
            return records.stream()
                .anyMatch(record -> record.getMessage().contains(message));
        }
        
        public java.util.List<LogRecord> getLogRecords() {
            return new java.util.ArrayList<>(records);
        }
    }
}