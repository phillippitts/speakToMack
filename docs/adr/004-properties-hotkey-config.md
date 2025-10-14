# ADR-004: Properties-Based Hotkey Configuration

## Status
Accepted (2025-01-14)

## Context
Users have different keyboard layouts, accessibility needs, and workflow preferences. Hardcoded hotkeys (e.g., `RIGHT_META`) in code force recompilation for customization.

**Requirements:**
- Support multiple trigger types (single key, modifier combo, double-tap)
- No code changes to modify hotkey
- Validate configuration at startup
- Extensible for future shortcuts (pause, cancel, voice commands)

## Decision
Externalize hotkey configuration via `application.yml` with factory-based trigger creation.

**Architecture:**
```yaml
hotkey:
  trigger:
    type: single-key              # single-key|double-tap|modifier-combination
    key: RIGHT_META               # JNativeHook key name
    modifiers: []                 # SHIFT, CONTROL, ALT, META
  
  combination:
    primary-key: D
    modifiers: [META, SHIFT]      # Cmd+Shift+D example
```

**Implementation:**
- `HotkeyProperties` class maps YAML to Java objects
- `HotkeyTriggerFactory` creates concrete trigger from config
- `HotkeyManager` uses injected trigger (Strategy pattern)
- `KeyCodeMapper` converts human-readable names to JNativeHook codes

## Consequences

### Positive
- ✅ **User customization** without recompilation
- ✅ **Validated at startup**: Invalid keys fail fast with clear error
- ✅ **Extensible**: Add new trigger types (triple-tap, hold duration) easily
- ✅ **Documented**: Configuration examples in README
- ✅ **Cross-platform ready**: Key names abstract OS differences

### Negative
- ❌ **Configuration complexity**: Users must understand key names
- ❌ **Runtime-only validation**: Typos not caught until app starts
- ❌ **No conflict detection**: Cannot detect macOS reserved shortcuts

### Mitigation
- Comprehensive key name documentation
- Startup logs show active hotkey: "Configured hotkey: Cmd+Shift+D"
- Future: REST endpoint to test hotkey before restart

## Alternatives Considered

### Hardcoded Hotkey
- **Rejected**: No user customization
- **Advantage**: Simplest implementation
- **Disadvantage**: Forces uncomfortable key for some users

### GUI Configuration
- **Rejected for MVP**: Requires desktop UI framework
- **Future enhancement**: JavaFX settings panel
- **Disadvantage**: Complexity, native dependencies

### Environment Variables
- **Rejected**: Poor type safety, hard to document complex configs
- **Advantage**: 12-factor app compliance
- **Disadvantage**: String parsing errors, no structure

## References
- Guidelines: Lines 275-389 (Hotkey Configuration)
- Implementation: Task 3.3 (Hotkey Configuration Loading)
- Key mappings: `KeyCodeMapper` class
