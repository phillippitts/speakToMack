# Continuous Integration (CI)

This project uses GitHub Actions for automated testing and quality checks.

## CI Pipeline Overview

The CI pipeline runs on every push to `main` and on all pull requests. It consists of 5 parallel jobs:

### 1. Unit Tests (Fast Feedback)
**Platforms**: Ubuntu, macOS 14, macOS 13
**Duration**: ~2-3 minutes
**What it does**:
- Runs all unit tests (280+ tests)
- Excludes integration tests that require models
- Validates code compiles on all platforms
- Uploads JAR artifact from Ubuntu build

**Why multiple platforms?**
- macOS: Target platform for end users
- Ubuntu: Faster for CI, used for integration tests
- Cross-platform verification ensures portability

### 2. Integration Tests (Full Validation)
**Platform**: Ubuntu only (for speed)
**Duration**: ~8-10 minutes (first run), ~3-5 minutes (cached)
**What it does**:
- Downloads STT models (~200 MB, cached)
- Builds whisper.cpp binary (cached)
- Runs integration tests with real models
- Validates end-to-end transcription flow

**Caching strategy**:
- Models cached by `setup-models.sh` hash
- Whisper.cpp binary cached by `build-whisper.sh` hash
- Subsequent runs are much faster

### 3. Verify Whisper Build (macOS)
**Platform**: macOS 14
**Duration**: ~5-7 minutes (first run), ~1 minute (cached)
**What it does**:
- Verifies whisper.cpp builds on macOS
- Checks binary is executable and has correct architecture
- Validates macOS-specific build steps

### 4. Code Quality (Checkstyle)
**Platform**: Ubuntu
**Duration**: ~1-2 minutes
**What it does**:
- Runs checkstyle on all Java code
- Enforces code style standards
- Fails on any violations (maxWarnings=0)

### 5. CI Success (Summary)
**Platform**: Ubuntu
**Duration**: <10 seconds
**What it does**:
- Aggregates results from all jobs
- Single status check for branch protection
- Fails if any upstream job fails

---

## Why CI Jobs Might Fail

### Common Failures and Solutions

#### 1. Unit Tests Fail
**Symptom**: `unit-test` job fails with test failures
**Common causes**:
- Test assertions broken by code changes
- Platform-specific issues (macOS vs Linux)
- JNativeHook exceptions (usually harmless, check if tests actually passed)

**How to debug**:
```bash
# Download test reports artifact from failed CI run
# Or run locally on same platform:
./gradlew clean test
```

#### 2. Integration Tests Fail
**Symptom**: `integration-test` job fails
**Common causes**:
- Models not downloaded correctly
- Whisper binary not built
- Absolute path configuration issues
- Integration tests not properly tagged

**How to debug**:
```bash
# Run integration tests locally
./gradlew integrationTest

# Check model paths
ls -la models/
ls -la tools/whisper.cpp/main
```

#### 3. Whisper Build Fails (macOS)
**Symptom**: `verify-whisper-macos` job fails
**Common causes**:
- Build script changes broke macOS build
- whisper.cpp repository changed
- Xcode Command Line Tools missing

**How to debug**:
```bash
# Test locally on macOS
./build-whisper.sh

# Check for binary
find tools/whisper.cpp -name "main" -o -name "whisper"
```

#### 4. Checkstyle Fails
**Symptom**: `code-quality` job fails
**Common causes**:
- Code style violations
- Missing Javadoc
- Line length exceeded
- Unused imports

**How to debug**:
```bash
# Run checkstyle locally
./gradlew checkstyleMain checkstyleTest

# View report
open build/reports/checkstyle/main.html
```

---

## CI Configuration Details

### File Location
`.github/workflows/ci.yml`

### Key Features

**Gradle caching**: Uses `gradle/actions/setup-gradle@v3` for automatic caching of:
- Gradle dependencies
- Build cache
- Wrapper distributions

**Model caching**: STT models (~200 MB) are cached to avoid re-downloading:
```yaml
- uses: actions/cache@v4
  with:
    path: models/
    key: stt-models-${{ hashFiles('setup-models.sh') }}
```

**Whisper.cpp caching**: Binary cached per platform:
```yaml
- uses: actions/cache@v4
  with:
    path: tools/whisper.cpp/
    key: whisper-cpp-${{ runner.os }}-${{ hashFiles('build-whisper.sh') }}
```

**Artifact retention**:
- Test reports: 7 days (failures only)
- JAR artifacts: 30 days (Ubuntu builds)

---

## Running CI Locally

### Full CI simulation (all platforms)
Not possible locally, but you can test each job:

**Unit tests (all platforms)**:
```bash
# macOS
./gradlew clean build -x integrationTest

# Linux (use Docker)
docker run --rm -v $(pwd):/workspace -w /workspace gradle:8-jdk21 \
  ./gradlew clean build -x integrationTest
```

**Integration tests**:
```bash
# Download models
./setup-models.sh

# Build whisper.cpp
./build-whisper.sh

# Run integration tests
./gradlew integrationTest
```

**Checkstyle**:
```bash
./gradlew checkstyleMain checkstyleTest
```

### Testing CI changes

**Before pushing**:
1. Test locally: `./gradlew clean build integrationTest`
2. Verify checkstyle: `./gradlew checkstyleMain checkstyleTest`
3. Check whisper builds: `./build-whisper.sh`

**After pushing**:
1. Watch CI runs in GitHub Actions tab
2. Download artifacts if jobs fail
3. Check logs for specific errors

---

## CI Performance

### Typical run times (with cache)

| Job | Duration | Notes |
|-----|----------|-------|
| unit-test (Ubuntu) | 2-3 min | Fastest platform |
| unit-test (macOS-14) | 3-4 min | ARM64 architecture |
| unit-test (macOS-13) | 3-4 min | Intel architecture |
| integration-test | 3-5 min | With cached models |
| verify-whisper-macos | 1-2 min | With cached binary |
| code-quality | 1-2 min | Fast checkstyle |
| **Total (parallel)** | **4-5 min** | All jobs run concurrently |

### First run (no cache)

| Job | Duration | Notes |
|-----|----------|-------|
| integration-test | 8-10 min | Downloads 200 MB models |
| verify-whisper-macos | 5-7 min | Builds whisper.cpp |

---

## Branch Protection Rules

Recommended settings for `main` branch:

**Required status checks**:
- `CI Success` (summary job that depends on all others)

**Settings**:
- ✅ Require status checks to pass before merging
- ✅ Require branches to be up to date before merging
- ✅ Do not allow bypassing the above settings

---

## Troubleshooting

### Cache issues

**Symptom**: CI is slow despite caching
**Solution**: Clear cache manually

Go to: Repository → Actions → Caches → Delete old caches

### Out of disk space

**Symptom**: Job fails with "No space left on device"
**Solution**: Models + whisper.cpp + Gradle cache can exceed GitHub Actions limits

Add cleanup step before job:
```yaml
- name: Free disk space
  run: |
    sudo rm -rf /usr/share/dotnet
    sudo rm -rf /opt/ghc
```

### macOS runner issues

**Symptom**: macOS jobs timeout or fail inconsistently
**Solution**: macOS runners are slower and sometimes unreliable

Options:
1. Use `macos-latest` instead of specific versions
2. Increase timeout: `timeout-minutes: 30`
3. Skip macOS if not critical: Use matrix conditionals

---

## Future Improvements

**Planned enhancements**:
1. ✅ Add code coverage reporting (JaCoCo)
2. ✅ Run security scans (OWASP dependency-check)
3. ✅ Add performance benchmarks (JMH)
4. ✅ Create release workflow (auto-publish JARs)
5. ✅ Add Docker image builds

See: [DEPLOYMENT.md](../DEPLOYMENT.md) for production CI/CD strategy.
