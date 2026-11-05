package advfp.laws

import advfp.typeclasses.*

/** A typeclass's laws and those of the typeclasses it extends, so checking
  * `Monad[Option]` checks all three (docs/kernel/m01-the-law-checker.md#the-law-suites).
  * A suite is plain data: `all` lists its laws and `Runner.check` runs each one.
  */
final case class Suite(name: String, parents: List[Suite], laws: List[(String, Prop[?])]):
  /** The parents' laws first, then this suite's, each named `"Monad: associativity"`.
    * A law reached through two parents appears once. The name decides what is
    * the same law: two different laws with one name in a suite keep only the
    * first, with no error.
    */
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

  /** `lift` builds a generator of `F[X]` from one of `X`, for every `X`: the
    * laws need `F[Fun[Int, Int]]` as well as `F[Int]`, so a `Gen[F[Int]]` is not
    * enough. `[X] => (g: Gen[X]) => Gen.listOf(g)` is `List`'s. A `lift` that
    * never produces an effect, always `pure`, leaves the laws checking little.
    */
  def functor[F[_]: Functor](lift: [X] => Gen[X] => Gen[F[X]])(using Eq[F[Int]]): Suite =
    val fas = lift[Int](ints)

    Suite(
      name = "Functor",
      parents = List.empty,
      laws = FunctorLaws.identity(fas) ::
        FunctorLaws.composition(fas, fs) :: Nil
    )

  def applicative[F[_]: Applicative](lift: [X] => Gen[X] => Gen[F[X]])(using Eq[F[Int]]): Suite =
    val fas = lift[Int](ints)
    val ffs = lift[Fun[Int, Int]](fs)

    Suite(
      name = "Applicative",
      parents = functor(lift) :: Nil,
      laws = ApplicativeLaws.identity(fas) ::
        ApplicativeLaws.homomorphism(ints, fs) ::
        ApplicativeLaws.interchange(ints, ffs) ::
        ApplicativeLaws.composition(fas, ffs) ::
        ApplicativeLaws.mapAndPure(fas, fs) :: Nil
    )

  def monad[F[_]: Monad](lift: [X] => Gen[X] => Gen[F[X]])(using Eq[F[Int]]): Suite =
    val fas = lift[Int](ints)
    val ffs = lift[Fun[Int, Int]](fs)
    val ffs_ = Gen.fun(ints, fas)

    Suite(
      name = "Monad",
      parents = applicative(lift) :: Nil,
      laws = MonadLaws.leftIdentity(ints, ffs_) ::
        MonadLaws.rightIdentity(fas) ::
        MonadLaws.associativity(fas, ffs_) ::
        MonadLaws.apAndFlatMap(fas, ffs) :: Nil
    )
end Suite
