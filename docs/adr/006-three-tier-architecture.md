# ADR-006: 3-Tier Spring Architecture

## Status
Accepted (2025-01-14)

## Context
Need clear separation of concerns for:
- REST API endpoints (future)
- Business logic (transcription, reconciliation)
- Data persistence (PostgreSQL)

Team consists of experienced Spring developers familiar with layered architecture.

## Decision
Follow **3-tier architecture** pattern:

```
src/main/java/com/phillippitts/speaktomack/
├── presentation/       # Tier 1: Controllers, DTOs
├── service/           # Tier 2: Business logic
├── repository/        # Tier 3: Data access
├── domain/           # Shared entities
└── config/           # Spring configuration
```

**Rules:**
- Dependency flow: Presentation → Service → Repository (never reverse)
- No business logic in controllers
- No HTTP concerns in services
- No business logic in repositories
- Constructor injection only (no `@Autowired` fields)

## Consequences

### Positive
- ✅ **Clear boundaries**: Each layer has single responsibility
- ✅ **Testability**: Mock dependencies per layer
- ✅ **Team scalability**: Developers work on different layers simultaneously
- ✅ **Spring best practice**: Aligns with framework conventions
- ✅ **Future REST API**: Presentation layer ready for HTTP endpoints

### Negative
- ❌ **Boilerplate**: DTOs, entity conversions add code
- ❌ **Indirection**: Simple features span 3 files
- ❌ **Over-engineering risk**: Desktop app may not need full REST tier

### Mitigation
- Delay REST API (Phase 2) — use events for orchestration initially
- Keep layers thin for simple features
- Records reduce DTO boilerplate
- Package-by-feature option available later

## Alternatives Considered

### Flat Package Structure
- **Rejected**: No clear boundaries, hard to navigate
- **Advantage**: Fewer files, simpler
- **Disadvantage**: Business logic leaks into presentation

### Hexagonal Architecture (Ports & Adapters)
- **Rejected for MVP**: Over-engineering for current scope
- **Advantage**: Technology-agnostic core
- **Disadvantage**: More interfaces, higher complexity
- **Future option**: Consider if adding multiple input sources

### Modular Monolith (Spring Modulith)
- **Deferred**: Dependency already present, not leveraged yet
- **Advantage**: Module boundaries enforced
- **Disadvantage**: Premature for current feature set

## References
- Guidelines: Lines 82-118 (3-Tier Architecture)
- Package structure mandated in all implementation tasks
