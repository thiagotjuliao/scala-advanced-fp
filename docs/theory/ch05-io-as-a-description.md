# Chapter 05 — IO as a description

## Why this exists

Chapter 04 turned a computation into a value: an `Eval` says how to compute
something, and nothing happens until `.value` asks. That was enough for pure
computations, where *when* a value is computed changes only how long it
takes. A program that reads input, writes output or fails is different, and
the difference shows up the first time it is refactored.

```scala
def readLine(): String = scala.io.StdIn.readLine()

(readLine(), readLine())   // reads two lines

val line = readLine()
(line, line)               // reads one
```

Extracting a repeated expression into a `val` is the most ordinary
refactoring there is, and here it changed what the program does. The
expression `readLine()` is not a value standing for a string: it is an action,
and an action happens where it is written. Every refactoring that moves,
names, duplicates or removes an expression has to ask first whether the
expression does something — and the type, `String`, does not say.

The same fact blocks code that would otherwise be easy to write. A `retry`
that runs an action again after it fails cannot take the action's *result*:
by the time it has a `String`, the read has happened, failed or not, and there
is nothing left to run again. A by-name parameter postpones the action, but
only for one call; it cannot be stored, combined with another, or handed to a
function that decides later how many times to run it. And the failure comes
out as an exception, which is not part of any type and escapes every
expression that does not catch it.

What is missing is the step chapter 04 took for `Eval`, taken for effects: a
value that *describes* the action — read a line, then print it, and if that
fails, do this — and a single place at the edge of the program that runs the
description.

## The idea

**An `IO[A]` is a description of a program that produces an `A`.** Building
one runs nothing. `IO.delay(readLine())` is a value; the line is read when the
value is run, every time it is run. With that, `(read, read)` and `val r =
read; (r, r)` are the same pair of descriptions, and a program that runs both
reads two lines either way. This is *referential transparency*: an expression
can be replaced by its value without changing the program. Every refactoring
the compiler cannot check — naming, inlining, moving code into a function —
depends on it.

**The description is a small algebra.** Five cases are enough for a
sequential program with errors:

| case | means |
| --- | --- |
| `Pure(a)` | a value already computed — no effect |
| `Delay(thunk)` | run `thunk` when the program runs; if it throws, that is the program's error |
| `FlatMap(io, f)` | run `io`, feed its value to `f`, run what `f` returns |
| `RaiseError(e)` | fail with `e` |
| `HandleErrorWith(io, h)` | run `io`; if *it* fails with `e`, run `h(e)` instead |

`map`, `attempt`, `guarantee`, `retry` and the rest are written on top of
these, and none of them needs a new case.

**Running it is a loop with a stack of its own.** The interpreter walks the
description one node at a time. `FlatMap(io, f)` pushes `f` and goes on with
`io`; `HandleErrorWith(io, h)` pushes `h` and goes on with `io`. When a value
comes out, the loop pops: a function is applied to it, and a handler is
dropped, since nothing failed inside its scope. When an error comes out, the
loop pops too, discarding functions until it finds a handler, and runs that.
The stack is an immutable list on the heap, so a million nested `flatMap`s or
handlers are a million list cells, not a million JVM frames — chapter 04's
trampoline, with a second kind of frame. Chapter 04's re-association rule is
no longer needed: pushing the continuation does the same work, once per node.

**An error is a value travelling down that stack.** A `RaiseError` does not
throw; it makes the loop unwind its own stack, which is why a handler sees only
errors raised *inside* it. A handler that was pushed and then popped on
success is gone, and an error raised after it — in a later `flatMap` — goes
past it to the next one out. An exception thrown by a `Delay`'s thunk, by the
function of a `FlatMap` or by a handler is caught where it is thrown and
turned into the same value, so nothing escapes the loop except a fatal error —
a `StackOverflowError`, an `OutOfMemoryError` — that no handler could do
anything about.

**`unsafeRun()` is the end of the world.** It is the only place effects
happen, and a program calls it once, at its edge, in `main`. Everything inside
is descriptions combined with descriptions. The name is a warning: calling it
in the middle of a program brings back everything `IO` was meant to remove.

**A description can be run more than once.** That is what `retry` needs: it
holds the description, and on failure it runs it again. `twice(io)` runs `io`
twice; `repeatUntil(io)(p)` runs it until the value satisfies `p`. Each is a
few lines, and none can be written with a value that has already happened.

**`Monad[IO]` puts it in the hierarchy.** `pure` and `flatMap` are `Pure` and
`FlatMap`, and the laws hold — but comparing two `IO`s takes care. A
description holds functions, which have no equality, so two programs are equal
when *running* them gives the same result and does the same effects in the
same order. A law checked on results alone cannot see an instance that runs
an effect at the wrong time, or the wrong number of times.

## In code

The executable version of this chapter is
[`Walkthrough.scala`](../../modules/exercises/src/main/scala/advfp/ch05ioasadescription/Walkthrough.scala).
Read it alongside this document. It compiles with the rest of the module, so
none of it is pseudo-code, and `sbt playground/console` is where to poke at it.

## Pitfalls

**A run loop that runs the source first is not a loop.** The interpreter
that reads like the table above — run the source, then feed its value to `f`
and run that; run the source, and if it failed, run the handler — is correct,
and spends a JVM frame per node. Measured 2026-10-19: it passes 17 of exercise
01's 22 tests, and the five that build a million nodes fail with

```text
java.lang.StackOverflowError: null
```

Annotate the loop with `@tailrec`, and the compiler rejects that version
before a test has to find the right depth.

**A `try` around the recursive call is not a tail call.** Catching the
exception a function throws is the obvious next step, and written inside the
loop it stops it being one:

```text
Cannot rewrite recursive call: it is not in tail position
```

It is also wrong, besides being slow: the `try` wraps the rest of the whole
run, not just the call. Catch in a helper that returns an `IO` — a
`RaiseError` when something was thrown — and loop on its result.

**Handlers kept apart from the functions catch errors that are not theirs.** A
loop with two lists, one of functions and one of handlers, looks simpler and
loses the one fact that matters: where a handler's scope ends. A handler whose
source succeeded is never removed, and the next error anywhere reaches it.
Measured 2026-10-19: two of exercise 01's tests fail, among them
`Pure(1).handleErrorWith(h).flatMap(_ => RaiseError(boom))`, which runs `h`
and finishes with `Right(1)` instead of failing. One stack, holding both kinds
of frame in order, is what keeps each handler to its own source.

**`guarantee` written with `flatMap` and `handleErrorWith` runs the finalizer
twice.** In `io.flatMap(a => fin.map(_ => a)).handleErrorWith(e =>
fin.flatMap(_ => IO.raiseError(e)))` the handler wraps the success path's
finalizer too: when that finalizer fails, the handler catches its error and
runs it again. Measured 2026-10-19: two of exercise 02's fifteen tests fail —
the finalizer that fails after a success runs twice, and when both fail, the
finalizer's error replaces the program's. Fixing only the first — `attempt`
the program, but not the finalizer that runs after a failure — still fails
the second.

**A `flatMap` that runs while it builds passes all three monad laws.** An
instance that runs its source with `unsafeRun()` and calls `f` on the value
gives the same results as a correct one, and writes the same log too: the log
is written while the program is built instead of while it runs, but it is
written once either way. Measured 2026-10-19: of exercise 04's fifteen tests it
fails only the two that build a program, look at the log, and then run it —
once and twice. The laws need an equality that sees *when*, and a test that
runs the same value twice.

**A recursive call made while building builds everything.** `val rest =
retry(io, attempts - 1); io.handleErrorWith(_ => rest)` describes the same
program as the recursive call inside the handler, and builds every retry
before the first one runs. Measured 2026-10-19: it passes every test of
exercise 03 except the one with a million attempts, which overflows while
building the program, before `unsafeRun()` is called.

**`IO.pure` of an effect is not a description.** `IO.pure(readLine())`
evaluates its argument where it is written, like any strict call, and wraps
the line already read. It has the right type and the wrong meaning. An effect
goes in `IO.delay`, which takes its argument by name.

**Fatal errors are not the program's.** The loop catches with `NonFatal`, so a
`StackOverflowError` or an `InterruptedException` thrown inside a `delay`
comes out of `unsafeRun()` as a thrown exception, past every handler —
checked 2026-10-19 for both. Catching `Throwable` instead would hand a handler
an error it can do nothing about, and swallow a thread's request to stop.

## Exercises

| # | asks for |
| --- | --- |
| 01 | the `IO` algebra's `flatMap`, `map`, `handleErrorWith` and its run loop: stack-safe, with errors scoped to their handlers |
| 02 | `attempt`, `rethrow`, `handleError` and `guarantee`, written on the primitives |
| 03 | `twice`, `retry` and `repeatUntil`: programs that run a description more than once |
| 04 | `Monad[IO]` with its laws checked on effects, and a stack-safe `traverse` |

```bash
sbt "exercises/testOnly advfp.ch05ioasadescription.*"
```

## Further reading

- Simon Peyton Jones, *Tackling the Awkward Squad: monadic input/output,
  concurrency, exceptions, and foreign-language calls in Haskell*, 2001 — `IO`
  as a description run by the outside world, and the semantics of exceptions
  in it.
- Philip Wadler, *How to Declare an Imperative*, ACM Computing Surveys, 1997 —
  why a program that performs effects can still be a value.
- Paul Chiusano and Rúnar Bjarnason, *Functional Programming in Scala*,
  chapter 13, *External effects and I/O* — the same road in Scala, from a
  stack-overflowing `IO` to a trampolined one.
- The Cats Effect source, `core/shared/src/main/scala/cats/effect/IOFiber.scala`
  in typelevel/cats-effect — the production run loop: the same stack of
  continuations, with cancellation and asynchrony added, which are the next
  chapter's subject.
