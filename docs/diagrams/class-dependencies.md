# Class and Package Dependencies

## Core Interface Hierarchy

```mermaid
classDiagram
    class SttEngine {
        <<interface>>
        +initialize()
        +transcribe(byte[]) TranscriptionResult
        +isHealthy() boolean
        +close()
        +getEngineName() String
        +consumeTokens() Optional~List~String~~
        +consumeRawJson() Optional~String~
    }

    class AbstractSttEngine {
        <<abstract>>
        #Object lock
        #boolean initialized
        #boolean closed
        +initialize() final
        +isHealthy() final
        +close() final
        #doInitialize()* abstract
        #doClose()* abstract
        #ensureInitialized() final
        #handleTranscriptionError() final
    }

    class VoskSttEngine {
        -VoskConfig config
        -Model model
        -ConcurrencyGuard guard
        -ApplicationEventPublisher publisher
        +transcribe(byte[]) TranscriptionResult
        #doInitialize()
        #doClose()
        +getEngineName() String
    }

    class WhisperSttEngine {
        -WhisperConfig config
        -WhisperProcessManager manager
        -ConcurrencyGuard guard
        -ApplicationEventPublisher publisher
        -boolean jsonMode
        +transcribe(byte[]) TranscriptionResult
        #doInitialize()
        #doClose()
        +getEngineName() String
        +consumeTokens() Optional~List~String~~
        +consumeRawJson() Optional~String~
    }

    SttEngine <|.. AbstractSttEngine
    AbstractSttEngine <|-- VoskSttEngine
    AbstractSttEngine <|-- WhisperSttEngine

    VoskSttEngine --> ConcurrencyGuard : uses
    WhisperSttEngine --> ConcurrencyGuard : uses
    WhisperSttEngine --> WhisperProcessManager : delegates to
```

## Reconciliation Strategy Pattern

```mermaid
classDiagram
    class TranscriptReconciler {
        <<interface>>
        +reconcile(EngineResult, EngineResult) TranscriptionResult
        +getStrategyName() String
    }

    class AbstractReconciler {
        <<abstract>>
        #selectBestResult(EngineResult, EngineResult, String) TranscriptionResult
    }

    class SimpleReconciler {
        +reconcile() TranscriptionResult
    }

    class ConfidenceReconciler {
        +reconcile() TranscriptionResult
    }

    class WordOverlapReconciler {
        -double threshold
        +reconcile() TranscriptionResult
        -calculateOverlap() double
    }

    class LongerTextReconciler {
        +reconcile() TranscriptionResult
    }

    class DiffBasedReconciler {
        +reconcile() TranscriptionResult
        -calculateEditDistance() int
    }

    TranscriptReconciler <|.. AbstractReconciler
    AbstractReconciler <|-- SimpleReconciler
    AbstractReconciler <|-- ConfidenceReconciler
    AbstractReconciler <|-- WordOverlapReconciler
    AbstractReconciler <|-- LongerTextReconciler
    AbstractReconciler <|-- DiffBasedReconciler
```

## Hotkey Trigger Strategy Pattern

```mermaid
classDiagram
    class HotkeyTrigger {
        <<interface>>
        +name() String
        +onKeyPressed(NormalizedKeyEvent) boolean
        +onKeyReleased(NormalizedKeyEvent) boolean
    }

    class SingleKeyTrigger {
        -String targetKey
        -boolean held
        +onKeyPressed() boolean
        +onKeyReleased() boolean
    }

    class DoubleTapTrigger {
        -String targetKey
        -int thresholdMs
        -long lastPressTime
        -boolean active
        +onKeyPressed() boolean
        +onKeyReleased() boolean
    }

    class ModifierCombinationTrigger {
        -String primaryKey
        -Set~String~ requiredModifiers
        -boolean held
        +onKeyPressed() boolean
        +onKeyReleased() boolean
    }

    HotkeyTrigger <|.. SingleKeyTrigger
    HotkeyTrigger <|.. DoubleTapTrigger
    HotkeyTrigger <|.. ModifierCombinationTrigger

    class HotkeyTriggerFactory {
        +from(HotkeyProperties) HotkeyTrigger
    }

    HotkeyTriggerFactory ..> HotkeyTrigger : creates
```

## Orchestration Layer Composition

```mermaid
classDiagram
    class DualEngineOrchestrator {
        -CaptureOrchestrator captureOrch
        -TranscriptionOrchestrator transcriptionOrch
        -HotkeyManager hotkeyManager
        -ApplicationEventPublisher publisher
        +onHotkeyPressed(HotkeyPressedEvent)
        +onHotkeyReleased(HotkeyReleasedEvent)
        +shutdown()
    }

    class CaptureOrchestrator {
        -AudioCaptureService captureService
        -CaptureStateMachine stateMachine
        -ApplicationEventPublisher publisher
        +startCapture() UUID
        +stopCapture(UUID) byte[]
        +cancelCapture()
    }

    class TranscriptionOrchestrator {
        -ParallelSttService parallelStt
        -TranscriptReconciler reconciler
        -FallbackManager fallbackManager
        -ReconciliationDependencies deps
        +transcribeAndType(byte[], UUID)
    }

    class CaptureStateMachine {
        -Lock lock
        -UUID activeSession
        +startCapture(UUID) boolean
        +stopCapture(UUID) boolean
        +cancelCapture() UUID
        +getActiveSession() UUID
        +isActive() boolean
    }

    DualEngineOrchestrator --> CaptureOrchestrator : delegates
    DualEngineOrchestrator --> TranscriptionOrchestrator : delegates
    CaptureOrchestrator --> CaptureStateMachine : uses
    CaptureOrchestrator --> AudioCaptureService : uses
    TranscriptionOrchestrator --> ParallelSttService : uses
    TranscriptionOrchestrator --> TranscriptReconciler : uses
    TranscriptionOrchestrator --> FallbackManager : uses
```

## Package Dependency Graph

```mermaid
graph TB
    subgraph presentation["com.phillippitts.speaktomack.presentation"]
        Controller[Controllers<br/>DTOs<br/>ExceptionHandlers]
    end

    subgraph service["com.phillippitts.speaktomack.service"]
        Orch[orchestration]
        STT[stt + stt.parallel]
        Audio[audio.capture]
        Hotkey[hotkey]
        Reconcile[reconcile]
        Fallback[fallback]
        Typing[typing]
    end

    subgraph domain["com.phillippitts.speaktomack.domain"]
        Entities[TranscriptionResult<br/>AudioFormat]
    end

    subgraph config["com.phillippitts.speaktomack.config"]
        Props[Properties<br/>Beans]
    end

    subgraph exception["com.phillippitts.speaktomack.exception"]
        Exceptions[TranscriptionException<br/>InvalidAudioException]
    end

    subgraph util["com.phillippitts.speaktomack.util"]
        Utils[TimeUtils<br/>ProcessTimeouts]
    end

    Controller --> Orch
    Orch --> Audio
    Orch --> STT
    Orch --> Hotkey
    Orch --> Reconcile
    Orch --> Fallback
    Fallback --> Typing
    STT --> domain
    Reconcile --> domain
    Audio --> domain

    Controller --> exception
    STT --> exception
    Audio --> exception

    STT --> config
    Audio --> config
    Hotkey --> config
    Orch --> config

    STT --> util
    Audio --> util

    style presentation fill:#e1f5ff
    style service fill:#fff4e1
    style domain fill:#c8e6c9
    style config fill:#f3e5f5
    style exception fill:#ffcdd2
    style util fill:#fff9c4
```

## Dependency Rules

### Allowed Dependencies (Clean Architecture)

```mermaid
flowchart LR
    Pres[Presentation] --> Svc[Service]
    Svc --> Domain[Domain]
    Pres --> Domain
    Svc --> Config[Config]
    Pres --> Config
    Svc --> Exception[Exception]
    Pres --> Exception
    Svc --> Util[Util]

    style Pres fill:#e1f5ff
    style Svc fill:#fff4e1
    style Domain fill:#c8e6c9
    style Config fill:#f3e5f5
    style Exception fill:#ffcdd2
    style Util fill:#fff9c4
```

### Forbidden Dependencies

| From | To | Reason |
|------|-----|--------|
| Domain | Service | Domain must be pure, no service dependencies |
| Domain | Config | Domain must be configuration-agnostic |
| Config | Service | Configuration should not depend on business logic |
| Util | Service | Utilities must be reusable across layers |
| Exception | Service | Exceptions are cross-cutting concerns |

## Key Design Patterns by Package

| Package | Pattern | Implementation |
|---------|---------|----------------|
| `service.stt` | Template Method | AbstractSttEngine (doInitialize/doClose) |
| `service.reconcile` | Strategy | TranscriptReconciler interface + 5 implementations |
| `service.hotkey` | Strategy + Factory | HotkeyTrigger + HotkeyTriggerFactory |
| `service.stt` | Adapter | VoskSttEngine wraps JNI, WhisperSttEngine wraps binary |
| `service.orchestration` | Facade | DualEngineOrchestrator coordinates subsystems |
| `service.*` | Observer | ApplicationEventPublisher for loose coupling |
| `service.stt` | Object Pool | ConcurrencyGuard with Semaphore |

## Thread Safety Guarantees

| Class | Thread Safety | Mechanism |
|-------|---------------|-----------|
| VoskSttEngine | Thread-safe | Per-call recognizer, synchronized model access |
| WhisperSttEngine | Thread-safe | Per-call temp files, synchronized token cache |
| AbstractSttEngine | Thread-safe | Synchronized state transitions (lock) |
| CaptureStateMachine | Thread-safe | ReentrantLock for all state access |
| PcmRingBuffer | Thread-safe | Synchronized methods |
| ConcurrencyGuard | Thread-safe | Semaphore for bounded concurrency |
| DefaultParallelSttService | Thread-safe | CompletableFuture with bounded executor |
| HotkeyManager | Thread-safe | CopyOnWriteArrayList for listeners |
| All Reconcilers | Thread-safe | Stateless (no shared mutable state) |
| FallbackManager | Thread-safe | Stateless operations |

## Immutable Value Objects

All domain objects are immutable Java 17 records:

```mermaid
classDiagram
    class TranscriptionResult {
        <<record>>
        +String text
        +double confidence
        +String engineUsed
    }

    class EngineResult {
        <<record>>
        +String text
        +double confidence
        +List~String~ tokens
        +long durationMs
        +String engineName
        +String rawJson
    }

    class NormalizedKeyEvent {
        <<record>>
        +Type type
        +String key
        +Set~String~ modifiers
        +long whenMillis
    }

    class VoskConfig {
        <<record>>
        +String modelPath
        +int sampleRate
        +int maxAlternatives
    }

    class WhisperConfig {
        <<record>>
        +String binaryPath
        +String modelPath
        +int timeoutSeconds
        +String language
        +int threads
    }
```

## Configuration Binding Flow

```mermaid
flowchart LR
    Props[application.properties] --> Spring[Spring Boot]
    Spring --> VoskProps[@ConfigurationProperties]
    Spring --> WhisperProps[@ConfigurationProperties]
    Spring --> AudioProps[@ConfigurationProperties]
    Spring --> HotkeyProps[@ConfigurationProperties]

    VoskProps --> VoskConfig[VoskConfig record]
    WhisperProps --> WhisperConfig[WhisperConfig record]
    AudioProps --> AudioCaptureProperties[AudioCaptureProperties]
    HotkeyProps --> HotkeyProperties[HotkeyProperties]

    VoskConfig --> Bean1[@Bean VoskSttEngine]
    WhisperConfig --> Bean2[@Bean WhisperSttEngine]

    style Props fill:#f3e5f5
    style Spring fill:#e1f5ff
    style Bean1 fill:#c8e6c9
    style Bean2 fill:#c8e6c9
```
