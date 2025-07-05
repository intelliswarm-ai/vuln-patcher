package ai.intelliswarm.vulnpatcher.core;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.splitter.DocumentSplitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class ContextManager {
    
    private static final Logger LOGGER = Logger.getLogger(ContextManager.class.getName());
    
    // Configuration for chunk management
    private static final int CHUNK_SIZE = 1500; // tokens per chunk
    private static final int CHUNK_OVERLAP = 200; // overlap between chunks
    private static final int MAX_CONTEXT_TOKENS = 100000; // Max context for large models
    private static final int RETRIEVAL_LIMIT = 20; // Max chunks to retrieve
    
    @Inject
    EmbeddingModel embeddingModel;
    
    // Per-session embedding stores for managing large codebases
    private final Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();
    
    public class SessionContext {
        private final String sessionId;
        private final EmbeddingStore<TextSegment> embeddingStore;
        private final Map<String, FileContext> fileContexts;
        private final Map<String, String> fileChecksums;
        private long totalTokens;
        
        public SessionContext(String sessionId) {
            this.sessionId = sessionId;
            this.embeddingStore = new InMemoryEmbeddingStore<>();
            this.fileContexts = new ConcurrentHashMap<>();
            this.fileChecksums = new ConcurrentHashMap<>();
            this.totalTokens = 0;
        }
        
        public void addFile(String filePath, String content, String fileType) {
            try {
                // Create file context with metadata
                FileContext fileContext = new FileContext(filePath, content, fileType);
                
                // Split content into chunks with overlap
                List<TextSegment> chunks = createChunks(content, filePath, fileType);
                
                // Generate embeddings for each chunk
                for (TextSegment chunk : chunks) {
                    Embedding embedding = embeddingModel.embed(chunk).content();
                    embeddingStore.add(embedding, chunk);
                }
                
                fileContexts.put(filePath, fileContext);
                fileChecksums.put(filePath, calculateChecksum(content));
                totalTokens += estimateTokenCount(content);
                
                LOGGER.info(String.format("Added file %s with %d chunks to context", filePath, chunks.size()));
                
            } catch (Exception e) {
                LOGGER.severe("Error adding file to context: " + e.getMessage());
            }
        }
        
        public List<RelevantContext> getRelevantContext(String query, int maxChunks) {
            List<RelevantContext> relevantContexts = new ArrayList<>();
            
            try {
                // Embed the query
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                
                // Find most relevant chunks
                var relevant = embeddingStore.findRelevant(queryEmbedding, maxChunks);
                
                for (var match : relevant) {
                    TextSegment segment = match.embedded();
                    Map<String, Object> metadata = segment.metadata().toMap();
                    
                    RelevantContext context = new RelevantContext();
                    context.setContent(segment.text());
                    context.setFilePath((String) metadata.get("filePath"));
                    context.setStartLine((Integer) metadata.get("startLine"));
                    context.setEndLine((Integer) metadata.get("endLine"));
                    context.setRelevanceScore(match.score());
                    context.setFileType((String) metadata.get("fileType"));
                    
                    relevantContexts.add(context);
                }
                
            } catch (Exception e) {
                LOGGER.severe("Error retrieving relevant context: " + e.getMessage());
            }
            
            return relevantContexts;
        }
        
        public void clearOldContext() {
            if (totalTokens > MAX_CONTEXT_TOKENS * 0.8) {
                // Implement LRU eviction or smart pruning
                LOGGER.info("Context size exceeded threshold, pruning old entries");
                // TODO: Implement intelligent pruning based on access patterns
            }
        }
    }
    
    public static class FileContext {
        private final String filePath;
        private final String content;
        private final String fileType;
        private final long timestamp;
        private final Map<String, Object> metadata;
        
        public FileContext(String filePath, String content, String fileType) {
            this.filePath = filePath;
            this.content = content;
            this.fileType = fileType;
            this.timestamp = System.currentTimeMillis();
            this.metadata = new HashMap<>();
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public String getContent() { return content; }
        public String getFileType() { return fileType; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    public static class RelevantContext {
        private String content;
        private String filePath;
        private Integer startLine;
        private Integer endLine;
        private Double relevanceScore;
        private String fileType;
        
        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public Integer getStartLine() { return startLine; }
        public void setStartLine(Integer startLine) { this.startLine = startLine; }
        public Integer getEndLine() { return endLine; }
        public void setEndLine(Integer endLine) { this.endLine = endLine; }
        public Double getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
    }
    
    // Create or get session context
    public SessionContext getOrCreateSession(String sessionId) {
        return sessionContexts.computeIfAbsent(sessionId, SessionContext::new);
    }
    
    // Remove session context
    public void removeSession(String sessionId) {
        sessionContexts.remove(sessionId);
    }
    
    // Create chunks with metadata
    private List<TextSegment> createChunks(String content, String filePath, String fileType) {
        List<TextSegment> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        
        int currentLine = 0;
        while (currentLine < lines.length) {
            StringBuilder chunkBuilder = new StringBuilder();
            int startLine = currentLine;
            int tokenCount = 0;
            
            // Build chunk up to CHUNK_SIZE tokens
            while (currentLine < lines.length && tokenCount < CHUNK_SIZE) {
                String line = lines[currentLine];
                chunkBuilder.append(line).append("\n");
                tokenCount += estimateTokenCount(line);
                currentLine++;
            }
            
            // Add overlap from next chunk
            int overlapLine = currentLine;
            int overlapTokens = 0;
            StringBuilder overlapBuilder = new StringBuilder();
            
            while (overlapLine < lines.length && overlapTokens < CHUNK_OVERLAP) {
                String line = lines[overlapLine];
                overlapBuilder.append(line).append("\n");
                overlapTokens += estimateTokenCount(line);
                overlapLine++;
            }
            
            // Create text segment with metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            metadata.put("startLine", startLine + 1); // 1-indexed
            metadata.put("endLine", currentLine); // exclusive
            metadata.put("fileType", fileType);
            metadata.put("chunkIndex", chunks.size());
            
            String chunkContent = chunkBuilder.toString();
            if (!overlapBuilder.isEmpty()) {
                chunkContent += "\n... (overlap) ...\n" + overlapBuilder;
            }
            
            TextSegment segment = TextSegment.from(chunkContent, metadata);
            chunks.add(segment);
            
            // Move back for overlap in next iteration
            currentLine = Math.max(startLine + 1, currentLine - (CHUNK_OVERLAP / 4));
        }
        
        return chunks;
    }
    
    // Simple token estimation (can be replaced with proper tokenizer)
    private int estimateTokenCount(String text) {
        // Rough estimate: 1 token per 4 characters
        return text.length() / 4;
    }
    
    // Calculate checksum for change detection
    private String calculateChecksum(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }
    
    // Get context summary for a session
    public Map<String, Object> getSessionSummary(String sessionId) {
        SessionContext context = sessionContexts.get(sessionId);
        if (context == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("sessionId", sessionId);
        summary.put("totalFiles", context.fileContexts.size());
        summary.put("totalTokens", context.totalTokens);
        summary.put("files", new ArrayList<>(context.fileContexts.keySet()));
        
        return summary;
    }
}