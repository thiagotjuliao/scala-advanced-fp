package advfp.ch02applicativemonadandtraverse

import advfp.core.unsolved

/** Exercise 04 — An applicative that is not a monad.
  *
  * `ZipList` combines lists position by position:
  * `ZipList.of(f, g).ap(ZipList.of(x, y, z))` is `ZipList.of(f(x), g(y))`, as
  * long as the shorter of the two.
  *
  *   - `zipListApplicative`: `pure` and `ap`. Choose `pure` so that the
  *     identity law holds for lists of *every* length — `pure(identity).ap(v)`
  *     must give back `v` whole.
  *   - `m`, `f` and `g`: a counterexample showing that `diagonal`, the
  *     natural `flatMap` for this `ap`, is not associative:
  *     `diagonal(diagonal(m)(f))(g)` and `diagonal(m)(a => diagonal(f(a))(g))`
  *     must differ. `m` and everything `f` and `g` return must be finite.
  *
  * With no lawful `flatMap`, there is no `Monad[ZipList]`, and the zipping
  * `ap` is an applicative that no monad can produce.
  *
  * Hint: `diagonal` stops at the first position it cannot fill. Work out, by
  * hand, which positions each side of the law has to fill, and make `f` return
  * lists of different lengths.
  */
object Exercise04:

  /** A list whose applicative zips instead of crossing. Equality is the
    * structural equality of `LazyList`: it walks both lists to the first
    * difference or the end of the shorter one, so two equal infinite lists
    * never finish comparing.
    */
  final case class ZipList[A](values: LazyList[A])

  object ZipList:
    def of[A](values: A*): ZipList[A] = ZipList(LazyList.from(values))

  /** The natural `flatMap` for a zipping `ap`, and one that agrees with it:
    * the `i`-th result is the `i`-th element of `f` applied to the `i`-th
    * element, and the list ends at the first `i` where that element does not
    * exist. Given, not yours.
    */
  def diagonal[A, B](fa: ZipList[A])(f: A => ZipList[B]): ZipList[B] =
    ZipList(
      fa.values.zipWithIndex
        .map((a, i) => f(a).values.drop(i).headOption)
        .takeWhile(_.isDefined)
        .flatten
    )

  given zipListApplicative: Applicative[ZipList] = unsolved

  def m: ZipList[Int] = unsolved

  def f: Int => ZipList[Int] = unsolved

  def g: Int => ZipList[Int] = unsolved
end Exercise04
