# 0003 — Integrated shrinking

2026-10-28 · M1 · accepted

## Context

A failing input is reported after shrinking it. Shrinking can live with the
type (a `Shrink[A]` beside the `Gen[A]`, as in QuickCheck) or with the
generator (each generated value carries a lazy tree of smaller candidates, as
in Hedgehog).

## Decision

Integrated: `Gen[A]` produces a `Tree[A]`, and `map` and `flatMap` on `Gen`
map and bind the trees.

## Alternatives

- **Type-directed, `Shrink[A]`.** Simpler trees, and shrinking through `flatMap`
  is not degraded. But `gen.map(f)` loses the shrinker: there is no
  `Shrink[B]` from a `Shrink[A]` and an `A => B`. The suites build almost
  every generator by composition, so almost every one would need a hand-written
  shrinker. And a type's shrinker does not know the generator's invariants:
  `Shrink[Int]` shrinks a `choose(10, 20)` value to 0.

## Consequences

Every shrunk value is one the generator could have produced, with no extra
work. The cost is in `flatMap`. When `ga.flatMap(f)` shrinks the outer value
`a`, the inner value is regenerated from `f(a')` with the same seed, rather
than shrunk, so a shrink can lose progress already made on the inner part.
For the suites, whose inputs are mostly independent (built with `map2` /
`product`), this rarely shows. Generators that can be written with the
applicative operations should be: `product` shrinks both sides independently.
