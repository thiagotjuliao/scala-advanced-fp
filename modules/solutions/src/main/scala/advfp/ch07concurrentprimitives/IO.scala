package advfp.ch07concurrentprimitives

import java.util.concurrent.{CountDownLatch, ExecutorService, Executors, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/** How a fiber ended. `Canceled` is not an error: nothing failed, the fiber was
  * asked to stop.
  */
enum Outcome[+A]:
  case Succeeded(value: A)
  case Errored(error: Throwable)
  case Canceled

/** Where fibers run: chapter 06's two strategies, given. */
trait Scheduler:

  /** Runs `task` on some thread, later; returns at once. */
  def execute(task: Runnable): Unit

  /** Stops the threads. Tasks already running are interrupted, and tasks
    * executed afterwards are rejected.
    */
  def shutdown(): Unit

object Scheduler:

  /** A fixed pool of `threads` OS threads. A task that blocks holds one of them. */
  def pool(threads: Int): Scheduler =
    require(threads >= 1, s"a pool needs at least one thread, got $threads")
    // Daemon threads: a program stuck on a pool must not keep the JVM alive.
    from(
      Executors.newFixedThreadPool(
        threads,
        Thread.ofPlatform().daemon(true).name("pool-", 0).factory()
      )
    )

  /** A virtual thread per task. A task that blocks parks its virtual thread and
    * frees the OS thread under it.
    */
  def virtual(): Scheduler = from(Executors.newVirtualThreadPerTaskExecutor())

  private def from(executor: ExecutorService): Scheduler =
    new Scheduler:
      def execute(task: Runnable): Unit = executor.execute(task)

      def shutdown(): Unit =
        val _ = executor.shutdownNow()

/** A running `IO`. */
sealed trait Fiber[+A]:

  /** Waits for the fiber to end, without holding a thread, and produces how it
    * ended.
    */
  def join: IO[Outcome[A]]

  /** Asks the fiber to stop, and waits until it has, its finalizers included. A
    * fiber stops at its next cancellation point outside any `uncancelable`
    * region, so one inside a long `delay` or region finishes that first.
    * Canceling a fiber that has already ended does nothing.
    */
  def cancel: IO[Unit]

  // For the runtime's own combinators (`join`, `racePair`): runs `callback`
  // once the fiber has ended, on the thread that ended it.
  private[ch07concurrentprimitives] def onComplete(callback: Outcome[A] => Unit): Unit

/** A description of a program that produces an `A`, fails, or is canceled:
  * given, not an exercise.
  *
  * Chapter 06's `IO`, with two additions: `onCancel` runs a finalizer when a
  * fiber is canceled, and `uncancelable` turns cancellation off for a region.
  * The chapter's document, "Inside the regions", says how they work.
  */
sealed abstract class IO[+A]:

  def flatMap[B](f: A => IO[B]): IO[B] = IO.FlatMap(this, f)

  def map[B](f: A => B): IO[B] = flatMap(a => IO.Pure(f(a)))

  /** Runs `handler` if *this* program fails. A canceled fiber runs no handler. */
  def handleErrorWith[B >: A](handler: Throwable => IO[B]): IO[B] =
    IO.HandleErrorWith(this, handler)

  /** Runs `finalizer` if the fiber is canceled while this program runs, and not
    * if it succeeds or fails. The finalizer cannot itself be canceled, an error
    * it raises is dropped, and finalizers run innermost first, before the
    * fiber's `Canceled` is reported.
    */
  def onCancel(finalizer: IO[Unit]): IO[A] = IO.OnCancel(this, finalizer)

  /** Starts this program in a new fiber, on the same scheduler, and produces
    * its handle at once. The new fiber can be canceled even if this one is
    * inside an `uncancelable` region.
    */
  def start: IO[Fiber[A]] = IO.Start(this)

  /** Runs this program in a fiber on `scheduler`, and blocks the calling thread
    * until it ends. Called once, at the edge of the program.
    *
    * Throws `TimeoutException` if the program has not ended within `timeout`,
    * after asking it to stop: a test that hangs is a failure, not a stuck
    * build.
    */
  def unsafeRun(scheduler: Scheduler, timeout: FiniteDuration = 10.seconds): Outcome[A] =
    val fiber = IO.FiberLoop(this, scheduler)
    val outcome = AtomicReference[Option[Outcome[A]]](None)
    val ended = CountDownLatch(1)
    fiber.onComplete: o =>
      outcome.set(Some(o))
      ended.countDown()
    fiber.begin()
    if !ended.await(timeout.toNanos, TimeUnit.NANOSECONDS) then
      fiber.requestCancel()
      throw TimeoutException(s"the program did not end within ${timeout.toString}")
    outcome.get.getOrElse(throw AssertionError("a fiber ended without an outcome"))
end IO

object IO:

  /** What `uncancelable` hands its body: `poll(io)` runs `io` as cancelable as
    * the code around the region was. It only undoes its own region, so inside a
    * region nested in another, `io` still cannot be canceled.
    */
  type Poll = [X] => IO[X] => IO[X]

  def pure[A](a: A): IO[A] = Pure(a)

  /** Runs `thunk` each time the program runs. */
  def delay[A](thunk: => A): IO[A] = Delay(() => thunk)

  def defer[A](io: => IO[A]): IO[A] = delay(io).flatMap(identity)

  def raiseError[A](error: Throwable): IO[A] = RaiseError(error)

  val unit: IO[Unit] = Pure(())

  /** Suspends the fiber until `register`'s callback is called, and goes on with
    * the value or the error it is called with. Only the first call counts. The
    * fiber holds no thread while it waits, and, outside an `uncancelable`
    * region, a cancel ends the wait at once.
    */
  def async[A](register: (Either[Throwable, A] => Unit) => Unit): IO[A] = Async(register)

  /** Never ends, unless canceled. */
  val never: IO[Nothing] = async(_ => ())

  /** Suspends the fiber for `duration`, without holding a thread. */
  def sleep(duration: FiniteDuration): IO[Unit] =
    async: callback =>
      val _ = timer.schedule(
        (() => callback(Right(()))): Runnable,
        duration.toNanos,
        TimeUnit.NANOSECONDS
      )

  /** Runs `body` with cancellation turned off: a cancel requested inside takes
    * effect at the first cancellation point after the region, or inside
    * `poll(io)`. A wait outside `poll` cannot be canceled at all, so a region
    * that waits for something that never comes can never be stopped.
    */
  def uncancelable[A](body: Poll => IO[A]): IO[A] = Uncancelable(body)

  /** Starts both programs and produces the outcome of the first one to end,
    * with the other's fiber, still running. The loser is the caller's to
    * cancel or to join.
    */
  def racePair[A, B](
      a: IO[A],
      b: IO[B]
  ): IO[Either[(Outcome[A], Fiber[B]), (Fiber[A], Outcome[B])]] =
    for
      fa <- a.start
      fb <- b.start
      first <- async[Either[(Outcome[A], Fiber[B]), (Fiber[A], Outcome[B])]]: callback =>
        // Both fibers may end at once, on two threads, and call back twice:
        // `async` keeps the first call and ignores the second.
        fa.onComplete(o => callback(Right(Left((o, fb)))))
        fb.onComplete(o => callback(Right(Right((fa, o)))))
    yield first

  private lazy val timer =
    Executors.newSingleThreadScheduledExecutor(
      Thread.ofPlatform().daemon(true).name("io-timer").factory()
    )

  // Plain classes, not case classes: a description holds functions, and the
  // structural equality a case class would give two of them means nothing.
  private final class Pure[+A](val a: A) extends IO[A]

  private final class Delay[+A](val thunk: () => A) extends IO[A]

  private final class RaiseError(val error: Throwable) extends IO[Nothing]

  private final class FlatMap[X, +A](val source: IO[X], val f: X => IO[A]) extends IO[A]

  private final class HandleErrorWith[+A](val source: IO[A], val handler: Throwable => IO[A])
      extends IO[A]

  private final class OnCancel[+A](val source: IO[A], val finalizer: IO[Unit]) extends IO[A]

  private final class Uncancelable[+A](val body: Poll => IO[A]) extends IO[A]

  // `poll(io)`: `io` runs with the mask set back to `masked`.
  private final class Unmask[+A](val io: IO[A], val masked: Boolean) extends IO[A]

  private final class Start[+A](val io: IO[A]) extends IO[Fiber[A]]

  private final class Async[+A](val register: (Either[Throwable, A] => Unit) => Unit) extends IO[A]

  // Chapter 05's typed stack of what is left to do, with three more frames.
  private enum Stack[-A, +R]:
    case Done[R]() extends Stack[R, R]
    case Bind[A, B, R](f: A => IO[B], rest: Stack[B, R]) extends Stack[A, R]
    case Handle[A, R](handler: Throwable => IO[A], rest: Stack[A, R]) extends Stack[A, R]
    // A finalizer waiting to see whether the fiber is canceled.
    case Finalize[A, R](finalizer: IO[Unit], rest: Stack[A, R]) extends Stack[A, R]
    // The end of a region: the mask goes back to what it was.
    case Restore[A, R](masked: Boolean, rest: Stack[A, R]) extends Stack[A, R]
    // A canceled fiber: what comes back here is dropped, whatever it is, and the
    // next finalizer in `rest` runs. Nothing else in `rest` ever will.
    case Unwind[A, R](rest: Stack[Nothing, R]) extends Stack[A, R]

  import Stack.*

  private enum State[A]:
    case Running(waiters: List[Outcome[A] => Unit])
    case Ended(outcome: Outcome[A])

  import State.*

  // A pending suspension a cancel may end. `claimed` is taken exactly once, by
  // the callback that resumes the loop or by the cancel that unwinds it instead;
  // `unwind` is how a cancel does that, since only the loop knows its stack.
  private final class Suspension(val claimed: AtomicBoolean, val unwind: Runnable)

  // One fiber: its loop, its cancellation flag, and whoever waits for it.
  //
  // The mutable state is the documented exception: three atomics, because a
  // fiber is touched by several threads at once — its own loop, the fibers
  // that join or cancel it, and the callbacks that resume it. Each is changed
  // only by compare-and-set or by a single write, never by a lock. Why that is
  // enough is in chapter 06's "Inside the runtime", and what the regions add is
  // in this chapter's "Inside the regions"; RuntimeSpec checks both.
  private final class FiberLoop[A](program: IO[A], scheduler: Scheduler) extends Fiber[A]:

    private val canceled = AtomicBoolean(false)

    private val state = AtomicReference[State[A]](Running(Nil))

    // The suspension the loop is waiting in, if a cancel may end it. A wait
    // inside a region does not appear here, which is what makes it uncancelable.
    private val suspension = AtomicReference[Option[Suspension]](None)

    def begin(): Unit = scheduler.execute(() => loop(program, Done(), false))

    def join: IO[Outcome[A]] = async(callback => onComplete(o => callback(Right(o))))

    def cancel: IO[Unit] = delay(requestCancel()).flatMap(_ => join).map(_ => ())

    // The flag first, then the suspension: `suspend` does the same two in the
    // other order, so at least one of them sees the other.
    def requestCancel(): Unit =
      canceled.set(true)
      suspension.get.foreach: pending =>
        if pending.claimed.compareAndSet(false, true) then scheduler.execute(pending.unwind)

    private[ch07concurrentprimitives] def onComplete(callback: Outcome[A] => Unit): Unit =
      state.get match
        case running @ Running(waiters) =>
          if !state.compareAndSet(running, Running(callback :: waiters)) then onComplete(callback)
        case Ended(outcome) => callback(outcome)

    private def complete(outcome: Outcome[A]): Unit =
      state.getAndSet(Ended(outcome)) match
        case Running(waiters) => waiters.reverse.foreach(_(outcome))
        case Ended(_) => assert(false, "a fiber ended twice")

    // Chapter 06's loop, with the mask as one more argument. Every cancellation
    // point reads `canceled && !masked`, and goes into `Unwind` instead of ending
    // the fiber, so the finalizers run first; `Unwind` is entered with the mask
    // on, so they cannot be canceled in turn. Only the loop reads or writes the
    // mask, so it needs no atomic: it travels with the stack.
    @tailrec private def loop[X](io: IO[X], stack: Stack[X, A], masked: Boolean): Unit =
      io match
        case pure: Pure[X] =>
          stack match
            case Done() => complete(Outcome.Succeeded(pure.a))
            case Bind(f, rest) =>
              if canceled.get && !masked then loop(unit, Unwind(rest), true)
              else loop(protect(f(pure.a)), rest, masked)
            case Handle(_, rest) => loop(pure, rest, masked)
            case Finalize(_, rest) => loop(pure, rest, masked)
            case Restore(outer, rest) => loop(pure, rest, outer)
            case Unwind(rest) =>
              nextFinalizer(rest) match
                case Some((finalizer, below)) => loop(finalizer, Unwind(below), true)
                case None => complete(Outcome.Canceled)
        case delay: Delay[X] => loop(protect(Pure(delay.thunk())), stack, masked)
        case raise: RaiseError =>
          stack match
            case Done() => complete(Outcome.Errored(raise.error))
            case Bind(_, rest) => loop(raise, rest, masked)
            case Handle(handler, rest) =>
              if canceled.get && !masked then loop(unit, Unwind(rest), true)
              else loop(protect(handler(raise.error)), rest, masked)
            case Finalize(_, rest) => loop(raise, rest, masked)
            case Restore(outer, rest) => loop(raise, rest, outer)
            case Unwind(rest) =>
              nextFinalizer(rest) match
                case Some((finalizer, below)) => loop(finalizer, Unwind(below), true)
                case None => complete(Outcome.Canceled)
        case bind: FlatMap[x, X] => loop(bind.source, Bind(bind.f, stack), masked)
        case handle: HandleErrorWith[X] =>
          loop(handle.source, Handle(handle.handler, stack), masked)
        case on: OnCancel[X] => loop(on.source, Finalize(on.finalizer, stack), masked)
        case region: Uncancelable[X] =>
          val poll: Poll = [Y] => (inner: IO[Y]) => Unmask(inner, masked)
          loop(protect(region.body(poll)), Restore(masked, stack), true)
        case unmask: Unmask[X] =>
          if canceled.get && !unmask.masked then loop(unit, Unwind(stack), true)
          else loop(unmask.io, Restore(masked, stack), unmask.masked)
        case start: Start[y] =>
          val child = FiberLoop(start.io, scheduler)
          child.begin()
          loop(Pure(child), stack, masked)
        case async: Async[X] => suspend(async, stack, masked)

    @tailrec private def nextFinalizer(
        stack: Stack[Nothing, A]
    ): Option[(IO[Unit], Stack[Nothing, A])] =
      stack match
        case Done() => None
        case Bind(_, rest) => nextFinalizer(rest)
        case Handle(_, rest) => nextFinalizer(rest)
        case Finalize(finalizer, rest) => Some((finalizer, rest))
        case Restore(_, rest) => nextFinalizer(rest)
        case Unwind(rest) => nextFinalizer(rest)

    private def suspend[X](async: Async[X], stack: Stack[X, A], masked: Boolean): Unit =
      val claimed = AtomicBoolean(false)
      if !masked then
        suspension.set(Some(Suspension(claimed, () => loop(unit, Unwind(stack), true))))
      if !masked && canceled.get && claimed.compareAndSet(false, true) then
        loop(unit, Unwind(stack), true)
      else
        val resume: Either[Throwable, X] => Unit = result =>
          if claimed.compareAndSet(false, true) then
            if !masked then suspension.set(None)
            val next: IO[X] = result match
              case Right(x) => Pure(x)
              case Left(e) => RaiseError(e)
            // A cancellation point of its own: a cancel that lost the claim to
            // this callback must still see the fiber's finalizers run, or a
            // permit handed over just now would go with the fiber.
            scheduler.execute: () =>
              if canceled.get && !masked then loop(unit, Unwind(stack), true)
              else loop(next, stack, masked)
        try async.register(resume)
        catch case NonFatal(e) => resume(Left(e))
  end FiberLoop

  private def protect[A](io: => IO[A]): IO[A] =
    try io
    catch case NonFatal(e) => RaiseError(e)
end IO
