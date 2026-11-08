# BRIEFING — 2026-11-08T09:42:00Z

## Mission
Build a 100% offline, native Android digital note-taking and PDF annotation app in Kotlin (FlowNotes) replicating core capabilities of Freenotes with zero backend, zero hosting costs, and zero external API dependencies.

## 🔒 My Identity
- Archetype: orchestrator
- Roles: orchestrator, user_liaison, human_reporter, successor
- Working directory: /storage/emulated/0/FlowNotes/.agents/teamwork/orchestrator
- Original parent: sentinel
- Original parent conversation ID: a915e106-77b9-4dbd-8999-57f9b5c5963f

## 🔒 My Workflow
- **Pattern**: Project
- **Scope document**: /storage/emulated/0/FlowNotes/.agents/teamwork/PROJECT.md
1. **Decompose**: Survey full scope via 3 explorers, define modular milestones in PROJECT.md
2. **Dispatch & Execute**: Dual-track: Implementation Sub-orchestrators + E2E Testing Orchestrator
3. **On failure** (in this order):
   - Retry: nudge stuck agent or re-send task
   - Replace: spawn fresh agent with partial progress
   - Skip: proceed without (only if non-critical)
   - Redistribute: split stuck agent's remaining work
   - Redesign: re-partition decomposition
   - Escalate: report to parent (sub-orchestrators only, last resort)
4. **Succession**: At 16 spawns, write handoff.md, spawn successor
- **Work items**:
  0. Survey full scope [completed]
  1. Decompose into milestones & E2E track [in-progress]
  2. Implementation & verification loops [pending]
  3. Final verification & victory audit [pending]
- **Current phase**: 1 (Decompose & Plan)
- **Current focus**: Synthesize PROJECT.md and launch E2E Testing Track + Milestone M1

## 🔒 Key Constraints
- NEVER write, modify, or create source code files directly.
- NEVER run build/test commands yourself — require workers to do so.
- NEVER investigate or explore the problem at the code level — dispatch Explorers for technical investigation.
- Audit is a binary veto: integrity violation fails unconditionally.
- Never reuse a subagent after it has delivered its handoff — always spawn fresh.

## Current Parent
- Conversation ID: a915e106-77b9-4dbd-8999-57f9b5c5963f
- Updated: not yet

## Key Decisions Made
- Project Orchestration Pattern selected.
- Survey completed: 3 reports aggregated (codebase, requirements spec, architecture).
- Dual-track approach selected: Native Android Gradle/CLI test pipeline + E2E test suite.
- 5 modular milestones defined.

## Team Roster
| Agent | Type | Work Item | Status | Conv ID |
|-------|------|-----------|--------|---------|
| survey_explorer_1 | teamwork_preview_explorer | Survey codebase & build environment | completed | 5f98980c-919f-4bf1-95c5-2d0817c48658 |
| survey_spec_miner_1 | teamwork_preview_spec_miner | Survey requirements & spec mining | completed | 13c75f5c-7a93-4e5e-9608-e704fce18a3a |
| survey_explorer_2 | teamwork_preview_explorer | Survey technical architecture & modular plan | completed | 8bd5080b-7fb7-482a-aec3-cd20c9a745fe |
| e2e_test_writer | teamwork_preview_test_writer | Dual Track: E2E Test Suite & TEST_INFRA | in-progress | 4636bcc8-3e4e-437a-b7a8-2668b517f7d8 |
| m1_explorer_1 | teamwork_preview_explorer | M1: Android Layouts & Scaffolding | completed | 5a3021e3-f2cf-4ac5-8fdd-5212e277e091 |
| m1_explorer_2 | teamwork_preview_explorer | M1: Build System & Compilation Pipeline | completed | b33064b6-ed91-4762-a112-9f8ff5d73c98 |
| m1_explorer_3 | teamwork_preview_explorer | M1: Data Persistence & Companion UI | completed | 27dc4168-8fa8-4408-8424-a9bf09ca2c17 |
| m1_worker_1 | teamwork_preview_worker | M1: Implementation & Verification | in-progress | 92bcdc77-24e3-4620-9642-7de59710b9d7 |

## Succession Status
- Succession required: no
- Spawn count: 8 / 16
- Pending subagents: 4636bcc8-3e4e-437a-b7a8-2668b517f7d8, 92bcdc77-24e3-4620-9642-7de59710b9d7
- Predecessor: none
- Successor: not yet spawned

## Active Timers
- Heartbeat cron: task-155 (*/10 * * * *)
- Safety timer: none
- On succession: kill all timers before spawning successor
- On context truncation: run manage_task(Action="list") — re-create if missing

## Artifact Index
- /storage/emulated/0/FlowNotes/.agents/teamwork/ORIGINAL_REQUEST.md — User requirements
- /storage/emulated/0/FlowNotes/.agents/teamwork/orchestrator/DISPATCH.md — Dispatch log
- /storage/emulated/0/FlowNotes/.agents/teamwork/orchestrator/progress.md — Liveness & status
