package ai.intelliswarm.vulnpatcher;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class VulnPatcherApplication {
    
    public static void main(String... args) {
        System.out.println("Starting VulnPatcher Application...");
        Quarkus.run(args);
    }
}