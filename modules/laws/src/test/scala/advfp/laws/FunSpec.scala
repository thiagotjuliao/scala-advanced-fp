package advfp.laws

import munit.FunSuite

class FunSpec extends FunSuite:
  import PrimitivesSpec.{five, minimise, seeds, values}

  private val many = GenSpec.seeds.take(200)
  private val funs = Gen.fun(Gen.choose(0, 10), Gen.choose(100, 200))

  test("a Fun is its table where the table has the input, and its default elsewhere"):
    val f = Fun(Map(1 -> 10, 2 -> 20), 0)
    assertEquals(List(f(1), f(2), f(3), f(-1)), List(10, 20, 0, 0))
    assertEquals(Fun(Map.empty[Int, Int], 7)(1), 7)

  test("a Fun prints as its table and its default"):
    assertEquals(Fun(Map(1 -> 2, 2 -> 3), 5).toString, "{1 -> 2, 2 -> 3, _ -> 5}")
    assertEquals(Fun(Map.empty[Int, Int], 5).toString, "{_ -> 5}")

  test("fun's table has at most size entries, and none at size 0"):
    for n <- 0 to 30; seed <- seeds do
      val entries = funs.run(seed, Size.of(n)).value.table.size
      assert(entries <= n, s"$entries entries at size $n")
    seeds.foreach: seed =>
      assertEquals(funs.run(seed, Size.of(0)).value.table, Map.empty[Int, Int])

  test("fun's keys, values and default come from their generators, two levels down"):
    seeds.foreach: seed =>
      values(funs.run(seed, five), depth = 2).foreach: f =>
        val clue = s"$f at $seed"
        assert(f.table.keys.forall(k => 0 <= k && k <= 10), clue)
        assert((f.default :: f.table.values.toList).forall(v => 100 <= v && v <= 200), clue)

  test("fun shrinks to the constant function first, with the same default"):
    assert(many.exists(funs.run(_, five).value.table.nonEmpty))
    many.foreach: seed =>
      val tree = funs.run(seed, five)
      val f = tree.value
      if f.table.nonEmpty then
        assertEquals(
          tree.shrinks.headOption.map(_.value),
          Some(Fun(Map.empty[Int, Int], f.default)),
          s"$f"
        )

  test("fun shrinks its default last"):
    many.foreach: seed =>
      val tree = funs.run(seed, five)
      val moved = tree.shrinks.map(_.value.default != tree.value.default).toList
      assertEquals(moved, moved.sorted, s"from ${tree.value}")
      if tree.value.default != 100 then assert(moved.contains(true), s"from ${tree.value}")

  test("fun shrinks to the edge of a failure at one point"):
    val tree = seeds.map(funs.run(_, five)).find(_.value(0) >= 150).get
    val f = minimise(tree, _(0) >= 150)
    assertEquals(f(0), 150, f.toString)
    assert(f.table.size <= 1, f.toString)

  test("fun: the same seed and size give the same tree"):
    seeds.foreach: seed =>
      assertEquals(values(funs.run(seed, five), depth = 2), values(funs.run(seed, five), depth = 2))
end FunSpec
