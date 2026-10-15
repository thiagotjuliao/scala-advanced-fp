# The kernel

The design of the pipeline kernel, written before its code. The scope and the
acceptance criteria of each milestone are in
[CURRICULUM.md](../../CURRICULUM.md#milestones); the documents here turn them
into decisions.

| milestone | design | after |
| --- | --- | --- |
| M1 — the law checker | `m01-the-law-checker.md` | chapter 02 |
| M2 — the runtime | `m02-the-runtime.md` | chapter 08 |
| M3 — the DSL | `m03-the-dsl.md` | chapter 12 |
| M4 — the executor | `m04-the-executor.md` | chapter 15 |
| M5 — the planner | `m05-the-planner.md` | chapter 19 |
| M6 — kernel 1.0 | `m06-kernel-1-0.md` | chapter 21 |

A design document is written when its milestone starts, not before: each one
is shaped by the chapters that precede it.

`decisions/` holds the decisions made while building, one file each, numbered
in order (`0001-gen-as-a-function-of-the-seed.md`): what was decided, what else
was on the table, and why it lost.
