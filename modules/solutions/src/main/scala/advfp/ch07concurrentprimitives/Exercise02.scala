package advfp.ch07concurrentprimitives

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

/** Exercise 02 — Write `Deferred`, a value that arrives once, and that fibers
  * wait for without holding a thread. *Solved.*
  *
  * Two states: `Empty`, with the callbacks of everyone waiting, and `Filled`. One
  * `AtomicReference` holds both, so a reader and the writer can never disagree
  * about which state it is in. A `get` that finds it empty adds its callback with
  * `compareAndSet`, and retries if the state changed underneath it: it lands
  * either in the list, and `complete` calls it, or on `Filled`, and it calls
  * itself. There is no third place, and that is the race settled.
  *
  * `complete` swaps in `Filled` atomically and looks at what it replaced: `Empty`
  * means this call won, so it runs the waiters; `Filled` means it lost, and it
  * changes nothing. The near miss is reading the state and then writing, which
  * lets two completes both answer `true`, and one's value overwrite the other's
  * after a reader saw it. Another is blocking, on a `CountDownLatch` inside a
  * `delay`, which is correct on virtual threads and stops `pool(1)` for good.
  * Measured 2026-11-05: the blocking version fails only the tests on `pool(1)`, by
  * timeout, and passes all twelve others.
  *
  * The waiters run on the thread that calls `complete`, but each one only hands a
  * fiber to the scheduler, so a thousand of them take no time to speak of. The
  * list is reversed so that they are told in the order they arrived.
  */
object Exercise02:

  trait Deferred[A]:
    def get: IO[A]
    def tryGet: IO[Option[A]]
    def complete(a: A): IO[Boolean]

  object Deferred:
    def apply[A]: IO[Deferred[A]] = IO.delay(Cell(AtomicReference[State[A]](State.Empty(Nil))))

  private enum State[A]:
    case Empty(waiters: List[A => Unit])
    case Filled(value: A)

  private final class Cell[A](state: AtomicReference[State[A]]) extends Deferred[A]:
    import State.*

    def get: IO[A] = IO.async(callback => register(a => callback(Right(a))))

    def tryGet: IO[Option[A]] =
      IO.delay:
        state.get match
          case Filled(a) => Some(a)
          case Empty(_) => None

    def complete(a: A): IO[Boolean] = IO.delay(fill(a))

    @tailrec private def register(waiter: A => Unit): Unit =
      state.get match
        case Filled(a) => waiter(a)
        case empty @ Empty(waiters) =>
          if !state.compareAndSet(empty, Empty(waiter :: waiters)) then register(waiter)

    private def fill(a: A): Boolean =
      val before = state.getAndUpdate:
        case Empty(_) => Filled(a)
        case filled => filled
      before match
        case Empty(waiters) =>
          waiters.reverse.foreach(_(a))
          true
        case Filled(_) => false
  end Cell
end Exercise02
