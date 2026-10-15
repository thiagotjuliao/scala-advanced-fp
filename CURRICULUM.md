# Curriculum

Advanced functional programming in Scala 3, from the typeclass hierarchy to a
working pipeline kernel. Twenty-one chapters in five parts, and six milestones
that build one system, the kernel, from an empty module.

## How the course is built

**Chapters** teach. Each one is a theory document, a walkthrough the compiler
checks, and exercises: a stub, a spec that stays red until the stub is solved,
and the answer key in `solutions`. The layout is the one
`scala-type-programming` settled on; [CONTRIBUTING.md](CONTRIBUTING.md) has the
order a chapter is written in.

**Milestones** build. Each part closes with one, and a milestone has no stub and
no answer key. Its design is written first, together, in
`docs/kernel/mNN-*.md`: the problem, the types, the laws, where the interpreters
draw their boundaries, and the test plan. The code and its tests are then
written from an empty module. Decisions made on the way are recorded in
`docs/kernel/decisions/`.

**The thread** is the kernel: the engine-agnostic core of a declarative
pipeline framework, in the spirit of Spark Declarative Pipelines. Tables are
declared, their dependencies are known before anything runs, the graph is
validated and planned, and an interpreter executes it. M1 builds the tool every
later test relies on; M2 to M6 each add one layer of the kernel, and every layer
is the subject of the part it closes.

The kernel depends on **no engine**. Tables are in-memory collections of
records, so every behaviour can be tested exhaustively. A Spark backend belongs
to the real `scala-dsl-sdp` project, which can consume the kernel later; it is
out of scope here, and keeping it out is what keeps the kernel's design honest.

**No libraries** besides munit, and munit only in tests. Every abstraction the
course uses is one the course builds: the typeclasses in chapters 01–03, the
effect runtime in chapters 05–08, the law checker in M1.

## Overview

| part | chapters | closes with |
| --- | --- | --- |
| 0 — Foundations | 01–08 | M1 law checker (after 02), M2 runtime (after 08) |
| I — Beyond the monad | 09–12 | M3 the DSL |
| II — Effects as interfaces | 13–15 | M4 the executor |
| III — Recursion and optics | 16–19 | M5 the planner |
| IV — Categories that pay rent | 20–21 | M6 kernel 1.0 |

Chapter numbers are fixed. Chapters `ch30` and up are reserved for optional
chapters added later (finger trees and monoid-annotated structures are the
first candidate), so the main trail never needs renumbering.

---

## Part 0 — Foundations

The hierarchy, the classic data types and the effect runtime, built from
scratch. Everything after this part assumes it and uses the course's own
versions, never a library's.

### Chapter 01 — Typeclasses and laws

`Semigroup`, `Monoid`, `Eq`, `Show` and `Functor`, written as Scala 3 typeclasses
(`given`, `using`, `extension`). The central idea: a typeclass is its operations
*and* its laws, and an instance that compiles but breaks a law is a bug the
compiler cannot see. Coherence, orphan instances, and why one type can have
several lawful monoids.

- Instances of `Monoid` for `Int` under addition and under multiplication, kept
  apart by opaque types.
- `Monoid` for `Map[K, V]` given `Semigroup[V]`, and `Option[A]` given `Semigroup[A]`.
- `Functor` for a binary tree and for `Either[E, *]`.
- A deliberately unlawful instance, and the sample that exposes it.

### Chapter 02 — Applicative, Monad and Traverse

`Applicative` and `Monad` as a hierarchy, `flatMap` versus `ap`, and why every
monad is an applicative but not every applicative is a monad. `Foldable` and
`Traverse`, with `traverse` and `sequence` derived from `map2`.

- `Applicative` and `Monad` for `Option`, `Either[E, *]` and `List`.
- `map2`, `product` and `mapN` derived from `ap`, and `ap` derived from `flatMap`.
- `Traverse` for a rose tree, and `traverse` with `Option` as the short-circuit.
- `ZipList`: an applicative that is not a monad, and the law a `flatMap` would break.

> **Milestone 1 — the law checker.** See [M1](#m1--the-law-checker).

### Chapter 03 — State, Reader, Writer and Validated

The classic data types, each as a monad built by hand. `Validated` as the
applicative that accumulates errors, and the precise reason it cannot be a monad
without breaking `ap`'s agreement with `flatMap`.

- `State[S, A]` with `get`, `set`, `modify`, and a stack machine run in it.
- `Reader` for configuration, `Writer` for a log, both from scratch.
- `Validated[NonEmptyList[E], A]` and a form validated field by field.
- A proof, in a sample, that `Validated` with a `flatMap` breaks `ap`.

### Chapter 04 — Eval and trampolining

Strict, lazy and memoized evaluation as values (`Now`, `Later`, `Always`).
Trampolining: turning a call stack into a data structure on the heap, so a deep
recursion cannot overflow.

- `Eval` with `map` and `flatMap`, stack-safe under a million nested binds.
- Mutual recursion (`isEven` / `isOdd`) made stack-safe with a trampoline.
- `foldRight` over a long list without overflowing.
- A stack-safe `State`, by running it on `Eval`.

### Chapter 05 — IO as a description

`IO[A]` as a value that describes a program instead of running one. The data
type (`Pure`, `Delay`, `FlatMap`, `RaiseError`, `HandleErrorWith`) and a
stack-safe interpreter loop. Referential transparency as the property that makes
an `IO` reusable and refactorable.

- The `IO` algebra and its run loop, stack-safe and with errors.
- `attempt`, `handleErrorWith` and `guarantee`.
- The same program written with side effects and with `IO`, and the refactoring
  that breaks only the first.
- `Monad[IO]` with its laws checked.

### Chapter 06 — Fibers and schedulers

Concurrency on top of the description: `start`, `join` and a `Fiber` handle.
Cancellation as a first-class outcome. The scheduler as a swappable strategy,
over an OS thread pool and over virtual threads, and what changes between the
two.

- `Outcome` (`Succeeded`, `Errored`, `Canceled`) and `start` / `join`.
- Cooperative cancellation with cancellation points between binds.
- Two schedulers behind one interface, and the same test suite against both.
- `race`, which cancels the loser.

### Chapter 07 — Concurrent primitives

Shared state without locks, as effects: `Ref` on `AtomicReference`, `Deferred`
as a one-shot promise, and the primitives built from those two alone.

- `Ref` with `update`, `modify` and `getAndUpdate`, retried on contention.
- `Deferred` with `get` blocking semantically, not on a thread.
- `Semaphore` from `Ref` and `Deferred`, fair and cancellation-safe.
- A bounded `Queue` with back-pressure on `offer`.

### Chapter 08 — Resource and Stream

`Resource` as acquisition and release that survive errors and cancellation, and
compose. `Stream` as a pull-based, effectful sequence: the consumer asks, so the
producer cannot outrun it.

- `Resource` with `make`, `use`, `flatMap`, released in reverse order.
- Release proven to run on error and on cancellation.
- A pull-based `Stream[F, A]` with `map`, `evalMap`, `take` and `compile`.
- `parEvalMap` bounded by a semaphore.

> **Milestone 2 — the runtime.** See [M2](#m2--the-runtime).

---

## Part I — Beyond the monad

What an interpreter can *see* in a program, and what it cannot. A monad decides
what comes next from a value it only has at run time; an applicative fixes its
structure in advance. That difference is the whole basis of the kernel's DSL.

### Chapter 09 — Static and dynamic structure

Applicative versus monad as a statement about information: what can be known
about a computation before running it. `Const` as the applicative that only
collects, and the analysis it makes possible.

- Counting the effects of an applicative program without running it, via `Const`.
- The same count attempted on a monadic program, and the exact point it fails.
- `Traverse` laws, and a traversal that breaks naturality.
- An applicative parser that reports what it would accept before parsing anything.

### Chapter 10 — Selective functors

The middle ground: `select`, `branch` and `whenS`. Conditional effects whose
*possible* branches are all visible in advance, even though only one runs.

- `Selective` for `Option`, `Either` and `Const` (under- and over-approximation).
- `ifS` and `whenS` derived from `select`.
- The static analysis of a selective program: every effect it *might* run.
- A build system's "rebuild only if changed" rule, expressed selectively.

### Chapter 11 — Free constructions

A free structure is a program as data, interpreted later and possibly in many
ways. The free monad, the free applicative, Freer, and what each one leaves
inspectable.

- `Free[F, A]` with `foldMap` into any monad, and a stack-safe interpreter.
- `FreeApplicative[F, A]` and an analysis that walks the program without running it.
- Freer: dropping the `Functor[F]` requirement, and what it costs.
- One small language, embedded freely three ways, and what each interpreter can see.

### Chapter 12 — Yoneda, Coyoneda and Codensity

Three tools that change the shape of a computation without changing its meaning.
`Coyoneda` gives any type constructor a free functor; `Yoneda` fuses repeated
`map`s; `Codensity` turns left-associated binds into right-associated ones and
fixes the quadratic free monad.

- `Coyoneda` lifting a non-functor into `Free`.
- `map` fusion with `Yoneda`, measured.
- The quadratic slowdown of a left-associated free monad, then fixed with `Codensity`.
- The isomorphism `Yoneda[F, A] ≅ F[A]`, both directions, checked on samples.

> **Milestone 3 — the DSL.** See [M3](#m3--the-dsl).

---

## Part II — Effects as interfaces

Three ways to describe what a program does, separately from how: tagless final,
transformer stacks, and algebraic effects with handlers. The part ends with the
kernel's executor written against an interface and run by two interpreters.

### Chapter 13 — Tagless final in depth

Algebras as typeclasses over `F[_]`, interpreters as instances, and natural
transformations (`F ~> G`) as the way between them. Laws for algebras, not only
for typeclasses: what makes an interpreter *correct*.

- A key-value algebra with a pure `State` interpreter and an `IO` interpreter.
- Laws of the algebra (`put` then `get`), checked against both interpreters.
- `mapK`: moving an algebra along a natural transformation.
- Instrumenting an algebra (counting calls) without touching its interpreters.

### Chapter 14 — mtl, Kleisli and transformers

Monad transformers (`StateT`, `EitherT`, `ReaderT`, `Kleisli`) from scratch, the
mtl-style classes that abstract over them (`MonadState`, `MonadError`), and what
each layer costs.

- `StateT`, `EitherT` and `Kleisli` with their `Monad` instances.
- `MonadState` and `MonadError`, and the n² instance problem met head-on.
- The same program on a transformer stack and in tagless final with mtl classes.
- The ordering of `StateT` over `EitherT` versus the reverse, and what state
  survives an error in each.

### Chapter 15 — Algebraic effects and direct style

Effects as operations, handlers as their interpretations, and the continuation
that connects them. Then direct style in Scala 3: capabilities as context
parameters, `boundary` / `break`, and what capture checking would add.

- A handler-based encoding: operations, a handler, and resumption.
- Two handlers for one program (a real one and a test double).
- The same program in direct style, with capabilities passed as `using` parameters.
- Where direct style leaks: a capability captured by a closure that outlives it.

> **Milestone 4 — the executor.** See [M4](#m4--the-executor).

---

## Part III — Recursion and optics

Recursion factored out of data structures, and structure navigated without
being taken apart. The part ends with the kernel's planner.

### Chapter 16 — Recursion schemes I

`Fix`, pattern functors, algebras and coalgebras. Catamorphisms, anamorphisms and
hylomorphisms, and the claim that most recursive functions are one of them.

- `Fix` and a pattern functor for an expression language.
- An evaluator and a pretty-printer as catamorphisms.
- A range generator as an anamorphism, and merge sort as a hylomorphism.
- The fusion law of a catamorphism, demonstrated on samples.

### Chapter 17 — Recursion schemes II

Paramorphisms (the original subtree alongside the result), histomorphisms (all
earlier results), futumorphisms, and the monadic versions of each scheme.

- Constant folding as a paramorphism.
- A dynamic-programming problem as a histomorphism.
- `cataM`, an evaluator that can fail.
- A rewrite pass that needs `para` and could not be written with `cata`.

### Chapter 18 — Comonads and zippers

The dual of a monad: a value in a context that can be extracted from, and
extended. Zippers, `Store`, `Env` and `Traced`, and the cellular automaton as
the canonical comonadic program.

- `Comonad` and its laws, with the list zipper as an instance.
- Conway's Game of Life on a 2-D zipper with `coflatMap`.
- `Store` and the lens hidden inside it.
- `Env` for read-only context, `Traced` for monoid-indexed lookups.

### Chapter 19 — Profunctor optics

Lenses, prisms, traversals and isos, encoded as functions over profunctors so
that they compose by ordinary function composition.

- `Profunctor`, `Strong` and `Choice`, with laws.
- `Lens` and `Prism` in the profunctor encoding, composed by `andThen`.
- `Traversal` via `Wander`, focusing every leaf of a tree.
- The same lens written in the van Laarhoven encoding, and the comparison.

> **Milestone 5 — the planner.** See [M5](#m5--the-planner).

---

## Part IV — Categories that pay rent

The category theory that changes how code is written, and nothing beyond it.

### Chapter 20 — Natural transformations and adjunctions

Natural transformations as polymorphic functions with a law, and adjunctions as
the source of familiar monads: `State` falls out of the curry adjunction.

- Naturality checked for `headOption`, `toList` and a non-example.
- The curry adjunction, and `State` derived from it.
- `Writer` and `Reader` as the two halves of another adjunction.
- Free ⊣ forgetful, with `Free` from chapter 11 as the left adjoint.

### Chapter 21 — Category, Arrow and profunctors

`Category` and `Arrow`: composition without a value to bind. When an arrow beats
a monad, which is when the structure of the computation must stay visible.
Mealy machines as stateful arrows.

- `Category` and `Arrow` for functions and for `Kleisli`.
- `first`, `second`, `***` and `&&&`, with their laws.
- Mealy machines as an `Arrow`, and a running average built from them.
- An arrow whose structure can be inspected statically, and why a monad's cannot.

> **Milestone 6 — kernel 1.0.** See [M6](#m6--kernel-10).

---

## Milestones

Each milestone opens with its design document and closes when its acceptance
criteria hold, all of them tested. The criteria below are the starting point
for the design conversation, not the design.

### M1 — the law checker

`modules/laws`. After chapter 02.

A small property-based testing tool and law suites built on it, used by every
later test in `laws` and `kernel`.

- `Gen[A]` as a pure function of a seed, with `Monad[Gen]` from chapter 02.
- `forAll`, failure reports that show the counter-example, and a reproducible seed.
- Shrinking toward a minimal counter-example.
- Ready-made law suites: Semigroup, Monoid, Functor, Applicative, Monad, Traverse.
- **Accepted when:** the law suites catch the unlawful instances of chapters 01
  and 02, each with a shrunk counter-example.

### M2 — the runtime

`modules/kernel`, package `kernel.runtime`. After chapter 08.

The effect system the kernel runs on, assembled from Part 0.

- `IO` with errors, cancellation and fibers; both schedulers behind one interface.
- `Ref`, `Deferred`, `Semaphore`, `Resource`.
- **Accepted when:** `Monad[IO]` passes the M1 suites; the cancellation and
  resource-safety tests pass under both schedulers.

### M3 — the DSL

`kernel.dsl`. After chapter 12.

How a pipeline is declared: tables, sources, and the transformations between
them, as a free applicative or selective program.

- A table definition that names its inputs through the DSL, never as strings
  outside it.
- The dependency graph extracted *without running anything*.
- Conditional inputs (a fallback source, say) expressed selectively, with the
  analysis reporting every input that might be read.
- **Accepted when:** the extracted graph matches the declared one for every
  example pipeline, and a definition whose inputs depend on run-time data is
  rejected by the types, not at run time.

### M4 — the executor

`kernel.engine`. After chapter 15.

Running a validated graph, written against an algebra.

- `Engine[F]` in tagless final: read a table, write a table, run a transformation.
- A pure interpreter on `State`, for tests, and a concurrent one on the M2 runtime
  that runs independent tables in parallel, bounded by a semaphore.
- Failure policy: a failed table fails its descendants and nothing else.
- **Accepted when:** one specification suite passes against both interpreters,
  and the concurrent one never runs a table before its inputs are complete.

### M5 — the planner

`kernel.plan`. After chapter 19.

Between declaration and execution: validation and optimization.

- Graph validation accumulating every error (cycles, missing inputs, duplicate
  names) with `Validated`.
- Each table's logical plan as a fixed point of a pattern functor; rewrite rules
  as algebras (filter fusion, projection pruning, dead-column elimination).
- Optics for reaching into a plan to annotate or rewrite a node.
- **Accepted when:** every rule preserves results (checked with M1 against the
  unoptimized plan), and an invalid graph reports all of its errors at once.

### M6 — kernel 1.0

After chapter 21.

Composition and data quality, and the kernel as one piece.

- Pipelines composed as arrows, so a composed pipeline is still analysable.
- Expectations (row-level and table-level checks) as a natural transformation
  over the executor, with warn, drop and fail policies.
- An end-to-end example: declare, validate, plan, execute, check.
- **Accepted when:** the example runs under both interpreters with identical
  results, and adding an expectation needs no change to any table definition.

## The gate

`sbt verify` is the definition of green, run by CI and by `finish-chapter`:

- formatting (`scalafmtCheckAll`, `scalafmtSbtCheck`);
- `core`, `solutions`, `laws` and `kernel` pass with `testFull`;
- `exercises` compiles, with its specs red where work remains.

`laws`, `kernel` and `solutions` compile with `CompilerFlags.strict`
(`-source:future -Wall -Werror`): a warning there is a defect. `exercises` uses
`base`, because a stub ignores its parameters on purpose.
