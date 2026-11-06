package advfp.ch07concurrentprimitives

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

/** Exercise 01 — Write `Ref`, a shared cell fibers change without losing an
  * update. *Solved.*
  *
  * Everything is `modify`. It reads the cell, computes the next value and the
  * result from that read, and writes with `compareAndSet`, which succeeds only if
  * the cell still holds the very object it read. If another fiber wrote first
  * nothing has happened yet, so it starts over from the new value. One attempt
  * always succeeds, which is what "lock-free" means: a fiber descheduled halfway
  * holds nothing anyone is waiting for.
  *
  * The near miss is `set(f(get))`, two steps with a gap between them: the spec
  * forces two fibers into the gap, and one update disappears. Measured 2026-11-05:
  * that version fails four of the nine tests under each scheduler.
  *
  * `compareAndSet` compares references, not values, and that is safe here only
  * because `current` is the object the cell returned. `A` is generic, so an `Int`
  * stays boxed all the way through. An `AtomicReference[Int]` used directly
  * unboxes on the way out and boxes a new `Integer` on the way in, and above 127
  * it never compares equal: the chapter's document has the measurement.
  */
object Exercise01:

  trait Ref[A]:
    def get: IO[A]
    def set(a: A): IO[Unit]
    def update(f: A => A): IO[Unit]
    def getAndUpdate(f: A => A): IO[A]
    def modify[B](f: A => (A, B)): IO[B]

  object Ref:
    // A program, not a value: `Ref.of(0)` run twice is two cells, which is what
    // lets a program say whether two fibers share one.
    def of[A](a: A): IO[Ref[A]] = IO.delay(Cell(AtomicReference(a)))

  private final class Cell[A](cell: AtomicReference[A]) extends Ref[A]:
    def get: IO[A] = IO.delay(cell.get)

    def set(a: A): IO[Unit] = IO.delay(cell.set(a))

    def update(f: A => A): IO[Unit] = modify(a => (f(a), ()))

    def getAndUpdate(f: A => A): IO[A] = modify(a => (f(a), a))

    def modify[B](f: A => (A, B)): IO[B] = IO.delay(attempt(f))

    @tailrec private def attempt[B](f: A => (A, B)): B =
      val current = cell.get
      val (next, result) = f(current)
      if cell.compareAndSet(current, next) then result else attempt(f)
end Exercise01
