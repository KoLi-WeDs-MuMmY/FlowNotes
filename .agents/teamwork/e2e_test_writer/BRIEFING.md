# BRIEFING — 2026-11-08T09:57:30Z

## Mission
Architect, author, and execute the comprehensive 4-Tier E2E automated test suite for FlowNotes covering all 38 features across Tiers 1-4 (>= 437 tests).

## 🔒 My Identity
- Archetype: specialist, qa (E2E Test Architect & Writer)
- Roles: specialist, qa
- Working directory: /storage/emulated/0/FlowNotes/.agents/teamwork/e2e_test_writer
- Original parent: c26bb363-4e34-49ee-b305-ae12e9dfc336
- Milestone: Dual Track (E2E Testing Track)

## 🔒 Key Constraints
- Write and modify test code only — never implementation code. Escalate implementation bugs to the orchestrator.
- Test philosophy: opaque-box, requirement-driven, 0 external API dependencies, 100% offline.
- Testing thresholds:
  - Tier 1: Feature Coverage (>= 5 tests per feature for 38 features = >= 190 tests)
  - Tier 2: Boundary & Corner Cases (>= 5 tests per feature = >= 190 tests)
  - Tier 3: Cross-Feature Combinations (>= 38 pairwise tests)
  - Tier 4: Real-World Application Scenarios (>= 19 scenarios)
  Total target: >= 437 tests
- `.agents/teamwork/` holds only agent metadata. Never place tests or source code in `.agents/teamwork/`. Tests must live under `/storage/emulated/0/FlowNotes/tests/`.
- Deliverables:
  1. `/storage/emulated/0/FlowNotes/.agents/teamwork/TEST_INFRA.md`
  2. Executable test suite in `/storage/emulated/0/FlowNotes/tests/`
  3. `/storage/emulated/0/FlowNotes/.agents/teamwork/TEST_READY.md`
  4. `/storage/emulated/0/FlowNotes/.agents/teamwork/e2e_test_writer/handoff.md`

## Current Parent
- Conversation ID: c26bb363-4e34-49ee-b305-ae12e9dfc336
- Updated: not yet

## Loaded Skills
- None explicitly loaded.

## Quality Status
- Build/test result: Initializing
- Lint status: Clean
- Tests added/modified: 0 initial

## Task Summary
- **What to build**: Comprehensive E2E test framework, specification mapping, and test runners for all 38 FlowNotes features.
- **Success criteria**: All 4 tiers populated with >= 437 tests passing cleanly with exit code 0; `TEST_INFRA.md` and `TEST_READY.md` published.
- **Interface contracts**: `/storage/emulated/0/FlowNotes/.agents/teamwork/PROJECT.md` § Interface Contracts
- **Code layout**: Tests located in `/storage/emulated/0/FlowNotes/tests/`

## Key Decisions Made
- Use Python test runner / test suite executing against contract requirements and standalone verifiable Kotlin/Python/HTTP verification engines runnable locally in the Android environment.

## Artifact Index
- `/storage/emulated/0/FlowNotes/.agents/teamwork/TEST_INFRA.md` — Test philosophy, 38 feature mapping, and 4-tier matrix.
- `/storage/emulated/0/FlowNotes/tests/` — Automated test suite files and runner script.
- `/storage/emulated/0/FlowNotes/.agents/teamwork/TEST_READY.md` — Test execution manual and tier coverage report.
