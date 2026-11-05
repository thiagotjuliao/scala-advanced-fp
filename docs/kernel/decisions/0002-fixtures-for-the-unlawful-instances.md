# 0002 — Fixtures for the unlawful instances

2026-10-28 · M1 · accepted

## Context

M1 is accepted when its suites catch the unlawful instances of chapters 01
and 02. Those instances are in `solutions`, and they implement each chapter's
own typeclasses: `reversing` is an
`advfp.ch01typeclassesandlaws.Functor[List]`, not a `typeclasses.Functor[List]`.

## Decision

Each unlawful instance is recreated in `laws`' tests, as a fixture against
`typeclasses`, with a comment naming the chapter and exercise it comes from.
The list is in [the design](../m01-the-law-checker.md#acceptance).

## Alternatives

- **`laws` depends on `solutions` for test.** It would compile, but it would
  not help: the suites cannot accept another hierarchy's instances, so an
  adapter would be needed per typeclass. And the milestone's tests would lean
  on the answer key, which is free to change with its chapter.

## Consequences

The acceptance criterion checks the *kind* of bug each exercise teaches, not
the object in `solutions`. If a chapter's instance changes, the fixture does
not follow it. That is acceptable: the fixture's purpose is to be a bug the
suite must catch.
