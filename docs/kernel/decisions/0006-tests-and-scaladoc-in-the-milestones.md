# 0006 — Tests and Scaladoc in the milestones

2026-10-29 · M1 · accepted · amends [0001](0001-a-typeclasses-module.md)

## Context

The milestone modules — `typeclasses`, `laws` and `kernel` — were Thiago's
only, tests included. The milestones' point is the implementations and the
laws behind them, built by hand. The tests that pin those laws down, and the
Scaladoc that states them, are where an outside eye helps most: the first `Eq`
had two reflexivity bugs (`Left` against `Left`, then `Nil` against `Nil`)
that the compiler could not see and that only a test of the law catches.

## Decision

In `modules/typeclasses`, `modules/laws` and `modules/kernel`, Claude may
write the tests and the Scaladoc. The implementations — every trait, type,
method and instance, and every fix to them — stay Thiago's. `CLAUDE.md` holds
the rule.

## Alternatives

- **Keep the modules Thiago's only.** The rule is simpler, but the tests are
  where a wrong implementation is caught, and they lagged behind the code.
- **Open the modules entirely.** Writing the implementations is the exercise.

## Consequences

A test Claude writes must fail against the bug it is meant to catch, checked
by reintroducing that bug once. `EqSpec` was checked that way against four
mutants of `Eq`. A test may not stand in for a design decision: what a type
promises is settled in the milestone's design document first, and the tests
follow it.
