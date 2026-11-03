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
  /** Always `a`, with nothing to shrink to. */
  def const[A](a: A): Gen[A] =
    Monad[Gen].pure(a)

  /** A uniform `Int` from `lo` to `hi`, both included; the size is ignored.
    *
    * It shrinks toward the value of the range nearest to 0: 0 when the range
    * holds it, else `lo` or `hi`. Never out of the range, so `choose(10, 20)`
    * never shrinks to 0. The candidates halve the distance to that target, the
    * target first, so the shrinker finds the edge of a failure by bisection.
    *
    * @throws IllegalArgumentException
    *   if `lo > hi`, at the call, not when the generator runs
    */
  def choose(lo: Int, hi: Int): Gen[Int] =
    require(lo <= hi, s"chosen interval must be valid: $lo and $hi")

    Gen: (seed, _) =>
      val count = hi.toLong - lo.toLong + 1
      val value = (lo + Math.floorMod(seed.next._1, count)).toInt
      val target =
        if lo <= 0 && 0 <= hi then 0L
        else if hi < 0 then hi.toLong
        else lo.toLong

      Tree.unfold(value): n =>
        LazyList
          .iterate(n.toLong - target)(_ / 2)
          .takeWhile(_ != 0)
          .map(d => (n - d).toInt)

  /** `true` or `false`, each half the time; the size is ignored. `true`
    * shrinks to `false`, and `false` does not shrink.
    */
  def bool: Gen[Boolean] =
    choose(0, 1).map(_ == 1)

  /** One of the alternatives, each as likely as the others however many
    * values it can produce: `oneOf(const(0), choose(1, 3))` gives 0 half the
    * time.
    *
    * It shrinks to an earlier alternative first, then within the one chosen,
    * so the simplest alternative goes first. The index and the alternative
    * draw from the two halves of a split seed, so a switch of alternative
    * regenerates it from the same half (see `flatMap`).
    */
  def oneOf[A](first: Gen[A], rest: Gen[A]*): Gen[A] =
    val gens = first +: rest
    val genIndex = choose(0, gens.length - 1)
    genIndex.flatMap(gens(_))

  /** One of the alternatives, each drawn in proportion to its weight:
    * `frequency((1, a), (3, b))` gives `a` a quarter of the time.
    *
    * The weights choose the alternative, and do not shrink: the index shrinks
    * as `oneOf`'s does, to an earlier alternative first, the first one first,
    * then within the one chosen. So the simplest alternative goes first,
    * whatever its weight.
    *
    * @throws IllegalArgumentException
    *   if a weight is 0 or less, or the weights add up to more than
    *   `Int.MaxValue`, at the call, not when the generator runs
    */
  def frequency[A](first: (Int, Gen[A]), rest: (Int, Gen[A])*): Gen[A] =
    val pairs = first +: rest
    val ws = pairs.map(_._1)

    require(ws.forall(_ > 0), "weights must be positive")
    require(ws.map(_.toLong).sum <= Int.MaxValue, s"weights cannot sum over ${Int.MaxValue}")

    val sums = ws.scanLeft(0)(_ + _).tail

    Gen: (seed, size) =>
      val (s0, s1) = seed.split
      val n = choose(1, ws.sum).run(s0, size).value
      val i = sums.zipWithIndex.dropWhile(_._1 < n).head._2

      val indices = Tree.unfold(i): j =>
        LazyList.iterate(j)(_ / 2).takeWhile(_ != 0).map(j - _)
      Monad[Tree].flatMap(indices)(j => pairs(j)._2.run(s1, size))

  /** `None` a quarter of the time, since a law about `Option` says little on
    * `None`. `Some` shrinks to `None` first, then within the value.
    */
  def option[A](ga: Gen[A]): Gen[Option[A]] =
    frequency((1, const(None)), (3, ga.map(Some(_))))

  /** `Left` half the time. `Right` shrinks to a `Left` first, then within
    * the value; a `Left` stays a `Left`.
    */
  def either[E, A](ge: Gen[E], ga: Gen[A]): Gen[Either[E, A]] =
    frequency((1, ge.map(Left(_))), (1, ga.map(Right(_))))

  /** A list whose length is uniform from 0 to the size, each element from a
    * seed of its own.
    *
    * It shrinks by removing elements first, in blocks whose length halves:
    * the whole list, so the empty list is the first candidate, then each
    * block of `n / 2`, `n / 4`, ... down to single elements, from left to
    * right. Then each element shrinks through its own candidates, from left
    * to right. The elements are kept with their trees, so what remains after
    * a removal still shrinks; a `flatMap` on the length would regenerate them
    * instead (docs/kernel/m01-the-law-checker.md#gen).
    */
  def listOf[A](ga: Gen[A]): Gen[List[A]] =
    Gen: (seed, size) =>
      val (s0, s1) = seed.split
      val n = choose(0, size.toInt).run(s0, size).value
      val seeds = List.iterate(s1, n)(_.split._2).map(_.split._1)
      val trees = seeds.map(s => ga.run(s, size))

      val res = Tree.unfold(trees): ts =>
        val removals = LazyList
          .iterate(ts.length)(_ / 2)
          .takeWhile(_ >= 1)
          .flatMap: k =>
            LazyList
              .range(0, ts.length, k)
              .map: start =>
                ts.take(start) ++ ts.drop(start + k)

        val shrunk = LazyList
          .range(0, ts.length)
          .flatMap: i =>
            ts(i).shrinks.map: t =>
              ts.updated(i, t)

        removals #::: shrunk
      Monad[Tree].map(res)(_.map(_.value))

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
end Gen
