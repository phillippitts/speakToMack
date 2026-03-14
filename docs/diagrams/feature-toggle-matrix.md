# Feature Toggle Matrix

Complete reference for all feature toggles in the blckvox application,
documenting bean-level toggles that control Spring component loading and
behavioral toggles that modify runtime behavior without affecting bean creation.

---

## 1. Feature Toggle Decision Flowchart

Shows each primary toggle as a decision diamond, the beans and subsystems
activated for each path, and inter-toggle dependencies.

```mermaid
flowchart TD
    Start([Application Startup]) --> V["stt.validation.enabled?"]

    %% ── Validation ──
    V -->|true| VAL[ModelValidationService]
    V -->|false| SKIPVAL[Skip model validation]

    VAL -->|Vosk model missing| FAIL1([ModelNotFoundException<br/>fail-fast])
    VAL -->|Whisper binary missing| FAIL2([ModelNotFoundException<br/>fail-fast])
    VAL -->|All models present| LC

    SKIPVAL --> LC

    %% ── Live Caption ──
    LC{"live-caption.enabled?"}
    LC -->|true| LCBEANS["JavaFxLifecycle<br/>VoskStreamingService<br/>LiveCaptionManager<br/>LiveCaptionWindow"]
    LC -->|false| NOLC["No JavaFX init<br/>No streaming recognizer<br/>No tray checkbox<br/>Zero overhead"]

    LCBEANS -->|Requires| VOSKMODEL[(Vosk model at<br/>stt.vosk.model-path)]

    %% ── Tray ──
    LC --> TRAY
    TRAY{"tray.enabled?"}
    TRAY -->|true| TRAYBEANS["SystemTrayManager<br/>Menu bar icon<br/>Start / Stop items"]
    TRAY -->|false| NOTRAY["No tray icon<br/>No visual state<br/>No menu controls"]

    TRAYBEANS --> TRAYCAP{"live-caption.enabled<br/>also true?"}
    TRAYCAP -->|Yes| CHECKBOX["Tray includes<br/>Live Caption checkbox"]
    TRAYCAP -->|No| NOCHECKBOX["Tray omits<br/>Live Caption checkbox"]

    %% ── Reconciliation ──
    TRAY --> REC
    REC{"stt.reconciliation.enabled?"}
    REC -->|true| RECBEANS["ReconciliationDependencies<br/>ParallelSttService<br/>TranscriptReconciler<br/>ReconciledTranscriptionOrchestrator"]
    REC -->|false| NOREC["DefaultTranscriptionOrchestrator<br/>EngineSelectionStrategy<br/>single-engine path"]

    RECBEANS --> STRAT{"stt.reconciliation.strategy?"}
    STRAT -->|simple| SIMPLE[SimplePreferenceReconciler]
    STRAT -->|confidence| CONF[ConfidenceReconciler]
    STRAT -->|overlap| OVERLAP[WordOverlapReconciler]

    OVERLAP -->|Best with| WJSON["stt.whisper.output=json<br/>word-level tokens"]

    %% ── Watchdog ──
    REC --> WD
    WD{"stt.watchdog.enabled?"}
    WD -->|true| WDBEANS["SttEngineWatchdog<br/>Sliding window budget<br/>3 restarts / 60 min<br/>10 min cooldown<br/>Confidence blacklisting"]
    WD -->|false| NOWD["No auto-restart<br/>Engine failures permanent<br/>until app restart"]

    %% ── Dynamic Scaling ──
    WD --> DS
    DS{"stt.concurrency<br/>.dynamic-scaling-enabled?"}
    DS -->|true| DSBEANS["ConcurrencyScaler<br/>CPU/memory monitoring<br/>Dynamic permit adjustment"]
    DS -->|false| NODS["Static limits<br/>vosk-max=4<br/>whisper-max=2"]

    %% ── Styles ──
    style Start fill:#e0e0e0,stroke:#333
    style VAL fill:#c8e6c9,stroke:#2e7d32
    style LCBEANS fill:#c8e6c9,stroke:#2e7d32
    style TRAYBEANS fill:#c8e6c9,stroke:#2e7d32
    style RECBEANS fill:#c8e6c9,stroke:#2e7d32
    style WDBEANS fill:#c8e6c9,stroke:#2e7d32
    style DSBEANS fill:#c8e6c9,stroke:#2e7d32
    style SKIPVAL fill:#fff4e1,stroke:#f57f17
    style NOLC fill:#fff4e1,stroke:#f57f17
    style NOTRAY fill:#fff4e1,stroke:#f57f17
    style NOREC fill:#fff4e1,stroke:#f57f17
    style NOWD fill:#fff4e1,stroke:#f57f17
    style NODS fill:#fff4e1,stroke:#f57f17
    style FAIL1 fill:#ffcdd2,stroke:#c62828
    style FAIL2 fill:#ffcdd2,stroke:#c62828
    style VOSKMODEL fill:#e1f5ff,stroke:#0277bd
```

---

## 2. Configuration Scenarios Table

Realistic configuration profiles showing which toggles are active, the
resulting subsystem footprint, expected latency characteristics, and
intended use case.

| Scenario | `live-caption` | `reconciliation` | `watchdog` | `tray` | `validation` | `dynamic-scaling` | Active Subsystems | Latency Profile | Use Case |
|---|---|---|---|---|---|---|---|---|---|
| **Default (Production)** | true | true (overlap) | true | true | true | false | All subsystems active. Dual-engine parallel STT with overlap reconciliation. Tray icon with live caption checkbox. Watchdog monitors health. Static concurrency. | Medium -- two engines run in parallel, reconciliation adds post-processing | Full-featured desktop deployment on macOS with all safety nets |
| **Fast Mode** | false | false | true | true | true | false | Single-engine STT via EngineSelectionStrategy. Tray icon without caption checkbox. Watchdog protects the single engine. | Low -- only one engine runs per transcription, no reconciliation overhead | When speed matters more than accuracy, or hardware is limited |
| **Maximum Accuracy** | true | true (overlap) | true | true | true | true | Everything active plus dynamic scaling. ConcurrencyScaler adjusts permits under load. JSON whisper output for word-level tokens. | Higher -- dual-engine plus dynamic scaling overhead, but best accuracy | Transcription-critical workloads where accuracy is paramount |
| **Headless / CI** | false | true (simple) | true | false | true | false | No UI components. No tray, no JavaFX. Dual-engine reconciliation still active. | Medium -- dual-engine without UI overhead | Server-side or CI/CD pipeline transcription, no display available |
| **Minimal** | false | false | false | false | false | false | Bare STT only. Single engine, no validation, no watchdog, no UI. Smallest bean graph. | Lowest -- single engine, zero overhead from ancillary subsystems | Development / debugging with mock engines, test profiles |
| **Testing (Unit)** | false | false | false | false | false | false | Same as Minimal. `stt.validation.enabled=false` prevents ModelNotFoundException with mocked engines. | N/A -- unit tests with mocked services | `@SpringBootTest` with `application-test.properties` |
| **Presentation Demo** | true | false | true | true | true | false | Single-engine for speed with full UI. Live captions provide visual feedback for demos. Watchdog keeps engine alive. | Low-Medium -- single engine with UI overlay | Live demos, presentations, screen recordings |
| **High-Throughput** | false | true (confidence) | true | false | false | true | No UI. Confidence-based reconciliation skips dual-engine when Vosk is confident. Dynamic scaling adapts to load. | Variable -- single-engine when confident, dual-engine when uncertain | Batch processing, multi-user server scenarios |

---

## 3. Toggle Dependency Graph

Shows which toggles depend on, interact with, or enhance other toggles.
Solid arrows indicate hard dependencies; dashed arrows indicate soft
interactions where one toggle enhances or modifies the behavior of another.

```mermaid
graph LR
    subgraph Primary["Primary Toggles (Bean-Level)"]
        LC["live-caption.enabled"]
        REC["stt.reconciliation.enabled"]
        WD["stt.watchdog.enabled"]
        TRAY["tray.enabled"]
        VAL["stt.validation.enabled"]
        DS["stt.concurrency<br/>.dynamic-scaling-enabled"]
    end

    subgraph Secondary["Secondary Toggles (Behavioral)"]
        TM["hotkey.toggle-mode"]
        ER["typing.enable-robot"]
        CF["typing.clipboard-only-fallback"]
        RC["typing.restore-clipboard"]
        WO["stt.whisper.output"]
    end

    subgraph External["External Dependencies"]
        VOSK[(Vosk model)]
        WHISPER[(Whisper binary<br/>+ model)]
        JFX[(JavaFX runtime)]
        AWT[(java.awt.Robot<br/>+ Accessibility)]
    end

    %% Hard dependencies (solid arrows)
    LC -->|"requires"| VOSK
    LC -->|"requires"| JFX
    VAL -->|"validates"| VOSK
    VAL -->|"validates"| WHISPER
    ER -->|"requires"| AWT

    %% Soft interactions (dashed arrows)
    LC -.->|"adds checkbox to"| TRAY
    REC -.->|"strategy=overlap<br/>works best with"| WO
    WD -.->|"monitors engines<br/>used by"| REC
    DS -.->|"adjusts permits<br/>used by"| REC
    CF -.->|"overrides"| ER
    RC -.->|"only relevant<br/>when"| CF

    %% Cross-cutting
    WD -.->|"can blacklist engine<br/>affecting"| REC

    style Primary fill:#e1f5ff,stroke:#0277bd
    style Secondary fill:#fff9c4,stroke:#f9a825
    style External fill:#f3e5f5,stroke:#7b1fa2
```

### Dependency Notes

| Relationship | Type | Detail |
|---|---|---|
| `live-caption` --> Vosk model | Hard | `VoskStreamingService` loads the model at `stt.vosk.model-path` for real-time streaming recognition |
| `live-caption` --> JavaFX | Hard | `JavaFxLifecycle` calls `Platform.startup()` to initialize the JavaFX toolkit |
| `live-caption` --> `tray` | Soft | When both are true, `SystemTrayManager` adds a "Live Caption" `CheckboxMenuItem`; when `live-caption` is false the checkbox is omitted |
| `reconciliation` (overlap) --> `whisper.output=json` | Soft | `WordOverlapReconciler` benefits from word-level timestamp tokens only available in JSON mode |
| `watchdog` --> `reconciliation` | Soft | Watchdog can blacklist a low-confidence engine, which degrades dual-engine reconciliation to single-engine fallback |
| `dynamic-scaling` --> `reconciliation` | Soft | ConcurrencyScaler adjusts the permits in `DynamicConcurrencyGuard`, which gates how many parallel engine invocations `ParallelSttService` can run |
| `clipboard-only-fallback` --> `enable-robot` | Override | When `clipboard-only-fallback=true`, the Robot API is bypassed regardless of `enable-robot` |
| `restore-clipboard` --> paste path | Conditional | Only meaningful when text is delivered via clipboard paste (either `clipboard-only-fallback=true` or Robot fallback) |
| `validation` --> Vosk/Whisper | Hard | `ModelValidationService` checks directory structure (Vosk) and binary+model existence (Whisper) at startup; throws `ModelNotFoundException` on failure |

---

## 4. Runtime Behavior Matrix

Truth table for the 6 primary toggles in realistic combinations.
Each row describes the resulting system behavior. Only meaningful
combinations are listed (not all 64 permutations).

```mermaid
block-beta
    columns 8
    block:header
        columns 8
        h1["live-caption"] h2["reconciliation"] h3["watchdog"] h4["tray"] h5["validation"] h6["dyn-scaling"] h7["Profile"] h8["Behavior"]
    end
```

| # | `live-caption` | `reconciliation` | `watchdog` | `tray` | `validation` | `dyn-scaling` | Profile Name | System Behavior |
|---|:-:|:-:|:-:|:-:|:-:|:-:|---|---|
| 1 | T | T | T | T | T | F | **Default** | Full UI. Dual-engine with overlap reconciliation. Watchdog auto-restarts. Tray with caption checkbox. Models validated at startup. Static concurrency (vosk=4, whisper=2). |
| 2 | F | F | T | T | T | F | **Fast Mode** | No JavaFX. Single engine via `EngineSelectionStrategy`. Watchdog protects active engine. Tray shows state but no caption checkbox. Models validated. |
| 3 | F | T | T | F | T | F | **Headless** | No UI at all. Dual-engine reconciliation runs server-side. Watchdog monitors both engines. Models validated. Suitable for headless Linux / CI. |
| 4 | T | T | T | T | T | T | **Max Accuracy** | Everything on. Dynamic scaling adjusts concurrency based on CPU (>80%) and memory (>85%). Best accuracy at cost of resource monitoring overhead. |
| 5 | F | F | F | F | F | F | **Minimal / Test** | Bare bones. Single engine, no validation (won't throw on missing models), no watchdog, no UI. Ideal for `@SpringBootTest` with mocks. |
| 6 | T | F | T | T | T | F | **Demo** | Live captions for visual feedback. Single engine for speed. Watchdog keeps it alive. Tray shows recording state. Good for presentations. |
| 7 | F | T | T | F | F | T | **High-Throughput** | No UI, no validation. Dual-engine with dynamic scaling. Watchdog auto-recovers. Skips validation for container deployments with pre-validated images. |
| 8 | F | F | T | T | F | F | **Dev Sandbox** | Tray for manual Start/Stop. Single engine. Watchdog recovery. No validation (allows hot-swapping models). No caption overlay. |

### Reading the Matrix

- **T** = toggle is `true` (bean is loaded / feature is active)
- **F** = toggle is `false` (bean is skipped / feature is inactive)
- Row 1 matches the defaults shipped in `application.properties`
- Row 5 matches the typical `application-test.properties` override

---

## 5. Secondary Toggles Reference

Behavioral toggles that do not control Spring bean creation but modify
runtime behavior within already-loaded components.

### Secondary Toggle Summary

```mermaid
flowchart TD
    subgraph HotkeyBehavior["Hotkey Behavior"]
        HT{"hotkey.toggle-mode?"}
        HT -->|"true (default)"| TOGGLE["Toggle mode<br/>1st press = start recording<br/>2nd press = stop + transcribe"]
        HT -->|false| PTT["Push-to-talk mode<br/>Press = start recording<br/>Release = stop + transcribe"]
    end

    subgraph TypingPipeline["Typing Output Pipeline"]
        ER{"typing.enable-robot?"}
        ER -->|"true (default)"| ROBOT["java.awt.Robot API<br/>Simulates Cmd+V keystroke"]
        ER -->|false| CLIP["Clipboard-only path"]

        CF{"typing.clipboard-only-fallback?"}
        CF -->|"true"| CLIPONLY["Skip Robot entirely<br/>Copy to clipboard<br/>User pastes manually"]
        CF -->|"false (default)"| TRYROBOT["Try Robot first<br/>Fall back to clipboard<br/>on SecurityException"]

        ROBOT --> RC
        CLIPONLY --> RC
        TRYROBOT --> RC

        RC{"typing.restore-clipboard?"}
        RC -->|"true (default)"| RESTORE["Save clipboard before paste<br/>Restore after typing complete"]
        RC -->|false| NORESTORE["Clipboard left with<br/>transcribed text"]
    end

    subgraph WhisperOutput["Whisper Output Mode"]
        WO{"stt.whisper.output?"}
        WO -->|"text"| TEXT["Plain text stdout<br/>No word-level timestamps<br/>No segment boundaries"]
        WO -->|"json (default)"| JSON["JSON stdout<br/>Word-level tokens<br/>Segment timestamps<br/>Enables pause detection<br/>Enables overlap reconciler"]
    end

    style HotkeyBehavior fill:#e1f5ff,stroke:#0277bd
    style TypingPipeline fill:#fff9c4,stroke:#f9a825
    style WhisperOutput fill:#c8e6c9,stroke:#2e7d32
```

### Secondary Toggle Detail Table

| Property | Default | Type | Controlled By | Effect When True | Effect When False | Notes |
|---|---|---|---|---|---|---|
| `hotkey.toggle-mode` | `true` | boolean | `HotkeyProperties` | Toggle: first activation starts recording, second stops and triggers transcription | Push-to-talk: key-down starts, key-up stops and triggers transcription | Both modes use the same hotkey (`hotkey.key` + `hotkey.type`) |
| `typing.enable-robot` | `true` | boolean | `TypingService` | Uses `java.awt.Robot` to simulate paste keystroke (Cmd+V on macOS) | Robot API is never instantiated; all output goes through clipboard | Requires macOS Accessibility permission for Robot |
| `typing.clipboard-only-fallback` | `false` | boolean | `TypingService` | Skips Robot entirely; copies text to clipboard and expects user to paste | Attempts Robot first, falls back to clipboard on failure | Overrides `enable-robot` when true |
| `typing.restore-clipboard` | `true` | boolean | `TypingService` | Saves current clipboard content before paste, restores it afterward | Clipboard retains the transcribed text after paste | Only relevant when clipboard is used (always for fallback, always for clipboard-only) |
| `stt.whisper.output` | `json` | enum | `WhisperSttEngine` | JSON output with word-level tokens and segment timestamps; enables `WordOverlapReconciler` and silence-gap paragraph breaks | Plain text output only; `WordOverlapReconciler` degrades, no segment-based pause detection | Must be `json` for `stt.orchestration.silence-gap-ms` to work with Whisper |

### Typing Pipeline Decision Tree

The following table shows the effective output method based on the
combination of typing toggles:

| `enable-robot` | `clipboard-only-fallback` | `restore-clipboard` | Effective Behavior |
|:-:|:-:|:-:|---|
| T | F | T | Robot pastes via Cmd+V. Clipboard saved before, restored after. |
| T | F | F | Robot pastes via Cmd+V. Clipboard left with transcribed text. |
| T | T | T | Robot skipped. Text copied to clipboard. User pastes manually. Clipboard restored after delay. |
| T | T | F | Robot skipped. Text copied to clipboard. Clipboard keeps transcribed text. |
| F | F | T | Robot disabled. Clipboard fallback used. Clipboard saved and restored. |
| F | F | F | Robot disabled. Clipboard fallback used. Clipboard keeps transcribed text. |
| F | T | T | Same as `enable-robot=F, clipboard-only=F, restore=T` -- both paths converge to clipboard. |
| F | T | F | Same as `enable-robot=F, clipboard-only=F, restore=F` -- both paths converge to clipboard. |

---

## Appendix: Source Code References

All toggle annotations verified against source:

| Toggle Property | Annotation | Source File |
|---|---|---|
| `live-caption.enabled` | `@ConditionalOnProperty(name = "live-caption.enabled", havingValue = "true")` | `JavaFxLifecycle.java`, `LiveCaptionManager.java`, `VoskStreamingService.java` |
| `stt.reconciliation.enabled` | `@ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled", havingValue = "true")` | `ReconciliationConfig.java`, `ReconciliationDependencies.java`, `OrchestrationConfig.java` |
| `stt.watchdog.enabled` | `@ConditionalOnProperty(prefix = "stt.watchdog", name = "enabled", havingValue = "true", matchIfMissing = true)` | `SttEngineWatchdog.java` |
| `tray.enabled` | `@ConditionalOnProperty(name = "tray.enabled", matchIfMissing = true)` | `SystemTrayManager.java` |
| `stt.validation.enabled` | `@ConditionalOnProperty(name = "stt.validation.enabled", havingValue = "true", matchIfMissing = true)` | `ModelValidationService.java` |
| `stt.concurrency.dynamic-scaling-enabled` | `@ConditionalOnProperty(prefix = "stt.concurrency", name = "dynamic-scaling-enabled", havingValue = "true")` | `ConcurrencyScaler.java` |

### matchIfMissing Behavior

Toggles with `matchIfMissing = true` default to **enabled** even when the
property is absent from `application.properties`. Toggles without
`matchIfMissing` (or with it set to `false`) default to **disabled** when
the property is absent.

| Toggle | `matchIfMissing` | Default When Property Absent |
|---|:-:|---|
| `live-caption.enabled` | not set | **Disabled** (bean not loaded) |
| `stt.reconciliation.enabled` | not set | **Disabled** (single-engine path via `matchIfMissing = true` on the `havingValue = "false"` alternative bean) |
| `stt.watchdog.enabled` | true | **Enabled** |
| `tray.enabled` | true | **Enabled** |
| `stt.validation.enabled` | true | **Enabled** |
| `stt.concurrency.dynamic-scaling-enabled` | not set | **Disabled** (static concurrency) |
