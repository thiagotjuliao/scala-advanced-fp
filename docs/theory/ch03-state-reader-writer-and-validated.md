# Chapter 03 — State, Reader, Writer and Validated

## Why this exists

Roll three dice with a pure random number generator. Each roll takes a seed
and returns the next seed with the number:

```scala
def roll(seed: Long): (Long, Int)

val (s1, a) = roll(seed)
val (s2, b) = roll(s1)
val (s3, c) = roll(s1) // a typo, and c is b again
```

The program is pure and correct in shape, and every line is plumbing: take
the state out, pass it on, take the new one out. The bug is in the plumbing,
where the compiler cannot see it, because `s1` and `s2` have the same type.
Configuration has the same disease in a milder form — a `config` parameter
threaded through every function that might, three calls down, need one field
of it — and so does a log, returned next to every value and concatenated by
hand at every call.

Then validate a sign-up form with `Either`. The name is empty and the age is
not a number, and the user is told about the name only: `Either` stops at the
first `Left`, so to report both, the code leaves the abstraction and matches
every combination by hand.

Each of these is a pattern of plumbing with one right way to do it. Written
once as a data type with a `Monad` — or, for the form, only an `Applicative`
— the plumbing disappears from the program and cannot be got wrong.

## The idea

| type | is | `flatMap` does | instance |
| --- | --- | --- | --- |
| `State[S, A]` | `S => (S, A)` | passes the state each step returns to the next | `Monad` |
| `Reader[R, A]` | `R => A` | hands the same environment to both steps | `Monad` |
| `Writer[W, A]` | `(W, A)` | concatenates the logs, the first step's first | `Monad`, given `Monoid[W]` |
| `Validated[E, A]` | `Valid(a)` or `Invalid(e)` | — | `Applicative`, given `Semigroup[E]` |

**State is a function, and `flatMap` composes it.** A `State[S, A]` does not
hold a state: it is a program that will be given one. `fa.flatMap(f)` runs
`fa` on the incoming state, feeds the value to `f`, and runs the result on the
state `fa` returned. That last clause is the whole monad — the dice bug was
passing the wrong state, and here there is nothing to pass. `get`, `set` and
`modify` are the only places the state is touched, and they are written once.

**Reader is State that never changes the state.** `R => A`, with `flatMap`
giving both steps the same `R`. `ask` reads the environment, and `local` runs
a sub-program in a modified one — a staging host, a test clock — without the
rest of the program noticing. Chapter 13's interpreters and chapter 14's
`Kleisli` are this type grown up.

**Writer is State that can only append.** `(W, A)`, and `flatMap`
concatenates: the log of the first step, then the second's. `pure` must log
nothing, so `W` needs an empty value and an associative append — a `Monoid` —
and the monad laws are the monoid laws seen through `flatMap`: left identity
is `empty |+| w == w`, associativity is the monoid's.

**Validated gives up `flatMap` to keep every error.** Its `ap` looks at both
sides: two `Valid`s apply, one `Invalid` wins, and two `Invalid`s combine their
errors with a `Semigroup`. It only needs a `Semigroup`, not a `Monoid`: `pure`
is `Valid`, which never touches an error. The errors are a `NonEmptyList`
because an `Invalid` with no errors would be a failure with nothing to say.

**Why `Validated` has no monad.** A `flatMap` on `Invalid(e)` has no `A` to
pass to `f`, so it can only return `Invalid(e)` — and then the second
computation is never looked at. That `flatMap` is fine on its own: it is
`Either`'s, and it obeys the three monad laws. The trouble is the agreement law
of chapter 02, `ff.ap(fa) == ff.flatMap(f => fa.map(f))`: on two `Invalid`s the
left side has both errors, the right side has the first. A type can have this
`ap` or a monad, not both. Code that needs the second step to depend on the
first — check the age only once it parsed — converts to `Either` for that step
and back, and accepts that the errors after it are not reported.

## In code

The executable version of this chapter is
[`Walkthrough.scala`](../../modules/exercises/src/main/scala/advfp/ch03statereaderwriterandvalidated/Walkthrough.scala).
Read it alongside this document. It compiles with the rest of the module, so
none of it is pseudo-code, and `sbt playground/console` is where to poke at it.

## Pitfalls

**`State` on a plain function is not stack-safe.** Every `flatMap` wraps the
previous function in a new one, and running it calls them nested. A loop of a
thousand steps runs; ten thousand overflow:

```text
java.lang.StackOverflowError
```

Measured 2026-10-16 with `foldLeft` building
`acc.flatMap(_ => modify(_ + 1)).flatMap(_ => get)`, on the forked JVM sbt
runs, default stack size. Chapter 04 fixes it by running `State` on `Eval`.

**A partially applied type is a type lambda.** `State[S, A]` has two
parameters and `Monad` wants one. The kind-projector spelling of Scala 2 is
not Scala 3 syntax:

```text
Monad[State[Int, *]]
Not found: type *
```

Write the lambda: `Monad[[A] =>> State[Int, A]]`, or an alias,
`type Counter[A] = State[Int, A]`.

**A bare `Valid` has no error type.** The enum is covariant, so
`Validated.Valid(f)` is a `Valid[Nothing, Int => Int]`. When the argument of
`ap` fixes the error type, inference recovers; when it is bare too —
`Validated.Valid(f).ap(Validated.Valid(1))` — nothing does, and the
applicative is looked for without a `Semigroup` to find:

```text
value ap is not a member of advfp.ch03statereaderwriterandvalidated.Exercise03.Validated.Valid[Nothing, Int
  => Int].
An extension method was tried, but could not be fully constructed:
```

Give the value its type — `val f: Checked[Int => Int] = Validated.Valid(...)`
— or build it with a constructor that returns the full type, as the
exercises' `valid` and `invalid` do.

**A `flatMap` on `Validated` silently stops accumulating.** Add one as an
extension and a `for` comprehension over validations compiles and runs, and
reports the first error only — nothing fails, a user just sees one problem at
a time. Validations of independent fields are combined with `ap`, never in a
`for`.

**The order of a log is the order of `|+|`.** A Writer that appends
`next.log |+| fa.log` has the right type, passes the identity laws, and writes
the log backwards. Only a sample with two non-empty logs shows it.

**The laws do not say which applicative you wrote.** A Validated `ap` that
keeps only one side's errors, on two `Invalid`s, passes all four applicative
laws — keeping the function's is exactly `Either`'s applicative, which is
lawful. The laws rule out broken instances, not different ones. Accumulation
is a property of its own, tested on two `Invalid`s; exercise 03's spec has
both, and it is the only part that goes red for the `Either` version.

## Exercises

| # | asks for |
| --- | --- |
| 01 | `State` with `get`, `set` and `modify`, its `Monad`, and a stack machine run in it |
| 02 | `Reader` for a configuration with `local`, and `Writer` logging a Collatz sequence |
| 03 | `Validated` accumulating into a `NonEmptyList`, and a sign-up form validated field by field |
| 04 | the only `flatMap` `Validated` can have, and the sample where it disagrees with `ap` |

```bash
sbt "exercises/testOnly advfp.ch03statereaderwriterandvalidated.*"
```

## Further reading

- Philip Wadler, *Monads for functional programming*, 1995 — state and output
  as monads, among the paper's first examples.
- Conor McBride and Ross Paterson, *Applicative programming with effects*,
  JFP 2008 — accumulating errors with a monoid, the applicative that is not a
  monad.
- Jeremy Gibbons and Ralf Hinze, *Just do it: simple monadic equational
  reasoning*, ICFP 2011 — the laws of `get` and `put`, and proofs about
  stateful programs written with them alone.
