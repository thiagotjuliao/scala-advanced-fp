# Scala advanced FP

Advanced functional programming in **Scala 3.9.0**, laid out as a course that
runs, and a pipeline kernel built across it from an empty module.

Twenty-one chapters, from the typeclass hierarchy and an effect runtime to free
structures, recursion schemes, optics and arrows. Each chapter is a theory
document, a walkthrough that compiles, and exercises whose tests stay red until
they are solved. Six milestones, built from scratch with no stub and no answer
key, turn the chapters into one system: the engine-agnostic kernel of a
declarative pipeline framework.

The plan is in [CURRICULUM.md](CURRICULUM.md).

## Getting started

```bash
sbt verify      # the gate: formatted, answer key and milestones green, exercises compile
sbt practice    # the to-do list: every unsolved exercise, red
```

`practice` is supposed to fail. The failures are the point — each one names an
exercise that has not been done yet.

## How a chapter is laid out

| where | what |
| --- | --- |
| `docs/theory/chNN-*.md` | the prose: motivation, mechanism, pitfalls |
| `modules/exercises/…/chNN*/Walkthrough.scala` | the same content, as code |
| `modules/exercises/…/chNN*/ExerciseNN.scala` | the stubs, and their specs |
| `modules/solutions/…/chNN*/ExerciseNN.scala` | the answer key, and the same specs |

The exercise and solution modules share a package name, so a solved exercise
and its answer are the same file in two trees — `diff` reads cleanly.

A stub's body is `unsolved`, which has type `Nothing`: it fits any signature, so
the stub compiles, and calling it throws `Unsolved`, so its spec goes red. A
claim about types rather than values — that an instance exists, that a program
is rejected — is written as a snippet assertion from `CourseSuite`, which turns
a compile error into a failing test instead of a broken build.

## How a milestone is laid out

| where | what |
| --- | --- |
| `docs/kernel/mNN-*.md` | the design, written before any code |
| `docs/kernel/decisions/` | the decisions made on the way, one file each |
| `modules/typeclasses` | the typeclass hierarchy `laws` and `kernel` share |
| `modules/laws` | M1: property-based testing and law suites |
| `modules/kernel` | M2–M6: the kernel |

All three modules start empty, and their implementations are written by hand
(the tests and Scaladoc aside; see
[ADR 0006](docs/kernel/decisions/0006-tests-and-scaladoc-in-the-milestones.md)). The
chapters never depend on them.

## Running

| command | what it does |
| --- | --- |
| `sbt verify` | the gate — formatting, `core`, the answer key, `typeclasses`, `laws` and `kernel` with `testFull`, and the exercises compiling |
| `sbt practice` | every exercise spec, red where work remains |
| `sbt "exercises/testOnly advfp.ch01typeclassesandlaws.*"` | one chapter's exercises |
| `sbt "solutions/testOnly advfp.ch01typeclassesandlaws.*"` | the same, against the answer key |
| `sbt scalafmtAll` | apply the formatting contract |
| `sbt playground/console` | a REPL with the exercises on the classpath |

Under sbt 2 the plain `test` task is incremental: it runs what failed last
time, what never ran, and what a changed dependency touched. That is what you
want while working and useless as a gate, so `verify` uses `testFull`.

## Handing in a solved chapter

```bash
./scripts/solve-chapter.sh 2 my-solutions
```

Commits your work on chapter 2's exercises as `solutions — chapter 02` and
pushes it to `my-solutions`, switching to that branch first (or creating it).
Only the chapter's exercise sources go into the commit, with `notes/ch02.md` if
you keep one; anything else modified is listed and left alone. The check runs
against the staged tree: formatting, and every spec of the chapter green.
`main` is refused — it is the course, and keeps the stubs.

Keep what *you* learned, and where you got stuck, in `notes/chNN.md` on your
solutions branch, a line per idea. The course never ships a `notes/` directory,
so nothing that arrives from `main` touches yours.

## Writing a chapter

```bash
./scripts/new-chapter.sh 2 "Applicative, Monad and Traverse"   # scaffold
./scripts/finish-chapter.sh 2                                  # verify, commit, tag ch02, push
```

`new-chapter` renders the document, the walkthrough and three exercises with
their specs on both sides from `docs/templates/`, and never overwrites anything.
`finish-chapter` stages the chapter, checks out the *index* into
`target/finish-chapter/` and runs the gate there — against the tree that is
about to be tagged, not the working directory — and refuses a chapter with a
template document, a missing spec or solution, or an answer key that still says
`unsolved` or `TODO`. [CONTRIBUTING.md](CONTRIBUTING.md) has the order a
chapter is written in.

Every script comes as `.sh` (bash) and `.ps1` (PowerShell 7), with the same
arguments.

## Layout

```text
CURRICULUM.md                 the chapters and milestones, in order
build.sbt                     six modules, one gate, two aliases
docs/
  theory/chNN-*.md            one document per chapter
  kernel/                     the kernel's design documents and decisions
  templates/                  what new-chapter renders
modules/
  core/                       unsolved, CourseSuite
  exercises/                  walkthroughs, stubs, specs
  solutions/                  the answer key, and the same specs
  laws/                       milestone 1 — written from scratch
  kernel/                     milestones 2 to 6 — written from scratch
  playground/                 scratch; verified by nothing
scripts/
  new-chapter.{sh,ps1}        renders a chapter from the templates
  finish-chapter.{sh,ps1}     stages, verifies that tree, commits, tags, pushes
  solve-chapter.{sh,ps1}      commits a solved chapter to your own branch
```

## Conventions

Shared with every project built from
[project-templates](https://github.com/thiagotjuliao/project-templates), in
[CONVENTIONS.md](CONVENTIONS.md); [CLAUDE.md](CLAUDE.md) has what is specific
to this one. `core`, `solutions`, `typeclasses`, `laws` and `kernel` compile
their main sources with `-source:future -Wall -Werror`. Developed and built on Java 21.
