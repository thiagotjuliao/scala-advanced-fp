package advfp.ch05ioasadescription

import advfp.core.CourseSuite

import java.util.concurrent.atomic.AtomicReference

import Exercise04.{*, given}

class Exercise04Spec extends CourseSuite:

  private val deep = 1_000_000

  // What a program did: the lines it wrote, in order. One log per run, and
  // the only mutable thing in the spec.
  private final class Log:
    private val lines = AtomicReference(Vector.empty[String])

    def write(line: String): IO[Unit] = IO.delay(lines.updateAndGet(_ :+ line)).map(_ => ())

    def read: Vector[String] = lines.get

  // A program is written against a log, so it can be run on a fresh one.
  private type Program[A] = Log => IO[A]

  // Two programs are equal when running them gives the same result *and* writes
  // the same lines. Comparing results alone cannot see an instance that runs an
  // effect too early, twice, or not at all.
  private def observe[A](program: Program[A]): (Either[Throwable, A], Vector[String]) =
    val log = Log()
    val result = program(log).unsafeRun()
    (result, log.read)

  private def assertSame[A](x: Program[A], y: Program[A]): Unit =
    assertEquals(observe(x), observe(y))

  private final class Boom(label: String) extends RuntimeException(label)

  private val boom = Boom("boom")

  // The laws are about the instance, so they go through it. `IO` has `map` and
  // `flatMap` methods of its own, and `io.flatMap(f)` would call those instead.
  private def pure[A](a: A): IO[A] = Monad[IO].pure(a)

  private def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = Monad[IO].flatMap(fa)(f)

  private def fmap[A, B](fa: IO[A])(f: A => B): IO[B] = Monad[IO].map(fa)(f)

  // Samples that succeed, fail, throw, and write — a law checked only on `pure`
  // proves nothing about the order of effects.
  private def programs: List[Program[Int]] =
    List(
      _ => pure(5),
      log => log.write("a").map(_ => 6),
      _ => IO.raiseError(boom),
      _ => IO.delay(throw boom),
      log => log.write("b").flatMap(_ => log.write("c")).map(_ => 7)
    )

  private def steps: List[Int => Program[Int]] =
    List(
      n => _ => pure(n + 1),
      n => log => log.write(s"f$n").map(_ => n * 3),
      n => _ => if n > 5 then IO.raiseError(boom) else pure(n)
    )

  // --- the instance ------------------------------------------------------------

  test("pure is a value, with no effect") {
    assertEquals(observe(_ => pure("x")), (Right("x"), Vector.empty))
  }

  test("flatMap runs nothing until the program runs") {
    val log = Log()
    val io = bind(log.write("first"))(_ => log.write("second"))
    assertEquals(log.read, Vector.empty)
    assertEquals(io.unsafeRun(), Right(()))
    assertEquals(log.read, Vector("first", "second"))
  }

  test("a program built with flatMap does its effects again on every run") {
    val log = Log()
    val io = bind(log.write("x"))(_ => pure(1))
    assertEquals(io.unsafeRun(), Right(1))
    assertEquals(io.unsafeRun(), Right(1))
    assertEquals(log.read, Vector("x", "x"))
  }

  test("flatMap skips the function after an error") {
    assertEquals(
      observe(log => bind(IO.raiseError[Int](boom))(_ => log.write("f"))),
      (Left(boom), Vector.empty)
    )
  }

  test("map transforms the value and keeps the effect") {
    assertEquals(observe(log => fmap(log.write("a"))(_ => 2)), (Right(2), Vector("a")))
  }

  test("ap runs the function's program before the argument's") {
    val program: Program[Int] =
      log => fmap(log.write("f"))(_ => (n: Int) => n + 1).ap(fmap(log.write("a"))(_ => 1))
    assertEquals(observe(program), (Right(2), Vector("f", "a")))
  }

  // --- the laws ----------------------------------------------------------------

  test("left identity") {
    forAllSamples(steps): f =>
      assertSame(log => bind(pure(3))(f(_)(log)), f(3))
  }

  test("right identity") {
    forAllSamples(programs): p =>
      assertSame(log => bind(p(log))(pure), p)
  }

  test("associativity") {
    forAllSamples(for p <- programs; f <- steps; g <- steps yield (p, f, g)): (p, f, g) =>
      assertSame(
        log => bind(bind(p(log))(f(_)(log)))(g(_)(log)),
        log => bind(p(log))(a => bind(f(a)(log))(g(_)(log)))
      )
  }

  test("map agrees with flatMap and pure") {
    forAllSamples(programs): p =>
      assertSame(log => fmap(p(log))(_ + 1), log => bind(p(log))(n => pure(n + 1)))
  }

  // --- traverse ----------------------------------------------------------------

  test("traverse of no elements is an empty list, with no effect") {
    assertEquals(
      observe(log => traverse(List.empty[Int])(n => log.write(s"$n"))),
      (Right(Nil), Vector.empty)
    )
  }

  test("traverse keeps the results and the effects in order") {
    val program: Program[List[Int]] =
      log => traverse(List(1, 2, 3))(n => log.write(s"$n").map(_ => n * 10))
    assertEquals(observe(program), (Right(List(10, 20, 30)), Vector("1", "2", "3")))
  }

  test("traverse runs nothing until the program runs") {
    val log = Log()
    val io = traverse(List(1, 2))(n => log.write(s"$n"))
    assertEquals(log.read, Vector.empty)
    assertEquals(io.unsafeRun(), Right(List((), ())))
  }

  test("traverse stops at the first error, and runs nothing after it") {
    val program: Program[List[Int]] = log =>
      traverse(List(1, 2, 3)) { n =>
        log.write(s"$n").flatMap(_ => if n == 2 then IO.raiseError(boom) else pure(n))
      }
    assertEquals(observe(program), (Left(boom), Vector("1", "2")))
  }

  test("traverse runs over a million elements") {
    val numbers = List.range(0, deep)
    assertEquals(traverse(numbers)(n => IO.delay(n + 1)).unsafeRun(), Right(numbers.map(_ + 1)))
  }
end Exercise04Spec
