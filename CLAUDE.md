# Working in this repository

A course on advanced functional programming in Scala 3, and a pipeline kernel
built across it. [CURRICULUM.md](CURRICULUM.md) is the plan: the chapters, the
milestones, and the gate.

How code and prose are written here is shared with every project built from
project-templates, in `CONVENTIONS.md`. Where it and this file disagree, this
file wins.

@CONVENTIONS.md

## Who writes what

| where | written by |
| --- | --- |
| `modules/core`, `modules/exercises`, `modules/solutions`, `docs/theory` | Claude, chapter by chapter |
| `docs/kernel/` — design documents and ADRs | Thiago and Claude, together |
| `modules/typeclasses`, `modules/laws`, `modules/kernel` | **Thiago only** |

**Claude does not write source code in `modules/typeclasses`, `modules/laws` or
`modules/kernel`** — no implementations, no tests, no stubs, not even a fix,
however small. The milestones exist so that they are built by hand from an
empty module. In those modules Claude reviews, explains, points at the bug, and
helps with the design document; the code is written by Thiago. If a task seems
to need code there, stop and say so instead.

## Before writing a chapter

Read [CONTRIBUTING.md](CONTRIBUTING.md) and follow its order — document, spec,
stub, solution, walkthrough. Each step out of order produces a chapter that
looks finished and teaches nothing.

Scaffold with `./scripts/new-chapter.sh NN "Title"` rather than creating files
by hand. `finish-chapter` checks for exactly what the templates lay out.

## The rules that break things quietly

**Every spec must compile against the stub.** A stub's body is `unsolved`, so a
value-level claim simply goes red. A claim about *types* — an instance exists, a
program is rejected — only compiles once the exercise is solved, so it goes
inside an `assertTypeChecks` / `assertTypeError` snippet, never in the test body
as plain code.

**Check that an assertion is discriminating** by running it against the stub
and watching it fail for the right reason. A law checked only on samples where
every instance agrees passes for a wrong instance too.

**The chapters' specs never depend on `typeclasses`, `laws` or `kernel`.** Laws
in specs are checked with `forAllSamples` from `CourseSuite`, on hand-picked
samples. The specs must not lean on code the learner is still writing.

## Commands

| command | |
| --- | --- |
| `sbt verify` | the gate: formatting, `core`, `solutions`, `typeclasses`, `laws` and `kernel` with `testFull`, `exercises/Test/compile` |
| `sbt practice` | `exercises/testFull` — red by design |
| `./scripts/finish-chapter.sh NN` | stage, verify the staged tree, commit, tag `chNN`, push |
| `./scripts/solve-chapter.sh NN BRANCH` | learner side: commit chapter NN's solved exercises to BRANCH and push; never `main` |

`testFull`, not `test`: under sbt 2 `test` is incremental and its greenest
possible run is the one that ran nothing.

## Conventions

- Scala 3 indentation syntax, formatted by `.scalafmt.conf`. Run `sbt scalafmtAll`.
- English everywhere: code, comments, documents, commit messages.
- `core`, `solutions`, `typeclasses`, `laws` and `kernel` compile their main
  sources with `CompilerFlags.strict`; a warning there is a defect.
- Commits for chapters are `chapter NN — lowercase title`; tags are `chNN`,
  annotated. Chapters are committed and tagged by `finish-chapter`, not by hand.
- Mutation in the runtime chapters (an interpreter loop, an `AtomicReference`)
  is the documented exception `CONVENTIONS.md` allows: one place, with a
  comment saying why.
