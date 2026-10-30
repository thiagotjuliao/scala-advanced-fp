package advfp.typeclasses

import advfp.typeclasses.Eq.===
import advfp.typeclasses.Semigroup.given
import munit.{FunSuite, Location}

class MonoidSpec extends FunSuite:

  /** Both identity laws, on every sample. Associativity is `SemigroupSpec`'s:
    * these instances are the ones it checks.
    */
  private def assertIdentity[A: {Monoid, Eq}](samples: List[A])(using Location): Unit =
    val empty = Monoid[A].empty
    samples.foreach: a =>
      assert((empty |+| a) === a, s"not a left identity at $a: ${empty |+| a}")
      assert((a |+| empty) === a, s"not a right identity at $a: ${a |+| empty}")

  test("Int: 0 is the identity"):
    assertEquals(Monoid[Int].empty, 0)
    assertIdentity(List(0, 1, -1, Int.MaxValue, Int.MinValue))

  test("Option: None is the identity"):
    assertEquals(Monoid[Option[Int]].empty, None)
    assertIdentity[Option[Int]](List(None, Some(0), Some(1), Some(Int.MinValue)))

  test("Option is a monoid over a type that is only a semigroup"):
    val options: List[Option[Either[Int, Int]]] = List(None, Some(Left(1)), Some(Right(0)))
    assertEquals(Monoid[Option[Either[Int, Int]]].empty, None)
    assertIdentity(options)

  test("List: the empty list is the identity"):
    assertEquals(Monoid[List[Int]].empty, Nil)
    assertIdentity(List(Nil, List(0), List(1, 2)))

  test("Either is not a monoid"):
    assert(
      compileErrors("Monoid[Either[Int, Int]]").contains("No given instance"),
      "Either has a Monoid; ADR 0007 keeps it a Semigroup"
    )

  // ADR 0007: one instance per type, found at every level of the hierarchy.
  // A given without parameters is one object, so `eq` checks it. A given with
  // parameters builds a new object per search, so the check is its class.

  test("Int: Semigroup and Monoid are the same instance"):
    assert(Semigroup[Int] eq Monoid[Int])

  test("Option: Semigroup and Monoid are the same instance"):
    assertEquals(Semigroup[Option[Int]].getClass, Monoid[Option[Int]].getClass)
    assert(Semigroup[Option[Int]].isInstanceOf[Monoid[?]])

  test("List: Semigroup and Monoid are the same instance"):
    assertEquals(Semigroup[List[Int]].getClass, Monoid[List[Int]].getClass)
    assert(Semigroup[List[Int]].isInstanceOf[Monoid[?]])
end MonoidSpec
