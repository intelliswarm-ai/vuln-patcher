package ai.intelliswarm.vulnpatcher.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ScanResult {
    private String scanId;
    private String repositoryUrl;
    private String branch;
    private LocalDateTime scanDate;
    private List<VulnerabilityMatch> vulnerabilities = new ArrayList<>();
    private ScanStatus status;
    private long duration;
    private int filesScanned;
    private int vulnerabilitiesFound;
    
    public static class VulnerabilityMatch {
        private Vulnerability vulnerability;
        private String filePath;
        private int lineNumber;
        private String affectedCode;
        private double confidence;
        private String language;
        private String matchType;
        private List<String> indicators;
        
        public enum MatchType {
            DEPENDENCY_VERSION,
            CODE_PATTERN,
            SEMANTIC_ANALYSIS,
            MANUAL_REVIEW
        }
        
        // Getters and setters
        public Vulnerability getVulnerability() { return vulnerability; }
        public void setVulnerability(Vulnerability vulnerability) { this.vulnerability = vulnerability; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
        
        public String getAffectedCode() { return affectedCode; }
        public void setAffectedCode(String affectedCode) { this.affectedCode = affectedCode; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getMatchType() { return matchType; }
        public void setMatchType(String matchType) { this.matchType = matchType; }
        
        public List<String> getIndicators() { return indicators; }
        public void setIndicators(List<String> indicators) { this.indicators = indicators; }
    }
    
    public enum ScanStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    // Getters and setters
    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }
    
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    
    public LocalDateTime getScanDate() { return scanDate; }
    public void setScanDate(LocalDateTime scanDate) { this.scanDate = scanDate; }
    
    public List<VulnerabilityMatch> getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(List<VulnerabilityMatch> vulnerabilities) { this.vulnerabilities = vulnerabilities; }
    
    public ScanStatus getStatus() { return status; }
    public void setStatus(ScanStatus status) { this.status = status; }
    
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    
    public int getFilesScanned() { return filesScanned; }
    public void setFilesScanned(int filesScanned) { this.filesScanned = filesScanned; }
    
    public int getVulnerabilitiesFound() { return vulnerabilitiesFound; }
    public void setVulnerabilitiesFound(int vulnerabilitiesFound) { this.vulnerabilitiesFound = vulnerabilitiesFound; }
}