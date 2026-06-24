# AI Development Artifacts

This directory contains the **raw, verbatim materials** that drove the AI
session used to build this project. The brief requires submission of *all
raw files used during development*; everything material from that session is
included here so the work can be audited end-to-end.

## Contents

| File | What it is |
|---|---|
| [`problem-statement.md`](problem-statement.md) | The take-home brief, exactly as received from the recruiter (no paraphrasing). This is the *source of truth* the AI was instructed to satisfy. |
| [`design-notes.md`](design-notes.md) | The pre-code design decisions — entity model, pricing formula, booking state machine, locking strategy, RBAC matrix, and how notifications are wired. Written before any Java file was created; reflects what the human + AI agreed on. |
| [`session-transcript.md`](session-transcript.md) | A high-level reconstruction of the AI session: what was asked, the order in which files were produced, every failing test encountered and the fix that made it green, and the static-analysis findings that were addressed afterwards. |

## How the AI was used (one-paragraph summary)

The assignment was built with a single AI coding assistant (Claude) acting as a
pair-programmer. The human engineer set design constraints in natural language
before any code was written (entity shape, locking discipline, sorted-ID lock
ordering, async notification dispatch), then let the assistant produce files in
parallel batches. After every batch the build was compiled, tests were run, and
red bars were fed back as plain failure text — no silent skips. See
[`../../CLAUDE.md`](../../CLAUDE.md) for the full workflow narrative and
[`../../SKILLS.md`](../../SKILLS.md) for the engineering skills exercised.
