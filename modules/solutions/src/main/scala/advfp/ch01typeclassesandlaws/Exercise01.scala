package advfp.ch01typeclassesandlaws

/** Exercise 01 — Give `Int` two monoids, and keep them apart. *Solved.*
  *
  * Each instance is its operation and the identity of that operation: `0` for
  * addition, `1` for multiplication. The identities are the part worth
  * checking twice. `Monoid.instance(0)(_ * _)` compiles, passes associativity
  * on every sample, multiplies any two values correctly — and makes
  * `combineAll` of every list zero, because the fold starts from `empty`.
  * Associativity says nothing about the identity, which is why the identity
  * has laws of its own.
  *
  * The opaque types are what make two instances possible. Outside this object,
  * `Sum` and `Int` are unrelated types, so `Monoid[Sum]` and `Monoid[Prod]` do
  * not compete, and nothing answers `Monoid[Int]` at all: a caller who wants a
  * fold over integers has to say which one. Inside the object the alias is
  * transparent, so the instances are written with plain `+` and `*` and cost
  * nothing at run time — a `Sum` is an `Int`, never a box around one.
  *
  * `combineAll` starts the fold from `empty`, which is what makes the empty
  * list well defined: a semigroup alone could combine a non-empty list, but
  * would have nothing to return for `Nil`.
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
    as.foldLeft(Monoid[A].empty)(_ |+| _)
