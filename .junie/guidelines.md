Project: speakToMack (Spring Boot 3.5, Java 21, Gradle)

Scope
- This document captures project-specific guidance for building, testing, and developing this codebase. It assumes an experienced Java/Spring developer and focuses on repository-specific details and gotchas.

Build and Configuration
- Toolchain: Java 21. The Gradle build configures the Java toolchain, so using the provided Gradle Wrapper is recommended to avoid local JDK mismatch.
  - Command examples:
    - Unix/macOS: ./gradlew clean build
    - Windows (PowerShell/CMD): gradlew.bat clean build
- Key build plugins and versions:
  - org.springframework.boot 3.5.6
  - io.spring.dependency-management 1.1.7
  - Spring Modulith BOM 1.4.3 for modulith dependencies
- Dependency highlights (build.gradle):
  - implementation: spring-boot-starter-web, spring-modulith-starter-core
  - testImplementation: spring-boot-starter-test, spring-boot-testcontainers, spring-modulith-starter-test, testcontainers junit-jupiter
  - testRuntimeOnly: junit-platform-launcher
- Running the application:
  - Standard app main: com.phillippitts.speaktomack.SpeakToMackApplication
  - Test profile bootstrap (optional): TestSpeakToMackApplication (in test sources) shows how to start the app with test-specific configuration classes.
- Configuration:
  - src/main/resources/application.properties is currently minimal; add environment-specific properties via Spring profiles as needed (e.g., application-dev.properties). No externalized configuration is required for current tests.

Testing
- Frameworks: JUnit 5 (Jupiter), Spring Boot test starter, Spring Modulith test support, Testcontainers (available via Spring Boot’s Testcontainers integration). Tests use JUnit Platform (Gradle test task is already configured with useJUnitPlatform()).
- Running tests:
  - All tests: ./gradlew test
  - Single class: ./gradlew test --tests com.phillippitts.speaktomack.SpeakToMackApplicationTests
  - Single method: ./gradlew test --tests com.phillippitts.speaktomack.SpeakToMackApplicationTests.contextLoads
- Observed runtime note (Java 21 + Mockito):
  - You may see a warning: "Mockito is currently self-attaching to enable the inline-mock-maker..." along with Byte Buddy agent messages. Tests still pass. If this becomes intrusive or blocks CI in future JDKs, consider:
    - Enabling dynamic agent loading for tests: tasks.test.jvmArgs("-XX:+EnableDynamicAgentLoading")
    - Or using a dedicated Mockito agent per Mockito docs, or depending on mockito-inline if needed.
- Testcontainers:
  - Testcontainers is configured through Spring Boot's dev-services integration, but no specific containers are declared at the moment. Current tests pass without Docker running. If you add container-backed integrations (e.g., PostgreSQL, Redis), ensure Docker is available during test execution.
- Spring Boot test wiring present in repo:
  - SpeakToMackApplicationTests is annotated with @SpringBootTest and @Import(TestcontainersConfiguration.class) and validates that the context loads.
  - TestSpeakToMackApplication demonstrates starting the application using SpringApplication.from(...) with a test configuration class.
- Adding a new test (example):
  - Create a JUnit 5 test under src/test/java within the same base package:
    package com.phillippitts.speaktomack;

    import org.junit.jupiter.api.Test;
    import static org.junit.jupiter.api.Assertions.assertEquals;

    class SampleSanityTest {
        @Test
        void addsNumbers() {
            assertEquals(5, 2 + 3);
        }
    }
  - Run it: ./gradlew test --tests com.phillippitts.speaktomack.SampleSanityTest
  - Verified locally: a similar sample test passed.

Guidelines for Adding and Executing New Tests
- Keep test classes in package com.phillippitts.speaktomack to leverage component scanning defaults.
- Prefer @SpringBootTest for integration/context tests. For lighter tests, prefer plain JUnit tests or Spring slice tests (e.g., @WebMvcTest) to reduce boot time.
- If using Spring Modulith testing features:
  - The modulith test dependency is available. Consider @ApplicationModuleTest and Module canvas tests when you begin modularizing features. Align module boundaries with packages and verify dependencies via modulith tooling.
- Use @Import(TestcontainersConfiguration.class) (or a profile-specific @TestConfiguration) when you need to wire test-only beans or container configs.
- When adding Testcontainers-managed services:
  - Prefer Spring Boot’s testcontainers support (spring-boot-testcontainers) with service connection annotations (@ServiceConnection) where applicable.
  - Ensure Docker is available for CI/test runs; otherwise, guard container-dependent tests behind a profile or condition (e.g., @EnabledIfEnvironmentVariable) to avoid failing local runs without Docker.

Additional Development Notes
- Java version is enforced via Gradle toolchain (Java 21). IDEs should be configured to use JDK 21 for proper language level and to avoid annotation processing or preview feature mismatches.
- Code style: Follow conventional Spring formatting. If you prefer an automated formatter, Google Java Style with spotless or a similar plugin is fine; none is enforced yet.
- Packaging and base package: com.phillippitts.speaktomack. Keep new components within or under this base package to be discovered by component scanning.
- Dependency management: Prefer managed versions via the Spring Boot plugin and the Spring Modulith BOM (already imported). Avoid hardcoding versions for Spring artifacts unless necessary.
- Running the app in dev: ./gradlew bootRun (optional; added by Spring Boot plugin). For integration testing using the real app, use TestSpeakToMackApplication pattern to customize the context.
- CI considerations:
  - Use the Gradle Wrapper for reproducibility.
  - If enabling Testcontainers with real services, ensure the CI agent has Docker, or use Testcontainers’ Docker alternatives (e.g., Docker Desktop, Colima, or TC Cloud if adopted).
  - Address the Mockito agent warning as noted above to future-proof JDK updates.

Troubleshooting
- If tests fail to discover JUnit 5 tests, confirm useJUnitPlatform() is active (it is in build.gradle) and that testRuntimeOnly junit-platform-launcher is present (it is).
- If context fails to load in @SpringBootTest, check that new configuration classes are in the base package or are imported explicitly.
- If you see container startup failures after adding containerized dependencies, validate Docker availability and image tags; adjust Testcontainers image tags to match production where relevant.

---

## Architecture & Design Patterns

### 3-Tier Architecture (Mandatory Structure)

The speakToMack project follows Spring's 3-tier architecture pattern:

**Package Structure:**
```
src/main/java/com/phillippitts/speaktomack/
├── presentation/          # Tier 1: Controllers, DTOs, exception handlers
│   ├── controller/
│   ├── dto/
│   └── exception/
├── service/              # Tier 2: Business logic, orchestration
│   ├── stt/             # STT engine implementations
│   └── reconciliation/   # Reconciliation strategies
├── repository/           # Tier 3: Data access (JPA repositories)
├── domain/              # Domain entities (shared across tiers)
└── config/              # Spring configuration classes
```

**Layer Responsibilities:**
- **Presentation Layer**: Handle HTTP requests/responses, input validation, data transfer
  - Components: `@RestController`, DTOs
  - Rule: No business logic in controllers
  
- **Service Layer**: Core business logic, orchestration, processing rules
  - Components: `@Service`, `@Component`
  - Rule: No HTTP concerns in services
  
- **Data Access Layer**: Database operations, external data sources, caching
  - Components: `@Repository`, JPA entities
  - Rule: No business logic in repositories

**Dependency Flow:** Presentation → Service → Data Access (never reverse)

### Core Design Patterns

#### Strategy Pattern (High Priority)
**Use Case:** Reconciliation strategies, STT engine selection

```java
// Interface
public interface TranscriptReconciler {
    TranscriptionResult reconcile(SttResult vosk, SttResult whisper);
}

// Implementations: SimplePreferenceReconciler, WordOverlapReconciler, 
// DiffMergeReconciler, ConfidenceReconciler, WeightedVotingReconciler

// Configuration-driven selection
@Bean
@ConditionalOnProperty(name = "stt.reconciliation.strategy", havingValue = "simple")
public TranscriptReconciler simpleReconciler() {
    return new SimplePreferenceReconciler();
}
```

#### Factory Pattern (High Priority)
**Use Case:** Create STT engines and hotkey triggers from configuration

```java
@Component
public class SttEngineFactory {
    public SttEngine createEngine(String type, EngineConfig config) {
        return switch (type.toLowerCase()) {
            case "vosk" -> new VoskSttEngine(config);
            case "whisper" -> new WhisperSttEngine(config);
            default -> throw new IllegalArgumentException("Unknown engine: " + type);
        };
    }
}
```

#### Adapter Pattern (High Priority)
**Use Case:** Wrap native libraries (Vosk JNI, Whisper binary) with clean Java interfaces

```java
public interface SttEngine extends AutoCloseable {
    void start();
    void acceptAudio(byte[] pcm16le, int offset, int length);
    String finalizeResult();
}

// VoskSttEngine adapts Vosk JNI library to SttEngine interface
// WhisperSttEngine adapts whisper.cpp process to SttEngine interface
```

#### Observer/Event-Driven Pattern (High Priority)
**Use Case:** Decouple hotkey events from business logic

```java
// Events
public record HotkeyPressedEvent(Instant timestamp) {}
public record TranscriptionCompletedEvent(TranscriptionResult result) {}

// Listener
@EventListener
public void onHotkeyPressed(HotkeyPressedEvent event) {
    audioService.start();
}
```

#### Other Patterns (Future Phases)
- **Template Method**: Common STT engine lifecycle
- **Decorator**: Logging, metrics, caching
- **Circuit Breaker**: Production resilience (Resilience4j)
- **State**: Dictation session lifecycle
- **Command**: Voice commands (future)
- **Chain of Responsibility**: Audio processing pipeline

---

## Model Setup Strategy

### Recommended Approach: Manual Setup Script

**Decision:** Use manual download script with validation (not Git LFS, not auto-download)

**Rationale:**
- ✅ Fast repository clones (~5 MB without models)
- ✅ Clear error messages guide developers
- ✅ CI-friendly with caching
- ✅ Works offline after initial setup
- ✅ Models not committed to repository

**Setup Script:** `setup-models.sh`

```bash
#!/bin/bash
set -e

MODELS_DIR="./models"
mkdir -p "$MODELS_DIR"

echo "📥 Downloading Vosk model (50 MB)..."
curl -L "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip" \
    -o "$MODELS_DIR/vosk.zip"
unzip -q "$MODELS_DIR/vosk.zip" -d "$MODELS_DIR"
rm "$MODELS_DIR/vosk.zip"

echo "📥 Downloading Whisper model (150 MB)..."
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin" \
    -o "$MODELS_DIR/ggml-base.en.bin"

echo "✅ Models ready in $MODELS_DIR"
echo ""
echo "Directory structure:"
ls -lh "$MODELS_DIR"
```

**Usage:**
```bash
chmod +x setup-models.sh
./setup-models.sh
```

**Model Validation (Fail Fast):**

Add to Spring configuration:
```java
@Configuration
public class SttConfig {
    @Value("${stt.vosk.model-path}")
    private String voskModelPath;
    
    @PostConstruct
    public void validateModels() {
        if (!Files.exists(Paths.get(voskModelPath))) {
            throw new IllegalStateException(
                "Vosk model not found at: " + voskModelPath + "\n" +
                "Run ./setup-models.sh to download required models."
            );
        }
    }
}
```

**Directory Structure After Setup:**
```
speakToMack/
├── models/                          # Git-ignored
│   ├── vosk-model-small-en-us-0.15/
│   │   ├── am/
│   │   ├── graph/
│   │   └── conf/
│   └── ggml-base.en.bin
├── setup-models.sh
└── .gitignore                       # Contains: models/
```

---

## Hotkey Configuration

### Externalized via Properties File

All keyboard shortcuts are configurable via `application.yml` without code changes.

**Configuration Structure:**

```yaml
hotkey:
  # Primary trigger configuration
  trigger:
    type: single-key              # Options: single-key, double-tap, modifier-combination
    key: RIGHT_META               # JNativeHook key code name
    modifiers: []                 # Optional: SHIFT, CONTROL, ALT, META
    
  # Double-tap configuration (when type=double-tap)
  double-tap:
    threshold-ms: 300             # Max time between presses
    
  # Modifier combination (when type=modifier-combination)
  # Example: Cmd+Shift+D for dictation
  combination:
    primary-key: D
    modifiers:
      - META                      # Command/Win key
      - SHIFT

  # Additional shortcuts (future features)
  shortcuts:
    pause: 
      key: ESCAPE
      modifiers: []
    cancel:
      key: ESCAPE
      modifiers: [CONTROL]
```

**Supported Key Names:**

**Modifier Keys:**
- `LEFT_META`, `RIGHT_META` (Command/Win)
- `LEFT_SHIFT`, `RIGHT_SHIFT`
- `LEFT_CONTROL`, `RIGHT_CONTROL`
- `LEFT_ALT`, `RIGHT_ALT`

**Function Keys:** `F1` through `F24`

**Special Keys:** `ESCAPE`, `SPACE`, `ENTER`, `TAB`, `BACKSPACE`

**Letter Keys:** `A` through `Z`

**Configuration Examples:**

```yaml
# Single key (default: Right Command)
hotkey:
  trigger:
    type: single-key
    key: RIGHT_META

# Modifier combination (Cmd+Shift+D)
hotkey:
  trigger:
    type: modifier-combination
  combination:
    primary-key: D
    modifiers: [META, SHIFT]

# Double-tap Function key
hotkey:
  trigger:
    type: double-tap
    key: F13
  double-tap:
    threshold-ms: 300
```

**Architecture:**

```java
// Abstraction
public interface HotkeyTrigger {
    String getName();
    boolean matches(NativeKeyEvent event);
}

// Factory creates trigger from config
@Component
public class HotkeyTriggerFactory {
    public HotkeyTrigger createFromConfig(HotkeyProperties properties) {
        return switch (properties.getTrigger().getType()) {
            case "single-key" -> new SingleKeyTrigger(...);
            case "double-tap" -> new DoubleTapTrigger(...);
            case "modifier-combination" -> new ModifierCombinationTrigger(...);
            default -> throw new IllegalArgumentException("Unknown type");
        };
    }
}

// Manager uses configured trigger
@Service
public class HotkeyManager implements NativeKeyListener {
    private final HotkeyTrigger activeTrigger;
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (activeTrigger.matches(e)) {
            eventPublisher.publishEvent(new HotkeyPressedEvent(Instant.now()));
        }
    }
}
```

---

## Database Strategy

### Recommended: PostgreSQL for MVP

**Decision:** Start with PostgreSQL (relational) for all data types.

**Rationale:**
- ✅ ACID guarantees critical for audit logs
- ✅ JSONB support provides NoSQL flexibility where needed
- ✅ Spring Data JPA simplicity for Java developers
- ✅ Full-text search capability (transcription search)
- ✅ Managed services available (AWS RDS, DigitalOcean)
- ✅ Handles 10K+ writes/sec with proper indexing (sufficient for MVP)

**Schema Example:**

```sql
-- Transcriptions table
CREATE TABLE transcriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    selected_engine VARCHAR(50),
    reconciliation_reason VARCHAR(100),
    vosk_result JSONB,          -- Flexible engine result storage
    whisper_result JSONB,
    audio_size_bytes INTEGER,
    duration_ms BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    ip_address INET,
    INDEX idx_user_created (user_id, created_at DESC),
    INDEX idx_created (created_at DESC)
);

-- User preferences
CREATE TABLE user_preferences (
    user_id VARCHAR(255) PRIMARY KEY,
    default_engine VARCHAR(50) DEFAULT 'vosk',
    reconciliation_strategy VARCHAR(50) DEFAULT 'simple',
    language VARCHAR(10) DEFAULT 'en',
    auto_punctuation BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Audit logs
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    metadata JSONB,
    timestamp TIMESTAMP DEFAULT NOW(),
    INDEX idx_user_time (user_id, timestamp DESC)
);
```

**Migration Path (Future Scale):**
1. **Phase 1 (MVP)**: PostgreSQL only
2. **Phase 2**: Add read replicas, Redis cache
3. **Phase 3**: Partition tables by date (`transcriptions_2025_01`, etc.)
4. **Phase 4**: Add Clickhouse for analytics, keep PostgreSQL for transactional data

**NoSQL Trade-offs (Documented but Not Recommended for MVP):**

| Database | Pros | Cons | Use When |
|----------|------|------|----------|
| **PostgreSQL** | ACID, JPA, Full-text search | Schema migrations | MVP, < 10K writes/sec |
| **MongoDB** | Flexible schema, Sharding | Eventual consistency | > 10K writes/sec |
| **DynamoDB** | Serverless, Predictable latency | AWS lock-in, Query limits | AWS Lambda architecture |

---

## Testing Framework Examples

### Available Libraries (Bundled in spring-boot-starter-test)

- **JUnit 5 (Jupiter)**: `@Test`, `@BeforeEach`, `@ParameterizedTest`
- **Mockito**: Mocking framework (`@Mock`, `@InjectMocks`, `when()`, `verify()`)
- **AssertJ**: Fluent assertions (preferred over JUnit assertions)
- **Awaitility**: Async/concurrent testing (add explicitly)

### Add Awaitility Dependency

```gradle
dependencies {
    testImplementation 'org.awaitility:awaitility:4.2.0'
}
```

### Mockito Example (Unit Test)

```java
@ExtendWith(MockitoExtension.class)
class DictationServiceTest {
    @Mock
    private ParallelSttService mockSttService;
    
    @Mock
    private TypingService mockTypingService;
    
    @InjectMocks
    private DictationOrchestrator orchestrator;
    
    @Test
    void shouldTranscribeAndPasteWhenHotkeyReleased() {
        // Arrange
        byte[] audio = new byte[16000];
        TranscriptionResult result = new TranscriptionResult(
            "hello world", "whisper", "simple", 850L
        );
        when(mockSttService.transcribeDual(any(), anyLong())).thenReturn(result);
        
        // Act
        orchestrator.onHotkeyUp(audio);
        
        // Assert
        verify(mockTypingService).paste("hello world");
        verify(mockSttService).transcribeDual(audio, 5000L);
    }
}
```

### AssertJ Examples (Fluent Assertions)

```java
@Test
void shouldCreateValidTranscriptionResult() {
    TranscriptionResult result = new TranscriptionResult(
        "hello world", "vosk", "simple", 120L
    );
    
    // Fluent assertions (preferred over assertEquals)
    assertThat(result.text())
        .isNotNull()
        .startsWith("hello")
        .contains("world")
        .hasSize(11);
    
    assertThat(result.durationMs()).isPositive().isLessThan(200L);
    assertThat(result.selectedEngine()).isIn("vosk", "whisper");
}

@Test
void shouldHandleCollections() {
    List<String> engines = List.of("vosk", "whisper");
    
    assertThat(engines)
        .hasSize(2)
        .contains("vosk", "whisper")
        .doesNotContain("google");
}
```

### Awaitility Example (Async Testing)

```java
@Test
void shouldCompleteAsyncTranscription() {
    CompletableFuture<TranscriptionResult> future = 
        parallelSttService.transcribeDualAsync(audioData);
    
    // Wait up to 5 seconds for future to complete
    await()
        .atMost(5, SECONDS)
        .until(future::isDone);
    
    TranscriptionResult result = future.join();
    assertThat(result.text()).isNotEmpty();
}

@Test
void shouldDetectAudioCaptureStopped() {
    AtomicBoolean captureActive = new AtomicBoolean(true);
    
    // Simulate async stop
    CompletableFuture.runAsync(() -> {
        try {
            Thread.sleep(500);
            captureActive.set(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    });
    
    // Poll until condition met
    await()
        .atMost(2, SECONDS)
        .pollInterval(50, MILLISECONDS)
        .untilFalse(captureActive);
}
```

### Integration Test Example

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VoskIntegrationTest {
    @Autowired
    private SttEngine voskEngine;
    
    @Test
    void shouldTranscribeSilence() {
        byte[] silence = new byte[16000]; // 1 second of silence at 16kHz
        
        voskEngine.start();
        voskEngine.acceptAudio(silence, 0, silence.length);
        String result = voskEngine.finalizeResult();
        
        assertThat(result).isNotNull();
    }
}
```

---

## Implementation Approach: Incremental Tasks

### Philosophy: Always Deliver Working Code

**Principles:**
- Each task adds **one feature** and is **independently testable**
- Tests pass before moving to next task
- Commit after each deliverable with descriptive message
- No "sprint" planning — continuous incremental delivery

### Task Structure (Template)

Each task includes:
1. **Goal**: What functionality is being added
2. **Deliverable**: Concrete working code
3. **Tests**: Validation that feature works
4. **Validation Command**: How to verify (e.g., `./gradlew test --tests XxxTest`)
5. **Commit Message**: Conventional commit format

### Example Task Progression

**Task 1: Setup Script & Validation (30 minutes)**

Goal: Enable model download and verify build passes

Deliverable:
- `setup-models.sh` script
- Updated `.gitignore`
- Models downloaded

Validation:
```bash
./setup-models.sh
ls -lh models/
./gradlew clean build
```

Commit: `chore: add model setup script and gitignore models directory`

---

**Task 2: Core Domain Model (1 hour)**

Goal: Define immutable domain objects

Deliverable:
```java
public record TranscriptionResult(
    String text,
    String selectedEngine,
    String reconciliationReason,
    long durationMs
) {
    public TranscriptionResult {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
    }
}
```

Tests:
```java
@Test
void shouldCreateValidResult() {
    TranscriptionResult result = new TranscriptionResult(
        "hello", "vosk", "simple", 100L
    );
    assertThat(result.text()).isEqualTo("hello");
}

@Test
void shouldRejectNullText() {
    assertThatThrownBy(() -> new TranscriptionResult(null, "vosk", "simple", 100L))
        .isInstanceOf(IllegalArgumentException.class);
}
```

Validation: `./gradlew test --tests TranscriptionResultTest`

Commit: `feat: add TranscriptionResult domain model with validation`

---

**Task 3: STT Engine Interface (30 minutes)**

Goal: Define abstraction for speech-to-text engines

Deliverable:
```java
/**
 * Abstraction for speech-to-text engines.
 * Implementations: VoskSttEngine, WhisperSttEngine.
 */
public interface SttEngine extends AutoCloseable {
    void start();
    void acceptAudio(byte[] pcm16le, int offset, int length);
    String finalizeResult();
}
```

Validation: Compiles, Javadoc generates

Commit: `feat: add SttEngine interface`

---

**Task 4: Vosk Implementation (2 hours)**

Goal: Integrate Vosk library

Deliverable: `VoskSttEngine` implementation with tests

Validation: `./gradlew test --tests VoskSttEngineTest`

Commit: `feat: implement VoskSttEngine with integration test`

---

### Benefits of Incremental Approach

✅ Always have working code on `main` branch  
✅ Easy to demo progress at any point  
✅ Low risk — small changes are easy to debug  
✅ Fast feedback loop  
✅ No "big bang" integration at the end  

---

## Updated Build Configuration

**Complete `build.gradle` with All Dependencies:**

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.6'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'checkstyle'  // Clean code enforcement
}

group = 'com.phillippitts'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    
    // STT engines
    implementation 'net.java.dev.jna:jna:5.13.0'           // Required by Vosk
    implementation 'org.vosk:vosk:0.3.45'                  // Vosk STT
    
    // Hotkey support
    implementation 'com.github.kwhat:jnativehook:2.2.2'    // Global hotkeys
    
    // Database
    implementation 'org.flywaydb:flyway-core'              // Migrations
    runtimeOnly 'org.postgresql:postgresql'                // Production DB
    runtimeOnly 'com.h2database:h2'                        // Development DB
    
    // Utilities
    implementation 'org.json:json:20231013'                // JSON parsing for Vosk
    
    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.awaitility:awaitility:4.2.0'   // Async testing
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
    // Address Mockito warning on Java 21
    jvmArgs '-XX:+EnableDynamicAgentLoading'
}

checkstyle {
    toolVersion = '10.12.0'
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
}
```

---

## Privacy & Compliance Considerations

### GDPR Compliance Patterns

**Data Minimization:**
- Audio data is **ephemeral** by default (not persisted)
- Transcription text stored with 90-day retention
- IP addresses anonymized

**Configuration:**
```yaml
privacy:
  audio-retention: none              # Options: none, session, persistent
  transcription-retention-days: 90
  anonymize-ip: true
  gdpr-mode: true
```

**User Deletion (Right to Erasure):**
```java
@EventListener
public void handleUserDeletionRequest(UserDeletionEvent event) {
    transcriptionRepository.deleteByUserId(event.userId());
    auditRepository.deleteByUserId(event.userId());
    log.info("Completed GDPR deletion for user: {}", event.userId());
}
```

**Consent Management:**
```java
@PostMapping("/api/v1/privacy/consent")
public void recordConsent(@RequestBody ConsentDto consent) {
    consentRepository.save(Consent.builder()
        .userId(consent.userId())
        .dataProcessing(consent.agreeToProcessing())
        .transcriptionHistory(consent.allowHistory())
        .timestamp(Instant.now())
        .build());
}
```

---

## macOS-Specific Setup

### Required Permissions

**Accessibility Permission** (for keystroke injection):
1. System Settings → Privacy & Security → Accessibility
2. Click the lock icon to make changes
3. Add your Java runtime or IDE to allowed apps
4. Restart application after granting permission

**Microphone Permission** (for audio capture):
- Java Sound API will trigger system prompt on first access
- Grant permission when prompted

### JNativeHook on macOS

**ARM64 (M1/M2/M3) vs Intel:**
- JNativeHook 2.2.2 supports both architectures
- No additional configuration needed
- If issues arise, verify JVM architecture matches system: `java -version`

### Troubleshooting macOS Issues

**"Cannot synthesize keystrokes":**
- Ensure Accessibility permission granted
- Check System Settings → Privacy & Security → Accessibility
- App must be in allowed list

**"Microphone access denied":**
- System Settings → Privacy & Security → Microphone
- Grant permission and restart app

**Hotkey not detected:**
- Test with `NativeKeyEvent` logging
- Some keys reserved by macOS (e.g., Mission Control)
- Try different key combinations

---

## Next Steps

1. **Run model setup:** `./setup-models.sh`
2. **Update build.gradle** with dependencies from this guide
3. **Verify build:** `./gradlew clean build`
4. **Create first task:** Implement `TranscriptionResult` domain model
5. **Follow incremental approach** — one task at a time, always keep code working

---

### Clean Code Principles Section for Guidelines

Yes, I can provide you with the **Clean Code Principles (Robert C. Martin)** section that should be appended to your `.junie/guidelines.md` file. Since the system is in readonly mode, I cannot directly modify the file, but here is the complete content that needs to be added after line 81:

---

### Clean Code Principles (Robert C. Martin)

**These principles are MANDATORY for all code in the speakToMack project. Emphasis on naming conventions is critical.**

#### Naming Conventions (CRITICAL)

**Names must reveal intent without requiring comments.** Poor naming is the root cause of technical debt.

##### Classes and Interfaces

**✅ GOOD Examples:**
- `TranscriptionResult` - Clear what it represents
- `AudioCaptureService` - Domain-specific noun phrase
- `SttEngine` - Concise, meaningful interface name
- `WhisperSttEngine` - Implementation clearly identified
- `TranscriptReconciler` - Action-oriented service name

**❌ BAD Examples:**
- `Manager`, `Helper`, `Processor`, `Handler` - Too generic
- `DataObject`, `Info`, `Impl` - Meaningless suffixes
- `ITranscription` - Hungarian notation (avoid I prefix)
- `TranscriptionData` - What kind of data?

**Rules:**
- Use noun or noun phrases
- Interface names describe capability: `Transcribable`, `Reconcilable`
- Avoid generic suffixes unless they add meaning
- Prefer `AbstractSttEngine` only when abstraction is clear

##### Methods

**✅ GOOD Examples:**
- `transcribe()` - Clear action verb
- `captureAudio()` - Verb phrase describing action
- `reconcileResults()` - Specific verb with object
- `isValid()` - Boolean question (is/has/can/should)
- `hasCompleted()` - Boolean state check
- `finalizeTranscription()` - Clear lifecycle method

**❌ BAD Examples:**
- `doStuff()`, `process()`, `handle()` - What does it do?
- `execute()`, `manage()` - Too generic
- `getData()` for computed values - Use `calculateSimilarity()`
- `run()` without context - Ambiguous

**Rules:**
- Use verb or verb phrases
- Boolean methods: prefix with `is/has/can/should`
- Avoid `get` for computed values: `calculateSimilarity()` not `getSimilarity()`
- Builder methods: fluent naming like `withTimeout()`, `enablePartialResults()`

##### Variables

**✅ GOOD Examples:**
- `audioBuffer` - Clear what it holds
- `selectedEngine` - Specific state
- `reconciliationStrategy` - Full descriptive name
- `transcriptionResult` - No abbreviation
- `isTranscribing` - Boolean with prefix

**❌ BAD Examples:**
- `temp`, `tmp`, `data`, `obj` - Meaningless
- `txnRes` - Abbreviations obscure meaning
- `buf`, `mgr`, `svc` - Save 2 characters, lose clarity
- `str`, `arr` - Type as name

**Rules:**
- Descriptive nouns, **no abbreviations**
- Loop variables: descriptive names for business logic, `i/j/k` only for trivial counters
- Boolean variables: prefix with `is/has/can`
- Collections: plural nouns (`transcriptions`, `engines`, `strategies`)
- Constants: `UPPER_SNAKE_CASE` with full words: `MAX_AUDIO_SIZE_BYTES` not `MAX_SZ`

##### Packages

**✅ GOOD Examples:**
- `com.phillippitts.speaktomack.transcription`
- `com.phillippitts.speaktomack.audio`
- `com.phillippitts.speaktomack.stt`
- `com.phillippitts.speaktomack.reconciliation`

**❌ BAD Examples:**
- `.utils` - What kind of utils?
- `.helpers` - Too generic
- `.managers` - Says nothing about domain
- `.common` - Dumping ground

**Rules:**
- Use domain concepts, not technical terms
- Layer names: `.presentation`, `.service`, `.repository` (not `.controllers`, `.dao`)
- Feature-based when appropriate

#### Comment Guidelines

**Code should be self-documenting. Comments explain WHY, not WHAT.**

##### When to Comment (REQUIRED)

**Public APIs** - Javadoc for all public classes and methods:
```java
/**
 * Transcribes audio using dual-engine parallel processing.
 * 
 * Runs Vosk and Whisper concurrently on separate threads, then reconciles
 * results using the configured strategy. Falls back to single-engine mode if
 * one engine fails or times out.
 * 
 * @param audioData PCM audio data (16kHz, 16-bit, mono)
 * @param timeoutMs maximum wait time per engine in milliseconds
 * @return transcription result with selected text and metadata
 * @throws InvalidAudioException if audio format is invalid
 */
public TranscriptionResult transcribeDual(byte[] audioData, long timeoutMs)
```

**Complex algorithms** - Explain the approach:
```java
// Jaccard similarity: |intersection| / |union|
// Used to detect when engines strongly disagree (poor audio quality indicator)
double similarity = intersection.size() / (double) union.size();
```

**Business rules** - Document requirements:
```java
// GDPR Article 17: Right to erasure requires deletion within 30 days
LocalDateTime deletionCutoff = LocalDateTime.now().minusDays(30);
```

**Non-obvious decisions** - Explain rationale:
```java
// Use SoftReference to allow GC under memory pressure
// Models are expensive to load but can be recreated if needed
private final Map<String, SoftReference<Model>> modelCache;
```

**TODOs with ticket numbers**:
```java
// TODO(SPEAK-123): Add per-word confidence scoring from Vosk JSON
```

##### When NOT to Comment (FORBIDDEN)

- **Obvious code** that restates what's already clear
- **Commented-out code** - delete it, git preserves history
- **Redundant Javadoc** that adds no value
- **Closing brace comments** - use smaller methods instead

#### Function/Method Design

**Methods should do one thing, do it well, and do only that thing.**

##### Size
- **Target:** 5-20 lines (excluding braces)
- **Maximum:** 50 lines (beyond this, extract methods)
- If method doesn't fit on screen without scrolling, it's too long

##### Single Responsibility
```java
// ✅ GOOD: Each method does one thing
public void saveTranscription(TranscriptionResult result) {
    transcriptionRepository.save(toEntity(result));
}

// ❌ BAD: Method does two things (violates SRP)
public void saveAndPublish(TranscriptionResult result) {
    transcriptionRepository.save(toEntity(result));
    eventPublisher.publishEvent(new TranscriptionCompletedEvent(result));
}
```

##### Parameters
- **Maximum 3 parameters** (use parameter object or builder beyond this)
- **Avoid boolean flags** (they indicate method does different things)
- **Prefer specific types:** `Duration` over `int milliseconds`

##### Error Handling
- Use exceptions for exceptional cases, not control flow
- Create **domain-specific exceptions**: `InvalidAudioException`, `ModelNotFoundException`
- **Fail fast:** validate at boundaries, throw early
- **Provide context:** include values, paths, expected vs actual

```java
// ✅ GOOD: Specific exception with context
if (audio.length > MAX_AUDIO_SIZE_BYTES) {
    throw new InvalidAudioException(
        String.format("Audio size %d exceeds maximum %d bytes", 
            audio.length, MAX_AUDIO_SIZE_BYTES)
    );
}
```

#### Class Design

**Classes should have a single reason to change.**

##### Size
- **Target:** Under 200 lines
- **Maximum:** 500 lines (beyond this, split into multiple classes)

##### Dependency Injection (Spring)

```java
// ✅ GOOD: Constructor injection, all dependencies final
@Service
public class DictationOrchestrator {
    private final ParallelSttService sttService;
    private final TypingService typingService;
    private final AudioCaptureService audioService;
    
    public DictationOrchestrator(ParallelSttService sttService,
                                 TypingService typingService,
                                 AudioCaptureService audioService) {
        this.sttService = sttService;
        this.typingService = typingService;
        this.audioService = audioService;
    }
}

// ❌ BAD: Field injection, mutable
@Service
public class DictationOrchestrator {
    @Autowired private ParallelSttService sttService; // NO!
}
```

##### Encapsulation
- **Minimize public surface:** prefer package-private for internal classes
- **Immutable where possible:** final fields, no setters
- **Use records for DTOs**

#### SOLID Principles Applied

- **Single Responsibility:** Each service has one reason to change
- **Open/Closed:** `SttEngine` interface open for extension, closed for modification
- **Liskov Substitution:** All `SttEngine` implementations are interchangeable
- **Interface Segregation:** Focused role interfaces, not monolithic god interfaces
- **Dependency Inversion:** Depend on abstractions (`SttEngine`) not concretions

#### Code Review Checklist

Before committing, verify:

**Naming:**
- ☐ All class names are nouns revealing their purpose
- ☐ All method names are verbs revealing their action
- ☐ All variables have descriptive names (no abbreviations)
- ☐ No generic names like Manager, Helper, Processor, Handler

**Structure:**
- ☐ No method exceeds 50 lines
- ☐ No class exceeds 500 lines
- ☐ No method has more than 3 parameters
- ☐ All dependencies injected via constructor
- ☐ All fields are final where possible

**Documentation:**
- ☐ All public APIs have Javadoc
- ☐ Complex logic has inline comments explaining WHY
- ☐ No commented-out code
- ☐ All TODOs have ticket numbers

**Error Handling:**
- ☐ All exceptions provide context (values, paths)
- ☐ No swallowed exceptions
- ☐ Domain-specific exceptions used appropriately

**Testing:**
- ☐ Tests follow `shouldXxxWhenYyy` naming pattern
- ☐ One assertion concept per test
- ☐ Test names document behavior

#### Enforcement

These principles are **mandatory** for all code in this project.

During code review:
- **Naming violations:** ⛔ **BLOCKING** (must fix before merge)
- **Missing Javadoc on public API:** ⛔ **BLOCKING**
- **Method over 50 lines:** ⛔ **BLOCKING** (refactor required)
- **Field injection (@Autowired on fields):** ⛔ **BLOCKING**
- **Other violations:** ⚠️ SHOULD FIX (may be deferred if justified)

#### Automated Tools

Configure in IDE and CI pipeline:
- **Checkstyle:** Enforce naming conventions and structure rules
- **SonarLint:** Detect code smells and complexity issues
- **SpotBugs:** Find bugs and anti-patterns

**Build configuration** (add to `build.gradle`):
```gradle
plugins {
    id 'checkstyle'
}

checkstyle {
    toolVersion = '10.12.0'
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
}
```

---

## Logging Strategy: Log4j 2

### Why Log4j 2 Over Logback

**Decision:** Use Log4j 2 instead of Spring Boot's default Logback.

**Rationale:**
- ✅ **Superior performance**: Asynchronous loggers with lower latency
- ✅ **Garbage-free logging**: Reduces GC pressure in high-throughput scenarios
- ✅ **Plugin architecture**: Extensible with custom appenders
- ✅ **Lambda support**: Lazy evaluation for expensive log messages
- ✅ **Better configuration reload**: Hot reload without restart

### Configuration

#### Update `build.gradle`

```gradle
configurations {
    all {
        // Exclude default Logback in favor of Log4j 2
        exclude group: 'org.springframework.boot', module: 'spring-boot-starter-logging'
    }
}

dependencies {
    // Logging with Log4j 2 (replaces Logback)
    implementation 'org.springframework.boot:spring-boot-starter-log4j2'
    implementation 'com.lmax:disruptor:3.4.4'  // For async logging performance
}
```

#### Create `src/main/resources/log4j2-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="30">
    <Properties>
        <!-- Application name for log correlation -->
        <Property name="APP_NAME">speakToMack</Property>
        
        <!-- Log pattern with structured information -->
        <Property name="LOG_PATTERN">
            %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] [%X{requestId}] [%X{userId}] %-5level %logger{36} - %msg%n
        </Property>
        
        <!-- Async pattern includes location info (slower but useful for debugging) -->
        <Property name="ASYNC_LOG_PATTERN">
            %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] [%X{requestId}] %-5level %logger{36}.%M:%L - %msg%n
        </Property>
    </Properties>
    
    <Appenders>
        <!-- Console appender for development -->
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="${LOG_PATTERN}"/>
        </Console>
        
        <!-- File appender for application logs -->
        <RollingFile name="RollingFile" 
                     fileName="logs/speakToMack.log"
                     filePattern="logs/speakToMack-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="${LOG_PATTERN}"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
                <SizeBasedTriggeringPolicy size="100 MB"/>
            </Policies>
            <DefaultRolloverStrategy max="30"/>
        </RollingFile>
        
        <!-- Async appender wrapping RollingFile (production performance) -->
        <Async name="AsyncFile" bufferSize="512">
            <AppenderRef ref="RollingFile"/>
        </Async>
        
        <!-- Separate appender for audit logs (NEVER async - data integrity critical) -->
        <RollingFile name="AuditLog" 
                     fileName="logs/audit.log"
                     filePattern="logs/audit-%d{yyyy-MM-dd}.log.gz">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{userId}] [%X{action}] - %msg%n"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
            </Policies>
            <DefaultRolloverStrategy max="365"/>  <!-- Keep 1 year for compliance -->
        </RollingFile>
    </Appenders>
    
    <Loggers>
        <!-- Application logger -->
        <Logger name="com.phillippitts.speaktomack" level="debug" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="AsyncFile"/>
        </Logger>
        
        <!-- Audit logger (synchronous for ACID guarantees) -->
        <Logger name="com.phillippitts.speaktomack.audit" level="info" additivity="false">
            <AppenderRef ref="AuditLog"/>
        </Logger>
        
        <!-- Spring framework -->
        <Logger name="org.springframework" level="info"/>
        <Logger name="org.springframework.web" level="debug"/>
        
        <!-- Database -->
        <Logger name="org.hibernate.SQL" level="debug"/>
        <Logger name="org.hibernate.type.descriptor.sql.BasicBinder" level="trace"/>
        
        <!-- Root logger -->
        <Root level="info">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="AsyncFile"/>
        </Root>
    </Loggers>
</Configuration>
```

### Structured Logging with MDC (Mapped Diagnostic Context)

#### MDC Filter for Request Correlation

```java
package com.phillippitts.speaktomack.config.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds correlation ID and user context to all logs within a request.
 * 
 * MDC (Mapped Diagnostic Context) allows tracking related log entries
 * across async operations and service boundaries.
 */
@Component
public class MdcFilter implements Filter {
    
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String USER_ID_HEADER = "X-User-ID";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            // Request ID for correlation (generate if not provided)
            String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            ThreadContext.put("requestId", requestId);
            
            // User ID from security context or header
            String userId = extractUserId(httpRequest);
            if (userId != null) {
                ThreadContext.put("userId", userId);
            }
            
            // Additional context
            ThreadContext.put("method", httpRequest.getMethod());
            ThreadContext.put("uri", httpRequest.getRequestURI());
            
            chain.doFilter(request, response);
        } finally {
            // CRITICAL: Clear MDC after request to prevent memory leaks
            ThreadContext.clearAll();
        }
    }
    
    private String extractUserId(HttpServletRequest request) {
        // Extract from security context (future: Spring Security)
        // For now, check header
        return request.getHeader(USER_ID_HEADER);
    }
}
```

### Logging Best Practices

#### Service Layer Example

```java
package com.phillippitts.speaktomack.service;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Demonstrates proper logging patterns following Clean Code principles.
 */
@Service
public class TranscriptionService {
    
    // Logger name automatically matches class name
    private static final Logger log = LogManager.getLogger(TranscriptionService.class);
    
    public TranscriptionResult transcribe(byte[] audio) {
        // INFO: Business events (always logged in production)
        log.info("Starting transcription: audioSize={} bytes", audio.length);
        
        try {
            // DEBUG: Detailed flow for troubleshooting (disabled in production)
            log.debug("Initializing parallel STT engines");
            
            TranscriptionResult result = performTranscription(audio);
            
            // INFO: Successful business outcome with metrics
            log.info("Transcription completed: engine={}, duration={}ms, textLength={}", 
                result.selectedEngine(), 
                result.durationMs(), 
                result.text().length());
            
            return result;
        } catch (InvalidAudioException e) {
            // WARN: Expected error (user input issue)
            log.warn("Invalid audio format: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // ERROR: Unexpected failure (requires investigation)
            log.error("Transcription failed: audioSize={}", audio.length, e);
            throw new TranscriptionException("Failed to transcribe audio", e);
        }
    }
    
    /**
     * Lambda-based lazy evaluation for expensive log messages.
     * Only evaluates if DEBUG level is enabled.
     */
    private void logEngineDetails(SttEngine engine) {
        log.debug("Engine details: {}", () -> engine.getDetailedDiagnostics());  // Expensive call
    }
}
```

### Audit Logging (Compliance)

```java
package com.phillippitts.speaktomack.service.audit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Service;

/**
 * Audit logger for compliance (GDPR, HIPAA).
 * Uses dedicated synchronous appender to ensure events are never lost.
 */
@Service
public class AuditService {
    
    // Use dedicated audit logger (configured in log4j2-spring.xml)
    private static final Logger auditLog = LogManager.getLogger("com.phillippitts.speaktomack.audit");
    
    /**
     * Log user actions for compliance.
     * Format: timestamp [userId] [action] - details
     */
    public void logUserAction(String userId, String action, String details) {
        ThreadContext.put("userId", userId);
        ThreadContext.put("action", action);
        try {
            // Audit logs MUST be synchronous (no async appender)
            // to guarantee persistence before transaction commits
            auditLog.info(details);
        } finally {
            ThreadContext.remove("userId");
            ThreadContext.remove("action");
        }
    }
    
    public void logTranscription(String userId, int audioSize, String selectedEngine) {
        logUserAction(userId, "TRANSCRIBE", 
            String.format("audioSize=%d, engine=%s", audioSize, selectedEngine));
    }
    
    public void logDataDeletion(String userId, String reason) {
        logUserAction(userId, "DELETE_DATA", 
            String.format("reason=%s, timestamp=%s", reason, java.time.Instant.now()));
    }
}
```

---

## Exception Hierarchy: Clean Code Principles

### Design Philosophy

**Exceptions must be self-documenting and guide resolution.**

**Principles:**
1. **Names reveal intent**: `ModelNotFoundException`, not `DataException`
2. **Provide context**: Include paths, values, expected vs actual
3. **Fail fast**: Validate at boundaries, throw early
4. **One responsibility**: Each exception represents one failure mode
5. **Preserve cause**: Always chain original exception

### Exception Hierarchy

```java
package com.phillippitts.speaktomack.exception;

/**
 * Base exception for all application-specific errors.
 * 
 * Extends RuntimeException (unchecked) following Spring's philosophy:
 * - Most errors are unrecoverable at call site
 * - Reduces boilerplate try-catch blocks
 * - Handled by @ControllerAdvice at API boundary
 */
public class SpeakToMackException extends RuntimeException {
    
    private final String errorCode;
    
    public SpeakToMackException(String message) {
        super(message);
        this.errorCode = this.getClass().getSimpleName();
    }
    
    public SpeakToMackException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = this.getClass().getSimpleName();
    }
    
    public SpeakToMackException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Error code for client applications (e.g., "ModelNotFoundException").
     * Stable across refactorings, unlike class names.
     */
    public String getErrorCode() {
        return errorCode;
    }
}
```

### Domain-Specific Exceptions

```java
package com.phillippitts.speaktomack.exception;

/**
 * Thrown when STT model files cannot be found or loaded.
 * 
 * This is a configuration error (fail-fast on startup).
 * Resolution: Run ./setup-models.sh or check stt.vosk.model-path property.
 */
public class ModelNotFoundException extends SpeakToMackException {
    
    private final String modelPath;
    private final String engineType;
    
    public ModelNotFoundException(String engineType, String modelPath) {
        super(String.format(
            "STT model not found for engine '%s' at path: %s. " +
            "Ensure models are downloaded by running ./setup-models.sh and " +
            "verify the configuration property 'stt.%s.model-path' is correct.",
            engineType, modelPath, engineType
        ));
        this.modelPath = modelPath;
        this.engineType = engineType;
    }
    
    public String getModelPath() { return modelPath; }
    public String getEngineType() { return engineType; }
}
```

```java
package com.phillippitts.speaktomack.exception;

/**
 * Thrown when audio data is invalid or corrupted.
 * 
 * This is a client error (HTTP 400).
 * Resolution: Check audio format (16kHz, 16-bit PCM mono) and file integrity.
 */
public class InvalidAudioException extends SpeakToMackException {
    
    private final int audioSize;
    private final String reason;
    
    public InvalidAudioException(String reason) {
        super("Invalid audio data: " + reason);
        this.audioSize = 0;
        this.reason = reason;
    }
    
    public InvalidAudioException(int audioSize, String reason) {
        super(String.format(
            "Invalid audio data (size: %d bytes): %s. " +
            "Expected: 16kHz, 16-bit PCM, mono format. " +
            "Size must be between %d bytes (1 second) and %d bytes (5 minutes).",
            audioSize, reason, 16000 * 2, 16000 * 2 * 60 * 5
        ));
        this.audioSize = audioSize;
        this.reason = reason;
    }
    
    public int getAudioSize() { return audioSize; }
    public String getReason() { return reason; }
}
```

```java
package com.phillippitts.speaktomack.exception;

/**
 * Thrown when transcription engine times out or fails.
 * 
 * This is a transient error (HTTP 503 - retry with backoff).
 * Resolution: Increase timeout (stt.parallel.timeout-ms) or check system resources.
 */
public class TranscriptionException extends SpeakToMackException {
    
    private final String engineName;
    private final long durationMs;
    
    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
        this.engineName = "unknown";
        this.durationMs = 0;
    }
    
    public TranscriptionException(String engineName, String message, long durationMs, Throwable cause) {
        super(String.format(
            "Transcription failed for engine '%s' after %dms: %s",
            engineName, durationMs, message
        ), cause);
        this.engineName = engineName;
        this.durationMs = durationMs;
    }
    
    public String getEngineName() { return engineName; }
    public long getDurationMs() { return durationMs; }
}
```

### Global Exception Handler

```java
package com.phillippitts.speaktomack.presentation.exception;

import com.phillippitts.speaktomack.exception.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

/**
 * Global exception handler for REST API.
 * 
 * Converts domain exceptions to HTTP responses with appropriate status codes.
 * Logs errors for monitoring while protecting sensitive details from clients.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Configuration/setup error - fail fast on startup.
     */
    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<ApiError> handleModelNotFound(ModelNotFoundException ex) {
        log.error("Model not found: engine={}, path={}", 
            ex.getEngineType(), ex.getModelPath());
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError(
                ex.getErrorCode(),
                "Speech-to-text service unavailable",
                "Model not loaded. Contact administrator.",
                Instant.now()
            ));
    }
    
    /**
     * Client error - invalid input.
     */
    @ExceptionHandler(InvalidAudioException.class)
    public ResponseEntity<ApiError> handleInvalidAudio(InvalidAudioException ex) {
        log.warn("Invalid audio: size={}, reason={}", 
            ex.getAudioSize(), ex.getReason());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ApiError(
                ex.getErrorCode(),
                "Invalid audio format",
                ex.getMessage(),  // Safe to expose details (no sensitive data)
                Instant.now()
            ));
    }
    
    /**
     * Transient error - retry possible.
     */
    @ExceptionHandler(TranscriptionException.class)
    public ResponseEntity<ApiError> handleTranscriptionFailure(TranscriptionException ex) {
        log.error("Transcription failed: engine={}, duration={}ms", 
            ex.getEngineName(), ex.getDurationMs(), ex);
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError(
                ex.getErrorCode(),
                "Transcription service temporarily unavailable",
                "Please retry in a few seconds",
                Instant.now()
            ));
    }
    
    /**
     * Catch-all for unexpected errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError(
                "InternalServerError",
                "An unexpected error occurred",
                "Please contact support with request ID",
                Instant.now()
            ));
    }
}

/**
 * Standardized error response for API clients.
 */
record ApiError(
    String errorCode,
    String message,
    String details,
    Instant timestamp
) {}
```

---

## Security Best Practices (Detailed Rationale)

### 1. Input Validation (Prevent Injection Attacks)

#### Why It Matters
**Threat:** Malicious actors can exploit unvalidated inputs to inject code, overflow buffers, or consume excessive resources.

**Example Attack Vectors:**
- **SQL Injection**: Crafted user IDs like `'; DROP TABLE transcriptions; --`
- **Buffer Overflow**: Sending 10 GB audio file crashes service
- **Path Traversal**: Model path like `../../etc/passwd` reads sensitive files

#### Implementation

```java
package com.phillippitts.speaktomack.service.validation;

import com.phillippitts.speaktomack.exception.InvalidAudioException;
import org.springframework.stereotype.Component;

/**
 * Validates audio data before processing.
 * 
 * Security: Prevents denial-of-service via oversized inputs and
 * rejects malformed data that could exploit native library vulnerabilities.
 */
@Component
public class AudioValidator {
    
    // Size limits based on realistic use cases
    private static final int MIN_AUDIO_SIZE = 1024;              // 1 KB (~0.06 seconds at 16kHz)
    private static final long MAX_AUDIO_SIZE = 10 * 1024 * 1024; // 10 MB (~10 minutes at 16kHz)
    
    // WAV header magic numbers for format validation
    private static final byte[] WAV_RIFF_HEADER = {'R', 'I', 'F', 'F'};
    private static final byte[] WAV_WAVE_HEADER = {'W', 'A', 'V', 'E'};
    
    /**
     * Validate audio data meets security and format requirements.
     * 
     * @throws InvalidAudioException if validation fails
     */
    public void validate(byte[] audio) {
        // Defense 1: Null check (prevent NullPointerException in native code)
        if (audio == null) {
            throw new InvalidAudioException("Audio data is null");
        }
        
        // Defense 2: Size limits (prevent DoS via memory exhaustion)
        if (audio.length < MIN_AUDIO_SIZE) {
            throw new InvalidAudioException(audio.length, 
                "Audio too short (minimum: " + MIN_AUDIO_SIZE + " bytes)");
        }
        
        if (audio.length > MAX_AUDIO_SIZE) {
            throw new InvalidAudioException(audio.length, 
                "Audio exceeds maximum size (limit: " + MAX_AUDIO_SIZE + " bytes)");
        }
        
        // Defense 3: Format validation (prevent exploitation of parser vulnerabilities)
        validateWavFormat(audio);
    }
    
    private void validateWavFormat(byte[] audio) {
        // Check minimum header length
        if (audio.length < 44) {  // WAV header is 44 bytes
            throw new InvalidAudioException("Audio file too small for valid WAV format");
        }
        
        // Validate RIFF header
        if (!matchesHeader(audio, 0, WAV_RIFF_HEADER)) {
            throw new InvalidAudioException("Invalid WAV format: missing RIFF header");
        }
        
        // Validate WAVE header
        if (!matchesHeader(audio, 8, WAV_WAVE_HEADER)) {
            throw new InvalidAudioException("Invalid WAV format: missing WAVE header");
        }
    }
    
    private boolean matchesHeader(byte[] audio, int offset, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (audio[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
```

#### Spring Validation Annotations

```java
package com.phillippitts.speaktomack.presentation.dto;

import jakarta.validation.constraints.*;

/**
 * DTO with built-in validation constraints.
 * 
 * Security: Spring automatically validates before controller methods execute,
 * preventing invalid data from reaching business logic.
 */
public record TranscriptionRequest(
    
    @NotNull(message = "User ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,50}$", 
             message = "User ID must be 3-50 alphanumeric characters, dashes, or underscores")
    String userId,
    
    @NotNull(message = "Audio data is required")
    @Size(min = 1024, max = 10485760, message = "Audio size must be between 1KB and 10MB")
    byte[] audioData,
    
    @Pattern(regexp = "^(vosk|whisper)$", message = "Engine must be 'vosk' or 'whisper'")
    String preferredEngine
) {}
```

### 2. Rate Limiting (Prevent Abuse)

#### Why It Matters
**Threat:** Attackers can overwhelm the service with excessive requests, causing:
- **Denial of Service**: Legitimate users cannot access the service
- **Resource Exhaustion**: CPU/memory/disk consumed, leading to crashes
- **Cost Escalation**: Cloud bills spike due to auto-scaling

#### Implementation with Bucket4j

```java
package com.phillippitts.speaktomack.config.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user rate limiter using token bucket algorithm.
 * 
 * Security: Prevents single user from monopolizing resources.
 * Each user gets N requests per time window.
 */
@Component
public class RateLimiter {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    // Allow 10 transcriptions per minute per user
    private static final int CAPACITY = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
    
    /**
     * Check if user has available quota.
     * 
     * @return true if request allowed, false if rate limit exceeded
     */
    public boolean tryConsume(String userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, this::createBucket);
        return bucket.tryConsume(1);
    }
    
    private Bucket createBucket(String userId) {
        Bandwidth limit = Bandwidth.classic(
            CAPACITY, 
            Refill.intervally(CAPACITY, REFILL_PERIOD)
        );
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
}
```

#### Interceptor to Enforce Rate Limits

```java
package com.phillippitts.speaktomack.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepts requests and enforces rate limits.
 * 
 * Security: Prevents abuse before reaching controllers.
 * Returns HTTP 429 (Too Many Requests) when limit exceeded.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RateLimiter rateLimiter;
    
    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        String userId = extractUserId(request);
        
        if (!rateLimiter.tryConsume(userId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Try again later.\"}");
            return false;  // Block request
        }
        
        return true;  // Allow request
    }
    
    private String extractUserId(HttpServletRequest request) {
        // Extract from authentication context or use IP as fallback
        String userId = request.getHeader("X-User-ID");
        return userId != null ? userId : request.getRemoteAddr();
    }
}
```

### 3. Secure Configuration Management

#### Why It Matters
**Threat:** Hardcoded secrets or credentials in code/config files can be exposed via:
- **Version control leaks**: Secrets committed to Git history
- **Log exposure**: Passwords logged in plain text
- **Config file access**: Unauthorized file system access

#### Implementation

```yaml
# application.yml - NO secrets here
spring:
  datasource:
    url: ${DATABASE_URL}  # From environment variable
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  
stt:
  vosk:
    model-path: ${VOSK_MODEL_PATH:./models/vosk-model-small-en-us-0.15}
  whisper:
    model-path: ${WHISPER_MODEL_PATH:./models/ggml-base.en.bin}

# Encryption key for sensitive data at rest
security:
  encryption:
    key: ${ENCRYPTION_KEY}  # 256-bit AES key from environment
```

#### Secure Properties at Runtime

```java
package com.phillippitts.speaktomack.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Security configuration with validation.
 * 
 * Security: Fails fast if required secrets are missing.
 * Never logs sensitive values (no toString() implementation).
 */
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    
    private final Encryption encryption;
    
    @ConstructorBinding
    public SecurityProperties(Encryption encryption) {
        if (encryption == null || encryption.key() == null) {
            throw new IllegalStateException(
                "Encryption key not configured. Set ENCRYPTION_KEY environment variable."
            );
        }
        this.encryption = encryption;
    }
    
    public Encryption getEncryption() {
        return encryption;
    }
    
    public record Encryption(String key) {
        // Redact in logs
        @Override
        public String toString() {
            return "Encryption{key=***REDACTED***}";
        }
    }
}
```

### 4. Audit Logging (Compliance & Forensics)

#### Why It Matters
**Threat:** Without audit trails, you cannot:
- **Detect breaches**: No visibility into unauthorized access
- **Investigate incidents**: Cannot trace attacker actions
- **Prove compliance**: GDPR/HIPAA require access logs

**Compliance Requirements:**
- **GDPR Article 30**: Maintain records of processing activities
- **HIPAA §164.308**: Audit controls for access to protected health information

#### Implementation

```java
package com.phillippitts.speaktomack.service.audit;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Service;

/**
 * Comprehensive audit logging for compliance.
 * 
 * Security: Immutable log records for forensic analysis.
 * Uses synchronous appender (never async) to ensure persistence.
 */
@Service
public class ComplianceAuditService {
    
    private static final Logger auditLog = LogManager.getLogger("com.phillippitts.speaktomack.audit");
    
    /**
     * Log every transcription request (GDPR data processing record).
     */
    public void auditTranscription(String userId, TranscriptionResult result, String ipAddress) {
        ThreadContext.put("userId", userId);
        ThreadContext.put("action", "TRANSCRIBE");
        ThreadContext.put("ipAddress", anonymizeIp(ipAddress));
        
        try {
            auditLog.info("engine={}, duration={}ms, textLength={}", 
                result.selectedEngine(),
                result.durationMs(),
                result.text().length());
        } finally {
            ThreadContext.clearMap();
        }
    }
    
    /**
     * Log data deletion requests (GDPR Right to Erasure).
     */
    public void auditDataDeletion(String userId, String reason, int recordsDeleted) {
        ThreadContext.put("userId", userId);
        ThreadContext.put("action", "DELETE_USER_DATA");
        
        try {
            auditLog.info("reason={}, recordsDeleted={}, timestamp={}", 
                reason, recordsDeleted, java.time.Instant.now());
        } finally {
            ThreadContext.clearMap();
        }
    }
    
    /**
     * Log access to sensitive data (HIPAA requirement).
     */
    public void auditSensitiveAccess(String userId, String resourceType, String resourceId) {
        ThreadContext.put("userId", userId);
        ThreadContext.put("action", "ACCESS_SENSITIVE_DATA");
        
        try {
            auditLog.info("resourceType={}, resourceId={}", resourceType, resourceId);
        } finally {
            ThreadContext.clearMap();
        }
    }
    
    /**
     * Anonymize IP address for GDPR compliance (last octet zeroed).
     */
    private String anonymizeIp(String ip) {
        if (ip == null) return "unknown";
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) + ".0" : ip;
    }
}
```

### 5. Secure Defaults & Fail-Safe Configuration

#### Why It Matters
**Threat:** Insecure default configurations are a leading cause of breaches.

**Examples:**
- **Default credentials**: admin/admin passwords left unchanged
- **Open ports**: Services exposed to internet unnecessarily
- **Debug mode in production**: Stack traces reveal architecture

#### Implementation

```yaml
# application-production.yml
spring:
  # Disable stack traces in error responses (info leakage)
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false
  
  # Disable actuator endpoints in production (except health)
  boot:
    admin:
      enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health  # Only health check public
  endpoint:
    health:
      show-details: never  # Don't expose internal details

# Strict validation
server:
  error:
    include-stacktrace: never
    include-message: never
  
# Security headers
security:
  headers:
    content-security-policy: "default-src 'self'"
    x-frame-options: DENY
    x-content-type-options: nosniff
```

### Summary: Security Checklist

Before deploying to production, verify:

- [ ] **Input Validation**: All user inputs validated with size limits and format checks
- [ ] **Rate Limiting**: Per-user/IP rate limits configured
- [ ] **Secrets Management**: No hardcoded passwords; all secrets from environment
- [ ] **Audit Logging**: Synchronous audit log configured for compliance
- [ ] **Error Messages**: Production errors don't expose stack traces or internal details
- [ ] **Dependencies**: Regular vulnerability scans (`./gradlew dependencyCheckAnalyze`)
- [ ] **Encryption**: Sensitive data encrypted at rest (database encryption enabled)
- [ ] **HTTPS Only**: TLS 1.3 enforced, HTTP redirected to HTTPS
- [ ] **Least Privilege**: Database user has minimum required permissions
- [ ] **Backup & Recovery**: Encrypted backups with tested restore procedure

---

# Spring Boot Guidelines

## 1. Prefer Constructor Injection over Field/Setter Injection
* Declare all the mandatory dependencies as `final` fields and inject them through the constructor.
* Spring will auto-detect if there is only one constructor, no need to add `@Autowired` on the constructor.
* Avoid field/setter injection in production code.

## 2. Prefer package-private over public for Spring components
* Declare Controllers, their request-handling methods, `@Configuration` classes and `@Bean` methods with default (package-private) visibility whenever possible. There's no obligation to make everything `public`.

## 3. Organize Configuration with Typed Properties
* Group application-specific configuration properties with a common prefix in `application.properties` or `.yml`.
* Bind them to `@ConfigurationProperties` classes with validation annotations so that the application will fail fast if the configuration is invalid.
* Prefer environment variables instead of profiles for passing different configuration properties for different environments.

## 4. Define Clear Transaction Boundaries
* Define each Service-layer method as a transactional unit.
* Annotate query-only methods with `@Transactional(readOnly = true)`.
* Annotate data-modifying methods with `@Transactional`.
* Limit the code inside each transaction to the smallest necessary scope.


## 5. Disable Open Session in View Pattern
* While using Spring Data JPA, disable the Open Session in View filter by setting ` spring.jpa.open-in-view=false` in `application.properties/yml.`

## 6. Separate Web Layer from Persistence Layer
* Don't expose entities directly as responses in controllers.
* Define explicit request and response record (DTO) classes instead.
* Apply Jakarta Validation annotations on your request records to enforce input rules.

## 7. Follow REST API Design Principles
* **Versioned, resource-oriented URLs:** Structure your endpoints as `/api/v{version}/resources` (e.g. `/api/v1/orders`).
* **Consistent patterns for collections and sub-resources:** Keep URL conventions uniform (for example, `/posts` for posts collection and `/posts/{slug}/comments` for comments of a specific post).
* **Explicit HTTP status codes via ResponseEntity:** Use `ResponseEntity<T>` to return the correct status (e.g. 200 OK, 201 Created, 404 Not Found) along with the response body.
* Use pagination for collection resources that may contain an unbounded number of items.
* The JSON payload must use a JSON object as a top-level data structure to allow for future extension.
* Use snake_case or camelCase for JSON property names consistently.

## 8. Use Command Objects for Business Operations
* Create purpose-built command records (e.g., `CreateOrderCommand`) to wrap input data.
* Accept these commands in your service methods to drive creation or update workflows.

## 9. Centralize Exception Handling
* Define a global handler class annotated with `@ControllerAdvice` (or `@RestControllerAdvice` for REST APIs) using `@ExceptionHandler` methods to handle specific exceptions.
* Return consistent error responses. Consider using the ProblemDetails response format ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)).

## 10. Actuator
* Expose only essential actuator endpoints (such as `/health`, `/info`, `/metrics`) without requiring authentication. All the other actuator endpoints must be secured.

## 11. Internationalization with ResourceBundles
* Externalize all user-facing text such as labels, prompts, and messages into ResourceBundles rather than embedding them in code.

## 12. Use Testcontainers for integration tests
* Spin up real services (databases, message brokers, etc.) in your integration tests to mirror production environments.

## 13. Use random port for integration tests
* When writing integration tests, start the application on a random available port to avoid port conflicts by annotating the test class with:

    ```java
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    ```

## 14. Logging
* **Use a proper logging framework.**  
  Never use `System.out.println()` for application logging. Rely on SLF4J (or a compatible abstraction) and your chosen backend (Logback, Log4j2, etc.).

* **Protect sensitive data.**  
  Ensure that no credentials, personal information, or other confidential details ever appear in log output.

* **Guard expensive log calls.**  
  When building verbose messages at `DEBUG` or `TRACE` level, especially those involving method calls or complex string concatenations, wrap them in a level check or use suppliers:

```java
if (logger.isDebugEnabled()) {
    logger.debug("Detailed state: {}", computeExpensiveDetails());
}

// using Supplier/Lambda expression
logger.atDebug()
	.setMessage("Detailed state: {}")
	.addArgument(() -> computeExpensiveDetails())
    .log();
```


---

## Checkstyle Enforcement (Mandatory)

This project enforces Clean Code and naming standards via Checkstyle. All generated and committed code MUST pass Checkstyle locally and in CI. The build is configured to fail on any violations (maxWarnings = 0).

### Why we enforce Checkstyle
- Prevents style drift and naming inconsistencies as the codebase grows
- Encodes Clean Code rules (SRP, small methods, consistent naming) as guardrails
- Produces readable and maintainable diffs and code reviews

### How it’s configured
- Gradle plugin already applied in `build.gradle`:
  ```gradle
  plugins {
      id 'checkstyle'
  }
  
  checkstyle {
      toolVersion = '10.12.0'
      configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
      maxWarnings = 0 // Fail build on any violations
  }
  ```
- Rule set: `config/checkstyle/checkstyle.xml`
  - References these guidelines for naming, size limits, and structure
  - You may extend rules here if the team agrees (via PR & ADR if substantial)

### Running Checkstyle
- All checks (as part of full verification):
  ```bash
  ./gradlew clean check
  ```
- Only Checkstyle tasks:
  ```bash
  ./gradlew checkstyleMain checkstyleTest
  ```
- Single source set (for faster feedback):
  ```bash
  ./gradlew checkstyleMain
  ```

### What the rules enforce (high level)
- Naming conventions:
  - Classes & Interfaces: PascalCase (nouns that reveal intent; avoid Manager/Helper)
  - Methods: lowerCamelCase (verbs that reveal action)
  - Constants: UPPER_SNAKE_CASE
  - Packages: lower.case.feature.or.layer (e.g., `presentation.controller`)
- Structure & complexity:
  - Small methods (extract when exceeding acceptable size/complexity)
  - No wildcard imports; explicit imports only
  - No unused imports
  - Javadoc for public APIs (especially interfaces and public classes)
- Formatting essentials:
  - Indentation, whitespace, and newline rules
  - Braces on control structures and class/method declarations

See `config/checkstyle/checkstyle.xml` for authoritative rules.

### Fixing violations quickly
- Run Checkstyle as above to see exact file/line and rule id
- Most formatting/import issues can be auto-fixed by your IDE or by running code cleanup
- For naming/structure issues, prefer refactoring (rename, extract method/class)
- If you disagree with a rule for a specific case, raise a PR with:
  - The justification
  - Proposed rule adjustment or suppression (see below)
  - Impact analysis (files affected)

### Suppressions (use sparingly)
- Prefer fixing code. When a legitimate exception is justified (e.g., 3rd-party generated code), add a narrow suppression in a dedicated suppressions file and reference it from Checkstyle config.
- Avoid broad disables or package‑wide suppressions.

### IDE Integration
- IntelliJ IDEA: Settings → Tools → Checkstyle
  - Use the project’s `config/checkstyle/checkstyle.xml`
  - Enable “Scan with Checkstyle” on changes
- Consider enabling “Reformat on Save” & “Optimize Imports on Save”

### CI Enforcement
- The GitHub Actions workflow runs `./gradlew clean build`/`test`; Checkstyle executes during `check` phase
- Any Checkstyle violation will fail the CI job and block merges to `main`

### Authoring guidance (applies to code generators too)
- Generators should:
  - Produce files with correct header/package/import ordering
  - Follow naming conventions from these guidelines
  - Keep methods small and cohesive
- If generating code programmatically, run `checkstyleMain` as part of the generation pipeline (locally or in a pre-commit hook) to catch issues early

### Quick checklist before pushing
- [ ] `./gradlew checkstyleMain` passes
- [ ] No unused/wildcard imports
- [ ] Public APIs have Javadoc (where applicable)
- [ ] Names reveal intent (no Manager/Helper/Impl without strong reason)
- [ ] Methods are small and single-purpose

> Reminder: Checkstyle is here to help us keep the codebase healthy and scalable. Passing it locally saves time in CI and in code reviews.
