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

private[laws] object ApplicativeLaws:
  def identity[F[_]: Applicative](fas: Gen[F[Int]])(using
      Eq[F[Int]]
  ): (String, Prop[F[Int]]) =
    val ff: F[Int => Int] = Applicative[F].pure(a => a)

    "identity" ->
      Prop.forAll(fas): fa =>
        Applicative[F].ap(ff)(fa) <-> fa

  def composition[F[_]: Applicative](fas: Gen[F[Int]], fs: Gen[Fun[Int, Int]])(using
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
        val ff = Applicative[F].pure(f.curried)
        val fg = Applicative[F].pure(g.curried)
        val ffog = Applicative[F].pure(f.curried andThen g.curried)
        fg.ap(ff.ap(fa)) <-> ffog.ap(fa)

  def homomorphism[F[_]: Applicative](as: Gen[Int], fs: Gen[Fun[Int, Int]])(using
      Eq[F[Int]]
  ): (String, Prop[(Int, Fun[Int, Int])]) =
    val gen =
      for
        a <- as
        f <- fs
      yield (a, f)

    "homomorphism" ->
      Prop.forAll(gen): (a, f) =>
        val ff = Applicative[F].pure(f.curried)
        val fa = Applicative[F].pure(a)
        ff.ap(fa) <-> Applicative[F].pure(f(a))

  def interchange[F[_]: Applicative](as: Gen[Int], fs: Gen[Fun[Int, Int]])(using
      Eq[F[Int]]
  ): (String, Prop[(Int, Fun[Int, Int])]) =
    val gen =
      for
        a <- as
        f <- fs
      yield (a, f)

    "interchange" ->
      Prop.forAll(gen): (a, f) =>
        val fa = Applicative[F].pure(a)
        val ffa: F[(Int => Int) => Int] = Applicative[F].pure(f => f(a))
        val ff = Applicative[F].pure(f.curried)
        ff.ap(fa) <-> ffa.ap(ff)

  def mapAndPure[F[_]: Applicative](fas: Gen[F[Int]], fs: Gen[Fun[Int, Int]])(using
      Eq[F[Int]]
  ): (String, Prop[(F[Int], Fun[Int, Int])]) =
    val gen =
      for
        fa <- fas
        f <- fs
      yield (fa, f)

    "map agrees with pure(f).ap" ->
      Prop.forAll(gen): (fa, f) =>
        Applicative[F].map(fa)(f.curried) <->
          Applicative[F].pure(f.curried).ap(fa)
end ApplicativeLaws
