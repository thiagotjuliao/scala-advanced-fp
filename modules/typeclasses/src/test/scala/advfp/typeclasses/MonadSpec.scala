package advfp.typeclasses

import advfp.typeclasses.Eq.===
import munit.{FunSuite, Location}

class MonadSpec extends FunSuite:

  /** The three monad laws and the agreement of `ap` with `flatMap`, over every
    * combination of the samples. The continuations include the empty context
    * and, where the type allows it, more than one result, so a `flatMap` that
    * drops or reorders results shows itself.
    */
  private def assertMonadLaws[F[_]: Monad](
      values: List[F[Int]],
      continuations: List[Int => F[Int]],
      fs: List[F[Int => Int]]
  )(using Eq[F[Int]], Location): Unit =
    val F = Monad[F]
    import F.{flatMap, pure}
    for a <- List(0, 1, -1); f <- continuations do
      val left = flatMap(pure(a))(f)
      assert(left === f(a), s"left identity fails at $a: $left against ${f(a)}")
    values.foreach: m =>
      val obtained = flatMap(m)(pure)
      assert(obtained === m, s"right identity fails at $m: $obtained")
    for m <- values; f <- continuations; g <- continuations do
      val left = flatMap(flatMap(m)(f))(g)
      val right = flatMap(m)(a => flatMap(f(a))(g))
      assert(left === right, s"associativity fails at $m: $left against $right")
    for ff <- fs; fa <- values do
      val viaAp = F.ap(ff)(fa)
      val viaFlatMap = flatMap(ff)(f => F.map(fa)(f))
      assert(viaAp === viaFlatMap, s"ap disagrees with flatMap at $fa: $viaAp against $viaFlatMap")

  private val functions: List[Int => Int] = List(_ + 1, _ * 2)

  test("Option keeps the monad laws"):
    assertMonadLaws[Option](
      List(None, Some(0), Some(1)),
      List(a => if a > 0 then Some(a + 1) else None, a => Some(a * 2), _ => None),
      None :: functions.map(Some(_))
    )

  test("Option: flatMap continues from a Some, and stops at None"):
    assertEquals(Monad[Option].flatMap(Some(1))(a => Some(a + 1)), Some(2))
    assertEquals(Monad[Option].flatMap(Option.empty[Int])(a => Some(a + 1)), None)

  test("List keeps the monad laws"):
    assertMonadLaws[List](
      List(Nil, List(0), List(1, 2), List(3, 3)),
      List(
        a => List(a, a + 1),
        _ => Nil,
        a => List(a * 2),
        a => if a % 2 == 0 then List(a) else Nil
      ),
      List(Nil, functions.take(1), functions)
    )

  test("List: flatMap concatenates the results, in order"):
    assertEquals(Monad[List].flatMap(List(1, 2))(a => List(a, a * 10)), List(1, 10, 2, 20))

  private type IntOr[A] = Either[Int, A]

  test("Either keeps the monad laws"):
    assertMonadLaws[IntOr](
      List(Left(0), Left(1), Right(0), Right(1)),
      List(a => if a > 0 then Right(a + 1) else Left(a), a => Right(a * 2), _ => Left(7)),
      List(Left(2), Left(3)) ++ functions.map(Right(_))
    )

  test("Either: flatMap continues from a Right, and stops at the first Left"):
    val F = Monad[IntOr]
    assertEquals(F.flatMap(Right(1): IntOr[Int])(a => Right(a + 1)), Right(2))
    assertEquals(F.flatMap(Left(1): IntOr[Int])(_ => Left(2)), Left(1))

  test("map is derived from flatMap and pure, and agrees with the instance's ap"):
    assertEquals(Monad[List].map(List(1, 2))(_ + 1), List(2, 3))
    assertEquals(Monad[Option].map(Some(1))(_ + 1), Some(2))

  // ADR 0007: one instance per type, found at every level of the hierarchy.
  // `eq` for a given without parameters; the class for one with parameters.

  test("Option: Functor, Applicative and Monad are the same instance"):
    assert(Functor[Option] eq Monad[Option])
    assert(Applicative[Option] eq Monad[Option])

  test("List: Functor, Applicative and Monad are the same instance"):
    assert(Functor[List] eq Monad[List])
    assert(Applicative[List] eq Monad[List])

  test("Either: Functor, Applicative and Monad are the same instance"):
    assertEquals(Functor[IntOr].getClass, Monad[IntOr].getClass)
    assertEquals(Applicative[IntOr].getClass, Monad[IntOr].getClass)
    assert(Functor[IntOr].isInstanceOf[Monad[?]])
end MonadSpec
