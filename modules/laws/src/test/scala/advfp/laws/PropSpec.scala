package advfp.laws

import advfp.typeclasses.Eq
import munit.FunSuite

class PropSpec extends FunSuite:
  import Prop.forAll

  private val ints = Gen.choose(0, 10)

  /** Coarser than `==`, as in LawSpec: a law that holds under it and not
    * under `==` shows which one `forAll` asked.
    */
  private val parity: Eq[Int] = (x, y) => Math.floorMod(x, 2) == Math.floorMod(y, 2)

  test("a law that holds gives Holds"):
    assertEquals(forAll(ints)(a => a + 0 <-> a).verdict(3), Verdict.Holds)

  test("a law that fails gives both sides, left on the left"):
    assertEquals(forAll(ints)(a => a + 2 <-> a + 3).verdict(0), Verdict.Fails("2", "3"))

  test("forAll compares with the Eq it is given, not with =="):
    val law = (a: Int) => a <-> a + 2
    assertEquals(forAll(ints)(law).verdict(1), Verdict.Fails("1", "3"))
    locally:
      given Eq[Int] = parity
      assertEquals(forAll(ints)(law).verdict(1), Verdict.Holds)

  test("the sides are kept as their toString, which is what the report prints"):
    val lists = forAll(ints)(a => List(a, 1) <-> List(a))
    assertEquals(lists.verdict(0), Verdict.Fails("List(0, 1)", "List(0)"))
    given Eq[Fun[Int, Int]] = _ == _
    val funs = forAll(ints)(a => Fun(Map(a -> 2), 5) <-> Fun(Map.empty, 5))
    assertEquals(funs.verdict(1), Verdict.Fails("{1 -> 2, _ -> 5}", "{_ -> 5}"))

  test("a Boolean property holds on true, and fails on false with empty sides"):
    val even = forAll(ints)(a => a % 2 == 0)
    assertEquals(even.verdict(2), Verdict.Holds)
    assertEquals(even.verdict(3), Verdict.Fails("", ""))

  test("a property whose result is neither a Law nor a Boolean does not compile"):
    val int = compileErrors("forAll(ints)(a => a + 1)")
    assert(int.contains("No given instance of type advfp.laws.Testable[Int]"), int)
    val option = compileErrors("forAll(ints)(a => Option(a))")
    assert(option.contains("No given instance of type advfp.laws.Testable[Option[Int]]"), option)

  test("a law over a type with no Eq does not compile"):
    final class NoEq(val n: Int)
    val _ = NoEq(0).n
    val noEq = compileErrors("forAll(ints)(a => NoEq(a) <-> NoEq(a))")
    assert(
      noEq.contains("no implicit values were found that match type advfp.typeclasses.Eq[B]"),
      noEq
    )

  test("forAll catches nothing: what the property throws reaches the runner"):
    intercept[ArithmeticException](forAll(ints)(a => 1 / a <-> 0).verdict(0))
    intercept[ArithmeticException](forAll(ints)(a => 1 / a == 0).verdict(0))

  test("forAll keeps the generator it is given"):
    assert(forAll(ints)(a => a <-> a).gen eq ints)
    assert(forAll(ints)(_ => true).gen eq ints)

  test("one suite holds properties over different inputs and different sides"):
    val suite: List[Prop[?]] = List(
      forAll(ints)(a => a <-> a),
      forAll(Gen.listOf(ints))(as => as.reverse.reverse <-> as),
      forAll(Gen.pairs(ints, ints))((a, b) => a + b == b + a)
    )
    assertEquals(suite.size, 3)
end PropSpec
