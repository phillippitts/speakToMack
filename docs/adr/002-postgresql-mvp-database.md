# ADR-002: PostgreSQL for MVP Database

## Status
Accepted (2025-01-14)

## Context
Need to persist:
- Transcription history (text, engine, metadata)
- User preferences (language, engine choice, hotkey config)
- Audit logs (GDPR compliance)

Requirements:
- ACID guarantees for audit logs (legal requirement)
- Flexible schema for evolving engine result formats
- Full-text search for transcription history
- < 10K writes/sec (MVP scale)

## Decision
Use **PostgreSQL** as primary database for all data types.

**Schema:**
- Relational structure for structured data (users, preferences)
- JSONB columns for engine results (flexible, queryable)
- Spring Data JPA for persistence layer
- Flyway for migrations

**Migration Path:**
1. Phase 1 (MVP): Single PostgreSQL instance
2. Phase 2 (Scale): Add read replicas + Redis cache
3. Phase 3 (Growth): Partition tables by date
4. Phase 4 (Analytics): Add Clickhouse, keep PostgreSQL transactional

## Consequences

### Positive
- ✅ **ACID guarantees** for audit logs (GDPR/HIPAA compliance)
- ✅ **JSONB flexibility** stores engine results without schema changes
- ✅ **Spring Data JPA** simplicity (Java developers familiar)
- ✅ **Full-text search** via tsvector (transcription search feature)
- ✅ **Managed services** available (AWS RDS, DigitalOcean ~$15/month)
- ✅ **10K+ writes/sec** sufficient for MVP scale

### Negative
- ❌ **Schema migrations** required for structural changes
- ❌ **Vertical scaling limits** (~50K writes/sec single instance)
- ❌ **Horizontal sharding** requires extensions (Citus) or custom logic

### Mitigation
- JSONB columns reduce migration frequency
- Read replicas offload query traffic
- Partitioning handles growth to millions of rows
- Migration to NoSQL documented (MongoDB, Clickhouse) if needed

## Alternatives Considered

### MongoDB (NoSQL)
- **Rejected for MVP**: Eventual consistency risk for audit logs
- **Advantage**: Flexible schema, built-in sharding
- **Disadvantage**: More complex queries, no ACID across documents
- **When to use**: > 10K writes/sec, schema changes daily

### DynamoDB (AWS)
- **Rejected**: AWS lock-in, query limitations
- **Advantage**: Serverless, predictable latency
- **Disadvantage**: Cannot query across multiple fields, expensive at scale
- **When to use**: AWS Lambda architecture, global distribution

### Hybrid (PostgreSQL + MongoDB)
- **Rejected for MVP**: Operational complexity
- **Future option**: PostgreSQL for transactional, MongoDB for transcriptions
- **Phase 4 candidate**: PostgreSQL + Clickhouse (OLAP)

## References
- Guidelines: Lines 393-461 (Database Strategy)
- Trade-off analysis: SQL vs NoSQL comparison table
- Schema: Lines 410-446
