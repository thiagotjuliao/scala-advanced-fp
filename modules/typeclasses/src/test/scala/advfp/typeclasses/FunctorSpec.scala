package advfp.typeclasses

import advfp.typeclasses.Eq.===
import munit.{FunSuite, Location}

class FunctorSpec extends FunSuite:

  /** Functions that move values differently, so composing two of them in the
    * wrong order or twice gives a different result.
    */
  private val functions: List[(String, Int => Int)] =
    List("+1" -> (_ + 1), "*2" -> (_ * 2), "negate" -> (x => -x), "const 7" -> (_ => 7))

  /** Both laws, over every sample and every pair of functions. Samples with
    * two different elements are what a reordering functor needs to show
    * itself (chapter 01, exercise 04).
    */
  private def assertFunctorLaws[F[_]: Functor](
      samples: List[F[Int]]
  )(using Eq[F[Int]], Location): Unit =
    val F = Functor[F]
    samples.foreach: fa =>
      val mapped = F.map(fa)(identity)
      assert(mapped === fa, s"identity fails at $fa: $mapped")
    for fa <- samples; (fName, f) <- functions; (gName, g) <- functions do
      val left = F.map(F.map(fa)(f))(g)
      val right = F.map(fa)(f andThen g)
      assert(
        left === right,
        s"composition fails at $fa with $fName then $gName: $left against $right"
      )

  test("Option keeps the functor laws"):
    assertFunctorLaws[Option](List(None, Some(0), Some(1)))

  test("Option maps the content of a Some, and leaves None alone"):
    assertEquals(Functor[Option].map(Some(1))(_ + 1), Some(2))
    assertEquals(Functor[Option].map(Option.empty[Int])(_ + 1), None)

  test("List keeps the functor laws"):
    assertFunctorLaws[List](List(Nil, List(0), List(1, 2), List(1, 2, 3), List(3, 3)))

  test("List maps every element, in order"):
    assertEquals(Functor[List].map(List(1, 2, 3))(_ * 10), List(10, 20, 30))

  private type IntOr[A] = Either[Int, A]

  test("Either keeps the functor laws"):
    assertFunctorLaws[IntOr](List(Left(0), Left(1), Right(0), Right(1)))

  test("Either maps a Right, and passes a Left through without calling f"):
    assertEquals(Functor[IntOr].map(Right(1))(_ + 1), Right(2))
    assertEquals(Functor[IntOr].map(Left(1))(_ => fail("f was called on a Left")), Left(1))
end FunctorSpec
