package advfp.ch02applicativemonadandtraverse

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
    def empty[A]: ZipList[A] = ZipList.of()
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

  given zipListApplicative: Applicative[ZipList] =
    new Applicative[ZipList]:
      override def pure[A](a: A): ZipList[A] = ZipList(LazyList(a) #::: pure(a).values)

      extension [A](fa: ZipList[A])
        override def map[B](f: A => B): ZipList[B] =
          fa match
            case ZipList(values) => ZipList(values.map(f))

      extension [A, B](ff: ZipList[A => B])
        override def ap(fa: ZipList[A]): ZipList[B] =
          (ff, fa) match
            case (ZipList(fs), ZipList(as)) => ZipList(fs.lazyZip(as).map((f, a) => f(a)))

  def m: ZipList[Int] = ZipList.of(1, 2)

  def f: Int => ZipList[Int] =
    case 1 => ZipList.of(1) // z = 1
    case _ => ZipList.of(2, 3) // f(2): y0 = 2, y1 = 3

  def g: Int => ZipList[Int] =
    case 1 => ZipList.of(0) // g(z): não vazio
    case 2 => ZipList.of() // g(y0): vazio, derruba o lado direito
    case _ => ZipList.of(7, 8) // g(y1): dois elementos, a posição 1 existe
end Exercise04
