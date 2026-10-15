package advfp.ch02applicativemonadandtraverse

import advfp.core.CourseSuite

import Exercise01.given

class Exercise01Spec extends CourseSuite:

  private type Result[A] = Either[String, A]

  private val inc: Int => Int = _ + 1
  private val double: Int => Int = _ * 2

  // The laws of one monad, checked on samples. The instance is passed by name
  // and only touched inside a test body: against the stub, summoning it throws,
  // and that must fail a test, not the construction of the suite.
  private def monadLaws[F[_]](label: String, instance: => Monad[F])(
      values: List[F[Int]],
      functions: List[F[Int => Int]],
      steps: List[Int => F[Int]]
  ): Unit =
    test(s"$label: applicative identity") {
      val F = instance
      forAllSamples(values): v =>
        assertEquals(F.ap(F.pure((a: Int) => a))(v), v)
    }

    test(s"$label: homomorphism") {
      val F = instance
      forAllSamples(List(0, 7, -3)): x =>
        assertEquals(F.ap(F.pure(inc))(F.pure(x)), F.pure(inc(x)))
    }

    test(s"$label: interchange") {
      val F = instance
      forAllSamples(functions): u =>
        assertEquals(F.ap(u)(F.pure(5)), F.ap(F.pure((f: Int => Int) => f(5)))(u))
    }

    test(s"$label: composition") {
      val F = instance
      val compose = (f: Int => Int) => (g: Int => Int) => f compose g
      forAllSamples(for u <- functions; v <- functions; w <- values yield (u, v, w)): (u, v, w) =>
        assertEquals(F.ap(F.ap(F.ap(F.pure(compose))(u))(v))(w), F.ap(u)(F.ap(v)(w)))
    }

    test(s"$label: left identity") {
      val F = instance
      forAllSamples(steps): f =>
        assertEquals(F.flatMap(F.pure(3))(f), f(3))
    }

    test(s"$label: right identity") {
      val F = instance
      forAllSamples(values): v =>
        assertEquals(F.flatMap(v)(F.pure), v)
    }

    test(s"$label: associativity") {
      val F = instance
      forAllSamples(for v <- values; f <- steps; g <- steps yield (v, f, g)): (v, f, g) =>
        assertEquals(F.flatMap(F.flatMap(v)(f))(g), F.flatMap(v)(a => F.flatMap(f(a))(g)))
    }

    test(s"$label: ap agrees with flatMap") {
      val F = instance
      forAllSamples(for ff <- functions; fa <- values yield (ff, fa)): (ff, fa) =>
        assertEquals(F.ap(ff)(fa), F.flatMap(ff)(f => F.map(fa)(f)))
    }
  end monadLaws

  // --- Option ------------------------------------------------------------------

  test("Option: ap applies a function to a value, both present") {
    assertEquals(Monad[Option].ap(Some(inc))(Some(41)), Some(42))
  }

  test("Option: ap with either side missing is None") {
    val F = Monad[Option]
    assertEquals(F.ap(Option.empty[Int => Int])(Some(1)), None)
    assertEquals(F.ap(Some(inc))(None), None)
  }

  monadLaws[Option]("Option", Monad[Option])(
    values = List(Some(1), Some(-4), None),
    functions = List(Some(inc), Some(double), None),
    steps = List(n => Some(n * 10), n => if n > 0 then Some(n - 1) else None, _ => None)
  )

  // --- Either ------------------------------------------------------------------

  test("Either: ap applies a function to a value, both Right") {
    assertEquals(Monad[Result].ap(Right(inc))(Right(41)), Right(42))
  }

  test("Either: of two Lefts, ap keeps the function's — the first one") {
    val failedFunction: Result[Int => Int] = Left("no function")
    assertEquals(Monad[Result].ap(failedFunction)(Left("no value")), Left("no function"))
  }

  monadLaws[Result]("Either", Monad[Result])(
    values = List(Right(1), Right(-4), Left("a"), Left("b")),
    functions = List(Right(inc), Right(double), Left("f"), Left("g")),
    steps =
      List(n => Right(n * 10), n => if n > 0 then Right(n - 1) else Left(s"$n"), _ => Left("x"))
  )

  // --- List --------------------------------------------------------------------

  test("List: ap applies every function to every value, functions outermost") {
    assertEquals(
      Monad[List].ap(List(inc, double))(List(10, 20)),
      List(11, 21, 20, 40)
    )
  }

  test("List: flatMap concatenates the lists, in order") {
    assertEquals(Monad[List].flatMap(List(1, 2, 3))(n => List.fill(n)(n)), List(1, 2, 2, 3, 3, 3))
  }

  monadLaws[List]("List", Monad[List])(
    values = List(Nil, List(1), List(1, 2, 3)),
    functions = List(Nil, List(inc), List(inc, double)),
    steps = List(n => List(n, n + 1), _ => Nil, n => List.fill(n.abs % 3)(n))
  )
end Exercise01Spec
