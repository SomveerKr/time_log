# Project instructions

Build the local Android Time Log app defined in `SPEC.md`. The user's problem is forgetting to record time. Fast entry and reliable, understandable reminders take priority. Keep the UI minimal and visually polished.

## Start every implementation session

1. Read `PROGRESS.md`, then the workflow in `IMPLEMENTATION.md` and the relevant task. Read the referenced parts of `SPEC.md`. On the first session, read the whole specification once.
2. Inspect `git status --short`, the branch, recent commits, and the relevant diff. Reconcile them with progress before editing. Never discard unrelated changes or restart completed work.
3. Load the installed `ponytail:ponytail` or `ponytail` skill and use **full** level. Discover its current location; do not depend on the originating Mac's absolute path or cached version.
4. If Ponytail is absent, check the current Codex skill/plugin list once. Report its absence and follow the concise fallback below. Do not claim the skill was applied, silently install plugins, or let a missing optional skill stop ordinary implementation.
5. Resume the sole `in_progress` task; otherwise select the first `not_started` task whose predecessor is `done`. A blocked task stays selected until its blocker is resolved. Briefly state what you are resuming, then work.

## Work in small verified steps

- Follow `IMPLEMENTATION.md` in order. One task at a time. Complete its scope, required checks, and scoped security audit, update progress, and make a local Git commit before starting the next task. Commit promptly after the gate passes; do not delay a completed task for optional additional work.
- Save `PROGRESS.md` when starting a task, after meaningful work or checks, and before stopping. Record the exact next action, changed files, test results, blockers, and remaining work.
- Do not mark a task `done` because it compiles, because tests were planned, or because the session is ending. Its acceptance criteria and required checks must pass.
- If interrupted, preserve the working tree and describe its state. Do not commit failing work as a completed task. Recover from Git and files when a progress write was interrupted.
- Local task commits are part of the requested workflow. Stage only the task's changes and its progress update. Never push, publish, release, rewrite history, or change Git identity without authorization.
- If an instruction conflicts with a newer user request, follow the user and update the affected specification, task, and progress together. A documentation-only request does not authorize beginning app implementation.

## Ponytail fallback and context discipline

Use the smallest complete implementation. Reuse native Android APIs and existing components. Avoid speculative features, architecture layers, and dependencies. Read and search relevant files instead of repeatedly loading the whole repository. Keep progress concise and test output summarized. Re-run checks only after relevant changes or a new failure. Never save tokens by skipping persistence safety, reminder correctness, accessibility, or meaningful tests.

## Quality rules

- Kotlin, Jetpack Compose, Room, one app module. Use the detailed stack decisions in `SPEC.md`; document justified deviations before implementing them.
- Offline operation and local data are mandatory. Do not add analytics, accounts, network SDKs, AI services, or cloud sync.
- Never use destructive database migration or overwrite user data silently. Backup restore must validate first and replace data atomically after confirmation.
- Use synthetic data in tests. Do not commit personal backups, signing keys, SDK paths, device identifiers, or credentials.
- Inspect visible screens as well as test output. Never claim device testing when only an emulator or unit test ran.
- Use plain ASCII punctuation in documents and user-facing explanations. Keep progress updates short and concrete.
- End implementation responses with the completed task, tests, commit, next task, and any untested behavior or material risk. Do not repeat the whole plan.
