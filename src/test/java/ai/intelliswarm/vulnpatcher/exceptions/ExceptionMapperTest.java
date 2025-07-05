package ai.intelliswarm.vulnpatcher.exceptions;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExceptionMapperTest {
    
    private ExceptionMapper exceptionMapper;
    
    @BeforeEach
    void setUp() {
        exceptionMapper = new ExceptionMapper();
        RestAssured.basePath = "/test";
    }
    
    @Test
    @Order(1)
    @DisplayName("Should map VulnPatcherException to appropriate HTTP status")
    void testMapVulnPatcherException() {
        VulnPatcherException exception = new VulnPatcherException(
            VulnPatcherException.ErrorCode.REPO_NOT_FOUND,
            "Repository not found",
            "Repository: https://github.com/test/repo"
        );
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapVulnPatcherException(exception);
        
        assertNotNull(response);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        
        ExceptionMapper.ErrorResponse errorResponse = response.getEntity();
        assertNotNull(errorResponse);
        assertEquals(3001, errorResponse.errorCode);
        assertEquals("Repository not found", errorResponse.message);
        assertEquals("Repository: https://github.com/test/repo", errorResponse.details);
        assertNotNull(errorResponse.timestamp);
    }
    
    @ParameterizedTest
    @Order(2)
    @EnumSource(VulnPatcherException.ErrorCode.class)
    @DisplayName("Should map all error codes to correct HTTP status")
    void testAllErrorCodeMappings(VulnPatcherException.ErrorCode errorCode) {
        VulnPatcherException exception = new VulnPatcherException(errorCode, "Test error");
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapVulnPatcherException(exception);
        
        assertNotNull(response);
        
        // Verify correct status mapping
        int expectedStatus = getExpectedStatusForErrorCode(errorCode);
        assertEquals(expectedStatus, response.getStatus());
        
        // Verify error response
        ExceptionMapper.ErrorResponse errorResponse = response.getEntity();
        assertEquals(errorCode.getCode(), errorResponse.errorCode);
        assertEquals("Test error", errorResponse.message);
    }
    
    @Test
    @Order(3)
    @DisplayName("Should map ConstraintViolationException with detailed messages")
    void testMapConstraintViolationException() {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        
        // Create mock violations
        ConstraintViolation<?> violation1 = createMockViolation("repositoryUrl", "must not be blank");
        ConstraintViolation<?> violation2 = createMockViolation("branch", "invalid format");
        
        violations.add(violation1);
        violations.add(violation2);
        
        ConstraintViolationException exception = new ConstraintViolationException(violations);
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapConstraintViolationException(exception);
        
        assertNotNull(response);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        
        ExceptionMapper.ErrorResponse errorResponse = response.getEntity();
        assertEquals(VulnPatcherException.ErrorCode.INVALID_REQUEST.getCode(), errorResponse.errorCode);
        assertEquals("Validation failed", errorResponse.message);
        assertNotNull(errorResponse.details);
        assertTrue(errorResponse.details.contains("repositoryUrl"));
        assertTrue(errorResponse.details.contains("must not be blank"));
        assertTrue(errorResponse.details.contains("branch"));
        assertTrue(errorResponse.details.contains("invalid format"));
    }
    
    @Test
    @Order(4)
    @DisplayName("Should map generic exceptions to internal server error")
    void testMapGenericException() {
        Exception exception = new RuntimeException("Unexpected error");
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapGenericException(exception);
        
        assertNotNull(response);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        
        ExceptionMapper.ErrorResponse errorResponse = response.getEntity();
        assertEquals(VulnPatcherException.ErrorCode.INTERNAL_ERROR.getCode(), errorResponse.errorCode);
        assertEquals("An unexpected error occurred", errorResponse.message);
        assertNull(errorResponse.details); // Should not expose internal details
    }
    
    @Test
    @Order(5)
    @DisplayName("Should test exception mapping through REST endpoint")
    void testExceptionMappingThroughREST() {
        // Test VulnPatcherException
        given()
            .when()
            .get("/vulnpatcher-exception")
            .then()
            .statusCode(404)
            .body("errorCode", equalTo(3001))
            .body("message", equalTo("Repository not found"))
            .body("timestamp", notNullValue());
        
        // Test validation exception
        given()
            .when()
            .get("/validation-exception")
            .then()
            .statusCode(400)
            .body("errorCode", equalTo(9002))
            .body("message", equalTo("Validation failed"));
        
        // Test generic exception
        given()
            .when()
            .get("/generic-exception")
            .then()
            .statusCode(500)
            .body("errorCode", equalTo(9001))
            .body("message", equalTo("An unexpected error occurred"));
    }
    
    @Test
    @Order(6)
    @DisplayName("Should handle null exception details gracefully")
    void testNullExceptionDetails() {
        VulnPatcherException exception = new VulnPatcherException(
            VulnPatcherException.ErrorCode.INTERNAL_ERROR,
            null
        );
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapVulnPatcherException(exception);
        
        assertNotNull(response);
        ExceptionMapper.ErrorResponse errorResponse = response.getEntity();
        assertNull(errorResponse.message);
        assertNull(errorResponse.details);
    }
    
    @Test
    @Order(7)
    @DisplayName("Should convert ErrorResponse to Map correctly")
    void testErrorResponseToMap() {
        ExceptionMapper.ErrorResponse errorResponse = new ExceptionMapper.ErrorResponse();
        errorResponse.errorCode = 1001;
        errorResponse.message = "Test message";
        errorResponse.details = "Test details";
        errorResponse.timestamp = LocalDateTime.now().toString();
        
        Map<String, Object> map = errorResponse.toMap();
        
        assertNotNull(map);
        assertEquals(4, map.size());
        assertEquals(1001, map.get("errorCode"));
        assertEquals("Test message", map.get("message"));
        assertEquals("Test details", map.get("details"));
        assertNotNull(map.get("timestamp"));
    }
    
    @Test
    @Order(8)
    @DisplayName("Should not include null details in map")
    void testErrorResponseToMapWithNullDetails() {
        ExceptionMapper.ErrorResponse errorResponse = new ExceptionMapper.ErrorResponse();
        errorResponse.errorCode = 1001;
        errorResponse.message = "Test message";
        errorResponse.details = null;
        errorResponse.timestamp = LocalDateTime.now().toString();
        
        Map<String, Object> map = errorResponse.toMap();
        
        assertEquals(3, map.size());
        assertFalse(map.containsKey("details"));
    }
    
    @Test
    @Order(9)
    @DisplayName("Should handle authentication errors correctly")
    void testAuthenticationErrorMapping() {
        VulnPatcherException authException = new VulnPatcherException(
            VulnPatcherException.ErrorCode.AUTH_INVALID_TOKEN,
            "Invalid authentication token"
        );
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapVulnPatcherException(authException);
        
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertEquals(2002, response.getEntity().errorCode);
    }
    
    @Test
    @Order(10)
    @DisplayName("Should handle permission errors correctly")
    void testPermissionErrorMapping() {
        VulnPatcherException permException = new VulnPatcherException(
            VulnPatcherException.ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS,
            "Insufficient permissions"
        );
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapVulnPatcherException(permException);
        
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals(2003, response.getEntity().errorCode);
    }
    
    @Test
    @Order(11)
    @DisplayName("Should handle timeout errors correctly")
    void testTimeoutErrorMapping() {
        VulnPatcherException timeoutException = new VulnPatcherException(
            VulnPatcherException.ErrorCode.SCAN_TIMEOUT,
            "Scan operation timed out"
        );
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapVulnPatcherException(timeoutException);
        
        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), response.getStatus());
        assertEquals(4002, response.getEntity().errorCode);
    }
    
    @Test
    @Order(12)
    @DisplayName("Should preserve exception cause information")
    void testExceptionWithCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        VulnPatcherException exception = new VulnPatcherException(
            VulnPatcherException.ErrorCode.EXTERNAL_SERVICE_ERROR,
            "External service failed",
            "Service: GitHub API",
            cause
        );
        
        RestResponse<ExceptionMapper.ErrorResponse> response = 
            exceptionMapper.mapVulnPatcherException(exception);
        
        assertNotNull(response);
        ExceptionMapper.ErrorResponse errorResponse = response.getEntity();
        assertEquals("External service failed", errorResponse.message);
        assertEquals("Service: GitHub API", errorResponse.details);
    }
    
    // Helper methods
    
    private int getExpectedStatusForErrorCode(VulnPatcherException.ErrorCode errorCode) {
        return switch (errorCode) {
            case CONFIG_INVALID, CONFIG_MISSING, INVALID_REQUEST -> 400;
            case AUTH_FAILED, AUTH_INVALID_TOKEN -> 401;
            case AUTH_INSUFFICIENT_PERMISSIONS, REPO_ACCESS_DENIED -> 403;
            case REPO_NOT_FOUND -> 404;
            case SCAN_TIMEOUT, EXTERNAL_SERVICE_TIMEOUT -> 408;
            default -> 500;
        };
    }
    
    private ConstraintViolation<?> createMockViolation(String propertyPath, String message) {
        return new ConstraintViolation<Object>() {
            @Override
            public String getMessage() {
                return message;
            }
            
            @Override
            public String getMessageTemplate() {
                return "{" + message + "}";
            }
            
            @Override
            public Object getRootBean() {
                return null;
            }
            
            @Override
            public Class<Object> getRootBeanClass() {
                return Object.class;
            }
            
            @Override
            public Object getLeafBean() {
                return null;
            }
            
            @Override
            public Object[] getExecutableParameters() {
                return new Object[0];
            }
            
            @Override
            public Object getExecutableReturnValue() {
                return null;
            }
            
            @Override
            public jakarta.validation.Path getPropertyPath() {
                return new jakarta.validation.Path() {
                    @Override
                    public Iterator<Node> iterator() {
                        return null;
                    }
                    
                    @Override
                    public String toString() {
                        return propertyPath;
                    }
                };
            }
            
            @Override
            public Object getInvalidValue() {
                return null;
            }
            
            @Override
            public jakarta.validation.ConstraintDescriptor<?> getConstraintDescriptor() {
                return null;
            }
            
            @Override
            public <U> U unwrap(Class<U> type) {
                return null;
            }
        };
    }
    
    // Test REST endpoints for integration testing
    @Path("/test")
    public static class TestExceptionResource {
        
        @GET
        @Path("/vulnpatcher-exception")
        public Response throwVulnPatcherException() {
            throw new VulnPatcherException(
                VulnPatcherException.ErrorCode.REPO_NOT_FOUND,
                "Repository not found"
            );
        }
        
        @GET
        @Path("/validation-exception")
        public Response throwValidationException() {
            Set<ConstraintViolation<?>> violations = new HashSet<>();
            throw new ConstraintViolationException(violations);
        }
        
        @GET
        @Path("/generic-exception")
        public Response throwGenericException() {
            throw new RuntimeException("Generic error");
        }
    }
}