package advfp.typeclasses

import munit.FunSuite

class FoldableSpec extends FunSuite:

  /** Collects the visited elements in visiting order: `f` does not commute, so
    * a fold that runs right to left, skips an element or visits one twice
    * gives a different list.
    */
  private def visits[F[_]: Foldable](fa: F[Int]): List[Int] =
    Foldable[F].foldLeft(fa)(List.empty[Int])((seen, a) => a :: seen).reverse

  test("Option: None folds to the start value"):
    assertEquals(Foldable[Option].foldLeft(Option.empty[Int])(7)(_ + _), 7)

  test("Option: Some folds its one element"):
    assertEquals(Foldable[Option].foldLeft(Some(3))(7)(_ - _), 4)
    assertEquals(visits(Option(1)), List(1))

  test("List: the empty list folds to the start value"):
    assertEquals(Foldable[List].foldLeft(List.empty[Int])(7)(_ + _), 7)

  test("List visits every element once, left to right"):
    assertEquals(visits(List(1, 2, 3)), List(1, 2, 3))
    assertEquals(visits(List(3, 3)), List(3, 3))

  test("List starts from z on the left: ((z - a1) - a2) - a3"):
    assertEquals(Foldable[List].foldLeft(List(1, 2, 3))(10)(_ - _), 4)

  // Long enough to overflow the default stack with one frame per element.
  private val long = 100_000

  test("List folds a long list in constant stack"):
    assertEquals(
      Foldable[List].foldLeft(List.range(0, long))(0L)(_ + _),
      long.toLong * (long - 1) / 2
    )
end FoldableSpec
