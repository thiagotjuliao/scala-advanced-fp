# 0001 — A typeclasses module

2026-10-28 · M1 · accepted

## Context

The law checker needs `Monad[Gen]`, and its suites need a hierarchy to check.
Before this, the kernel had none of its own to use. Each chapter redeclares its
typeclasses so it can be studied alone, `laws` depends on nothing, and
`kernel`'s main sources depend on nothing. M2 needs `Monad[IO]` in `kernel`,
checked by the suites in `laws`, and both must mean the same `Monad`.

## Decision

A third built-from-scratch module, `modules/typeclasses`, holds the hierarchy
(`Eq` through `Traverse`) and its instances for the standard types. `laws` and
`kernel` depend on it for compile, `kernel` depends on `laws` for test only. It
is written by Thiago only, like the other two, and compiles with
`CompilerFlags.strict`. (Amended by
[0006](0006-tests-and-scaladoc-in-the-milestones.md): the tests and Scaladoc
of all three may be Claude's.)

## Alternatives

- **The hierarchy inside `laws`, with `kernel` depending on `laws` for
  compile.** The runtime would carry the testing tool, and `Gen` would be on
  the kernel's main classpath.
- **The hierarchy in both `laws` and `kernel`.** Two sources of truth, and a
  `Monad[IO]` written against `kernel.Monad` cannot be passed to a suite that
  expects `laws.Monad`.
- **Depend on a chapter's hierarchy, from `solutions`.** The milestones would
  lean on the answer key, and on one chapter's version of a hierarchy that
  later chapters extend.

## Consequences

`verify` gains `typeclasses/testFull`. The table of who writes what in
`CLAUDE.md` gains a row. When a later part needs a new typeclass (`Selective`
in M3, say), it goes here, not in the module that first uses it.
