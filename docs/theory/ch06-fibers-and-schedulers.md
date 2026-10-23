# Chapter 06 — Fibers and schedulers

## Why this exists

Chapter 05's `IO` runs one thing at a time, on the thread that called
`unsafeRun()`. A pipeline wants more than that almost at once: read two
tables at the same time, take whichever of two sources answers first, give up
on a read that takes longer than a minute. None of those can be described with
`flatMap`, which runs its second program only after the first has finished.

The JVM has threads, and every one of those is a few lines with them — but
each line costs something the type does not show.

```scala
val t = Thread(() => readTable("a"))
t.start()           // runs now, not when the program runs
t.join()            // this thread does nothing until t is done
```

`start()` is an effect performed where it is written, so the program is no
longer a description: chapter 05's refactorings are back to being unsafe. The
`join()` holds a whole OS thread — a megabyte of stack and a slot in the
kernel's scheduler — to do nothing, and a program that waits on a hundred
reads holds a hundred of them. And there is no way to *stop* `t`:
`Thread.stop` is gone from the JDK, and `interrupt()` only reaches code that
happens to be waiting on something that checks for it. A `Future` fixes the
waiting but not the rest: it starts the moment it is built, and it cannot be
canceled at all.

A timeout makes the last point concrete. "Read this table, or fail after a
minute" needs two things to run at once — the read and a clock — and, when
the clock wins, the read must *stop*: a timeout that leaves the read running
has only stopped waiting for it, and the read goes on holding its thread, its
connection and its memory.

What is missing is concurrency as part of the description: a program that
says "run this alongside", "wait for that one", "stop that one", and a single
place that decides which threads actually run them.

## The idea

**A fiber is a running description.** `io.start` is an `IO[Fiber[A]]`: run, it
begins running `io` alongside the current program and produces a handle at
once, without waiting. The fiber is chapter 05's run loop — an `IO` and a
stack of what is left to do — on whatever thread it was given. It is cheap: a
few objects on the heap, not an OS thread. Building `io.start` runs nothing,
so the program is still a value.

**A fiber ends in one of three ways.** Its `Outcome` is `Succeeded(a)`,
`Errored(e)` or `Canceled`. Canceled is not an error: nothing failed, someone
asked the fiber to stop. Folding it into `Either[Throwable, A]` would hand it
to every error handler on the way, and a handler that recovers from
"canceled" by retrying has undone the cancel. `fiber.join` is an
`IO[Outcome[A]]`, and what to do with each of the three is the joiner's
decision.

**Waiting does not hold a thread.** A fiber that joins another, or sleeps, is
*suspended*: an `IO.async` registers a callback and the run loop returns,
handing its thread back to the scheduler. When the callback fires — the
joined fiber finished, the timer went off — the loop resumes, on whatever
thread the scheduler picks, with the same stack it had. A thousand fibers
waiting on a thousand joins hold no thread at all.

**Cancellation is cooperative.** `fiber.cancel` sets a flag and waits for the
fiber to finish. The fiber checks the flag at its *cancellation points*: before
it applies a `flatMap`'s function, and before it runs an error handler.
Between two binds the description says "here is a boundary", and that is
where it may stop — never in the middle of a `delay`, which might be halfway
through writing a file. A fiber suspended in an `async` is canceled at once,
since nothing of it is running. The price is the other half of the same fact:
a `delay` that blocks for ten seconds delays its own cancel by ten seconds.
`Thread.interrupt` takes the opposite trade, and stops code at whatever point
it happens to check — which is why so much code swallows
`InterruptedException`.

**A scheduler is where fibers run, and it is swappable.** It has one
operation, `execute(task)`, and a run loop asks for nothing else. Two
strategies sit behind it:

| scheduler | a fiber runs on | a fiber that blocks a thread |
| --- | --- | --- |
| `Scheduler.pool(n)` | one of `n` OS threads | holds one of the `n`; `n` of them stop everything |
| `Scheduler.virtual()` | a virtual thread of its own | parks it; the OS thread under it moves on |

A fiber that only suspends — joins, sleeps, `async` — behaves the same on
both. What changes is *blocking*: `Thread.sleep`, a JDBC call, a
`CountDownLatch.await` inside a `delay`. On a pool, each one holds a thread;
on virtual threads, a blocked thread costs a few hundred bytes. The same test
suite runs against both, and a test that passes only on virtual threads is a
program that blocks where it should suspend.

**`race` runs two programs and cancels the loser.** `IO.racePair(a, b)` starts
both and produces the first outcome together with the other fiber, still
running. `race` decides what that means: the winner's value, or its error —
and in every case the loser is canceled before `race` returns, so nothing it
does is left running. `timeout` is a race against `IO.sleep`.

## In code

The executable version of this chapter is
[`Walkthrough.scala`](../../modules/exercises/src/main/scala/advfp/ch06fibersandschedulers/Walkthrough.scala).
Read it alongside this document. It compiles with the rest of the module, so
none of it is pseudo-code, and `sbt playground/console` is where to poke at it.

The runtime the exercises use is
[`IO.scala`](../../modules/exercises/src/main/scala/advfp/ch06fibersandschedulers/IO.scala),
given: chapter 05's `IO` with `start`, `async`, cancellation and the two
schedulers added.

## Pitfalls

**A fiber that never suspends keeps its thread.** This runtime does not take
a thread away from a fiber: the fiber gives it back when it suspends or ends.
A loop of `delay`s and `flatMap`s that never waits holds its thread for good.
Measured 2026-10-23: `IO.racePair(spin, IO.pure(1))`, where `spin` counts
forever, ends at once on `pool(4)` and on virtual threads, and on `pool(1)`
never ends at all — `spin` took the only thread before `IO.pure(1)` got it:

```text
java.util.concurrent.TimeoutException: the program did not end within 2 seconds
```

Production runtimes yield on their own every so many steps (Cats Effect calls
it auto-ceding); this one leaves it to the program.

**A blocking `delay` delays its own cancel.** Cancellation waits for the next
cancellation point, and a `delay` has none inside it. Measured 2026-10-23:
canceling a fiber inside `IO.delay(Thread.sleep(500))` took 492 ms on `pool(4)`
and 490 ms on virtual threads, while canceling one inside
`IO.sleep(500.millis)` took a millisecond at most, on all three schedulers. On
`pool(1)` it was worse: the fiber that wanted to cancel could not run until
the sleeping one gave the only thread back, and by then it had finished — the
outcome was `Succeeded`, not `Canceled`. Waiting belongs in `IO.sleep` and
`async`, which suspend, not in `Thread.sleep` inside a `delay`.

**Blocking on a pool turns concurrent programs sequential.** Two `delay`s that
each wait on a latch the other one opens — the meeting in exercise 03's spec —
run concurrently with `start` and `join`. Measured 2026-10-23: on `pool(4)` and
on virtual threads both see the other, `(true, true)`; on `pool(1)` the first
blocks the only thread for its whole two-second timeout before the second
starts, `(false, true)` after 2007 ms. The program is correct; the strategy is
what changed. Suspending instead of blocking — `IO.sleep`, `join` — makes the
number of threads irrelevant: 100,000 fibers each sleeping 200 ms ended in
378 ms on `pool(1)`.

**A cancellation check where a bind is pushed checks too early.** The loop
meets a `FlatMap` before its source runs, so reading the flag there misses
every cancel the source requests — and in `a.flatMap(f).flatMap(g)` every
function is pushed before the first effect runs, so nothing is ever checked
again. Measured 2026-10-23: that loop fails five of exercise 02's eleven
tests. The check belongs where a function is *applied*.

**`both` that joins before it starts is sequential.** `a.start`, `join`,
then `b.start` pairs the same values as starting both first, and runs one
after the other. Measured 2026-10-23: exercise 03's meeting test gets
`Succeeded((false,true))` under both schedulers instead of
`Succeeded((true,true))`.

**A race that does not cancel the loser has only stopped waiting.** The
loser goes on running after the race has returned, and nobody holds its
handle any more. Measured 2026-10-23: a `race` that returns the winner
without canceling fails four of exercise 04's ten tests under each scheduler —
the loser's counter keeps climbing, and the effect it was sleeping towards,
"too late", is written after the race has ended.

**A fiber left running by a wrong answer keeps running inside sbt.** Tests run
in sbt's own JVM, and a fiber nobody canceled is still spinning there when the
suite ends. Measured 2026-10-23, while writing this chapter: after a few runs
of a `race` that did not cancel its loser, the sbt server sat at 537% CPU. The
specs' spinning programs now stop when their scheduler is shut down, but a
fiber of your own may not: if the machine stays busy after a red run,
`sbt --client shutdown` and start again.

## Exercises

| # | asks for |
| --- | --- |
| 01 | `Scheduler.pool(n)` and `Scheduler.virtual()` behind one interface, and the contract both must keep |
| 02 | a run loop that ends in an `Outcome`, with cancellation points between binds and before handlers |
| 03 | `joinValue`, and `both`: two programs run concurrently, the first error canceling the other |
| 04 | `race`, which cancels the loser, and `timeout` on top of it |

```bash
sbt "exercises/testOnly advfp.ch06fibersandschedulers.*"
```

## Further reading

- Simon Marlow, Simon Peyton Jones, Andrew Moran and John Reppy,
  *Asynchronous Exceptions in Haskell*, PLDI 2001 — cancellation as a
  first-class outcome, and why where it may happen has to be controlled.
- Simon Marlow, *Parallel and Concurrent Programming in Haskell*, O'Reilly
  2013, chapters 8 and 9 — `async`, `wait`, `race` and cancellation, built
  from a handful of primitives.
- JEP 444, *Virtual Threads* (openjdk.org/jeps/444) — what a virtual thread
  is, what blocking one costs, and how it is mounted on a carrier thread.
- The Cats Effect source, `core/shared/src/main/scala/cats/effect/IOFiber.scala`
  in typelevel/cats-effect — the production fiber: the same loop, with
  `async`, cancellation, finalizers and automatic yielding.
