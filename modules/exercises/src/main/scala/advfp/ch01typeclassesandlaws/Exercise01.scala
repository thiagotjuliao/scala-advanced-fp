package advfp.ch01typeclassesandlaws

/** Exercise 01 — Give `Int` two monoids, and keep them apart.
  *
  * Integers form a monoid under addition and another under multiplication.
  * Both are lawful, and they disagree on everything: the identity, the result
  * of every combination. A single `given Monoid[Int]` would have to pick one,
  * and every caller would silently get that one.
  *
  * `Sum` and `Prod` are opaque types over `Int`: the same representation, two
  * distinct types, and therefore room for one instance each. Write both
  * instances in the companions, where implicit search finds them without an
  * import, and then `combineAll`, which folds a list with whatever monoid its
  * element type has.
  *
  * Hint: `combineAll` of an empty list still has an answer.
  */
object Exercise01:

  opaque type Sum = Int

  object Sum:
    def apply(n: Int): Sum = n
    extension (s: Sum) def value: Int = s

    /** Addition. */
    given Monoid[Sum] = Monoid.instance(0)(_ + _)

  opaque type Prod = Int

  object Prod:
    def apply(n: Int): Prod = n
    extension (p: Prod) def value: Int = p

    /** Multiplication. */
    given Monoid[Prod] = Monoid.instance(1)(_ * _)

  /** Combines every element, left to right. */
  def combineAll[A: Monoid](as: List[A]): A =
    def loop(as: List[A], acc: A): A =
      as match
        case Nil => acc
        case h :: t => loop(t, acc |+| h)
    loop(as, Monoid[A].empty)
end Exercise01
