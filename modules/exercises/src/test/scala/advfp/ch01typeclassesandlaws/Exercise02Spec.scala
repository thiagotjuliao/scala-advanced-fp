package advfp.ch01typeclassesandlaws

import advfp.core.CourseSuite

import Exercise02.given

class Exercise02Spec extends CourseSuite:

  // Concatenation does not commute, so an instance that swaps its arguments
  // shows up as a wrong string, not as a coincidentally right number.
  private given Semigroup[String] = _ + _

  private val maps: List[Map[String, String]] = List(
    Map.empty,
    Map("a" -> "x"),
    Map("a" -> "y", "b" -> "z"),
    Map("b" -> "w", "c" -> "v")
  )

  private val options: List[Option[String]] = List(None, Some(""), Some("a"), Some("bc"))

  test("maps keep every key, and combine the values of shared ones, left first") {
    val M = Monoid[Map[String, String]]
    assertEquals(
      M.combine(Map("a" -> "x", "b" -> "y"), Map("b" -> "z", "c" -> "w")),
      Map("a" -> "x", "b" -> "yz", "c" -> "w")
    )
  }

  test("the empty map is the identity, on both sides") {
    val M = Monoid[Map[String, String]]
    assertEquals(M.empty, Map.empty[String, String])
    forAllSamples(maps): m =>
      assertEquals(M.combine(M.empty, m), m)
      assertEquals(M.combine(m, M.empty), m)
  }

  test("combining maps is associative") {
    val M = Monoid[Map[String, String]]
    forAllSamples(for a <- maps; b <- maps; c <- maps yield (a, b, c)): (a, b, c) =>
      assertEquals(M.combine(M.combine(a, b), c), M.combine(a, M.combine(b, c)))
  }

  test("two Somes combine their contents, left first") {
    assertEquals(Monoid[Option[String]].combine(Some("a"), Some("b")), Some("ab"))
  }

  test("None is the identity, on both sides") {
    val M = Monoid[Option[String]]
    assertEquals(M.empty, None)
    forAllSamples(options): o =>
      assertEquals(M.combine(M.empty, o), o)
      assertEquals(M.combine(o, M.empty), o)
  }

  test("combining options is associative") {
    val M = Monoid[Option[String]]
    forAllSamples(for a <- options; b <- options; c <- options yield (a, b, c)): (a, b, c) =>
      assertEquals(M.combine(M.combine(a, b), c), M.combine(a, M.combine(b, c)))
  }

  test("a map of options combines all the way down") {
    val M = Monoid[Map[String, Option[String]]]
    assertEquals(
      M.combine(Map("k" -> Some("a"), "n" -> None), Map("k" -> Some("b"), "n" -> Some("c"))),
      Map("k" -> Some("ab"), "n" -> Some("c"))
    )
  }
end Exercise02Spec
