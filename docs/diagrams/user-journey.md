# User Journey Map

## First-Time User Onboarding Journey

```mermaid
journey
    title First-Time User: From Download to First Dictation
    section Install
        Download JAR: 3: User
        Download models (2 GB): 2: User
        Wait 10-15 min: 1: User
        Build whisper.cpp: 2: User
    section Configuration
        Update application.properties: 3: User
        Set whisper binary path: 3: User
        Choose hotkey type: 4: User
    section Permissions
        Run app first time: 4: User
        macOS security warning: 1: User
        Grant Microphone access: 3: User
        Grant Accessibility access: 4: User
        Test hotkey detection: 5: User
    section First Dictation
        Press hotkey: 5: User
        See feedback (cursor change): 5: User
        Speak into microphone: 5: User
        Release hotkey: 5: User
        Text appears: 5: User, System
        Success!: 5: User
```

## End-to-End User Flow Timeline

```mermaid
gantt
    title Complete User Journey: Installation to Production Use
    dateFormat X
    axisFormat %M min

    section Installation
    Download JAR                    :0, 2min
    Download Vosk model (1.8 GB)    :2min, 10min
    Download Whisper model (147 MB) :2min, 3min
    Build whisper.cpp binary        :5min, 5min

    section Configuration
    Edit application.properties     :10min, 5min
    Choose hotkey settings          :15min, 2min

    section Permissions
    First launch                    :17min, 1min
    Microphone permission dialog    :18min, 30s
    Accessibility permission dialog :milestone, 18.5min, 0
    Navigate to System Settings     :18.5min, 1min
    Grant both permissions          :19.5min, 1min
    Restart app                     :20.5min, 30s

    section Testing
    Test hotkey detection           :21min, 1min
    First transcription attempt     :22min, 30s
    Verify text output              :22.5min, 30s

    section Production Use
    Daily use begins                :milestone, 23min, 0
```

## Decision Tree: Choosing Your Hotkey Type

```mermaid
flowchart TD
    Start([Choose Hotkey Type]) --> Q1{Need simple<br/>single-key?}

    Q1 -->|Yes| Q2{Key rarely used<br/>for other tasks?}
    Q1 -->|No| Q3{Comfortable with<br/>modifier combos?}

    Q2 -->|Yes| Single[✅ Single-Key Trigger<br/>e.g., F13, Right Command<br/><br/>Pros: Simple, fast<br/>Cons: May conflict]
    Q2 -->|No| DoubleTap[✅ Double-Tap Trigger<br/>e.g., Right Command 2x<br/><br/>Pros: No conflicts<br/>Cons: Slower, threshold tuning]

    Q3 -->|Yes| Combo[✅ Modifier Combination<br/>e.g., Cmd+Shift+D<br/><br/>Pros: No conflicts, familiar<br/>Cons: Two-handed operation]
    Q3 -->|No| DoubleTap

    Single --> Reserved{Key in reserved<br/>shortcuts list?}
    Reserved -->|Yes| Warning[⚠️ Warning at startup<br/>Choose different key]
    Reserved -->|No| Config1[Configure in<br/>application.properties]

    DoubleTap --> Threshold{Adjust threshold?<br/>Default: 300ms}
    Threshold -->|Too slow| Increase[Increase to 400-500ms]
    Threshold -->|Too sensitive| Decrease[Decrease to 200-250ms]
    Threshold -->|Good| Config2[Configure in<br/>application.properties]

    Combo --> Config3[Configure modifiers<br/>and primary key]

    Warning --> Start
    Increase --> Config2
    Decrease --> Config2

    Config1 --> Done([Start using app])
    Config2 --> Done
    Config3 --> Done

    style Single fill:#c8e6c9
    style DoubleTap fill:#fff4e1
    style Combo fill:#e1f5ff
    style Warning fill:#ffcdd2
```

## Typical Daily Usage Flow

```mermaid
flowchart LR
    Work[Working in<br/>any app] --> Think{Need to<br/>dictate?}

    Think -->|No| Work
    Think -->|Yes| Position[Position cursor<br/>at insertion point]

    Position --> Hotkey[Press hotkey]
    Hotkey --> Visual[Visual feedback:<br/>Cursor changes]
    Visual --> Speak[Speak clearly<br/>into mic]
    Speak --> Release[Release hotkey]
    Release --> Wait[Wait 1-2s]
    Wait --> Text[Text appears<br/>at cursor]
    Text --> Continue{Continue<br/>working?}

    Continue -->|Yes| Work
    Continue -->|No| Done([End session])

    style Hotkey fill:#e1f5ff
    style Speak fill:#fff4e1
    style Text fill:#c8e6c9
```

## User Experience Maturity Curve

```mermaid
graph LR
    subgraph Week1["Week 1: Learning"]
        Day1[Day 1-2<br/>Setup + Testing]
        Day2[Day 3-4<br/>Hotkey muscle memory]
        Day3[Day 5-7<br/>Confidence building]
    end

    subgraph Week2["Week 2-4: Proficiency"]
        Refine[Fine-tune hotkey<br/>threshold/type]
        Strategy[Try reconciliation<br/>strategies]
        Habits[Develop dictation<br/>habits]
    end

    subgraph Month2["Month 2+: Mastery"]
        Auto[Automatic hotkey<br/>triggering]
        Speed[Fast transcription<br/>workflow]
        Integrate[Seamless app<br/>integration]
    end

    Day1 --> Day2
    Day2 --> Day3
    Day3 --> Refine
    Refine --> Strategy
    Strategy --> Habits
    Habits --> Auto
    Auto --> Speed
    Speed --> Integrate

    style Week1 fill:#ffe0b2
    style Week2 fill:#fff4e1
    style Month2 fill:#c8e6c9
```

## Common User Scenarios

### Scenario 1: Writing Email

```mermaid
sequenceDiagram
    actor User
    participant Email as Email App
    participant STM as speakToMack

    User->>Email: Click compose
    User->>Email: Click in subject line
    User->>STM: Press hotkey
    Note over User,STM: Cursor changes to indicate recording
    User->>User: Say: "Meeting follow-up"
    User->>STM: Release hotkey
    STM-->>Email: "Meeting follow-up"
    User->>Email: Tab to body
    User->>STM: Press hotkey
    User->>User: Say: "Hi team, thanks for the productive discussion today..."
    User->>STM: Release hotkey
    STM-->>Email: Full paragraph text
    User->>Email: Send email
```

### Scenario 2: Coding with Dictation

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant IDE
    participant STM as speakToMack

    Dev->>IDE: Open function
    Dev->>IDE: Position cursor in comment block
    Dev->>STM: Press hotkey
    Dev->>Dev: Say: "TODO: Refactor this method to use Strategy pattern"
    Dev->>STM: Release hotkey
    STM-->>IDE: // TODO: Refactor this method to use Strategy pattern
    Note over Dev: Switch to typing for code
    Dev->>IDE: Write implementation
    Dev->>IDE: Add unit test comment
    Dev->>STM: Press hotkey
    Dev->>Dev: Say: "Test case: verify null input throws exception"
    Dev->>STM: Release hotkey
    STM-->>IDE: // Test case: verify null input throws exception
```

### Scenario 3: Note-Taking in Meeting

```mermaid
sequenceDiagram
    actor User
    participant Notes as Notes App
    participant STM as speakToMack

    loop Every few minutes
        User->>Notes: New bullet point
        User->>STM: Press hotkey
        User->>User: Summarize discussion point
        User->>STM: Release hotkey
        STM-->>Notes: Bullet text appears
        User->>Notes: Continue listening
    end

    Note over User,Notes: At meeting end
    User->>Notes: Review and edit notes
    User->>Notes: Format as needed
```

## Permission Setup Visual Guide

```mermaid
flowchart TB
    Start([First App Launch]) --> Mic[macOS Microphone<br/>Permission Dialog]

    Mic --> Allow1{Grant Access?}
    Allow1 -->|Yes| MicGranted[✅ Microphone Enabled]
    Allow1 -->|No| MicDenied[❌ Cannot capture audio<br/>App partially functional]

    MicGranted --> AccPrompt[Accessibility<br/>Permission Needed]
    MicDenied --> AccPrompt

    AccPrompt --> Manual[Manual Setup Required:<br/>System Settings → Privacy & Security]

    Manual --> AccSettings[Navigate to<br/>Accessibility section]
    AccSettings --> AddApp[Add speakToMack.jar<br/>or terminal app]
    AddApp --> AccGranted[✅ Accessibility Enabled]

    AccGranted --> FullFunc[✅ Full Functionality<br/>Text pasting works]

    MicDenied --> Limited1[⚠️ Limited Mode<br/>Clipboard only]
    AccPrompt -->|Skip| Limited2[⚠️ Limited Mode<br/>Clipboard only]

    style MicGranted fill:#c8e6c9
    style AccGranted fill:#c8e6c9
    style FullFunc fill:#c8e6c9
    style MicDenied fill:#ffcdd2
    style Limited1 fill:#ffe0b2
    style Limited2 fill:#ffe0b2
```

## User Mental Model: How It Works

```mermaid
graph TB
    subgraph UserView["What User Sees"]
        Press["1. Press hotkey<br/>(visual feedback)"]
        Speak["2. Speak into mic<br/>(indicator active)"]
        Release["3. Release hotkey<br/>(processing...)"]
        Text["4. Text appears<br/>(in current app)"]
    end

    subgraph Behind["What Happens Behind the Scenes"]
        Audio["Audio capture starts<br/>(ring buffer)"]
        Process["Both engines run<br/>(Vosk + Whisper)"]
        Choose["Best result selected<br/>(reconciliation)"]
        Paste["Text pasted<br/>(fallback chain)"]
    end

    Press --> Audio
    Speak --> Audio
    Release --> Process
    Process --> Choose
    Choose --> Paste
    Paste --> Text

    style UserView fill:#e1f5ff
    style Behind fill:#f3e5f5
```

## Success Metrics: User Satisfaction Journey

```mermaid
graph LR
    subgraph Phase1["Days 1-7: Setup"]
        Install[Successful<br/>installation]
        Permission[Permissions<br/>granted]
        FirstWork[First successful<br/>dictation]
    end

    subgraph Phase2["Weeks 2-4: Adoption"]
        Daily[Using daily]
        Fast[Faster than typing<br/>for some tasks]
        Confident[Confident with<br/>hotkey]
    end

    subgraph Phase3["Month 2+: Habit"]
        Auto[Automatic use<br/>without thinking]
        Prefer[Prefer dictation<br/>for long-form]
        Recommend[Would recommend<br/>to others]
    end

    Install --> Permission
    Permission --> FirstWork
    FirstWork --> Daily
    Daily --> Fast
    Fast --> Confident
    Confident --> Auto
    Auto --> Prefer
    Prefer --> Recommend

    style Phase1 fill:#ffe0b2
    style Phase2 fill:#fff4e1
    style Phase3 fill:#c8e6c9
```

## Typical Time Investment

| Activity | First Time | Subsequent | Frequency |
|----------|-----------|------------|-----------|
| **Initial setup** | 25-30 min | N/A | Once |
| **Model download** | 10-15 min | N/A | Once |
| **Permission granting** | 2-3 min | N/A | Once per macOS update |
| **Hotkey configuration** | 5-10 min | 2 min | Rare (when changing) |
| **First dictation test** | 30 sec | N/A | Once |
| **Daily dictation use** | N/A | 2-3 sec per dictation | Multiple times daily |
| **Troubleshooting** | 5-10 min | 1-2 min | Rare |

## Expected Outcomes by User Type

```mermaid
mindmap
    root((speakToMack<br/>User Personas))
        Writers
            Long-form content
            Email composition
            Blog posts
            Documentation
        Developers
            Code comments
            Git commit messages
            Documentation
            Issue descriptions
        Accessibility Users
            Primary input method
            Reduce typing strain
            RSI prevention
            Ergonomic workflow
        Power Users
            Hotkey mastery
            Custom reconciliation
            Workflow automation
            Maximum efficiency
```

## User Pain Points & Solutions

| Pain Point | When It Occurs | Solution |
|------------|----------------|----------|
| Hotkey not triggering | Double-tap too slow/fast | Adjust `hotkey.threshold-ms` (200-500ms range) |
| Wrong transcription | Noisy environment | Move to quieter space, adjust mic input level |
| Text doesn't paste | Accessibility permission denied | Grant permission in System Settings |
| Slow transcription | Both engines running | Enable `stt.reconcile.enabled=true` (conditional dual-engine) |
| App won't start | Models not downloaded | Run `./setup-models.sh` |
| Whisper timeout | Complex audio processing | Increase `stt.whisper.timeout-seconds` to 15-20 |

## Emotional Journey Arc

```mermaid
graph LR
    Start([Download]) --> Excited[😊 Excited<br/>New tool]
    Excited --> Setup[😐 Focused<br/>Installation]
    Setup --> FirstFail[😟 Frustrated<br/>Permissions/config]
    FirstFail --> Resolve[🧐 Determined<br/>Troubleshooting]
    Resolve --> FirstSuccess[😀 Satisfied<br/>First dictation works]
    FirstSuccess --> Learning[😊 Engaged<br/>Exploring features]
    Learning --> Proficient[😌 Confident<br/>Daily use]
    Proficient --> Advocate[😍 Delighted<br/>Recommending to others]

    style FirstFail fill:#ffcdd2
    style FirstSuccess fill:#c8e6c9
    style Advocate fill:#c8e6c9
```
