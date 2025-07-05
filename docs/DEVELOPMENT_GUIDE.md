# VulnPatcher Development Guide

## Table of Contents

1. [Development Environment Setup](#development-environment-setup)
2. [Architecture Overview](#architecture-overview)
3. [Project Structure](#project-structure)
4. [Core Components](#core-components)
5. [Adding New Features](#adding-new-features)
6. [Testing Guidelines](#testing-guidelines)
7. [Code Style & Standards](#code-style--standards)
8. [Debugging Tips](#debugging-tips)
9. [Performance Optimization](#performance-optimization)
10. [Contributing](#contributing)

## Development Environment Setup

### Prerequisites

1. **Java Development Kit (JDK) 17+**
   ```bash
   # Verify installation
   java -version
   javac -version
   ```

2. **Maven 3.8+**
   ```bash
   # Verify installation
   mvn -version
   ```

3. **Ollama**
   ```bash
   # Install Ollama
   curl -fsSL https://ollama.ai/install.sh | sh
   
   # Start Ollama service
   ollama serve
   
   # Pull required models
   ollama pull deepseek-coder:33b
   ollama pull mixtral:8x7b
   ollama pull codellama:34b
   ```

4. **IDE Setup**
   - IntelliJ IDEA (recommended)
   - Visual Studio Code with Java extensions
   - Eclipse with Quarkus tools

### IDE Configuration

#### IntelliJ IDEA

1. Install Quarkus plugin
2. Enable annotation processing
3. Configure code style:
   ```
   File -> Settings -> Editor -> Code Style -> Java
   Import: config/intellij-java-style.xml
   ```

#### VS Code

1. Install extensions:
   - Java Extension Pack
   - Quarkus
   - Language Support for Java

2. Settings.json:
   ```json
   {
     "java.configuration.updateBuildConfiguration": "automatic",
     "java.server.launchMode": "Standard",
     "java.compile.nullAnalysis.mode": "automatic",
     "quarkus.tools.enable": true
   }
   ```

### Local Development Setup

1. **Clone Repository**
   ```bash
   git clone https://github.com/intelliswarm/vuln-patcher.git
   cd vuln-patcher
   ```

2. **Set Environment Variables**
   ```bash
   # Create .env file
   cat > .env << EOF
   # Git Providers
   GITHUB_TOKEN=your_github_token
   GITLAB_TOKEN=your_gitlab_token
   BITBUCKET_USERNAME=your_bitbucket_username
   BITBUCKET_APP_PASSWORD=your_app_password
   
   # Vulnerability Databases
   CVE_API_KEY=your_cve_api_key
   SNYK_TOKEN=your_snyk_token
   
   # Ollama
   OLLAMA_BASE_URL=http://localhost:11434
   
   # Development
   QUARKUS_PROFILE=dev
   EOF
   
   # Source environment
   source .env
   ```

3. **Build Project**
   ```bash
   mvn clean install
   ```

4. **Run in Dev Mode**
   ```bash
   mvn quarkus:dev
   ```

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    VulnPatcher System                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │ REST API     │  │ Git Providers │  │ Vuln Sources    │  │
│  │ (Reactive)   │  │ (Multi-Repo)  │  │ (Multi-Source)  │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬────────┘  │
│         │                  │                    │           │
│  ┌──────▼──────────────────▼───────────────────▼────────┐  │
│  │          Core Vulnerability Detection Engine          │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐ │  │
│  │  │ Scanner     │  │ Analyzer     │  │ Matcher     │ │  │
│  │  └─────────────┘  └──────────────┘  └─────────────┘ │  │
│  └───────────────────────────┬──────────────────────────┘  │
│                              │                              │
│  ┌───────────────────────────▼──────────────────────────┐  │
│  │           AI-Powered Fix Generation                   │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐ │  │
│  │  │ LLM Orch.   │  │ Multi-Agent  │  │ Validators  │ │  │
│  │  │ (LangChain) │  │ (MCP-based)  │  │             │ │  │
│  │  └─────────────┘  └──────────────┘  └─────────────┘ │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack

- **Framework**: Quarkus 3.9.0 with Spring compatibility
- **AI/LLM**: Quarkus LangChain4j integration
- **Reactive**: Mutiny and RESTEasy Reactive
- **Database**: PostgreSQL with Panache
- **Cache**: Redis
- **Monitoring**: Micrometer + Prometheus
- **Testing**: JUnit 5, RestAssured, Mockito

## Project Structure

```
vuln-patcher/
├── src/
│   ├── main/
│   │   ├── java/ai/intelliswarm/vulnpatcher/
│   │   │   ├── agents/              # AI agents (Security, SecLead, Expert)
│   │   │   ├── api/                 # REST endpoints
│   │   │   │   └── v1/              # API version 1
│   │   │   ├── config/              # Configuration classes
│   │   │   ├── core/                # Core business logic
│   │   │   ├── exceptions/          # Custom exceptions
│   │   │   ├── fixes/               # Fix generators by language
│   │   │   ├── git/                 # Git provider integrations
│   │   │   ├── health/              # Health checks
│   │   │   ├── interceptors/        # AOP interceptors
│   │   │   ├── matchers/            # Vulnerability matchers
│   │   │   ├── models/              # Domain models
│   │   │   ├── orchestrator/        # LLM workflow orchestration
│   │   │   ├── services/            # Business services
│   │   │   └── sources/             # Vulnerability data sources
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── META-INF/
│   │       └── db/migration/        # Flyway migrations
│   └── test/
│       ├── java/                    # Test classes
│       └── resources/               # Test resources
├── docs/                           # Documentation
├── config/                         # External configurations
├── scripts/                        # Utility scripts
├── pom.xml                        # Maven configuration
└── README.md
```

### Key Packages

1. **agents**: Multi-agent AI system
   - `Agent.java` - Base agent interface
   - `SecurityEngineerAgent.java` - Code generation
   - `SecLeadReviewerAgent.java` - Code review
   - `SecurityExpertReviewerAgent.java` - Security validation

2. **core**: Core detection and analysis
   - `VulnerabilityDetectionEngine.java` - Main engine
   - `ContextManager.java` - LLM context management
   - `StreamingCodeAnalyzer.java` - Large repo handling

3. **fixes**: Language-specific fix generators
   - `AbstractFixGenerator.java` - Base class
   - `JavaFixGenerator.java` - Java fixes
   - `PythonFixGenerator.java` - Python fixes
   - etc.

4. **orchestrator**: Workflow management
   - `LLMOrchestrator.java` - Agent coordination
   - `WorkflowEvent.java` - Event system

## Core Components

### 1. Vulnerability Detection Engine

```java
@ApplicationScoped
public class VulnerabilityDetectionEngine {
    
    @Inject
    List<VulnerabilitySource> sources;
    
    @Inject
    List<VulnerabilityMatcher> matchers;
    
    @Inject
    ContextManager contextManager;
    
    public Uni<ScanResult> scanRepository(ScanRequest request) {
        return Multi.createFrom().iterable(sources)
            .onItem().transformToUniAndConcatenate(source -> 
                source.fetchVulnerabilities()
                    .onFailure().recoverWithNull()
            )
            .collect().asList()
            .flatMap(vulnerabilities -> 
                analyzeCode(request, vulnerabilities)
            );
    }
}
```

### 2. AI Agent System

```java
@ApplicationScoped
public class SecurityEngineerAgent implements Agent {
    
    @Inject
    @CodeGeneration
    ChatLanguageModel model;
    
    @Override
    public Uni<AgentResponse> process(AgentRequest request) {
        String prompt = buildPrompt(request);
        
        return Uni.createFrom().item(() -> 
            model.generate(prompt))
            .map(response -> new AgentResponse(
                getName(),
                response,
                calculateConfidence(response)
            ));
    }
    
    private String buildPrompt(AgentRequest request) {
        return String.format("""
            You are a senior security engineer. 
            Analyze the following vulnerability and generate a secure fix:
            
            Vulnerability: %s
            Code: %s
            Language: %s
            
            Requirements:
            1. Fix must be secure and follow best practices
            2. Preserve existing functionality
            3. Include explanation
            """, 
            request.getVulnerability(),
            request.getCode(),
            request.getLanguage()
        );
    }
}
```

### 3. Context Management

```java
@ApplicationScoped
public class ContextManager {
    
    private static final int CHUNK_SIZE = 1500;
    private static final int OVERLAP = 200;
    
    @Inject
    EmbeddingModel embeddingModel;
    
    @Inject
    EmbeddingStore<TextSegment> embeddingStore;
    
    public List<String> getRelevantContext(String query, String sessionId) {
        // Generate embedding for query
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        
        // Search for relevant segments
        List<EmbeddingMatch<TextSegment>> matches = 
            embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .minScore(0.7)
                .filter(metadata -> metadata.getString("session").equals(sessionId))
                .build()
            ).matches();
        
        return matches.stream()
            .map(match -> match.embedded().text())
            .collect(Collectors.toList());
    }
}
```

### 4. Fix Generators

```java
public abstract class AbstractFixGenerator {
    
    @Inject
    LLMOrchestrator orchestrator;
    
    public Uni<Fix> generateFix(Vulnerability vulnerability, CodeContext context) {
        return orchestrator.runWorkflow(
            WorkflowRequest.builder()
                .type(WorkflowType.VULNERABILITY_FIX)
                .vulnerability(vulnerability)
                .context(context)
                .agents(Arrays.asList(
                    "SecurityEngineerAgent",
                    "SecLeadReviewerAgent",
                    "SecurityExpertReviewerAgent"
                ))
                .build()
        ).map(this::processFix);
    }
    
    protected abstract String getLanguageName();
    protected abstract Fix processFix(WorkflowResult result);
}
```

## Adding New Features

### Adding a New Vulnerability Source

1. **Create Source Implementation**
   ```java
   @ApplicationScoped
   public class NewVulnerabilitySource implements VulnerabilitySource {
       
       @ConfigProperty(name = "vulnpatcher.sources.new.api-key")
       String apiKey;
       
       @Inject
       @RestClient
       NewVulnClient client;
       
       @Override
       public Uni<List<Vulnerability>> fetchVulnerabilities() {
           return client.getVulnerabilities(apiKey)
               .map(this::mapToVulnerabilities);
       }
       
       @Override
       public String getSourceName() {
           return "NewSource";
       }
   }
   ```

2. **Add REST Client**
   ```java
   @Path("/api")
   @RegisterRestClient(configKey = "new-vuln-api")
   public interface NewVulnClient {
       
       @GET
       @Path("/vulnerabilities")
       Uni<List<NewVulnDto>> getVulnerabilities(@HeaderParam("API-Key") String apiKey);
   }
   ```

3. **Configure in application.properties**
   ```properties
   vulnpatcher.sources.new.enabled=true
   vulnpatcher.sources.new.api-key=${NEW_VULN_API_KEY}
   quarkus.rest-client.new-vuln-api.url=https://api.newvuln.com
   ```

### Adding a New Language Support

1. **Create Fix Generator**
   ```java
   @ApplicationScoped
   public class GoFixGenerator extends AbstractFixGenerator {
       
       @Override
       protected String getLanguageName() {
           return "Go";
       }
       
       @Override
       public boolean canHandle(String language) {
           return "go".equalsIgnoreCase(language) || 
                  "golang".equalsIgnoreCase(language);
       }
       
       @Override
       protected Fix processFix(WorkflowResult result) {
           // Go-specific fix processing
           return Fix.builder()
               .language("Go")
               .code(result.getCode())
               .description(result.getDescription())
               .patch(generateGoPatch(result))
               .build();
       }
       
       private String generateGoPatch(WorkflowResult result) {
           // Generate unified diff for Go code
           return "";
       }
   }
   ```

2. **Add Language-Specific Matchers**
   ```java
   @ApplicationScoped
   public class GoVulnerabilityMatcher extends AbstractVulnerabilityMatcher {
       
       @Override
       public List<VulnerabilityMatch> match(String code, Vulnerability vuln) {
           // Go-specific vulnerability matching logic
           return new ArrayList<>();
       }
   }
   ```

### Adding a New AI Agent

1. **Create Agent Implementation**
   ```java
   @ApplicationScoped
   public class PerformanceReviewerAgent implements Agent {
       
       @Inject
       @CodeReview
       ChatLanguageModel model;
       
       @Override
       public String getName() {
           return "PerformanceReviewer";
       }
       
       @Override
       public Uni<AgentResponse> process(AgentRequest request) {
           // Review fix for performance implications
           String prompt = buildPerformancePrompt(request);
           return Uni.createFrom().item(() -> 
               model.generate(prompt))
               .map(response -> new AgentResponse(
                   getName(),
                   response,
                   0.8
               ));
       }
   }
   ```

2. **Register in Workflow**
   ```java
   // In LLMOrchestrator or workflow configuration
   agents.add(new PerformanceReviewerAgent());
   ```

## Testing Guidelines

### Unit Testing

```java
@QuarkusTest
class VulnerabilityDetectionEngineTest {
    
    @Inject
    VulnerabilityDetectionEngine engine;
    
    @InjectMock
    VulnerabilitySource mockSource;
    
    @Test
    void testScanRepository() {
        // Given
        List<Vulnerability> vulns = Arrays.asList(
            createTestVulnerability("CVE-2021-44228", "CRITICAL")
        );
        when(mockSource.fetchVulnerabilities())
            .thenReturn(Uni.createFrom().item(vulns));
        
        // When
        ScanResult result = engine.scanRepository(
            new ScanRequest("https://github.com/test/repo", "main")
        ).await().atMost(Duration.ofSeconds(30));
        
        // Then
        assertThat(result.getVulnerabilitiesFound()).isEqualTo(1);
        assertThat(result.getVulnerabilities())
            .extracting(Vulnerability::getId)
            .containsExactly("CVE-2021-44228");
    }
}
```

### Integration Testing

```java
@QuarkusIntegrationTest
class VulnPatcherResourceIT {
    
    @Test
    void testFullScanWorkflow() {
        // Start scan
        Response scanResponse = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("""
                {
                    "repositoryUrl": "https://github.com/test/vulnerable-app",
                    "branch": "main",
                    "severityThreshold": "HIGH"
                }
                """)
            .when()
            .post("/api/v1/vulnpatcher/scan")
            .then()
            .statusCode(202)
            .extract().response();
        
        String scanId = scanResponse.jsonPath().getString("scanId");
        
        // Poll for completion
        await().atMost(5, TimeUnit.MINUTES).until(() -> {
            String status = given()
                .header("X-API-Key", "test-key")
                .get("/api/v1/vulnpatcher/scan/" + scanId)
                .jsonPath()
                .getString("status");
            return "completed".equals(status);
        });
        
        // Verify results
        given()
            .header("X-API-Key", "test-key")
            .get("/api/v1/vulnpatcher/scan/" + scanId)
            .then()
            .statusCode(200)
            .body("vulnerabilitiesFound", greaterThan(0));
    }
}
```

### Testing Best Practices

1. **Use Test Containers for External Dependencies**
   ```java
   @QuarkusTest
   @QuarkusTestResource(PostgreSQLTestResource.class)
   @QuarkusTestResource(RedisTestResource.class)
   class DatabaseIntegrationTest {
       // Tests with real database
   }
   ```

2. **Mock External Services**
   ```java
   @QuarkusTest
   class GitHubServiceTest {
       
       @InjectMock
       @RestClient
       GitHubClient githubClient;
       
       @Test
       void testRepositoryScan() {
           when(githubClient.getRepository(anyString(), anyString()))
               .thenReturn(Uni.createFrom().item(mockRepo()));
           // Test logic
       }
   }
   ```

3. **Test Coverage Requirements**
   - Line coverage: >98%
   - Branch coverage: >95%
   - Mutation coverage: >80%

## Code Style & Standards

### Java Code Style

1. **Naming Conventions**
   - Classes: PascalCase
   - Methods/Variables: camelCase
   - Constants: UPPER_SNAKE_CASE
   - Packages: lowercase

2. **Code Organization**
   ```java
   public class ExampleService {
       // 1. Static fields
       private static final Logger LOGGER = LoggerFactory.getLogger(ExampleService.class);
       
       // 2. Instance fields
       private final Repository repository;
       
       // 3. Constructors
       @Inject
       public ExampleService(Repository repository) {
           this.repository = repository;
       }
       
       // 4. Public methods
       public void publicMethod() {
           // Implementation
       }
       
       // 5. Private methods
       private void helperMethod() {
           // Implementation
       }
       
       // 6. Inner classes
       private static class InnerClass {
           // Implementation
       }
   }
   ```

3. **Annotations**
   ```java
   @ApplicationScoped  // CDI scope
   @Transactional     // Transaction boundary
   @Timed             // Metrics
   @Retry             // Fault tolerance
   @Timeout(30000)    // Timeout handling
   public class ResilientService {
       // Implementation
   }
   ```

### Quarkus Best Practices

1. **Use Reactive Programming**
   ```java
   // Good - Reactive
   public Uni<List<Item>> getItems() {
       return Multi.createFrom().items(1, 2, 3, 4, 5)
           .onItem().transformToUniAndConcatenate(id -> 
               fetchItem(id)
           )
           .collect().asList();
   }
   
   // Avoid - Blocking
   public List<Item> getItemsBlocking() {
       return ids.stream()
           .map(this::fetchItemBlocking)
           .collect(Collectors.toList());
   }
   ```

2. **Configuration**
   ```java
   @ConfigMapping(prefix = "vulnpatcher")
   public interface VulnPatcherConfig {
       
       @WithName("ai")
       AiConfig ai();
       
       interface AiConfig {
           @WithDefault("http://localhost:11434")
           String ollamaUrl();
           
           @WithDefault("30s")
           Duration timeout();
       }
   }
   ```

3. **Health Checks**
   ```java
   @Liveness
   public class CustomLivenessCheck implements HealthCheck {
       
       @Override
       public HealthCheckResponse call() {
           return HealthCheckResponse.up("custom-check");
       }
   }
   ```

## Debugging Tips

### 1. Enable Debug Logging

```properties
# In application.properties
quarkus.log.level=INFO
quarkus.log.category."ai.intelliswarm.vulnpatcher".level=DEBUG
quarkus.log.category."dev.langchain4j".level=DEBUG

# Or via environment variable
export QUARKUS_LOG_CATEGORY__AI_INTELLISWARM_VULNPATCHER__LEVEL=DEBUG
```

### 2. Remote Debugging

```bash
# Start with debug enabled
mvn quarkus:dev -Ddebug=5005

# Or in production
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/quarkus-app/quarkus-run.jar
```

### 3. Dev UI Tools

Access at http://localhost:8080/q/dev

- Configuration Editor
- Health UI
- Cache Inspector
- Scheduler Inspector
- REST Client Inspector

### 4. Performance Profiling

```java
@Inject
MeterRegistry registry;

public void profileMethod() {
    Timer.Sample sample = Timer.start(registry);
    try {
        // Method logic
    } finally {
        sample.stop(Timer.builder("method.execution.time")
            .tag("method", "profileMethod")
            .register(registry));
    }
}
```

### 5. Heap Dumps

```bash
# Generate heap dump
jcmd <pid> GC.heap_dump heap.hprof

# Analyze with Eclipse MAT or VisualVM
```

## Performance Optimization

### 1. Database Query Optimization

```java
// Use projections for large datasets
@Entity
public class Repository {
    // Full entity
}

@Entity
@Subselect("SELECT id, url, branch FROM repository")
public class RepositoryLight {
    // Lightweight projection
}

// Use native queries for complex operations
@Query(value = """
    SELECT v.* FROM vulnerability v
    JOIN vulnerability_match vm ON v.id = vm.vulnerability_id
    WHERE vm.repository_id = ?1
    AND v.severity >= ?2
    ORDER BY v.cvss_score DESC
    LIMIT 100
    """, nativeQuery = true)
List<Vulnerability> findTopVulnerabilities(Long repoId, String severity);
```

### 2. Caching Strategy

```java
@ApplicationScoped
public class VulnerabilityCache {
    
    @Inject
    @RedisClient
    RedisDataSource redis;
    
    @CacheResult(cacheName = "vulnerabilities")
    public Uni<Vulnerability> getVulnerability(String id) {
        return fetchFromDatabase(id);
    }
    
    @CacheInvalidate(cacheName = "vulnerabilities")
    public Uni<Void> invalidate(String id) {
        return Uni.createFrom().voidItem();
    }
    
    @Scheduled(every = "1h")
    void refreshCache() {
        // Refresh strategy
    }
}
```

### 3. Concurrent Processing

```java
@ApplicationScoped
public class ConcurrentScanner {
    
    private static final int PARALLELISM = 10;
    
    public Multi<ScanResult> scanFiles(List<String> files) {
        return Multi.createFrom().iterable(files)
            .onItem().transformToUniAndConcatenate(file ->
                scanFile(file)
                    .onFailure().recoverWithItem(ScanResult.empty())
            )
            .withLimit(PARALLELISM);
    }
}
```

### 4. Memory Management

```java
// Use streaming for large datasets
public Multi<Vulnerability> streamVulnerabilities() {
    return Multi.createFrom().publisher(
        session.stream(
            "SELECT v FROM Vulnerability v",
            Vulnerability.class
        )
    );
}

// Clear context after processing
@RequestScoped
public class RequestContext {
    
    @PreDestroy
    void cleanup() {
        // Clear any request-specific caches
        contextCache.clear();
    }
}
```

## Contributing

### Development Workflow

1. **Create Feature Branch**
   ```bash
   git checkout -b feature/your-feature
   ```

2. **Make Changes**
   - Write code
   - Add tests
   - Update documentation

3. **Run Tests**
   ```bash
   mvn clean test
   mvn verify  # Includes integration tests
   ```

4. **Code Quality Checks**
   ```bash
   # Format code
   mvn spotless:apply
   
   # Static analysis
   mvn spotbugs:check
   
   # Coverage
   mvn jacoco:report
   ```

5. **Commit**
   ```bash
   git commit -m "feat: add new vulnerability source for X"
   ```

### Pull Request Guidelines

1. **PR Title Format**
   - feat: New feature
   - fix: Bug fix
   - docs: Documentation
   - test: Tests
   - refactor: Code refactoring

2. **PR Description Template**
   ```markdown
   ## Description
   Brief description of changes
   
   ## Type of Change
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Breaking change
   - [ ] Documentation update
   
   ## Testing
   - [ ] Unit tests pass
   - [ ] Integration tests pass
   - [ ] Manual testing completed
   
   ## Checklist
   - [ ] Code follows style guidelines
   - [ ] Self-review completed
   - [ ] Documentation updated
   - [ ] No new warnings
   ```

### Code Review Process

1. All PRs require at least one approval
2. CI/CD must pass
3. Code coverage must not decrease
4. No critical security issues

---

Last Updated: January 2024
Version: 1.0.0