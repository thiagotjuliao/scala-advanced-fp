package advfp.laws

import advfp.typeclasses.*

private[laws] object SemigroupLaws:
  def associativity[A: {Semigroup, Eq}](as: Gen[A]): (String, Prop[(A, A, A)]) =
    val gen = for
      a <- as
      b <- as
      c <- as
    yield (a, b, c)

    "associativity" ->
      Prop.forAll(gen): (a, b, c) =>
        ((a |+| b) |+| c) <-> (a |+| (b |+| c))

private[laws] object MonoidLaws:
  def leftIdentity[A: {Monoid, Eq}](as: Gen[A]): (String, Prop[A]) =
    "left identity" ->
      Prop.forAll(as): a =>
        (Monoid[A].empty |+| a) <-> a

  def rightIdentity[A: {Monoid, Eq}](as: Gen[A]): (String, Prop[A]) =
    "right identity" ->
      Prop.forAll(as): a =>
        (a |+| Monoid[A].empty) <-> a

private[laws] object FunctorLaws:
  def identity[F[_]: Functor](fas: Gen[F[Int]])(using Eq[F[Int]]): (String, Prop[F[Int]]) =
    "identity" ->
      Prop.forAll(fas): fa =>
        Functor[F].map(fa)(a => a) <-> fa

  def composition[F[_]: Functor](fas: Gen[F[Int]], fs: Gen[Fun[Int, Int]])(using
      Eq[F[Int]]
  ): (String, Prop[(F[Int], Fun[Int, Int], Fun[Int, Int])]) =
    val gen =
      for
        fa <- fas
        f <- fs
        g <- fs
      yield (fa, f, g)

    "composition" ->
      Prop.forAll(gen): (fa, f, g) =>
        Functor[F].map(Functor[F].map(fa)(f(_)))(g(_)) <->
          Functor[F].map(fa)(a => g(f(a)))
