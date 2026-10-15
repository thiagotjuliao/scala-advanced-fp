package advfp.ch01typeclassesandlaws

import advfp.core.unsolved

/** Exercise 02 — Build monoids out of semigroups.
  *
  * A semigroup on the values is enough to make a monoid of two containers:
  *
  *   - `Map[K, V]`: combining two maps keeps every key; where a key is in both,
  *     the two values are combined, the left map's value first. The identity is
  *     the empty map.
  *   - `Option[A]`: `None` is the identity, and two `Some`s combine their
  *     contents.
  *
  * Neither instance needs a `Monoid[V]` or a `Monoid[A]` — the container
  * supplies the identity the values lack. That is the pattern worth noticing:
  * the empty map is how you get a `Monoid[Map[String, NonEmptyList[...]]]`
  * when `NonEmptyList` itself can never be empty.
  *
  * Hint: the order of the arguments to `combine` is part of the answer; the
  * spec combines strings, and strings do not commute.
  */
object Exercise02:

  given mapMonoid: [K, V: Semigroup] => Monoid[Map[K, V]] = unsolved

  given optionMonoid: [A: Semigroup] => Monoid[Option[A]] = unsolved
