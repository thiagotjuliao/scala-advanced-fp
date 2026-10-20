package advfp.ch05ioasadescription

import scala.annotation.tailrec
import scala.util.control.NonFatal

/** A description of a program that produces an `A`, or fails: given, not an
  * exercise.
  *
  * Exercises 02 to 04 are about *using* `IO`, and they must run before
  * exercise 01, which asks for the run loop, is solved. So this is a complete
  * one, shared by both modules — and, in effect, an answer to exercise 01.
  * Solve that one first.
  *
  * Building an `IO` runs nothing. `unsafeRun()` runs it, every time it is
  * called, in a loop whose stack does not grow with the number of `flatMap`s or
  * handlers. An exception thrown by a `delay`, by a function given to
  * `flatMap` or `map`, or by a handler is caught where it is thrown and becomes
  * the program's error; only fatal errors (`NonFatal` says which) escape.
  */
sealed abstract class IO[+A]:

  def flatMap[B](f: A => IO[B]): IO[B] = IO.FlatMap(this, f)

  def map[B](f: A => B): IO[B] = flatMap(a => IO.Pure(f(a)))

  /** Runs `handler` if *this* program fails. An error raised after it — in a
    * `flatMap` that follows — is not this handler's to see.
    */
  def handleErrorWith[B >: A](handler: Throwable => IO[B]): IO[B] =
    IO.HandleErrorWith(this, handler)

  /** Runs this program, performing its effects. Called once, at the edge of
    * the program; called anywhere else it undoes what `IO` is for.
    */
  def unsafeRun(): Either[Throwable, A] = IO.loop(this, IO.Stack.Done())

object IO:

  def pure[A](a: A): IO[A] = Pure(a)

  /** Runs `thunk` each time the program runs. */
  def delay[A](thunk: => A): IO[A] = Delay(() => thunk)

  /** An `IO` that is built only when it runs: the way to write a recursive
    * `IO`, so that building it does not recurse.
    */
  def defer[A](io: => IO[A]): IO[A] = delay(io).flatMap(identity)

  def raiseError[A](error: Throwable): IO[A] = RaiseError(error)

  val unit: IO[Unit] = Pure(())

  // Plain classes, not case classes: a description holds functions, and the
  // structural equality a case class would give two of them means nothing.
  private final class Pure[+A](val a: A) extends IO[A]

  private final class Delay[+A](val thunk: () => A) extends IO[A]

  private final class RaiseError(val error: Throwable) extends IO[Nothing]

  private final class FlatMap[X, +A](val source: IO[X], val f: X => IO[A]) extends IO[A]

  private final class HandleErrorWith[+A](val source: IO[A], val handler: Throwable => IO[A])
      extends IO[A]

  // What is left to do once the current `IO` produces an `A`, ending in an `R`.
  // A typed stack: a `Bind` turns an `A` into an `IO[B]` and hands it to a
  // stack that expects a `B`, so the loop never needs a cast. An immutable list
  // in all but name, and it lives on the heap — that is the stack safety.
  private enum Stack[-A, +R]:
    case Done[R]() extends Stack[R, R]
    case Bind[A, B, R](f: A => IO[B], rest: Stack[B, R]) extends Stack[A, R]
    case Handle[A, R](handler: Throwable => IO[A], rest: Stack[A, R]) extends Stack[A, R]

  import Stack.*

  // The run loop. Each case either finishes or takes one step and calls itself
  // in tail position. A value pops the stack: a `Bind` is applied, a `Handle`
  // is dropped, since nothing failed inside it. An error pops it too, dropping
  // `Bind`s until a `Handle` takes it. The `try` sits in `protect`, outside the
  // loop: a recursive call inside a `try` is not in tail position.
  @tailrec private def loop[A, R](io: IO[A], stack: Stack[A, R]): Either[Throwable, R] =
    io match
      case pure: Pure[A] =>
        stack match
          case Done() => Right(pure.a)
          case Bind(f, rest) => loop(protect(f(pure.a)), rest)
          case Handle(_, rest) => loop(pure, rest)
      case delay: Delay[A] => loop(protect(Pure(delay.thunk())), stack)
      case raise: RaiseError =>
        stack match
          case Done() => Left(raise.error)
          case Bind(_, rest) => loop(raise, rest)
          case Handle(handler, rest) => loop(protect(handler(raise.error)), rest)
      case bind: FlatMap[x, A] => loop(bind.source, Bind(bind.f, stack))
      case handle: HandleErrorWith[A] => loop(handle.source, Handle(handle.handler, stack))

  private def protect[A](io: => IO[A]): IO[A] =
    try io
    catch case NonFatal(e) => RaiseError(e)
end IO
