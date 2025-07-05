package ai.intelliswarm.vulnpatcher.git;

import ai.intelliswarm.vulnpatcher.core.StreamingCodeAnalyzer;
import ai.intelliswarm.vulnpatcher.core.ContextManager;
import ai.intelliswarm.vulnpatcher.git.providers.GitProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class GitRepositoryScanner {
    
    private static final Logger LOGGER = Logger.getLogger(GitRepositoryScanner.class.getName());
    
    @ConfigProperty(name = "vulnpatcher.git.temp-dir")
    String tempDir;
    
    @ConfigProperty(name = "vulnpatcher.git.clone-timeout-seconds")
    Integer cloneTimeout;
    
    @ConfigProperty(name = "vulnpatcher.github.token", defaultValue = "")
    String githubToken;
    
    @ConfigProperty(name = "vulnpatcher.gitlab.token", defaultValue = "")
    String gitlabToken;
    
    @Inject
    StreamingCodeAnalyzer codeAnalyzer;
    
    @Inject
    ContextManager contextManager;
    
    @Inject
    Instance<GitProvider> gitProviders;
    
    public CompletableFuture<ScanSession> scanRepository(RepositoryScanRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            ScanSession session = new ScanSession();
            session.setSessionId(UUID.randomUUID().toString());
            session.setRepositoryUrl(request.getRepositoryUrl());
            session.setBranch(request.getBranch() != null ? request.getBranch() : "main");
            session.setStartTime(LocalDateTime.now());
            session.setStatus(ScanSession.Status.IN_PROGRESS);
            
            Path repoPath = null;
            
            try {
                // Clone or open repository
                if (request.isLocal()) {
                    repoPath = Paths.get(request.getRepositoryUrl());
                    if (!Files.exists(repoPath)) {
                        throw new IllegalArgumentException("Local repository path does not exist: " + repoPath);
                    }
                } else {
                    repoPath = cloneRepository(request, session);
                }
                
                session.setLocalPath(repoPath.toString());
                
                // Analyze repository structure
                RepositoryInfo repoInfo = analyzeRepositoryStructure(repoPath);
                session.setRepositoryInfo(repoInfo);
                
                // Perform streaming code analysis
                StreamingCodeAnalyzer.StreamingScanResult analysisResult = 
                    codeAnalyzer.analyzeRepository(repoPath, session.getSessionId()).get();
                
                session.setAnalysisResult(analysisResult);
                
                // Extract dependency information
                DependencyInfo dependencies = extractDependencies(repoPath, repoInfo);
                session.setDependencies(dependencies);
                
                session.setEndTime(LocalDateTime.now());
                session.setStatus(ScanSession.Status.COMPLETED);
                
                LOGGER.info(String.format("Repository scan completed for %s. Session ID: %s", 
                    request.getRepositoryUrl(), session.getSessionId()));
                
            } catch (Exception e) {
                LOGGER.severe("Error scanning repository: " + e.getMessage());
                session.setStatus(ScanSession.Status.FAILED);
                session.setErrorMessage(e.getMessage());
                session.setEndTime(LocalDateTime.now());
            } finally {
                // Cleanup cloned repository if needed
                if (!request.isLocal() && repoPath != null && request.isCleanupAfterScan()) {
                    cleanupRepository(repoPath);
                }
            }
            
            return session;
        });
    }
    
    private Path cloneRepository(RepositoryScanRequest request, ScanSession session) 
            throws GitAPIException, IOException {
        
        String repoName = extractRepoName(request.getRepositoryUrl());
        Path clonePath = Paths.get(tempDir, session.getSessionId(), repoName);
        Files.createDirectories(clonePath);
        
        LOGGER.info("Cloning repository: " + request.getRepositoryUrl());
        
        Git.CloneCommand cloneCommand = Git.cloneRepository()
            .setURI(request.getRepositoryUrl())
            .setDirectory(clonePath.toFile())
            .setBranch(request.getBranch())
            .setCloneAllBranches(false)
            .setProgressMonitor(new LoggingProgressMonitor());
        
        // Set credentials if needed
        GitProvider provider = findProviderForUrl(request.getRepositoryUrl());
        if (provider != null) {
            CredentialsProvider credentials = provider.getCredentialsProvider(
                request.getRepositoryUrl(), request.getCredentials()
            );
            if (credentials != null) {
                cloneCommand.setCredentialsProvider(credentials);
            }
        }
        
        Git git = cloneCommand.call();
        git.close();
        
        return clonePath;
    }
    
    private RepositoryInfo analyzeRepositoryStructure(Path repoPath) throws IOException {
        RepositoryInfo info = new RepositoryInfo();
        
        // Detect project types
        Set<String> projectTypes = new HashSet<>();
        Map<String, Integer> languageFileCount = new HashMap<>();
        
        Files.walk(repoPath)
            .filter(Files::isRegularFile)
            .forEach(path -> {
                String fileName = path.getFileName().toString();
                
                // Detect project types by build files
                if (fileName.equals("pom.xml")) {
                    projectTypes.add("maven");
                } else if (fileName.equals("build.gradle") || fileName.equals("build.gradle.kts")) {
                    projectTypes.add("gradle");
                } else if (fileName.equals("package.json")) {
                    projectTypes.add("npm");
                } else if (fileName.equals("requirements.txt") || fileName.equals("setup.py")) {
                    projectTypes.add("python");
                } else if (fileName.equals("composer.json")) {
                    projectTypes.add("php");
                } else if (fileName.equals("Gemfile")) {
                    projectTypes.add("ruby");
                } else if (fileName.equals("go.mod")) {
                    projectTypes.add("go");
                } else if (fileName.equals("Cargo.toml")) {
                    projectTypes.add("rust");
                }
                
                // Count files by language
                String extension = getFileExtension(fileName);
                if (isCodeFile(extension)) {
                    String language = mapExtensionToLanguage(extension);
                    languageFileCount.merge(language, 1, Integer::sum);
                }
            });
        
        info.setProjectTypes(new ArrayList<>(projectTypes));
        info.setLanguages(new ArrayList<>(languageFileCount.keySet()));
        info.setLanguageDistribution(languageFileCount);
        
        // Get repository metadata
        try {
            Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(repoPath.toFile(), ".git"))
                .build();
            
            info.setCurrentBranch(repo.getBranch());
            info.setRemoteUrl(repo.getConfig().getString("remote", "origin", "url"));
            
            repo.close();
        } catch (Exception e) {
            LOGGER.warning("Could not read git metadata: " + e.getMessage());
        }
        
        return info;
    }
    
    private DependencyInfo extractDependencies(Path repoPath, RepositoryInfo repoInfo) {
        DependencyInfo dependencies = new DependencyInfo();
        Map<String, List<Dependency>> dependencyMap = new HashMap<>();
        
        try {
            // Extract Maven dependencies
            if (repoInfo.getProjectTypes().contains("maven")) {
                dependencyMap.put("maven", extractMavenDependencies(repoPath));
            }
            
            // Extract NPM dependencies
            if (repoInfo.getProjectTypes().contains("npm")) {
                dependencyMap.put("npm", extractNpmDependencies(repoPath));
            }
            
            // Extract Python dependencies
            if (repoInfo.getProjectTypes().contains("python")) {
                dependencyMap.put("python", extractPythonDependencies(repoPath));
            }
            
            // Add more dependency extractors as needed
            
            dependencies.setDependenciesByType(dependencyMap);
            
            // Calculate total count
            int totalCount = dependencyMap.values().stream()
                .mapToInt(List::size)
                .sum();
            dependencies.setTotalCount(totalCount);
            
        } catch (Exception e) {
            LOGGER.warning("Error extracting dependencies: " + e.getMessage());
        }
        
        return dependencies;
    }
    
    private List<Dependency> extractMavenDependencies(Path repoPath) throws IOException {
        List<Dependency> dependencies = new ArrayList<>();
        Path pomPath = repoPath.resolve("pom.xml");
        
        if (Files.exists(pomPath)) {
            // Simple XML parsing - in production, use proper XML parser
            List<String> lines = Files.readAllLines(pomPath);
            boolean inDependencies = false;
            Dependency current = null;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.contains("<dependencies>")) {
                    inDependencies = true;
                } else if (line.contains("</dependencies>")) {
                    inDependencies = false;
                } else if (inDependencies) {
                    if (line.contains("<dependency>")) {
                        current = new Dependency();
                        current.setType("maven");
                    } else if (line.contains("</dependency>") && current != null) {
                        dependencies.add(current);
                        current = null;
                    } else if (current != null) {
                        if (line.contains("<groupId>")) {
                            current.setGroupId(extractXmlValue(line, "groupId"));
                        } else if (line.contains("<artifactId>")) {
                            current.setArtifactId(extractXmlValue(line, "artifactId"));
                            current.setName(current.getGroupId() + ":" + current.getArtifactId());
                        } else if (line.contains("<version>")) {
                            current.setVersion(extractXmlValue(line, "version"));
                        }
                    }
                }
            }
        }
        
        return dependencies;
    }
    
    private List<Dependency> extractNpmDependencies(Path repoPath) throws IOException {
        List<Dependency> dependencies = new ArrayList<>();
        Path packagePath = repoPath.resolve("package.json");
        
        if (Files.exists(packagePath)) {
            // In production, use proper JSON parser
            String content = Files.readString(packagePath);
            // Simple extraction - replace with proper JSON parsing
            if (content.contains("\"dependencies\"")) {
                // Extract dependencies section and parse
                // This is simplified - use Jackson in production
            }
        }
        
        return dependencies;
    }
    
    private List<Dependency> extractPythonDependencies(Path repoPath) throws IOException {
        List<Dependency> dependencies = new ArrayList<>();
        Path requirementsPath = repoPath.resolve("requirements.txt");
        
        if (Files.exists(requirementsPath)) {
            List<String> lines = Files.readAllLines(requirementsPath);
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    Dependency dep = new Dependency();
                    dep.setType("python");
                    
                    // Parse requirement line
                    if (line.contains("==")) {
                        String[] parts = line.split("==");
                        dep.setName(parts[0]);
                        dep.setVersion(parts[1]);
                    } else if (line.contains(">=")) {
                        String[] parts = line.split(">=");
                        dep.setName(parts[0]);
                        dep.setVersion(">=" + parts[1]);
                    } else {
                        dep.setName(line);
                        dep.setVersion("*");
                    }
                    
                    dependencies.add(dep);
                }
            }
        }
        
        return dependencies;
    }
    
    private String extractXmlValue(String line, String tag) {
        int start = line.indexOf("<" + tag + ">") + tag.length() + 2;
        int end = line.indexOf("</" + tag + ">");
        if (start > 0 && end > start) {
            return line.substring(start, end);
        }
        return "";
    }
    
    private GitProvider findProviderForUrl(String url) {
        for (GitProvider provider : gitProviders) {
            if (provider.canHandle(url)) {
                return provider;
            }
        }
        return null;
    }
    
    private String extractRepoName(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }
    
    private void cleanupRepository(Path repoPath) {
        try {
            Files.walk(repoPath)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            LOGGER.warning("Failed to cleanup repository: " + e.getMessage());
        }
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
    
    private boolean isCodeFile(String extension) {
        Set<String> codeExtensions = Set.of(
            "java", "py", "js", "ts", "jsx", "tsx", "cpp", "c", "h", "hpp",
            "cs", "rb", "go", "rs", "php", "kt", "swift", "scala", "sql"
        );
        return codeExtensions.contains(extension.toLowerCase());
    }
    
    private String mapExtensionToLanguage(String extension) {
        Map<String, String> extensionMap = Map.of(
            "java", "java",
            "py", "python",
            "js", "javascript",
            "ts", "typescript",
            "cpp", "cpp",
            "c", "c",
            "cs", "csharp",
            "rb", "ruby",
            "go", "go",
            "rs", "rust"
        );
        
        return extensionMap.getOrDefault(extension.toLowerCase(), extension);
    }
    
    // Inner classes for data models
    public static class RepositoryScanRequest {
        private String repositoryUrl;
        private String branch;
        private boolean isLocal;
        private boolean cleanupAfterScan = true;
        private Map<String, String> credentials;
        
        // Getters and setters
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public boolean isLocal() { return isLocal; }
        public void setLocal(boolean local) { isLocal = local; }
        public boolean isCleanupAfterScan() { return cleanupAfterScan; }
        public void setCleanupAfterScan(boolean cleanupAfterScan) { this.cleanupAfterScan = cleanupAfterScan; }
        public Map<String, String> getCredentials() { return credentials; }
        public void setCredentials(Map<String, String> credentials) { this.credentials = credentials; }
    }
    
    public static class ScanSession {
        private String sessionId;
        private String repositoryUrl;
        private String branch;
        private String localPath;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Status status;
        private String errorMessage;
        private RepositoryInfo repositoryInfo;
        private StreamingCodeAnalyzer.StreamingScanResult analysisResult;
        private DependencyInfo dependencies;
        
        public enum Status {
            PENDING, IN_PROGRESS, COMPLETED, FAILED
        }
        
        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public RepositoryInfo getRepositoryInfo() { return repositoryInfo; }
        public void setRepositoryInfo(RepositoryInfo repositoryInfo) { this.repositoryInfo = repositoryInfo; }
        public StreamingCodeAnalyzer.StreamingScanResult getAnalysisResult() { return analysisResult; }
        public void setAnalysisResult(StreamingCodeAnalyzer.StreamingScanResult analysisResult) { 
            this.analysisResult = analysisResult; 
        }
        public DependencyInfo getDependencies() { return dependencies; }
        public void setDependencies(DependencyInfo dependencies) { this.dependencies = dependencies; }
    }
    
    public static class RepositoryInfo {
        private String currentBranch;
        private String remoteUrl;
        private List<String> projectTypes;
        private List<String> languages;
        private Map<String, Integer> languageDistribution;
        
        // Getters and setters
        public String getCurrentBranch() { return currentBranch; }
        public void setCurrentBranch(String currentBranch) { this.currentBranch = currentBranch; }
        public String getRemoteUrl() { return remoteUrl; }
        public void setRemoteUrl(String remoteUrl) { this.remoteUrl = remoteUrl; }
        public List<String> getProjectTypes() { return projectTypes; }
        public void setProjectTypes(List<String> projectTypes) { this.projectTypes = projectTypes; }
        public List<String> getLanguages() { return languages; }
        public void setLanguages(List<String> languages) { this.languages = languages; }
        public Map<String, Integer> getLanguageDistribution() { return languageDistribution; }
        public void setLanguageDistribution(Map<String, Integer> languageDistribution) { 
            this.languageDistribution = languageDistribution; 
        }
    }
    
    public static class DependencyInfo {
        private Map<String, List<Dependency>> dependenciesByType;
        private int totalCount;
        
        // Getters and setters
        public Map<String, List<Dependency>> getDependenciesByType() { return dependenciesByType; }
        public void setDependenciesByType(Map<String, List<Dependency>> dependenciesByType) { 
            this.dependenciesByType = dependenciesByType; 
        }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    }
    
    public static class Dependency {
        private String type;
        private String name;
        private String version;
        private String groupId; // For Maven
        private String artifactId; // For Maven
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    }
    
    private static class LoggingProgressMonitor implements org.eclipse.jgit.lib.ProgressMonitor {
        private static final Logger LOGGER = Logger.getLogger(LoggingProgressMonitor.class.getName());
        
        @Override
        public void start(int totalTasks) {
            LOGGER.info("Starting git operation with " + totalTasks + " tasks");
        }
        
        @Override
        public void beginTask(String title, int totalWork) {
            LOGGER.info("Beginning task: " + title);
        }
        
        @Override
        public void update(int completed) {
            // Log progress periodically
        }
        
        @Override
        public void endTask() {
            // Task completed
        }
        
        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}