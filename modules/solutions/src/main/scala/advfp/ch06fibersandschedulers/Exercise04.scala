package advfp.ch06fibersandschedulers

import java.util.concurrent.TimeoutException

import scala.concurrent.duration.FiniteDuration

/** Exercise 04 — Write `race`, which cancels the loser, and `timeout` on top of
  * it. *Solved.*
  *
  * `racePair` does the hard half: two fibers, one decision about which ended
  * first, and no thread held while waiting for it. What it leaves is the
  * loser, still running, and `race` is the decision about what to do with it.
  * The answer is the same in every case: cancel it, and wait until it has
  * stopped — `cancel` waits — before producing anything. A race that only
  * returns the winner has stopped *waiting* for the loser, and the loser goes
  * on doing whatever it would have done: the spec's loser writes "too late"
  * a hundred milliseconds after the race has ended. Measured 2026-10-23: that
  * version fails four of the ten tests under each scheduler.
  *
  * A winner that failed is still a winner. The race fails with its error, after
  * canceling the loser like any other time: an error is an outcome, and
  * waiting for a second opinion from the loser would turn "fails fast" into
  * "fails when the slower one is done".
  *
  * A winner that was canceled is the exception. Nothing in the race canceled
  * it, so someone outside did, and the race is decided by the program still
  * running: join it, and make its outcome the race's. Raising an error there
  * would turn a cancel of one side into the failure of both.
  *
  * `timeout` is a race against `IO.sleep`: the program wins with its value or
  * its error, the clock wins with a `TimeoutException`. Because `race` cancels
  * its loser, the program that ran too long is stopped — `timeout` ends both
  * the wait and the work. `sleep` suspends, so the clock holds no thread while
  * it runs, and when the program wins the clock is canceled at once.
  */
object Exercise04:

  def race[A, B](a: IO[A], b: IO[B]): IO[Either[A, B]] =
    IO.racePair(a, b)
      .flatMap:
        case Left((outcome, loser)) =>
          outcome match
            case Outcome.Canceled => loser.join.flatMap(o => fromOutcome(o).map(Right(_)))
            case _ => loser.cancel.flatMap(_ => fromOutcome(outcome).map(Left(_)))
        case Right((loser, outcome)) =>
          outcome match
            case Outcome.Canceled => loser.join.flatMap(o => fromOutcome(o).map(Left(_)))
            case _ => loser.cancel.flatMap(_ => fromOutcome(outcome).map(Right(_)))

  extension [A](io: IO[A])
    def timeout(duration: FiniteDuration): IO[A] =
      race(io, IO.sleep(duration)).flatMap:
        case Left(a) => IO.pure(a)
        case Right(()) => IO.raiseError(TimeoutException(s"timed out after ${duration.toString}"))

  // Both sides canceled leaves no value to produce: the race was canceled
  // from outside, twice over, and fails like a joined canceled fiber.
  private def fromOutcome[A](outcome: Outcome[A]): IO[A] =
    outcome match
      case Outcome.Succeeded(a) => IO.pure(a)
      case Outcome.Errored(e) => IO.raiseError(e)
      case Outcome.Canceled =>
        IO.raiseError(IllegalStateException("both sides of the race were canceled"))
end Exercise04
