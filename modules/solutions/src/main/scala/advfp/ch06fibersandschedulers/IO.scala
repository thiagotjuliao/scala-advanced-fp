package advfp.ch06fibersandschedulers

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

/** Where fibers run: given, not an exercise — exercise 01 writes the two
  * strategies itself.
  */
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

  /** Asks the fiber to stop, and waits until it has. A fiber stops at its next
    * cancellation point, so one inside a long `delay` finishes that `delay`
    * first. Canceling a fiber that has already ended does nothing.
    */
  def cancel: IO[Unit]

  // For the runtime's own combinators (`join`, `racePair`): runs `callback`
  // once the fiber has ended, on the thread that ended it.
  private[ch06fibersandschedulers] def onComplete(callback: Outcome[A] => Unit): Unit

/** A description of a program that produces an `A`, fails, or is canceled:
  * given, not an exercise.
  *
  * Chapter 05's `IO`, with three cases added: `Start` runs a program in a
  * fiber of its own, `Async` suspends the fiber until a callback is called, and
  * each fiber carries a cancellation flag its loop checks between binds.
  * Exercise 02 writes that loop's cancellation, exercises 03 and 04 use this
  * one.
  */
sealed abstract class IO[+A]:

  def flatMap[B](f: A => IO[B]): IO[B] = IO.FlatMap(this, f)

  def map[B](f: A => B): IO[B] = flatMap(a => IO.Pure(f(a)))

  /** Runs `handler` if *this* program fails. A canceled fiber runs no handler. */
  def handleErrorWith[B >: A](handler: Throwable => IO[B]): IO[B] =
    IO.HandleErrorWith(this, handler)

  /** Starts this program in a new fiber, on the same scheduler, and produces
    * its handle at once.
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

  def pure[A](a: A): IO[A] = Pure(a)

  /** Runs `thunk` each time the program runs. */
  def delay[A](thunk: => A): IO[A] = Delay(() => thunk)

  def defer[A](io: => IO[A]): IO[A] = delay(io).flatMap(identity)

  def raiseError[A](error: Throwable): IO[A] = RaiseError(error)

  val unit: IO[Unit] = Pure(())

  /** Suspends the fiber until `register`'s callback is called, and goes on with
    * the value or the error it is called with. Only the first call counts. The
    * fiber holds no thread while it waits, and it is canceled at once.
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
        // Both fibers may end at once, on two threads: one flag decides.
        val decided = AtomicBoolean(false)
        fa.onComplete(o =>
          if decided.compareAndSet(false, true) then callback(Right(Left((o, fb))))
        )
        fb.onComplete(o =>
          if decided.compareAndSet(false, true) then callback(Right(Right((fa, o))))
        )
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

  private final class Start[+A](val io: IO[A]) extends IO[Fiber[A]]

  private final class Async[+A](val register: (Either[Throwable, A] => Unit) => Unit) extends IO[A]

  // Chapter 05's typed stack of what is left to do.
  private enum Stack[-A, +R]:
    case Done[R]() extends Stack[R, R]
    case Bind[A, B, R](f: A => IO[B], rest: Stack[B, R]) extends Stack[A, R]
    case Handle[A, R](handler: Throwable => IO[A], rest: Stack[A, R]) extends Stack[A, R]

  import Stack.*

  private enum State[A]:
    case Running(waiters: List[Outcome[A] => Unit])
    case Ended(outcome: Outcome[A])

  import State.*

  // One fiber: its loop, its cancellation flag, and whoever waits for it.
  //
  // The mutable state is the documented exception: three atomics, because a
  // fiber is touched by several threads at once — its own loop, the fibers
  // that join or cancel it, and the callbacks that resume it. Each is changed
  // only by compare-and-set or by a single write, never by a lock.
  private final class FiberLoop[A](program: IO[A], scheduler: Scheduler) extends Fiber[A]:

    private val canceled = AtomicBoolean(false)

    private val state = AtomicReference[State[A]](Running(Nil))

    // The suspension the loop is waiting in, if any. Its flag is claimed
    // exactly once: by the callback that resumes the loop, or by a cancel
    // that ends the fiber instead.
    private val suspension = AtomicReference[Option[AtomicBoolean]](None)

    def begin(): Unit = scheduler.execute(() => loop(program, Done()))

    def join: IO[Outcome[A]] = async(callback => onComplete(o => callback(Right(o))))

    def cancel: IO[Unit] = delay(requestCancel()).flatMap(_ => join).map(_ => ())

    // The flag first, then the suspension: `suspend` does the same two in the
    // other order, so at least one of them sees the other.
    def requestCancel(): Unit =
      canceled.set(true)
      suspension.get.foreach: claimed =>
        if claimed.compareAndSet(false, true) then complete(Outcome.Canceled)

    private[ch06fibersandschedulers] def onComplete(callback: Outcome[A] => Unit): Unit =
      state.get match
        case running @ Running(waiters) =>
          if !state.compareAndSet(running, Running(callback :: waiters)) then onComplete(callback)
        case Ended(outcome) => callback(outcome)

    private def complete(outcome: Outcome[A]): Unit =
      state.getAndSet(Ended(outcome)) match
        case Running(waiters) => waiters.reverse.foreach(_(outcome))
        case Ended(_) => assert(false, "a fiber ended twice")

    // Chapter 05's loop, with three changes. Before a bind's function is
    // applied and before a handler runs, the cancellation flag is read: those
    // are the cancellation points. `Start` begins another fiber and goes on at
    // once. `Async` leaves the loop: the thread returns to the scheduler, and
    // the callback starts a new loop with the same stack.
    @tailrec private def loop[X](io: IO[X], stack: Stack[X, A]): Unit =
      io match
        case pure: Pure[X] =>
          stack match
            case Done() => complete(Outcome.Succeeded(pure.a))
            case Bind(f, rest) =>
              if canceled.get then complete(Outcome.Canceled)
              else loop(protect(f(pure.a)), rest)
            case Handle(_, rest) => loop(pure, rest)
        case delay: Delay[X] => loop(protect(Pure(delay.thunk())), stack)
        case raise: RaiseError =>
          stack match
            case Done() => complete(Outcome.Errored(raise.error))
            case Bind(_, rest) => loop(raise, rest)
            case Handle(handler, rest) =>
              if canceled.get then complete(Outcome.Canceled)
              else loop(protect(handler(raise.error)), rest)
        case bind: FlatMap[x, X] => loop(bind.source, Bind(bind.f, stack))
        case handle: HandleErrorWith[X] => loop(handle.source, Handle(handle.handler, stack))
        case start: Start[y] =>
          val child = FiberLoop(start.io, scheduler)
          child.begin()
          loop(Pure(child), stack)
        case async: Async[X] => suspend(async, stack)

    private def suspend[X](async: Async[X], stack: Stack[X, A]): Unit =
      val claimed = AtomicBoolean(false)
      suspension.set(Some(claimed))
      if canceled.get && claimed.compareAndSet(false, true) then complete(Outcome.Canceled)
      else
        val resume: Either[Throwable, X] => Unit = result =>
          if claimed.compareAndSet(false, true) then
            suspension.set(None)
            val next: IO[X] = result match
              case Right(x) => Pure(x)
              case Left(e) => RaiseError(e)
            scheduler.execute(() => loop(next, stack))
        try async.register(resume)
        catch case NonFatal(e) => resume(Left(e))
  end FiberLoop

  private def protect[A](io: => IO[A]): IO[A] =
    try io
    catch case NonFatal(e) => RaiseError(e)
end IO
