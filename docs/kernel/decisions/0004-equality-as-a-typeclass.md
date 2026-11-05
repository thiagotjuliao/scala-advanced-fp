# 0004 — Equality as a typeclass

2026-10-28 · M1 · accepted

## Context

The two sides of a law are compared. `==` is universal equality: it compiles
for any two values, it is reference equality for functions, and it does not
terminate on two equal infinite `LazyList`s, which is exactly what `ZipList`'s
`pure` produces (chapter 02, pitfalls).

## Decision

The suites compare with `Eq[A]`, from `typeclasses`. A suite asks for the
instances it needs. An `Eq` for a structure that may be infinite compares a
prefix of a stated length, and the length must be greater than any size the
generators produce, so every finite generated value is compared whole.

## Alternatives

- **`==` with `CanEqual`.** It solves the compile-time half (no accidental
  comparison of unrelated types) and neither of the others.
- **A timeout around the comparison.** It turns non-termination into a
  failure, and a failure that says "probably equal" is not a verdict.

## Consequences

The prefix bound is a correctness condition, not a tuning knob: two lists that
differ only after the bound compare equal. The ZipList fixtures state the bound
next to `maxSize`. Values that are functions (`Gen` in M1, `IO` in M2) get an
`Eq` defined by running both sides on the same inputs, which is a sampling
judgment, and its documentation says so.
