# LogEZ — session conventions

This project is governed by the Claude-Obsidian vault. Before making changes, read:

- `/Users/ladi/Documents/Claude/Claude-Obsidian/CLAUDE.md` — the operating manual (logging protocol, sync directive, workflow)
- `/Users/ladi/Documents/Claude/Claude-Obsidian/11-Projects/logEZ/` — this project's brief, rules, decisions, lessons, and the three append-only logs (`prompt-log.md`, `response-log.md`, `changelog.md`)

Non-negotiables that sessions keep missing without this pointer:

1. **Log every project interaction** — append the Owner's prompt verbatim to `prompt-log.md` (`[P-###]`), a response entry to `response-log.md` (minor = one line; major = detailed; uncertain → major), and a `changelog.md` entry for anything actually shipped. Append-only, never rewrite.
2. **Debug APKs are never delivered as `app-debug.apk`** — rename to `logEZ-debug<major>.<iteration>.apk` per `20-Patterns-Engineering/mobile-apk-versioned-debug-naming.md` (`<major>` = count of shipped milestones from `changelog.md`, sub-lettered milestones count separately; `<iteration>` resets to 1 each new major). Record the exact filename in `response-log.md`.
3. **"Read/check the vault" = full two-way sync** — update all three logs AND commit pending repo changes in logically-scoped commits (imperative messages explaining why; build/tests verified green before committing). Surface unpushed commits; don't push unasked.
4. Hard checkpoints: stop for Owner approval at phase/milestone boundaries unless the Owner explicitly waives it for a task.

Repo notes: `docs/` is deliberately gitignored (ADRs live there, local-only). Release signing reads gitignored `/keystore.properties`; never commit keystores.
