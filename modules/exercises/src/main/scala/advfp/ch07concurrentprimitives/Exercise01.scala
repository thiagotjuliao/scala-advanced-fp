package advfp.ch07concurrentprimitives

import advfp.core.unsolved

/** Exercise 01 — Write `Ref`, a shared cell fibers change without losing an
  * update.
  *
  * The `IO` is the one in `IO.scala`, given. `Primitives.scala` also has a `Ref`,
  * for the later exercises: try this one before reading it. A `Ref` here is a
  * `java.util.concurrent.atomic.AtomicReference` behind a description, and the
  * mutable cell is the one place the chapter allows.
  *
  *   - `Ref.of(a)`: a program that makes a new cell each time it runs.
  *   - `get`, `set(a)`: read and replace.
  *   - `update(f)`: replace the value `v` with `f(v)`.
  *   - `getAndUpdate(f)`: the same, and produce `v`.
  *   - `modify(f)`: `f(v)` is a pair `(next, result)`; store `next`, produce
  *     `result`.
  *
  * Nothing happens until the program runs. No update is ever lost, however many
  * fibers run at once, and no fiber waits for another, so no lock: when two read
  * the same value and one writes first, the other's attempt runs again from the
  * new value. A function that throws fails the program and changes nothing.
  *
  * Hint: `compareAndSet(expected, next)` writes only if the cell still holds
  * `expected`, and says whether it did. Which of the methods can the others be
  * written with?
  */
object Exercise01:

  trait Ref[A]:
    def get: IO[A]
    def set(a: A): IO[Unit]
    def update(f: A => A): IO[Unit]
    def getAndUpdate(f: A => A): IO[A]
    def modify[B](f: A => (A, B)): IO[B]

  object Ref:
    def of[A](a: A): IO[Ref[A]] = unsolved
end Exercise01
