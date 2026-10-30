# 0008 — `Seed` follows `java.util.SplittableRandom`

2026-10-30 · M1 · accepted

## Context

`Gen` is a pure function of a `Seed`, and the seed must split: in
`ga.flatMap(f)` each side gets its own half, so shrinking `ga` does not change
what `f(a)` generates (see [the design](../m01-the-law-checker.md#seed)).
SplitMix64 (Steele, Lea and Flood, *Fast Splittable Pseudorandom Number
Generators*, OOPSLA 2014) does both in a few lines of `Long` arithmetic.

A generator with a wrong constant or a swapped shift still looks random. Every
structural test (deterministic, children differ from each other and from the
parent, `gamma` odd) passes for it. A reference to compare against is what
makes the generator auditable.

## Decision

`Seed` is a `final case class Seed(state: Long, gamma: Long)` with a private
constructor, and it reproduces `java.util.SplittableRandom` bit for bit. In
the JDK the generator is mutable, so the mapping to pure functions is part of
the decision. As read in the OpenJDK 26.0.2.1 sources:

| JDK (mutable) | `Seed` (pure) |
| --- | --- |
| `new SplittableRandom(n)`: `seed = n`, `gamma = GOLDEN_GAMMA = 0x9e3779b97f4a7c15L` | `Seed(n)` |
| `nextLong()`: `seed += gamma`, returns `mix64(seed)` | `next`: the value, and the seed with `state + gamma` |
| `split()`: `new SplittableRandom(nextLong(), mixGamma(nextSeed()))`. It advances the parent twice. | `split`: the child, and the parent advanced twice (`state + 2 * gamma`) |

`mix64` and `mixGamma` are the JDK's private functions, constants and shifts
included. `mixGamma` always returns an odd number, which is what keeps the
period at 2^64. The private constructor keeps any other `gamma` out.

The tests use `SplittableRandom` as the oracle: for several initial seeds, the
values of long runs of `next` and of `split` trees must match the JDK's. The
JDK is not a library: the rule of no dependencies besides munit holds, and the
oracle is used in tests only.

## Alternatives

- **Our own SplitMix64, with no reference.** The same code, but only
  structural tests can check it, and those do not see a wrong constant.
- **A linear congruential generator.** Simpler, but it does not split: two
  seeds derived from one share the same sequence, shifted, so the halves of a
  `flatMap` would be correlated.
- **Wrapping `SplittableRandom` itself.** Auditable for free, but mutable: a
  `Gen` would have to copy it before every use to stay a pure function of
  its seed, and the copy is not part of its API.

## Consequences

`Seed` depends on nothing, and its tests on the JDK only. If a later JDK
changes `SplittableRandom`'s output, the oracle tests break and say so. The
`Seed` itself stays correct, and the constants above are the record of what it
implements.
