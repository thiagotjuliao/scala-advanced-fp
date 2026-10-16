package advfp.ch03statereaderwriterandvalidated

import advfp.core.CourseSuite

import Exercise02.{*, given}

class Exercise02Spec extends CourseSuite:

  // --- Reader --------------------------------------------------------------------

  private type Configured[A] = Reader[Config, A]

  private val production = Config("example.com", 443, secure = true)
  private val local = Config("localhost", 8080, secure = false)
  private val configs = List(production, local)

  private def assertSame[A](x: Configured[A], y: Configured[A]): Unit =
    forAllSamples(configs): c =>
      assertEquals(x.run(c), y.run(c))

  private def readers: List[Configured[Int]] =
    List(Monad[Configured].pure(1), Reader.ask[Config].map(_.port), Reader(_.host.length))

  private def readerSteps: List[Int => Configured[Int]] =
    List(n => Monad[Configured].pure(n + 1), n => Reader(c => c.port - n))

  test("Reader: ask returns the environment") {
    assertEquals(Reader.ask[Config].run(local), local)
  }

  test("Reader: flatMap hands both steps the same environment") {
    val both =
      Reader.ask[Config].flatMap(first => Reader.ask[Config].map(second => (first, second)))
    assertEquals(both.run(local), (local, local))
  }

  test("Reader: left identity, right identity and associativity") {
    forAllSamples(readerSteps): f =>
      assertSame(Monad[Configured].pure(3).flatMap(f), f(3))
    forAllSamples(readers): r =>
      assertSame(r.flatMap(Monad[Configured].pure), r)
    forAllSamples(for r <- readers; f <- readerSteps; g <- readerSteps yield (r, f, g)):
      (r, f, g) => assertSame(r.flatMap(f).flatMap(g), r.flatMap(a => f(a).flatMap(g)))
  }

  test("Reader: local runs the program in a modified environment") {
    val port = Reader.local[Config, Int](_.copy(port = 1))(Reader.ask[Config].map(_.port))
    assertEquals(port.run(local), 1)
  }

  test("Reader: local does not leak into what comes after it") {
    val program =
      Reader
        .local[Config, Int](_.copy(port = 1))(Reader.ask[Config].map(_.port))
        .flatMap(inner => Reader.ask[Config].map(outer => (inner, outer.port)))
    assertEquals(program.run(local), (1, 8080))
  }

  test("baseUrl picks the scheme from the configuration") {
    assertEquals(baseUrl.run(production), "https://example.com:443")
    assertEquals(baseUrl.run(local), "http://localhost:8080")
  }

  test("endpoint appends the path to the base URL") {
    assertEquals(endpoint("/users").run(production), "https://example.com:443/users")
  }

  test("onStaging prefixes the host, for the program it wraps only") {
    assertEquals(
      onStaging(endpoint("/users")).run(production),
      "https://staging.example.com:443/users"
    )
    assertEquals(onStaging(Reader.ask[Config]).run(local).port, 8080)
  }

  // --- Writer --------------------------------------------------------------------

  private type Logged[A] = Writer[List[String], A]

  test("Writer: tell logs its argument and returns nothing") {
    assertEquals(Writer.tell(List("hi")), Writer(List("hi"), ()))
  }

  test("Writer: pure logs nothing") {
    assertEquals(Monad[Logged].pure(1), Writer(List.empty[String], 1))
  }

  test("Writer: flatMap keeps the first step's log in front") {
    val program = Writer.tell(List("a", "b")).flatMap(_ => Writer(List("c"), 7))
    assertEquals(program, Writer(List("a", "b", "c"), 7))
  }

  test("Writer: left identity, right identity and associativity") {
    val writers: List[Logged[Int]] =
      List(Writer(Nil, 1), Writer(List("x"), 2), Writer(List("y", "z"), 3))
    val writerSteps: List[Int => Logged[Int]] =
      List(n => Monad[Logged].pure(n * 2), n => Writer(List(s"saw $n"), n + 1))
    forAllSamples(writerSteps): f =>
      assertEquals(Monad[Logged].pure(5).flatMap(f), f(5))
    forAllSamples(writers): w =>
      assertEquals(w.flatMap(Monad[Logged].pure), w)
    forAllSamples(for w <- writers; f <- writerSteps; g <- writerSteps yield (w, f, g)):
      (w, f, g) => assertEquals(w.flatMap(f).flatMap(g), w.flatMap(a => f(a).flatMap(g)))
  }

  test("collatz logs every value from n down to 1, and counts the steps") {
    assertEquals(collatz(6), Writer(List(6, 3, 10, 5, 16, 8, 4, 2, 1), 8))
  }

  test("collatz of 1 logs 1 and takes no steps") {
    assertEquals(collatz(1), Writer(List(1), 0))
  }

  test("collatz rejects a number that is not positive") {
    intercept[IllegalArgumentException](collatz(0))
  }

  test("collatz agrees with the sequence's known length") {
    // 27 takes 111 steps and climbs to 9232 on the way: a long log, with
    // values far from where it started.
    val result = collatz(27)
    assertEquals(result.value, 111)
    assertEquals(result.log.size, 112)
    assertEquals(result.log.max, 9232)
  }
end Exercise02Spec
