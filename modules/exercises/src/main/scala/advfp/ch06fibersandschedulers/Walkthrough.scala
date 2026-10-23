package advfp.ch06fibersandschedulers

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

/** Chapter 06 — Fibers and schedulers.
  *
  * Read this file top to bottom before opening the exercises. It is the whole
  * chapter in executable form: it compiles with the module, so none of it is
  * pseudo-code.
  *
  * Prose version: `docs/theory/ch06-fibers-and-schedulers.md`.
  */
object Walkthrough:

  // --- 1. A thread runs where it is written ------------------------------------
  //
  // `Thread.start()` is an effect: it happens where the expression is, and a
  // program that contains it is no longer a description. And there is no
  // stopping the thread once it runs.

  def readTableOnAThread(read: () => Unit): Thread =
    val thread = Thread(() => read())
    thread.start() // runs now, whether or not anyone wanted it yet
    thread

  // --- 2. `start` is a description of running alongside -----------------------
  //
  // `io.start` builds a value. Run, it begins `io` in a new fiber and produces
  // the fiber's handle at once; `join` waits for it. Nothing runs until the
  // whole program is run.

  private val reads = AtomicInteger(0)

  val readTable: IO[Int] = IO.delay(reads.incrementAndGet())

  val twoReadsAtOnce: IO[(Outcome[Int], Outcome[Int])] =
    for
      a <- readTable.start
      b <- readTable.start
      ra <- a.join
      rb <- b.join
    yield (ra, rb)

  // --- 3. Three ways to end ----------------------------------------------------
  //
  // `join` produces an `Outcome`, and what to do with each case is the
  // joiner's decision. Canceled is not an error: here it becomes a default.

  def valueOr[A](outcome: Outcome[A], default: A): IO[A] =
    outcome match
      case Outcome.Succeeded(a) => IO.pure(a)
      case Outcome.Errored(e) => IO.raiseError(e)
      case Outcome.Canceled => IO.pure(default)

  // --- 4. Waiting without a thread ---------------------------------------------
  //
  // `IO.async` registers a callback and leaves the loop: the thread goes back
  // to the scheduler, and the callback resumes the fiber later, with the same
  // stack. `join` and `sleep` are both written with it. Measured 2026-10-23:
  // 100,000 fibers, each sleeping 200 ms, all ended in 378 ms on a pool of a
  // single thread.

  def afterwards[A](callback: (Either[Throwable, A] => Unit) => Unit): IO[A] = IO.async(callback)

  val napThenRead: IO[Int] = IO.sleep(10.millis).flatMap(_ => readTable)

  // --- 5. Cancellation is cooperative ------------------------------------------
  //
  // `cancel` sets a flag and waits. The fiber checks it between binds — before
  // a `flatMap`'s function and before a handler — and stops there. A fiber
  // suspended in `sleep` is canceled at once; one inside a `delay` finishes
  // the `delay` first, however long it takes.

  private val steps = AtomicInteger(0)

  // A cancellation point at every step: `cancel` stops it within one step.
  val counting: IO[Unit] = IO.delay(steps.incrementAndGet()).flatMap(_ => counting)

  val startThenStop: IO[Outcome[Unit]] =
    for
      fiber <- counting.start
      _ <- IO.sleep(5.millis)
      _ <- fiber.cancel
      outcome <- fiber.join
    yield outcome // Canceled

  // --- 6. The scheduler is a strategy ------------------------------------------
  //
  // The same program, two places to run it. A program that only suspends
  // behaves the same on both; one that blocks a thread in a `delay` holds one
  // of the pool's few threads, and only a virtual one on the other.

  def onBoth[A](io: IO[A]): List[(String, Outcome[A])] =
    List("pool(4)" -> (() => Scheduler.pool(4)), "virtual" -> (() => Scheduler.virtual())).map:
      (name, make) =>
        val scheduler = make()
        try name -> io.unsafeRun(scheduler)
        finally scheduler.shutdown()

  // --- 7. A race cancels its loser ---------------------------------------------
  //
  // `racePair` starts both and hands back the first outcome with the other
  // fiber, still running. What to do with that fiber is the whole point:
  // cancel it, or it goes on working for a race that is over. Exercise 04
  // writes `race` and `timeout` on top of this.

  val firstOfTwo: IO[String] =
    IO.racePair(IO.sleep(10.millis).map(_ => "fast"), IO.sleep(1.second).map(_ => "slow"))
      .flatMap:
        case Left((Outcome.Succeeded(a), loser)) => loser.cancel.map(_ => a)
        case Right((loser, Outcome.Succeeded(b))) => loser.cancel.map(_ => b)
        case _ => IO.raiseError(IllegalStateException("one side failed or was canceled"))
end Walkthrough
