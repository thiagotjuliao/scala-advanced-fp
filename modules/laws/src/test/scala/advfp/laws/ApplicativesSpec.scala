package advfp.laws

import advfp.typeclasses.*
import advfp.typeclasses.Eq.===
import munit.FunSuite

class ApplicativesSpec extends FunSuite with PropAssertions:
  import Id.given

  private type Log[X] = Const[List[Int], X]
  private type OptionOfList[X] = Nested[Option, List, X]

  private given Eq[Log[Int]] = (x, y) => x.value === y.value
  private given Eq[OptionOfList[Int]] = (x, y) => x.value === y.value

  private val ints = Gen.choose(-5, 5)
  private val seeds = List.tabulate(5)(i => Config(seed = i.toLong))

  private val ids: [X] => Gen[X] => Gen[Id[X]] = [X] => (g: Gen[X]) => g
  private val logs: [X] => Gen[X] => Gen[Log[X]] =
    [X] => (_: Gen[X]) => Gen.listOf(ints).map(Const(_))
  private val nesteds: [X] => Gen[X] => Gen[OptionOfList[X]] =
    [X] => (g: Gen[X]) => Gen.option(Gen.listOf(g)).map(Nested(_))

  test("Id, Const and Nested are lawful applicatives"):
    seeds.foreach: config =>
      assertSuite(Suite.applicative[Id](ids), config)
      assertSuite(Suite.applicative[Log](logs), config)
      assertSuite(Suite.applicative[OptionOfList](nesteds), config)

  // The laws cannot tell these orders apart: flipping every combine keeps both
  // sides of composition equal, so the order is asserted on values.
  test("Const's ap keeps the function's log first"):
    val ff = Const[List[Int], Int => Int](List(1))
    val fa = Const[List[Int], Int](List(2))
    assertEquals(Applicative[Log].ap(ff)(fa).value, List(1, 2))

  test("Nested's ap runs each layer's effects with the functions outermost"):
    val ff = Nested[Option, List, Int => Int](Some(List(_ + 1, _ * 2)))
    val fa = Nested[Option, List, Int](Some(List(10, 20)))
    assertEquals(Applicative[OptionOfList].ap(ff)(fa).value, Some(List(11, 21, 20, 40)))

  test("Nested's ap is None when either side's outer layer is"):
    val ff = Nested[Option, List, Int => Int](Some(List(_ + 1)))
    val fa = Nested[Option, List, Int](None)
    assertEquals(Applicative[OptionOfList].ap(ff)(fa).value, None)
    val noFunction = Nested[Option, List, Int => Int](None)
    val value = Nested[Option, List, Int](Some(List(1)))
    assertEquals(Applicative[OptionOfList].ap(noFunction)(value).value, None)
end ApplicativesSpec
