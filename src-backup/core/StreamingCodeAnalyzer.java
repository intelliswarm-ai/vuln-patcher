package ai.intelliswarm.vulnpatcher.core;

import ai.intelliswarm.vulnpatcher.models.Vulnerability;
import ai.intelliswarm.vulnpatcher.models.ScanResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Stream;

@ApplicationScoped
public class StreamingCodeAnalyzer {
    
    private static final Logger LOGGER = Logger.getLogger(StreamingCodeAnalyzer.class.getName());
    
    // Configuration
    private static final int BATCH_SIZE = 100; // Files per batch
    private static final int WORKER_THREADS = Runtime.getRuntime().availableProcessors();
    private static final long MAX_FILE_SIZE_MB = 50; // Skip files larger than this
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".cpp", ".c", ".h", ".hpp",
        ".kt", ".sql", ".rb", ".go", ".rs", ".swift", ".php", ".cs", ".scala",
        ".xml", ".json", ".yaml", ".yml", ".properties", ".gradle", ".pom"
    );
    
    @Inject
    ContextManager contextManager;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(WORKER_THREADS);
    private final Map<String, FileAnalysisResult> analysisCache = new ConcurrentHashMap<>();
    
    public class FileAnalysisResult {
        private final String filePath;
        private final String fileType;
        private final Map<String, Object> metadata;
        private final List<String> dependencies;
        private final List<PotentialVulnerability> potentialVulnerabilities;
        
        public FileAnalysisResult(String filePath, String fileType) {
            this.filePath = filePath;
            this.fileType = fileType;
            this.metadata = new HashMap<>();
            this.dependencies = new ArrayList<>();
            this.potentialVulnerabilities = new ArrayList<>();
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public String getFileType() { return fileType; }
        public Map<String, Object> getMetadata() { return metadata; }
        public List<String> getDependencies() { return dependencies; }
        public List<PotentialVulnerability> getPotentialVulnerabilities() { return potentialVulnerabilities; }
    }
    
    public static class PotentialVulnerability {
        private String pattern;
        private int lineNumber;
        private String codeSnippet;
        private String category;
        private double confidence;
        
        // Getters and Setters
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
        public String getCodeSnippet() { return codeSnippet; }
        public void setCodeSnippet(String codeSnippet) { this.codeSnippet = codeSnippet; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }
    
    public CompletableFuture<StreamingScanResult> analyzeRepository(Path repositoryPath, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            StreamingScanResult result = new StreamingScanResult();
            result.setRepositoryPath(repositoryPath.toString());
            result.setStartTime(System.currentTimeMillis());
            
            try {
                // Get session context
                ContextManager.SessionContext sessionContext = contextManager.getOrCreateSession(sessionId);
                
                // Collect all relevant files
                List<Path> filesToAnalyze = collectFiles(repositoryPath);
                result.setTotalFiles(filesToAnalyze.size());
                
                LOGGER.info(String.format("Starting analysis of %d files in repository: %s", 
                    filesToAnalyze.size(), repositoryPath));
                
                // Process files in batches
                List<List<Path>> batches = createBatches(filesToAnalyze, BATCH_SIZE);
                AtomicLong processedFiles = new AtomicLong(0);
                
                List<CompletableFuture<BatchResult>> batchFutures = new ArrayList<>();
                
                for (List<Path> batch : batches) {
                    CompletableFuture<BatchResult> batchFuture = processBatch(
                        batch, sessionContext, processedFiles, result.getTotalFiles()
                    );
                    batchFutures.add(batchFuture);
                }
                
                // Wait for all batches to complete
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                
                // Aggregate results
                for (CompletableFuture<BatchResult> future : batchFutures) {
                    BatchResult batchResult = future.get();
                    result.getAnalyzedFiles().addAll(batchResult.analyzedFiles);
                    result.getDetectedPatterns().putAll(batchResult.detectedPatterns);
                }
                
                result.setEndTime(System.currentTimeMillis());
                result.setSuccess(true);
                
                LOGGER.info(String.format("Repository analysis completed. Processed %d files in %d ms",
                    processedFiles.get(), result.getEndTime() - result.getStartTime()));
                
            } catch (Exception e) {
                LOGGER.severe("Error during repository analysis: " + e.getMessage());
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
            }
            
            return result;
        });
    }
    
    private List<Path> collectFiles(Path repositoryPath) throws IOException {
        List<Path> files = new ArrayList<>();
        
        try (Stream<Path> pathStream = Files.walk(repositoryPath)) {
            pathStream
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFile)
                .filter(this::isWithinSizeLimit)
                .forEach(files::add);
        }
        
        return files;
    }
    
    private boolean isSupportedFile(Path path) {
        String fileName = path.getFileName().toString();
        return SUPPORTED_EXTENSIONS.stream()
            .anyMatch(ext -> fileName.endsWith(ext));
    }
    
    private boolean isWithinSizeLimit(Path path) {
        try {
            long sizeInMB = Files.size(path) / (1024 * 1024);
            return sizeInMB <= MAX_FILE_SIZE_MB;
        } catch (IOException e) {
            return false;
        }
    }
    
    private List<List<Path>> createBatches(List<Path> files, int batchSize) {
        List<List<Path>> batches = new ArrayList<>();
        
        for (int i = 0; i < files.size(); i += batchSize) {
            int end = Math.min(i + batchSize, files.size());
            batches.add(files.subList(i, end));
        }
        
        return batches;
    }
    
    private CompletableFuture<BatchResult> processBatch(
            List<Path> batch, 
            ContextManager.SessionContext sessionContext,
            AtomicLong processedFiles,
            long totalFiles) {
        
        return CompletableFuture.supplyAsync(() -> {
            BatchResult result = new BatchResult();
            
            for (Path file : batch) {
                try {
                    FileAnalysisResult analysisResult = analyzeFile(file, sessionContext);
                    result.analyzedFiles.add(analysisResult);
                    
                    // Track patterns
                    for (PotentialVulnerability vuln : analysisResult.getPotentialVulnerabilities()) {
                        result.detectedPatterns.merge(vuln.getCategory(), 1, Integer::sum);
                    }
                    
                    long processed = processedFiles.incrementAndGet();
                    if (processed % 100 == 0) {
                        LOGGER.info(String.format("Progress: %d/%d files analyzed (%.2f%%)",
                            processed, totalFiles, (processed * 100.0) / totalFiles));
                    }
                    
                } catch (Exception e) {
                    LOGGER.warning("Error analyzing file " + file + ": " + e.getMessage());
                }
            }
            
            return result;
        }, executorService);
    }
    
    private FileAnalysisResult analyzeFile(Path file, ContextManager.SessionContext sessionContext) 
            throws IOException {
        
        String filePath = file.toString();
        String fileType = detectFileType(file);
        FileAnalysisResult result = new FileAnalysisResult(filePath, fileType);
        
        // Read file content with streaming
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            StringBuilder contentBuilder = new StringBuilder();
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                contentBuilder.append(line).append("\n");
                
                // Perform line-by-line analysis
                analyzeLine(line, lineNumber, result);
            }
            
            // Add to context manager for semantic search
            String content = contentBuilder.toString();
            sessionContext.addFile(filePath, content, fileType);
            
            // Extract dependencies
            extractDependencies(content, fileType, result);
        }
        
        // Cache the result
        analysisCache.put(filePath, result);
        
        return result;
    }
    
    private void analyzeLine(String line, int lineNumber, FileAnalysisResult result) {
        // Pattern-based vulnerability detection
        Map<String, String> vulnerabilityPatterns = Map.of(
            "HARDCODED_SECRET", "(?i)(password|api_key|secret)\\s*=\\s*[\"'][^\"']+[\"']",
            "SQL_INJECTION", "(?i)(execute|query).*\\+.*(?:request|param|input)",
            "WEAK_CRYPTO", "(?i)(md5|sha1)\\s*\\(",
            "INSECURE_RANDOM", "(?i)math\\.random\\(\\)",
            "DEBUG_CODE", "(?i)(console\\.log|print|debug|todo)"
        );
        
        for (Map.Entry<String, String> entry : vulnerabilityPatterns.entrySet()) {
            if (line.matches(".*" + entry.getValue() + ".*")) {
                PotentialVulnerability vuln = new PotentialVulnerability();
                vuln.setCategory(entry.getKey());
                vuln.setLineNumber(lineNumber);
                vuln.setCodeSnippet(line.trim());
                vuln.setPattern(entry.getValue());
                vuln.setConfidence(0.7); // Base confidence, can be refined
                
                result.getPotentialVulnerabilities().add(vuln);
            }
        }
    }
    
    private void extractDependencies(String content, String fileType, FileAnalysisResult result) {
        // Extract dependencies based on file type
        switch (fileType) {
            case "java":
                extractJavaDependencies(content, result);
                break;
            case "python":
                extractPythonDependencies(content, result);
                break;
            case "javascript":
            case "typescript":
                extractJavaScriptDependencies(content, result);
                break;
            // Add more language-specific extractors
        }
    }
    
    private void extractJavaDependencies(String content, FileAnalysisResult result) {
        // Simple import extraction
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("import ")) {
                String dependency = line.substring(7).replace(";", "").trim();
                result.getDependencies().add(dependency);
            }
        }
    }
    
    private void extractPythonDependencies(String content, FileAnalysisResult result) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("import ") || line.trim().startsWith("from ")) {
                result.getDependencies().add(line.trim());
            }
        }
    }
    
    private void extractJavaScriptDependencies(String content, FileAnalysisResult result) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.contains("require(") || line.contains("import ")) {
                result.getDependencies().add(line.trim());
            }
        }
    }
    
    private String detectFileType(Path file) {
        String fileName = file.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        
        Map<String, String> extensionToType = Map.of(
            "java", "java",
            "py", "python",
            "js", "javascript",
            "ts", "typescript",
            "cpp", "cpp",
            "c", "c",
            "kt", "kotlin",
            "sql", "sql"
        );
        
        return extensionToType.getOrDefault(extension, extension);
    }
    
    private static class BatchResult {
        List<FileAnalysisResult> analyzedFiles = new ArrayList<>();
        Map<String, Integer> detectedPatterns = new ConcurrentHashMap<>();
    }
    
    public static class StreamingScanResult {
        private String repositoryPath;
        private long startTime;
        private long endTime;
        private boolean success;
        private String errorMessage;
        private long totalFiles;
        private List<FileAnalysisResult> analyzedFiles = new ArrayList<>();
        private Map<String, Integer> detectedPatterns = new ConcurrentHashMap<>();
        
        // Getters and Setters
        public String getRepositoryPath() { return repositoryPath; }
        public void setRepositoryPath(String repositoryPath) { this.repositoryPath = repositoryPath; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getTotalFiles() { return totalFiles; }
        public void setTotalFiles(long totalFiles) { this.totalFiles = totalFiles; }
        public List<FileAnalysisResult> getAnalyzedFiles() { return analyzedFiles; }
        public Map<String, Integer> getDetectedPatterns() { return detectedPatterns; }
    }
    
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}