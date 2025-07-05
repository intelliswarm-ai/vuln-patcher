package ai.intelliswarm.vulnpatcher;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.logging.Logger;

@QuarkusTest
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseTest {
    
    protected static final Logger LOGGER = Logger.getLogger(BaseTest.class.getName());
    
    @BeforeEach
    public void setUp(TestInfo testInfo) {
        LOGGER.info("Starting test: " + testInfo.getDisplayName());
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    protected void logTestCompletion(TestInfo testInfo) {
        LOGGER.info("Completed test: " + testInfo.getDisplayName());
    }
}