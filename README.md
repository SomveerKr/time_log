# Time Log Android app

Prepared on 13 October 2026. This dedicated project contains the requirements, implementation instructions and Android app with local entry, editable history, categories and elapsed Today gaps. See `PROGRESS.md` for verified status and `BUILDING.md` for build commands.

## Use these files in VS Code

1. Extract or copy this folder into the dedicated Android project directory. Keep `AGENTS.md` at the project root, beside the other four files. Do not put the files only in a chat attachment.
2. Open that directory as the VS Code workspace. Start Codex there so its project instructions apply.
3. Send the starting prompt below. Codex should work through one task at a time, save progress, test, and commit before starting the next task.
4. In later sessions, send the resume prompt. The files preserve context; they do not launch Codex or start background work when VS Code opens.

| File | Purpose |
| --- | --- |
| `SPEC.md` | Product behavior, minimalist visual design, data rules, architecture, and acceptance criteria. |
| `IMPLEMENTATION.md` | Ordered tasks, tests, commit rules, and interruption recovery. |
| `AGENTS.md` | Instructions Codex should load at the start of work in this project. |
| `PROGRESS.md` | Current task, completed work, test evidence, blockers, and exact next action. |

## Starting prompt

```text
Build the Android app described in SPEC.md. First read AGENTS.md,
PROGRESS.md, and IMPLEMENTATION.md. Use the installed Ponytail skill at
full level. Begin with the first unfinished task and follow the task order.
Implement one task, test it, save progress, and commit it before moving
to the next. Continue while the required checks are possible. Preserve
unrelated changes. Record any blocker and the exact resume action.
```

## Resume prompt

```text
Continue this project from PROGRESS.md. Follow AGENTS.md and Ponytail
full. Reconcile saved progress with the working tree and Git history,
then resume the unfinished task. Test and commit each task before the next.
```

## Ponytail verification

Ponytail was verified as installed and enabled at user scope on the originating Mac on 13 October 2026:

- Codex configuration: `~/.codex/config.toml`, section `[plugins."ponytail@ponytail"]`, with `enabled = true`.
- Installed skill read successfully: `~/.codex/plugins/cache/ponytail/ponytail/5.1.0/skills/ponytail/SKILL.md`.
- The skill is active in the originating Codex session.

The receiving Windows Codex environment also lists Ponytail as an installed skill; its current instructions were read and applied at full level. This is a user-level installation, not a copy inside this project. A different environment must verify its own installation. Do not claim a measured token saving: Ponytail keeps work focused and avoids unnecessary code and context.

Codex discovers project `AGENTS.md` instructions at session startup. Local plugin choices can be saved in the user configuration. See [Codex project instructions](https://learn.chatgpt.com/docs/agent-configuration/agents-md) and [plugin configuration](https://developers.openai.com/plugins/build/plugins).

## Scope of this delivery

The initial handoff contained documentation only. T01-T03 add the single-module Android project, tested persistence and entry/history UI; checks are tracked in `PROGRESS.md`. Reminders, charts and backup follow in the ordered tasks. The old Google Form and Google Sheet are outside this app project; do not change or import them automatically.
