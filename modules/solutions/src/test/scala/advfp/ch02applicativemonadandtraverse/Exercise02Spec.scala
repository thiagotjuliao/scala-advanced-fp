package advfp.ch02applicativemonadandtraverse

import advfp.core.CourseSuite

import Exercise02.{*, given}

class Exercise02Spec extends CourseSuite:

  private val one = Logged.say("one", 1)
  private val two = Logged.say("two", 2)
  private val three = Logged.say("three", 3)
  private val silent = Logged(Nil, 0)

  test("map2 combines the values") {
    assertEquals(one.map2(two)(_ + _).value, 3)
  }

  test("map2 runs the first argument's effect first") {
    assertEquals(one.map2(two)(_ + _).log, List("one", "two"))
  }

  test("map2 with pure on either side adds no effect") {
    val F = Applicative[Logged]
    assertEquals(F.pure(10).map2(two)(_ * _), Logged(List("two"), 20))
    assertEquals(one.map2(F.pure(10))(_ * _), Logged(List("one"), 10))
  }

  test("product pairs the values, first argument on the left") {
    assertEquals(one.product(two), Logged(List("one", "two"), (1, 2)))
  }

  test("product is map2 with the pairing function") {
    forAllSamples(List((one, two), (two, one), (silent, three))): (x, y) =>
      assertEquals(x.product(y), x.map2(y)((a, b) => (a, b)))
  }

  test("mapN combines three values, effects left to right") {
    assertEquals(
      (one, two, three).mapN((a, b, c) => s"$a$b$c"),
      Logged(List("one", "two", "three"), "123")
    )
  }

  test("map2, product and mapN hold for every applicative, not only Logged") {
    // A second applicative, defined here, so the derived combinators are seen
    // to work for an instance they were not written against.
    given Applicative[Option]:
      def pure[A](a: A): Option[A] = Some(a)
      extension [A, B](ff: Option[A => B])
        def ap(fa: Option[A]): Option[B] = ff.flatMap(f => fa.map(f))
    assertEquals(Option(2).map2(Option(3))(_ * _), Some(6))
    assertEquals(Option(2).product(Option.empty[Int]), None)
    assertEquals((Option(1), Option(2), Option(3)).mapN(_ + _ + _), Some(6))
  }

  test("apFromFlatMap applies the function") {
    val add = Logged.say("add", (n: Int) => n + 100)
    assertEquals(apFromFlatMap(add)(one).value, 101)
  }

  test("apFromFlatMap runs the function's effect before the value's") {
    val add = Logged.say("add", (n: Int) => n + 100)
    assertEquals(apFromFlatMap(add)(one).log, List("add", "one"))
  }

  test("apFromFlatMap agrees with the instance's own ap") {
    val functions = List(
      Logged.say("inc", (n: Int) => n + 1),
      Logged(Nil, (n: Int) => n * 2),
      Logged(List("a", "b"), (n: Int) => -n)
    )
    forAllSamples(for ff <- functions; fa <- List(one, silent, three) yield (ff, fa)): (ff, fa) =>
      assertEquals(apFromFlatMap(ff)(fa), ff.ap(fa))
  }
end Exercise02Spec
