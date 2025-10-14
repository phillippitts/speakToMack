# speakToMack Architecture Overview

## High-Level Component Diagram

```mermaid
graph TB
    User[User] -->|Press Hotkey| HM[HotkeyManager]
    HM -->|HotkeyPressedEvent| Orch[DictationOrchestrator]
    Orch -->|Start| AC[AudioCaptureService]
    AC -->|Audio Buffer| Orch
    Orch -->|Release Hotkey| PSS[ParallelSttService]
    
    PSS -->|Thread 1| Vosk[VoskSttEngine<br/>~100ms]
    PSS -->|Thread 2| Whisper[WhisperSttEngine<br/>~1-2s]
    
    Vosk -->|SttResult| Rec[TranscriptReconciler<br/>Strategy Pattern]
    Whisper -->|SttResult| Rec
    
    Rec -->|TranscriptionResult| Orch
    Orch -->|Text| FM[FallbackManager]
    FM -->|Paste/Clipboard/Notify| TS[TypingService]
    TS -->|Cmd+V| User
    
    style PSS fill:#f9f,stroke:#333,stroke-width:2px
    style Rec fill:#bbf,stroke:#333,stroke-width:2px
    style FM fill:#bfb,stroke:#333,stroke-width:2px
```

## 3-Tier Architecture Layers

```mermaid
graph LR
    subgraph Presentation["Presentation Layer (Tier 1)"]
        Controllers[REST Controllers<br/>DTOs<br/>Exception Handlers]
    end
    
    subgraph Service["Service Layer (Tier 2)"]
        Orchestrator[DictationOrchestrator]
        STT[STT Engines]
        Audio[Audio Capture]
        Hotkey[Hotkey Manager]
        Reconcile[Reconciliation]
    end
    
    subgraph Data["Data Access Layer (Tier 3)"]
        Repos[Repositories<br/>JPA Entities]
        DB[(PostgreSQL)]
    end
    
    Presentation -->|Uses| Service
    Service -->|Uses| Data
    Data -->|JDBC| DB
    
    style Presentation fill:#e1f5ff
    style Service fill:#fff4e1
    style Data fill:#e8f5e9
```

## Design Patterns Applied

```mermaid
graph TD
    subgraph Patterns["Design Patterns in speakToMack"]
        Strategy[Strategy Pattern<br/>TranscriptReconciler<br/>5 implementations]
        Factory[Factory Pattern<br/>HotkeyTriggerFactory<br/>SttEngineFactory]
        Adapter[Adapter Pattern<br/>VoskSttEngine wraps JNI<br/>WhisperSttEngine wraps binary]
        Observer[Observer Pattern<br/>Hotkey events<br/>Spring ApplicationEvent]
    end
    
    style Strategy fill:#ffebee
    style Factory fill:#e8f5e9
    style Adapter fill:#e3f2fd
    style Observer fill:#fff9c4
```

## Key Architectural Characteristics

| Characteristic | Priority | Implementation |
|----------------|----------|----------------|
| **Privacy** | Critical | 100% local processing, no cloud APIs |
| **Resilience** | Critical | Dual-engine fallback, 3-tier typing fallback |
| **Performance** | High | Parallel execution (latency = max, not sum) |
| **Extensibility** | High | Strategy pattern for reconciliation/hotkeys |
| **Observability** | High | Log4j 2 with MDC, Prometheus metrics |
| **Maintainability** | High | Clean Code principles, Checkstyle enforcement |
