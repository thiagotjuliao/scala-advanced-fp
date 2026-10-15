# Writing a chapter

The order below is not a style preference. Each step exists because doing it
later produces a chapter that looks finished and teaches nothing.

## 1. The document first

`docs/theory/chNN-*.md`, from `docs/templates/chapter.md`. Write *Why this
exists* before anything else, and write it from the code that cannot be written
without the idea — not from the idea's name. A chapter that opens with "a
selective functor is an applicative with `select`" has already lost the reader
who wanted to know when to reach for one.

*Pitfalls* is the section that dates best. Record what actually goes wrong —
the stack overflow, the law a plausible instance breaks, the compiler message
verbatim. Six months later that text is how the chapter gets found again.

## 2. The spec second

Before the stub, before the solution. The spec is the chapter's contract, and
writing it first is what keeps the exercise honest — an exercise written first
tends to get a spec that tests whatever it happens to do.

**Every spec must compile against the stub.** A stub's body is `unsolved`, so a
claim about a value just fails. A claim about *types* — that `Monad[ZipList]`
does not exist, that an applicative program cannot branch on a result — only
compiles once the exercise is solved, so it goes inside an `assertTypeChecks` /
`assertTypeError` snippet. Written as plain code it breaks the module instead
of failing a test.

**Laws are checked on samples**, with `forAllSamples`. The specs must not depend
on the law checker the learner builds in milestone 1. Choose the samples to
discriminate: the empty value, the identity, and the case a plausible but wrong
instance gets wrong. A law checked only where every instance agrees proves
nothing.

**Stack safety is a claim too.** When an exercise promises it, the spec runs it
at a depth that overflows a naive version — a million binds, not a hundred.

Then check that the spec is *discriminating*: run it against the stub, and
against a deliberately wrong solution, and watch both fail. If a wrong answer
passes, the spec is not testing the exercise.

Definitions every exercise of a chapter shares — the chapter's typeclasses, say
— go in one file, `Typeclasses.scala` or similar, present and identical in both
modules. They are given, not exercises, and they belong to the chapter: a later
chapter declares its own rather than importing them, so it can be studied on
its own.

## 3. The stub third

It must compile, it must not work, and it must say what is wanted in prose —
the reader is not meant to reverse-engineer the spec. Every body is `unsolved`.
One hint, at the end, pointing at the mechanism rather than the answer.

When an exercise builds on an earlier one of the same chapter, the stub
redeclares what it needs rather than importing the earlier stub: a chapter
whose exercise 3 cannot run until exercise 1 is solved has turned three
exercises into one.

## 4. The solution last

The scaladoc on a solution is the most-read text in the chapter, because it is
where the reader arrives after failing. Explain *why* this is the answer, what
law or property it relies on, and what the near-miss version breaks. A solution
that only shows what works teaches nothing `git diff` would not have.

Solutions compile under `CompilerFlags.strict`. A warning is fixed, not
silenced.

## 5. The walkthrough

`Walkthrough.scala` is the document in executable form: the chapter's ideas as
code that compiles. It lives in the exercises module only; there is nothing to
key.

## Then

```bash
sbt practice                    # the new exercises are red
sbt verify                      # the answer key is green
./scripts/finish-chapter.sh NN  # stage, verify that tree, commit, tag, push
```

`finish-chapter` re-runs the gate against the staged tree and refuses to tag a
chapter that is missing a document, a walkthrough, a spec or a solution, or
whose answer key still contains `unsolved` or `TODO`.

# Working on a milestone

The milestones are built in `modules/laws` and `modules/kernel`, by hand and
from scratch. There is no stub and no answer key, and nobody but the learner
writes code there.

1. **The design first**, in `docs/kernel/mNN-*.md`: the problem, the types and
   their laws, where each interpreter draws its boundary, and the test plan.
   [CURRICULUM.md](CURRICULUM.md) lists the starting scope and the acceptance
   criteria; the design turns them into decisions.
2. **A branch per milestone**, `milestone-NN`, from the latest `origin/main`.
   Commits along the way are ordinary ones, each with its tests.
3. **Decisions as they happen**, in `docs/kernel/decisions/NNNN-*.md`: what was
   decided, what else was on the table, and why it lost. A decision recorded a
   week later has already been rationalised.
4. **Done** when every acceptance criterion holds as a test and `sbt verify`
   passes. The branch is merged with a merge commit and the merge is tagged
   `mNN`.

# Naming

| thing | form | example |
| --- | --- | --- |
| package | `chNN` + the slug without dashes | `advfp.ch10selectivefunctors` |
| document | `chNN-slug.md` | `docs/theory/ch10-selective-functors.md` |
| exercise | `ExerciseNN.scala` | `Exercise03.scala` |
| spec | `ExerciseNNSpec.scala` | `Exercise03Spec.scala` |
| chapter tag | `chNN` | `ch10` |
| chapter commit | `chapter NN — lowercase title` | `chapter 10 — selective functors` |
| milestone design | `docs/kernel/mNN-slug.md` | `docs/kernel/m03-the-dsl.md` |
| milestone tag | `mNN` | `m03` |

Chapters `ch30` and up are reserved for optional chapters, so the main trail
can grow without renumbering.
