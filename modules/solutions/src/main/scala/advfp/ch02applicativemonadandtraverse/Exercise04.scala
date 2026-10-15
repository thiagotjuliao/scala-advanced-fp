package advfp.ch02applicativemonadandtraverse

/** Exercise 04 — An applicative that is not a monad. *Solved.*
  *
  * `ap` zips, so the result is as long as the shorter side. For
  * `pure(identity).ap(v)` to keep all of `v`, `pure` cannot be shorter than
  * any list — it has to be infinite, the value repeated forever. A one-element
  * `pure` passes the identity law on `Nil` and on every single-element list,
  * and truncates everything else to one element. Infinity costs nothing here
  * because `LazyList` computes only the elements a zip asks for.
  *
  * The counterexample works on the one thing `diagonal` does that a lawful
  * `flatMap` would not: it gives up at the first position it cannot fill. Take
  * `m = [0, 1]`, with `f(1) = [2, 3]`, `g(2) = []` and `g(3) = [9, 9]`.
  *
  *   - Left side, `diagonal(diagonal(m)(f))(g)`. The inner diagonal takes
  *     position 0 of `f(0)` and position 1 of `f(1)`: `[0, 3]`. Then position
  *     0 of `g(0)` and position 1 of `g(3)`: `[5, 9]`.
  *   - Right side, `diagonal(m)(a => diagonal(f(a))(g))`. At position 1 it needs
  *     position 1 of `diagonal(f(1))(g)` — but that diagonal starts at position
  *     0 with `g(2)`, which is empty, and stops there. The outer diagonal cannot
  *     fill position 1 and stops too: `[5]`.
  *
  * The left side only ever looks at `g(3)`; the right side also needs `g(2)`,
  * an element the left side threw away. Truncation depends on lengths, and
  * nesting the binds differently consults different lengths. A zip list whose
  * lists were all infinite would have no lengths to disagree about — infinite
  * streams *are* a monad, with exactly this diagonal — and it is finiteness
  * that breaks it.
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

  given zipListApplicative: Applicative[ZipList]:
    def pure[A](a: A): ZipList[A] = ZipList(LazyList.continually(a))

    extension [A, B](ff: ZipList[A => B])
      def ap(fa: ZipList[A]): ZipList[B] =
        ZipList(ff.values.zip(fa.values).map((f, a) => f(a)))

  def m: ZipList[Int] = ZipList.of(0, 1)

  def f: Int => ZipList[Int] =
    case 1 => ZipList.of(2, 3)
    case n => ZipList.of(n)

  def g: Int => ZipList[Int] =
    case 2 => ZipList.of()
    case 3 => ZipList.of(9, 9)
    case _ => ZipList.of(5)
end Exercise04
