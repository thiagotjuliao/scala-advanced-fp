package advfp.ch07concurrentprimitives

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/** A shared cell that changes atomically: given, so that exercises 03 and 04 do
  * not depend on exercise 01, which writes the same thing.
  *
  * The functions given to `update`, `modify` and `getAndUpdate` may run more than
  * once when fibers contend, so they must be pure.
  */
final class Ref[A] private (cell: AtomicReference[A]):
  def get: IO[A] = IO.delay(cell.get)

  def set(a: A): IO[Unit] = IO.delay(cell.set(a))

  def update(f: A => A): IO[Unit] = modify(a => (f(a), ()))

  /** Changes the cell and produces its previous value. */
  def getAndUpdate(f: A => A): IO[A] = modify(a => (f(a), a))

  /** Changes the cell and produces a value computed from the same read. */
  def modify[B](f: A => (A, B)): IO[B] = IO.delay(attempt(f))

  @tailrec private def attempt[B](f: A => (A, B)): B =
    val current = cell.get
    val (next, result) = f(current)
    if cell.compareAndSet(current, next) then result else attempt(f)

object Ref:
  /** A new cell each time the program runs: run it twice and there are two. */
  def of[A](a: A): IO[Ref[A]] = IO.delay(new Ref(AtomicReference(a)))

/** A value that arrives once: given, so that exercises 03 and 04 do not depend
  * on exercise 02, which writes the same thing.
  *
  * A canceled `get` leaves its callback here until `complete` runs it, and it
  * does nothing then.
  */
final class Deferred[A] private (state: AtomicReference[Deferred.State[A]]):
  import Deferred.State.*

  /** Waits for the value, without holding a thread. */
  def get: IO[A] = IO.async(callback => register(a => callback(Right(a))))

  def tryGet: IO[Option[A]] =
    IO.delay:
      state.get match
        case Filled(a) => Some(a)
        case Empty(_) => None

  /** Fills it, and tells everyone waiting; `true` if this call was the one that
    * filled it. Whoever calls it runs the waiters' callbacks, each of which only
    * hands a fiber to the scheduler.
    */
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
end Deferred

object Deferred:
  private enum State[A]:
    case Empty(waiters: List[A => Unit])
    case Filled(value: A)

  /** A new, empty one each time the program runs. */
  def apply[A]: IO[Deferred[A]] =
    IO.delay(new Deferred(AtomicReference[State[A]](State.Empty(Nil))))
