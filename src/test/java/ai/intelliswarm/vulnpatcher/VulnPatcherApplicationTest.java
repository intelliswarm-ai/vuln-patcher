package ai.intelliswarm.vulnpatcher;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class VulnPatcherApplicationTest {

    @Test
    public void testApplicationStarts() {
        // This test simply verifies that the Quarkus application starts without errors
        // If we get here, the application started successfully
        assertTrue(true, "Application started successfully");
    }
}