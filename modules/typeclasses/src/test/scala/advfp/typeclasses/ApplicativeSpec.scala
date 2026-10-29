package advfp.typeclasses

import advfp.typeclasses.Eq.===
import munit.{FunSuite, Location}

class ApplicativeSpec extends FunSuite:

  private val functions: List[Int => Int] = List(_ + 1, _ * 2, x => -x)

  /** The four laws, over every combination of the samples. The function
    * samples hold the empty context and more than one function where the type
    * allows it: a `List` applicative is only exposed by two functions and two
    * values together.
    */
  private def assertApplicativeLaws[F[_]: Applicative](
      values: List[F[Int]],
      fs: List[F[Int => Int]]
  )(using Eq[F[Int]], Location): Unit =
    val F = Applicative[F]
    import F.{ap, pure}
    values.foreach: v =>
      val obtained = ap(pure((a: Int) => a))(v)
      assert(obtained === v, s"identity fails at $v: $obtained")
    for a <- List(0, 1, -1); f <- functions do
      val left = ap(pure(f))(pure(a))
      assert(left === pure(f(a)), s"homomorphism fails at $a: $left")
    for u <- fs; a <- List(0, 1, -1) do
      val left = ap(u)(pure(a))
      val right = ap(pure((g: Int => Int) => g(a)))(u)
      assert(left === right, s"interchange fails at $a: $left against $right")
    val compose = (f: Int => Int) => (g: Int => Int) => f compose g
    for u <- fs; v <- fs; w <- values do
      val left = ap(ap(ap(pure(compose))(u))(v))(w)
      val right = ap(u)(ap(v)(w))
      assert(left === right, s"composition fails at $w: $left against $right")

  test("Option keeps the applicative laws"):
    assertApplicativeLaws[Option](
      List(None, Some(0), Some(1)),
      None :: functions.map(Some(_))
    )

  test("Option is Some only when both sides are"):
    val F = Applicative[Option]
    assertEquals(F.ap(Some((_: Int) + 1))(Some(1)), Some(2))
    assertEquals(F.ap(Option.empty[Int => Int])(Some(1)), None)
    assertEquals(F.ap(Some((_: Int) + 1))(None), None)

  test("List keeps the applicative laws"):
    assertApplicativeLaws[List](
      List(Nil, List(0), List(1, 2), List(3, 3)),
      List(Nil, functions.take(1), functions.take(2), functions)
    )

  test("List applies every function to every value, the functions outer"):
    val F = Applicative[List]
    assertEquals(F.ap(List((_: Int) + 10, (_: Int) + 20))(List(1, 2)), List(11, 12, 21, 22))
    assertEquals(F.pure(1), List(1))

  private type IntOr[A] = Either[Int, A]

  test("Either keeps the applicative laws"):
    assertApplicativeLaws[IntOr](
      List(Left(0), Left(1), Right(0), Right(1)),
      Left(2) :: functions.map(Right(_))
    )

  test("Either: when both sides are Left, the function's Left wins"):
    val F = Applicative[IntOr]
    assertEquals(F.ap(Left(1): IntOr[Int => Int])(Left(2)), Left(1))
    assertEquals(F.ap(Right((_: Int) + 1): IntOr[Int => Int])(Left(2)), Left(2))
    assertEquals(F.ap(Right((_: Int) + 1): IntOr[Int => Int])(Right(1)), Right(2))

  // ADR 0007: one instance per type, found at every level of the hierarchy.
  // `eq` for a given without parameters; the class for one with parameters.

  test("Option: Functor and Applicative are the same instance"):
    assert(Functor[Option] eq Applicative[Option])

  test("List: Functor and Applicative are the same instance"):
    assert(Functor[List] eq Applicative[List])

  test("Either: Functor and Applicative are the same instance"):
    assertEquals(Functor[IntOr].getClass, Applicative[IntOr].getClass)
    assert(Functor[IntOr].isInstanceOf[Applicative[?]])
end ApplicativeSpec
