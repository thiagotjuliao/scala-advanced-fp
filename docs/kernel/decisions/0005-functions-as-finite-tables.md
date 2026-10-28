# 0005 — Functions as finite tables

2026-10-28 · M1 · accepted

## Context

Composition, associativity, interchange and naturality are laws over
functions. Fixed functions such as `_ + 1` give the same blind spots that
fixed samples do. Chapter 02's `ZipList` is caught only when one function
returns an empty list at one point and a longer one at another. The
acceptance criterion also asks for a *shrunk* counter-example, so a generated
function must be printable and shrinkable, and a Scala closure is neither.

## Decision

A generated function is a `Fun[A, B]`: a finite `Map[A, B]` and a default
`B`. It is generated from a `Gen[A]` for the keys and a `Gen[B]` for the
values. It prints as a table and shrinks by dropping entries, then by
shrinking keys and values, down to the constant function.

## Alternatives

- **Closures from a fixed menu** (`identity`, `_ + 1`, `_ * 2`, ...). Printable,
  but a menu covers only the functions someone thought to fear, which is
  the problem the checker exists to solve.
- **`Cogen[A]`**, perturbing the seed with the input, as QuickCheck does
  underneath. It covers every input type, but the function it produces cannot
  be printed or shrunk without the full machinery of QuickCheck's `Fun`.

## Consequences

Keys need usable equality, since they go in a `Map`. All M1 suites use `Int`.
A table function is constant outside its keys, so it cannot distinguish
infinitely many inputs. That is not a limitation in practice: a
counter-example needs a function to differ at only the few points the test
visits.
