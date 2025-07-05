package ai.intelliswarm.vulnpatcher.exceptions;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Provider
public class ExceptionMapper {

    public RestResponse<ErrorResponse> mapVulnPatcherException(VulnPatcherException exception) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.errorCode = exception.getErrorCodeEnum() != null ? 
            exception.getErrorCodeEnum().getCode() : 9001;
        errorResponse.message = exception.getMessage();
        errorResponse.details = exception.getDetails();
        errorResponse.timestamp = LocalDateTime.now().toString();
        
        int statusCode = getHttpStatusForErrorCode(exception.getErrorCode());
        return RestResponse.status(Response.Status.fromStatusCode(statusCode), errorResponse);
    }

    public RestResponse<ErrorResponse> mapConstraintViolationException(ConstraintViolationException exception) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.errorCode = VulnPatcherException.ErrorCode.INVALID_REQUEST.getCode();
        errorResponse.message = "Validation failed";
        errorResponse.details = formatViolations(exception.getConstraintViolations());
        errorResponse.timestamp = LocalDateTime.now().toString();
        
        return RestResponse.status(Response.Status.BAD_REQUEST, errorResponse);
    }

    public RestResponse<ErrorResponse> mapGenericException(Exception exception) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.errorCode = VulnPatcherException.ErrorCode.INTERNAL_ERROR.getCode();
        errorResponse.message = "An unexpected error occurred";
        errorResponse.details = null; // Don't expose internal details
        errorResponse.timestamp = LocalDateTime.now().toString();
        
        return RestResponse.status(Response.Status.INTERNAL_SERVER_ERROR, errorResponse);
    }

    private int getHttpStatusForErrorCode(String errorCode) {
        if (errorCode == null) return 500;
        
        return switch (errorCode) {
            case "CONFIG_INVALID", "CONFIG_MISSING", "INVALID_REQUEST" -> 400;
            case "AUTH_FAILED", "AUTH_INVALID_TOKEN" -> 401;
            case "AUTH_INSUFFICIENT_PERMISSIONS", "REPO_ACCESS_DENIED" -> 403;
            case "REPO_NOT_FOUND" -> 404;
            case "SCAN_TIMEOUT", "EXTERNAL_SERVICE_TIMEOUT" -> 408;
            default -> 500;
        };
    }

    private String formatViolations(Set<ConstraintViolation<?>> violations) {
        return violations.stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining(", "));
    }

    public static class ErrorResponse {
        public int errorCode;
        public String message;
        public String details;
        public String timestamp;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("errorCode", errorCode);
            map.put("message", message);
            if (details != null) {
                map.put("details", details);
            }
            map.put("timestamp", timestamp);
            return map;
        }
    }
}