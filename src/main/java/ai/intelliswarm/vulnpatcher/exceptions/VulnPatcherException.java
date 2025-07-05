package ai.intelliswarm.vulnpatcher.exceptions;

public class VulnPatcherException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final String details;
    
    public VulnPatcherException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public VulnPatcherException(ErrorCode errorCode, String message, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public VulnPatcherException(ErrorCode errorCode, String message, String details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public String getErrorCode() {
        return errorCode != null ? errorCode.name() : "UNKNOWN";
    }
    
    public ErrorCode getErrorCodeEnum() {
        return errorCode;
    }
    
    public String getDetails() {
        return details;
    }
    
    public enum ErrorCode {
        // Configuration errors (1xxx)
        CONFIG_INVALID(1001),
        CONFIG_MISSING(1002),
        
        // Authentication errors (2xxx)
        AUTH_FAILED(2001),
        AUTH_INVALID_TOKEN(2002),
        AUTH_INSUFFICIENT_PERMISSIONS(2003),
        
        // Repository errors (3xxx)
        REPO_NOT_FOUND(3001),
        REPO_ACCESS_DENIED(3002),
        
        // Scan errors (4xxx)
        SCAN_FAILED(4001),
        SCAN_TIMEOUT(4002),
        
        // Fix generation errors (5xxx)
        FIX_GENERATION_FAILED(5001),
        
        // External service errors (6xxx)
        EXTERNAL_SERVICE_ERROR(6001),
        EXTERNAL_SERVICE_TIMEOUT(6002),
        
        // General errors (9xxx)
        INVALID_REQUEST(9002),
        INTERNAL_ERROR(9001);
        
        private final int code;
        
        ErrorCode(int code) {
            this.code = code;
        }
        
        public int getCode() {
            return code;
        }
    }
}