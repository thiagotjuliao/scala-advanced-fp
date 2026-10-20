package advfp.ch05ioasadescription

import java.util.concurrent.atomic.AtomicInteger

/** Chapter 05 — IO as a description.
  *
  * Read this file top to bottom before opening the exercises. It is the whole
  * chapter in executable form: it compiles with the module, so none of it is
  * pseudo-code.
  *
  * Prose version: `docs/theory/ch05-io-as-a-description.md`.
  */
object Walkthrough:

  // --- 1. An effect happens where it is written --------------------------------
  //
  // A stand-in for `readLine()`: each call returns the next number. The counter
  // is the effect being demonstrated, so it is mutable on purpose.

  private val source = AtomicInteger(0)

  def nextImpure(): Int = source.incrementAndGet()

  // Two calls, two numbers.
  def pairInlined(): (Int, Int) = (nextImpure(), nextImpure())

  // The same expression, extracted into a `val`: one call, one number, twice.
  // The most ordinary refactoring there is changed what the program does,
  // and nothing in the type `Int` warned about it.
  def pairExtracted(): (Int, Int) =
    val n = nextImpure()
    (n, n)

  // --- 2. A by-name parameter postpones, once ----------------------------------
  //
  // `=> A` delays an expression until the function uses it, and the function
  // can use it twice. That is half of what is needed.

  def twiceByName[A](a: => A): (A, A) = (a, a)

  // `twiceByName(nextImpure())` calls `nextImpure` twice. But the delayed
  // expression cannot be stored in a list, returned, combined with another, or
  // told what to do if it throws — it exists for the duration of one call.

  // --- 3. A description is a value ---------------------------------------------
  //
  // `IO.delay` stores the expression in a value. Building it runs nothing;
  // every `unsafeRun()` runs it again.

  val next: IO[Int] = IO.delay(source.incrementAndGet())

  // Inlined and extracted are now the same program: both pair two runs of
  // `next`. Replacing a name by its definition, or the other way round,
  // changes nothing — that is referential transparency.
  val pairOfRuns: IO[(Int, Int)] = next.flatMap(a => next.map(b => (a, b)))

  val pairOfRunsExtracted: IO[(Int, Int)] =
    val n = next
    n.flatMap(a => n.map(b => (a, b)))

  // --- 4. Errors are part of the description -----------------------------------
  //
  // `raiseError` fails without throwing, and an exception thrown inside a
  // `delay` becomes the same kind of failure. `handleErrorWith` is a scope: it
  // sees the errors of the program it wraps, and only those.

  final class NotANumber(text: String) extends RuntimeException(s"not a number: $text")

  def parse(text: String): IO[Int] =
    IO.delay(text.toInt).handleErrorWith(_ => IO.raiseError(NotANumber(text)))

  // Recovers from the parse failing: `Right(0)` when run.
  val recovered: IO[Int] = parse("x").handleErrorWith(_ => IO.pure(0))

  // Does not recover from the division failing: the handler's scope ended when
  // `parse("4")` succeeded. Run, it is a `Left` holding an `ArithmeticException`.
  val notRecovered: IO[Int] = parse("4").handleErrorWith(_ => IO.pure(0)).map(n => n / 0)

  // --- 5. Running is a loop with a stack of its own ----------------------------
  //
  // `unsafeRun()` walks the description one node at a time. For
  //
  //   parse("4").handleErrorWith(h).map(f)
  //
  // it pushes `f`, then `h`, then runs the `delay` inside `parse`. The value
  // pops `h` — nothing failed inside it — and then `f`, which divides by zero:
  // an error, with nothing left on the stack to handle it. The stack is a list
  // on the heap; a million `flatMap`s are a million cells, and the JVM stack
  // stays one frame deep. Exercise 01 writes that loop.

  // --- 6. A description can be run again ---------------------------------------
  //
  // Because `next` is a program and not a number, a function that receives it
  // can run it as often as it decides: twice, until it succeeds, until its
  // value is good enough. Exercise 03 writes three of those.

  // --- 7. The end of the world -------------------------------------------------
  //
  // Effects happen in one place: the call to `unsafeRun()` at the edge of the
  // program. A real `main` builds one `IO` out of all the others and runs it
  // once. Here, a method stands in for `main`.

  def main(): Either[Throwable, (Int, Int)] = pairOfRuns.unsafeRun()

  // --- 8. Laws, compared by running --------------------------------------------
  //
  // `Monad[IO]` holds its laws, but `==` on two descriptions compares function
  // objects and means nothing. Two `IO`s are equal when running each gives the
  // same result and does the same effects in the same order. Exercise 04
  // checks the laws that way, and finds that comparing results alone lets
  // through a `flatMap` that runs its effects at the wrong time.
end Walkthrough
