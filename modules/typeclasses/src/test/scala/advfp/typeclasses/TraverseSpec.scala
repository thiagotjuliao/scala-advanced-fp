package advfp.typeclasses

import advfp.typeclasses.Eq.===
import munit.{FunSuite, Location}

/** The applicatives the laws are stated in. Test helpers, not part of the
  * hierarchy: the design keeps `Id`, `Const` and `Nested` out of
  * `typeclasses` (docs/kernel/m01-the-law-checker.md).
  */
object TraverseSpec:
  type Id[A] = A

  given idApplicative: Applicative[Id]:
    def pure[A](a: A): A = a
    def ap[A, B](ff: A => B)(fa: A): B = ff(fa)

  /** Ignores the value and accumulates `M`, left operand first. */
  final case class Const[M, A](value: M)

  given constApplicative: [M: Monoid] => Applicative[[A] =>> Const[M, A]]:
    def pure[A](a: A): Const[M, A] = Const(Monoid[M].empty)
    def ap[A, B](ff: Const[M, A => B])(fa: Const[M, A]): Const[M, B] =
      Const(Monoid[M].combine(ff.value, fa.value))

  final case class Nested[G[_], H[_], A](value: G[H[A]])

  given nestedApplicative
      : [G[_]: Applicative, H[_]: Applicative] => Applicative[[A] =>> Nested[G, H, A]]:
    def pure[A](a: A): Nested[G, H, A] = Nested(Applicative[G].pure(Applicative[H].pure(a)))
    def ap[A, B](ff: Nested[G, H, A => B])(fa: Nested[G, H, A]): Nested[G, H, B] =
      Nested(Applicative[G].map2(ff.value, fa.value)((hf, ha) => Applicative[H].ap(hf)(ha)))

class TraverseSpec extends FunSuite:
  import TraverseSpec.{*, given}

  private val toOption: List[(String, Int => Option[Int])] =
    List(
      "Some unless < 2" -> (a => if a < 2 then None else Some(a)),
      "Some(2a)" -> (a => Some(a * 2))
    )

  private val toList: List[(String, Int => List[Int])] =
    List("two" -> (a => List(a, a + 10)), "none" -> (_ => Nil), "one" -> (a => List(a)))

  /** Identity, composition, naturality from `Option` to `List`, and the
    * agreement of `foldLeft` with a traversal in `Const`, over every sample and
    * every function.
    */
  private def assertTraverseLaws[F[_]: Traverse](samples: List[F[Int]])(using
      Eq[F[Int]],
      Eq[Option[F[Int]]],
      Eq[List[F[Int]]],
      Location
  ): Unit =
    val F = Traverse[F]
    for fa <- samples; f <- List[Int => Int](_ + 1, _ * 2) do
      val viaId = F.traverse[Id, Int, Int](fa)(f)
      assert(viaId === F.map(fa)(f), s"identity fails at $fa: $viaId")
    for fa <- samples; (fName, f) <- toOption; (gName, g) <- toList do
      val left = F.traverse[[A] =>> Nested[Option, List, A], Int, Int](fa)(a =>
        Nested(Applicative[Option].map(f(a))(g))
      )
      val right = Applicative[Option].map(F.traverse(fa)(f))(fb => F.traverse(fb)(g))
      assert(
        left.value === right,
        s"composition fails at $fa with $fName then $gName: ${left.value} against $right"
      )
    for fa <- samples; (fName, f) <- toOption do
      val left = F.traverse(fa)(f).toList
      val right = F.traverse(fa)(a => f(a).toList)
      assert(left === right, s"naturality fails at $fa with $fName: $left against $right")
    samples.foreach: fa =>
      val folded = F.foldLeft(fa)(List.empty[Int])((seen, a) => seen :+ a)
      val viaConst =
        F.traverse[[A] =>> Const[List[Int], A], Int, Int](fa)(a => Const(List(a))).value
      assert(
        folded === viaConst,
        s"foldLeft disagrees with traverse in Const at $fa: $folded against $viaConst"
      )
  end assertTraverseLaws

  private type IntOr[A] = Either[Int, A]

  test("Option keeps the traverse laws"):
    assertTraverseLaws[Option](List(None, Some(1), Some(2)))

  test("Option: None traverses to pure(None), Some to the effect of f"):
    assertEquals(Traverse[Option].traverse(Option.empty[Int])(a => List(a, a)), List(None))
    assertEquals(Traverse[Option].traverse(Some(1))(a => List(a, a + 1)), List(Some(1), Some(2)))

  test("List keeps the traverse laws"):
    assertTraverseLaws[List](List(Nil, List(1), List(1, 2), List(3, 3), List(2, 1, 3)))

  test("List keeps its shape and order when every effect succeeds"):
    assertEquals(
      Traverse[List].traverse(List(1, 2, 3))(a => Option(a * 10)),
      Some(List(10, 20, 30))
    )

  test("List runs the effects left to right: on Either, the first Left wins"):
    val obtained = Traverse[List].traverse[IntOr, Int, Int](List(1, 2, 3))(a => Left(a))
    assertEquals(obtained, Left(1))

  test("List runs the effects left to right: on List, the first element is the outer loop"):
    val obtained = Traverse[List].traverse(List(1, 2))(a => List(a, a * 10))
    assertEquals(obtained, List(List(1, 2), List(1, 20), List(10, 2), List(10, 20)))

  test("List calls f on every element, even after a failure"):
    val seen = List.newBuilder[Int]
    val obtained = Traverse[List].traverse(List(1, 2, 3)): a =>
      seen += a
      if a == 1 then None else Some(a)
    assertEquals(obtained, None)
    assertEquals(seen.result(), List(1, 2, 3))

  // Long enough to overflow the default stack with one frame per element.
  private val long = 100_000

  test("List traverses a long list in constant stack"):
    val obtained = Traverse[List].traverse(List.range(0, long))(Option(_))
    assertEquals(obtained.map(_.size), Some(long))

  // ADR 0007 and its addendum: one instance per type, found from both roots.

  test("Option: Functor, Monad, Foldable and Traverse are the same instance"):
    val instances = List(Functor[Option], Monad[Option], Foldable[Option], Traverse[Option])
    assert(instances.forall(_ eq Functor.optionInstances), s"instances differ: $instances")

  test("List: Functor, Monad, Foldable and Traverse are the same instance"):
    val instances = List(Functor[List], Monad[List], Foldable[List], Traverse[List])
    assert(instances.forall(_ eq Functor.listInstances), s"instances differ: $instances")
end TraverseSpec
