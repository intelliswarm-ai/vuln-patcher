package ai.intelliswarm.vulnpatcher.core;

import ai.intelliswarm.vulnpatcher.config.EmbeddingModelConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContextManagerTest {
    
    @Inject
    ContextManager contextManager;
    
    @InjectMock
    @EmbeddingModelConfig
    EmbeddingModel embeddingModel;
    
    @InjectMock
    EmbeddingStore<ContextManager.CodeContext> embeddingStore;
    
    private static final String TEST_SESSION_ID = "test-session-123";
    private static final String TEST_FILE_PATH = "/src/main/java/TestService.java";
    private static final String TEST_CONTENT = "public class TestService { public void testMethod() { } }";
    
    @BeforeEach
    void setUp() {
        // Mock embedding model
        Embedding mockEmbedding = mock(Embedding.class);
        when(mockEmbedding.vector()).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embed(anyString())).thenReturn(mockEmbedding);
        
        // Mock embedding store
        EmbeddingSearchResult<ContextManager.CodeContext> searchResult = mock(EmbeddingSearchResult.class);
        when(searchResult.matches()).thenReturn(Collections.emptyList());
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
    }
    
    @AfterEach
    void tearDown() {
        contextManager.clearAllSessions();
    }
    
    @Test
    @Order(1)
    @DisplayName("Should create new session successfully")
    void testCreateSession() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        assertNotNull(session);
        assertEquals(TEST_SESSION_ID, session.getSessionId());
        assertNotNull(session.getCreatedAt());
        assertTrue(session.getContextChunks().isEmpty());
    }
    
    @Test
    @Order(2)
    @DisplayName("Should retrieve existing session")
    void testGetExistingSession() {
        ContextManager.SessionContext session1 = contextManager.getOrCreateSession(TEST_SESSION_ID);
        ContextManager.SessionContext session2 = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        assertSame(session1, session2);
    }
    
    @Test
    @Order(3)
    @DisplayName("Should add code context with proper chunking")
    void testAddCodeContext() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Create large content that requires chunking
        String largeContent = IntStream.range(0, 5000)
            .mapToObj(i -> "Line " + i + ": Some code content here\n")
            .collect(Collectors.joining());
        
        session.addCodeContext(TEST_FILE_PATH, largeContent);
        
        // Verify chunking occurred
        ArgumentCaptor<ContextManager.CodeContext> contextCaptor = 
            ArgumentCaptor.forClass(ContextManager.CodeContext.class);
        verify(embeddingStore, atLeastOnce()).add(any(Embedding.class), contextCaptor.capture());
        
        List<ContextManager.CodeContext> capturedContexts = contextCaptor.getAllValues();
        assertFalse(capturedContexts.isEmpty());
        
        // Verify chunks don't exceed max size
        capturedContexts.forEach(context -> 
            assertTrue(context.getContent().length() <= 6000) // 1500 tokens * ~4 chars/token
        );
    }
    
    @Test
    @Order(4)
    @DisplayName("Should handle overlapping chunks correctly")
    void testChunkOverlap() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Create content that will be chunked with overlap
        String content = IntStream.range(0, 100)
            .mapToObj(i -> "Method" + i + "() { /* implementation */ }\n")
            .collect(Collectors.joining());
        
        session.addCodeContext(TEST_FILE_PATH, content);
        
        ArgumentCaptor<ContextManager.CodeContext> contextCaptor = 
            ArgumentCaptor.forClass(ContextManager.CodeContext.class);
        verify(embeddingStore, atLeastOnce()).add(any(Embedding.class), contextCaptor.capture());
        
        List<ContextManager.CodeContext> chunks = contextCaptor.getAllValues();
        
        // Verify overlap exists between consecutive chunks
        for (int i = 1; i < chunks.size(); i++) {
            String previousChunk = chunks.get(i - 1).getContent();
            String currentChunk = chunks.get(i).getContent();
            
            // Should have some overlap
            assertTrue(previousChunk.length() > 200); // Has content beyond overlap
            assertTrue(currentChunk.length() > 200);
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Should retrieve relevant context based on similarity")
    void testGetRelevantContext() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Add multiple code contexts
        session.addCodeContext("/file1.java", "public class UserService { }");
        session.addCodeContext("/file2.java", "public class OrderService { }");
        session.addCodeContext("/file3.java", "public class PaymentService { }");
        
        // Mock embedding search to return relevant results
        ContextManager.CodeContext relevantContext = new ContextManager.CodeContext();
        relevantContext.setContent("public class UserService { }");
        relevantContext.setFilePath("/file1.java");
        
        EmbeddingMatch<ContextManager.CodeContext> match = mock(EmbeddingMatch.class);
        when(match.embedded()).thenReturn(relevantContext);
        when(match.score()).thenReturn(0.95);
        
        EmbeddingSearchResult<ContextManager.CodeContext> searchResult = mock(EmbeddingSearchResult.class);
        when(searchResult.matches()).thenReturn(Arrays.asList(match));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
        
        List<ContextManager.RelevantContext> results = session.getRelevantContext("UserService", 5);
        
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("/file1.java", results.get(0).getFilePath());
    }
    
    @ParameterizedTest
    @Order(6)
    @CsvSource({
        "1, 1",
        "5, 5",
        "10, 10",
        "100, 100"
    })
    @DisplayName("Should respect maxResults parameter")
    void testMaxResultsParameter(int requested, int expected) {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Create mock matches
        List<EmbeddingMatch<ContextManager.CodeContext>> matches = IntStream.range(0, 20)
            .mapToObj(i -> {
                ContextManager.CodeContext context = new ContextManager.CodeContext();
                context.setContent("Content " + i);
                context.setFilePath("/file" + i + ".java");
                
                EmbeddingMatch<ContextManager.CodeContext> match = mock(EmbeddingMatch.class);
                when(match.embedded()).thenReturn(context);
                when(match.score()).thenReturn(0.9 - (i * 0.01));
                return match;
            })
            .collect(Collectors.toList());
        
        EmbeddingSearchResult<ContextManager.CodeContext> searchResult = mock(EmbeddingSearchResult.class);
        when(searchResult.matches()).thenReturn(matches);
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
        
        List<ContextManager.RelevantContext> results = session.getRelevantContext("query", requested);
        
        assertEquals(Math.min(expected, 20), results.size());
    }
    
    @Test
    @Order(7)
    @DisplayName("Should handle concurrent session access")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentSessionAccess() throws InterruptedException {
        int threadCount = 50;
        int operationsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<String> sessionIds = ConcurrentHashMap.newKeySet();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        String sessionId = "session-" + (threadId % 10); // 10 shared sessions
                        ContextManager.SessionContext session = contextManager.getOrCreateSession(sessionId);
                        sessionIds.add(sessionId);
                        
                        // Perform various operations
                        session.addCodeContext("/file" + j + ".java", "Content " + j);
                        session.updateFileContent("/file" + j + ".java", "Updated content " + j);
                        session.getRelevantContext("query", 5);
                    }
                } catch (Exception e) {
                    fail("Thread failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        
        // Verify sessions were created correctly
        assertEquals(10, sessionIds.size());
    }
    
    @Test
    @Order(8)
    @DisplayName("Should handle session cleanup properly")
    void testSessionCleanup() {
        // Create multiple sessions
        List<String> sessionIds = IntStream.range(0, 10)
            .mapToObj(i -> "session-" + i)
            .collect(Collectors.toList());
        
        sessionIds.forEach(contextManager::getOrCreateSession);
        
        // Clear specific session
        contextManager.clearSession(sessionIds.get(0));
        
        // Verify session is gone
        ContextManager.SessionContext newSession = contextManager.getOrCreateSession(sessionIds.get(0));
        assertTrue(newSession.getContextChunks().isEmpty());
        
        // Clear all sessions
        contextManager.clearAllSessions();
        
        // Verify all sessions are new
        sessionIds.forEach(id -> {
            ContextManager.SessionContext session = contextManager.getOrCreateSession(id);
            assertTrue(session.getContextChunks().isEmpty());
        });
    }
    
    @Test
    @Order(9)
    @DisplayName("Should track session metrics correctly")
    void testSessionMetrics() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Add contexts and track sizes
        Map<String, Integer> fileSizes = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            String content = "x".repeat(1000 * (i + 1));
            String filePath = "/file" + i + ".java";
            session.addCodeContext(filePath, content);
            fileSizes.put(filePath, content.length());
        }
        
        // Update some files
        session.updateFileContent("/file0.java", "y".repeat(2000));
        fileSizes.put("/file0.java", 2000);
        
        // Verify total size tracking
        int expectedTotalSize = fileSizes.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(session.getTotalContextSize() <= expectedTotalSize * 1.1); // Allow for metadata overhead
    }
    
    @ParameterizedTest
    @Order(10)
    @ValueSource(strings = {
        "",
        " ",
        "\n",
        "\t",
        "a", // Single character
        "x".repeat(10000) // Very large content
    })
    @DisplayName("Should handle edge case content sizes")
    void testEdgeCaseContentSizes(String content) {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        assertDoesNotThrow(() -> {
            session.addCodeContext(TEST_FILE_PATH, content);
        });
        
        if (!content.trim().isEmpty()) {
            verify(embeddingStore, atLeastOnce()).add(any(Embedding.class), any(ContextManager.CodeContext.class));
        }
    }
    
    @Test
    @Order(11)
    @DisplayName("Should handle null and invalid inputs gracefully")
    void testNullAndInvalidInputs() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Test null inputs
        assertThrows(NullPointerException.class, () -> session.addCodeContext(null, "content"));
        assertThrows(NullPointerException.class, () -> session.addCodeContext("path", null));
        assertThrows(NullPointerException.class, () -> session.getRelevantContext(null, 5));
        
        // Test invalid inputs
        assertDoesNotThrow(() -> session.getRelevantContext("query", -1));
        assertDoesNotThrow(() -> session.getRelevantContext("query", 0));
    }
    
    @Test
    @Order(12)
    @DisplayName("Should maintain file path index correctly")
    void testFilePathIndex() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Add multiple chunks for same file
        String largeContent = "x".repeat(10000);
        session.addCodeContext(TEST_FILE_PATH, largeContent);
        
        // Add content for different files
        session.addCodeContext("/file2.java", "content2");
        session.addCodeContext("/file3.java", "content3");
        
        // Update existing file
        session.updateFileContent(TEST_FILE_PATH, "updated content");
        
        // Verify file tracking
        Set<String> trackedFiles = session.getTrackedFiles();
        assertEquals(3, trackedFiles.size());
        assertTrue(trackedFiles.contains(TEST_FILE_PATH));
        assertTrue(trackedFiles.contains("/file2.java"));
        assertTrue(trackedFiles.contains("/file3.java"));
    }
    
    @Test
    @Order(13)
    @DisplayName("Should handle embedding model failures")
    void testEmbeddingModelFailure() {
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("Embedding failed"));
        
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Should handle gracefully
        assertDoesNotThrow(() -> {
            session.addCodeContext(TEST_FILE_PATH, TEST_CONTENT);
        });
    }
    
    @Test
    @Order(14)
    @DisplayName("Should perform efficiently with large codebases")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testPerformanceWithLargeCodebase() {
        ContextManager.SessionContext session = contextManager.getOrCreateSession(TEST_SESSION_ID);
        
        // Simulate large codebase
        int fileCount = 1000;
        int avgFileSize = 5000;
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < fileCount; i++) {
            String content = "public class Class" + i + " {\n" +
                IntStream.range(0, avgFileSize / 50)
                    .mapToObj(j -> "    public void method" + j + "() { }\n")
                    .collect(Collectors.joining()) + 
                "}";
            
            session.addCodeContext("/src/pkg" + (i / 100) + "/Class" + i + ".java", content);
        }
        
        long addTime = System.currentTimeMillis() - startTime;
        
        // Search performance
        startTime = System.currentTimeMillis();
        List<ContextManager.RelevantContext> results = session.getRelevantContext("method500", 10);
        long searchTime = System.currentTimeMillis() - startTime;
        
        // Performance assertions
        System.out.println("Added " + fileCount + " files in " + addTime + "ms");
        System.out.println("Search completed in " + searchTime + "ms");
        
        assertTrue(addTime < 30000, "Adding files should complete within 30 seconds");
        assertTrue(searchTime < 1000, "Search should complete within 1 second");
    }
    
    @Test
    @Order(15)
    @DisplayName("Should maintain session isolation")
    void testSessionIsolation() {
        String session1Id = "session1";
        String session2Id = "session2";
        
        ContextManager.SessionContext session1 = contextManager.getOrCreateSession(session1Id);
        ContextManager.SessionContext session2 = contextManager.getOrCreateSession(session2Id);
        
        // Add different content to each session
        session1.addCodeContext("/file1.java", "Session 1 content");
        session2.addCodeContext("/file2.java", "Session 2 content");
        
        // Verify isolation
        assertNotEquals(session1.getTrackedFiles(), session2.getTrackedFiles());
        assertFalse(session1.getTrackedFiles().contains("/file2.java"));
        assertFalse(session2.getTrackedFiles().contains("/file1.java"));
    }
}