package advfp.laws

import advfp.typeclasses.Monad

/** A generator: a pure function from a seed and a size to a value and its
  * shrink tree (docs/kernel/m01-the-law-checker.md#gen). The same seed and
  * size always give the same tree.
  *
  * A monad in distribution only: `flatMap` splits the seed, so the two sides
  * of a monad law run on different seeds and agree on what they can produce,
  * not on what they produce for a given seed. The functor laws do hold seed by
  * seed (docs/kernel/decisions/0010-gen-is-lawful-in-distribution.md).
  */
final case class Gen[A](run: (Seed, Size) => Tree[A]):

  def map[B](f: A => B): Gen[B] = Gen: (seed, size) =>
    Monad[Tree].map(run(seed, size))(f)

  /** Runs this generator and `f(a)` on the two halves of a split seed, so
    * they draw independent numbers, and with the same size. Shrinking `a`
    * regenerates `f(a)` from the same half, so what it generates changes only
    * through `a`; the candidates of `a` come first (see `Tree`'s monad).
    */
  def flatMap[B](f: A => Gen[B]): Gen[B] = Gen: (seed, size) =>
    val (s1, s2) = seed.split
    val fa = run(s1, size)
    Monad[Tree].flatMap(fa)(a => f(a).run(s2, size))

object Gen:
  /** The member `map` and `flatMap`, so the instance and the
    * for-comprehensions are one implementation
    * (docs/kernel/decisions/0009-gen-syntax-as-member-methods.md).
    */
  given Monad[Gen] = new Monad[Gen]:
    override def pure[A](a: A): Gen[A] =
      Gen((_, _) => Monad[Tree].pure(a))

    override def map[A, B](fa: Gen[A])(f: A => B): Gen[B] =
      fa.map(f)

    override def flatMap[A, B](fa: Gen[A])(f: A => Gen[B]): Gen[B] =
      fa.flatMap(f)

    override def ap[A, B](ff: Gen[A => B])(fa: Gen[A]): Gen[B] =
      ff.flatMap(f => map(fa)(f))
