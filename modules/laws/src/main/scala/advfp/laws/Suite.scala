package advfp.laws

import advfp.typeclasses.*

final case class Suite(name: String, parents: List[Suite], laws: List[(String, Prop[?])]):
  def all: List[(String, Prop[?])] =
    (
      parents.flatMap(_.all) ++
        laws.map((lawName, prop) => (s"$name: $lawName", prop))
    ).distinctBy(_._1)

object Suite:
  private val ints = Gen.choose(-5, 5)
  private val fs = Gen.fun(ints, ints)

  def semigroup[A: {Semigroup, Eq}](as: Gen[A]): Suite =
    Suite(
      name = "Semigroup",
      parents = List.empty,
      laws = SemigroupLaws.associativity(as) :: Nil
    )

  def monoid[A: {Monoid, Eq}](as: Gen[A]): Suite =
    Suite(
      name = "Monoid",
      parents = semigroup(as) :: Nil,
      laws = MonoidLaws.leftIdentity(as) :: MonoidLaws.rightIdentity(as) :: Nil
    )

  def functor[F[_]: Functor](lift: [X] => Gen[X] => Gen[F[X]])(using Eq[F[Int]]): Suite =
    val fas = lift[Int](ints)

    Suite(
      name = "Functor",
      parents = List.empty,
      laws = FunctorLaws.identity(fas) ::
        FunctorLaws.composition(fas, fs) :: Nil
    )

  def applicative[F[_]: Applicative](lift: [X] => Gen[X] => Gen[F[X]])(using Eq[F[Int]]): Suite =
    ???

  def monad[F[_]: Monad](lift: [X] => Gen[X] => Gen[F[X]])(using Eq[F[Int]]): Suite = ???
end Suite
