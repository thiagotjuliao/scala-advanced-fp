# 0010 — `Gen` is a monad in distribution, not seed by seed

2026-10-30 · M1 · accepted · amends the design's "Gen is a monad"

## Context

The design said two generators are equal when they agree on the root of their
trees for a fixed set of seeds and sizes, and that `Monad[Gen]` passes the
Monad suite under that equality. It does not, and cannot.

`flatMap` splits the seed, so that `ga` and `f(a)` draw independent numbers
(ADR 0008). Then the two sides of each law run on different seeds:

| law | one side runs | the other runs |
| --- | --- | --- |
| left identity | `f(a)` on `s` | `f(a)` on the parent half of `s` |
| right identity | `m` on `s` | `m` on the child half of `s` |
| associativity | `m`, `f`, `g` on one tree of splits | the same, on another |

Measured on 2026-10-30 with a generator of `seed.next`, on seeds 0, 1 and 42:
all three laws give different roots on every seed.

A `flatMap` that does not split passes all three seed by seed: every
generator in it sees the same seed. It is also useless, since
`for a <- g; b <- g yield (a, b)` then always has `a == b`.

## Decision

- **The functor laws are checked seed by seed.** `map` does not split, so
  they hold exactly. So does the agreement of `ap` with `flatMap`, and of the
  member methods with the instance (ADR 0009).
- **The monad laws are checked in distribution.** Each side runs on the same
  5000 seeds, `Seed(0)` to `Seed(4999)`, and the two histograms of roots must
  be within a total variation distance of 0.05. The generators are over small
  domains (four values, or sixteen), so a histogram is a fair picture of the
  distribution.
- **Independence is checked on its own.** Two generators of four values
  bound by `flatMap` must make the sixteen pairs uniformly, within the same
  tolerance. This is what the laws in distribution cannot see: the
  non-splitting `flatMap` above passes them, and only this check fails it.

The seeds are fixed, so the checks are deterministic: they pass on every run
or on none. The numbers were measured on the correct `Gen`:

| seeds | quarter vs uniform | left identity | right identity | associativity | pairs vs uniform |
| --- | --- | --- | --- | --- | --- |
| 500 | 0.0200 | 0.0260 | 0.0580 | 0.0300 | 0.0765 |
| 1000 | 0.0150 | 0.0310 | 0.0270 | 0.0250 | 0.0570 |
| 2000 | 0.0060 | 0.0270 | 0.0155 | 0.0220 | 0.0435 |
| 5000 | 0.0064 | 0.0122 | 0.0098 | 0.0256 | 0.0195 |

At 5000 seeds the largest distance is half the tolerance. The non-splitting
`flatMap` puts the pairs on the diagonal, at a distance of 0.75.

## Alternatives

- **Seed by seed, as the design had it.** Impossible with a splitting
  `flatMap`, as above.
- **A `flatMap` that does not split.** Lawful seed by seed, and correlated.
- **A statistical test with a p-value** (chi-squared). More precise about
  what "close" means, but with fixed seeds it is a deterministic pass or fail
  as well, and the total variation distance says the same in one line.
- **Not checking `Monad[Gen]` at all.** The design wants the checker to check
  its own generator, and a wrong `flatMap` is exactly the bug the independence
  check finds.

## Consequences

`Gen` is "lawful" in a weaker sense than the instances M1 checks: the suites'
`Eq` compares values, and `Gen` is compared by distribution. The Monad suite
is not reused for `Gen`; `GenSpec` states the laws on histograms. The same
question comes back in M2: two `IO`s are compared by running them, and an
`IO` that draws random numbers would meet it.
