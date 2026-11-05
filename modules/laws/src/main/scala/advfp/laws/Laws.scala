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

    "identity" ->
      Prop.forAll(fas): fa =>
        val ff: F[Int => Int] = Applicative[F].pure(a => a)
        Applicative[F].ap(ff)(fa) <-> fa

  def composition[F[_]: Applicative](fas: Gen[F[Int]], ffs: Gen[F[Fun[Int, Int]]])(using
      Eq[F[Int]]
  ): (String, Prop[(F[Int], F[Fun[Int, Int]], F[Fun[Int, Int]])]) =
    val gen =
      for
        fa <- fas
        ff <- ffs
        fg <- ffs
      yield (fa, ff, fg)

    "composition" ->
      Prop.forAll(gen): (fa, ff, fg) =>
        val ff_ = Applicative[F].map(ff)(_.apply)
        val fg_ = Applicative[F].map(fg)(_.apply)
        val compose: F[(Int => Int) => (Int => Int) => (Int => Int)] =
          Applicative[F].pure(g => f => g compose f)
        fg_.ap(ff_.ap(fa)) <-> compose.ap(fg_).ap(ff_).ap(fa)

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
        val ff = Applicative[F].pure(f(_))
        val fa = Applicative[F].pure(a)
        ff.ap(fa) <-> Applicative[F].pure(f(a))

  def interchange[F[_]: Applicative](as: Gen[Int], ffs: Gen[F[Fun[Int, Int]]])(using
      Eq[F[Int]]
  ): (String, Prop[(Int, F[Fun[Int, Int]])]) =
    val gen =
      for
        a <- as
        ff <- ffs
      yield (a, ff)

    "interchange" ->
      Prop.forAll(gen): (a, ff) =>
        val fa = Applicative[F].pure(a)
        val ff_ = Applicative[F].map(ff)(_.apply)
        val ffa: F[(Int => Int) => Int] = Applicative[F].pure(f => f(a))
        ff_.ap(fa) <-> ffa.ap(ff_)

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
        Applicative[F].map(fa)(f(_)) <-> Applicative[F].pure(f(_)).ap(fa)
end ApplicativeLaws

private[laws] object MonadLaws:
  def leftIdentity[F[_]: Monad](
      as: Gen[Int],
      fs: Gen[Fun[Int, F[Int]]]
  )(using Eq[F[Int]]): (String, Prop[(Int, Fun[Int, F[Int]])]) =
    val gen =
      for
        a <- as
        f <- fs
      yield (a, f)

    "left identity" ->
      Prop.forAll(gen): (a, f) =>
        val fa = Monad[F].pure(a)
        Monad[F].flatMap(fa)(f(_)) <-> f(a)

  def rightIdentity[F[_]: Monad](fas: Gen[F[Int]])(using Eq[F[Int]]): (String, Prop[F[Int]]) =
    "right identity" ->
      Prop.forAll(fas): fa =>
        fa <-> Monad[F].flatMap(fa)(Monad[F].pure)

  def associativity[F[_]: Monad](fas: Gen[F[Int]], fs: Gen[Fun[Int, F[Int]]])(using
      Eq[F[Int]]
  ): (String, Prop[(F[Int], Fun[Int, F[Int]], Fun[Int, F[Int]])]) =
    val gen =
      for
        fa <- fas
        f <- fs
        g <- fs
      yield (fa, f, g)

    "associativity" ->
      Prop.forAll(gen): (fa, f, g) =>
        val compose: Int => F[Int] = a => Monad[F].flatMap(f(a))(g(_))
        Monad[F].flatMap(Monad[F].flatMap(fa)(f(_)))(g(_)) <-> Monad[F].flatMap(fa)(compose)

  def apAndFlatMap[F[_]: Monad](fas: Gen[F[Int]], ffs: Gen[F[Fun[Int, Int]]])(using
      Eq[F[Int]]
  ): (String, Prop[(F[Int], F[Fun[Int, Int]])]) =
    val gen =
      for
        fa <- fas
        ff <- ffs
      yield (fa, ff)

    "ap agrees with flatMap" ->
      Prop.forAll(gen): (fa, ff) =>
        val ff_ : F[Int => Int] = Monad[F].map(ff)(_.apply)
        Monad[F].ap(ff_)(fa) <-> Monad[F].flatMap(ff_)(f => Monad[F].map(fa)(f(_)))
end MonadLaws
