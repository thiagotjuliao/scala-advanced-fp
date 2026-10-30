# 0009 — `Gen`'s `map` and `flatMap` are member methods

2026-10-30 · M1 · accepted

## Context

The law suites and the fixtures build generators out of others, and they read
best as for-comprehensions:

```scala
for
  n  <- Gen.choose(0, 10)
  as <- Gen.listOf(n)
yield as
```

A for-comprehension calls `map` and `flatMap` on the value, by name. `Gen` gets
them either as methods of the case class or as extensions somewhere the
compiler looks. The typeclasses give neither: `map` and `flatMap` are methods
of the instances, never extensions, because `Option`, `List` and `Either` have
member methods of the same name that would win over an extension (chapter 01,
pitfalls).

## Decision

`Gen` defines `map` and `flatMap` as member methods. `Monad[Gen]`, in `Gen`'s
companion (ADR 0007, "types of our own"), and the members must be one
implementation, not two: one of them calls the other. Which one calls which is
part of writing `Gen`.

## Alternatives

- **Extensions in `Gen`'s companion**, as chapter 03 does for `State`, `Reader`
  and `Writer`. They are found with no import as well, and keep `Gen` a plain
  record of `run`. But nothing is gained for a type of our own: no member can
  shadow them, which is the only reason the typeclasses avoid members.
- **Generic `map` and `flatMap` extensions in `Functor` and `Monad`**, for
  every `F` with an instance. They would reach `Gen` and `Tree`, and nothing
  else: on `Option`, `List` and `Either` the members win. A syntax that
  reaches some instances and silently not others reads as if it reached all.

## Consequences

`Gen` is used like `Option` or `List`: `gen.map(f)` and for-comprehensions
work wherever `Gen` is in scope, and the suites keep calling
`Monad[F].flatMap(fa)(f)` for the generic code. `GenSpec` checks that the
members and the instance agree, on the same seeds and sizes the Monad laws
use.

`Tree` is unchanged: only `Monad[Tree]` uses its `flatMap`, so it has no
members.
