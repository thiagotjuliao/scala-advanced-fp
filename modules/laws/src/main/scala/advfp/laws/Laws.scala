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
