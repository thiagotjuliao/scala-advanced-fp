package advfp.ch07concurrentprimitives

import advfp.core.unsolved

/** Exercise 02 — Write `Deferred`, a value that arrives once, and that fibers
  * wait for without holding a thread.
  *
  * The `IO` is the one in `IO.scala`, given; `IO.async(register)` is how a fiber
  * waits. `Primitives.scala` also has a `Deferred`, for the later exercises: try
  * this one before reading it.
  *
  *   - `Deferred[A]`: a program that makes a new, empty one each time it runs.
  *   - `get`: the value; if it has not arrived, wait for it.
  *   - `tryGet`: `Some(value)` if it has arrived, `None` if not, and never waits.
  *   - `complete(a)`: fill it, and let everyone waiting go on. Only the first call
  *     counts: it produces `true`, and any later one changes nothing and produces
  *     `false`.
  *
  * Nothing happens until the program runs. A thousand fibers waiting hold no
  * thread, so all of this runs on `Scheduler.pool(1)`. A `get` that lands at the
  * same moment as a `complete`, or as another `get`, is still told. A fiber
  * waiting in `get` can be canceled.
  *
  * Hint: what are the two states it can be in, and what does the empty one have
  * to remember? A callback `IO.async` hands you may be called from any thread.
  */
object Exercise02:

  trait Deferred[A]:
    def get: IO[A]
    def tryGet: IO[Option[A]]
    def complete(a: A): IO[Boolean]

  object Deferred:
    def apply[A]: IO[Deferred[A]] = unsolved
end Exercise02
