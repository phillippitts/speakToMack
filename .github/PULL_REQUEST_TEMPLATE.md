## Summary
Explain the change in 1–3 sentences. Link related issues or plan tasks.

## Type of change
- [ ] Feature
- [ ] Bug fix
- [ ] Documentation
- [ ] Refactor / Chore

## How to test
Describe how reviewers can validate the change locally. Include any flags/toggles.

```bash
./gradlew clean build
# optional: enable flags locally
# stt.reconciliation.enabled=true
# stt.whisper.output=json
```

## Checklist
- [ ] I ran `./gradlew check` locally (tests + Checkstyle)
- [ ] I added/updated unit tests where appropriate
- [ ] I updated docs (README and/or relevant guide) if behavior changed
- [ ] I followed the project’s coding standards (constructor injection, privacy‑safe logs)
- [ ] I considered PII safety (no full transcripts at INFO; metrics labels are PII‑free)
