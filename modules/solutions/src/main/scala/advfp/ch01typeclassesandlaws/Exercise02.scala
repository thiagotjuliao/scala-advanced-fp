package advfp.ch01typeclassesandlaws

/** Exercise 02 — Build monoids out of semigroups. *Solved.*
  *
  * Both instances supply an identity the values do not have, and then defer to
  * the values' semigroup wherever two values meet.
  *
  * For maps, the fold walks the right map into the left one. A key already on
  * the left gets `left |+| right`; a new key is copied over. The argument order
  * is the whole difference between `"yz"` and `"zy"`: `combine(x, y)` must mean
  * "x, then y" all the way down, or associativity of the values no longer
  * carries over to the maps.
  *
  * For options, `None` contributes nothing, so it is returned from neither side
  * unless both are empty. That one match is the identity law written out.
  *
  * The last spec is the payoff: `Map[String, Option[String]]` has a monoid
  * without anyone writing one for it, because `Option[String]` has a monoid,
  * hence a semigroup, and the map instance only asks for that. Instances
  * compose through their requirements.
  */
object Exercise02:

  given mapMonoid: [K, V: Semigroup] => Monoid[Map[K, V]] =
    Monoid.instance(Map.empty[K, V]): (left, right) =>
      right.foldLeft(left):
        case (merged, (key, value)) =>
          merged.updated(key, merged.get(key).fold(value)(_ |+| value))

  given optionMonoid: [A: Semigroup] => Monoid[Option[A]] =
    Monoid.instance(Option.empty[A]):
      case (Some(x), Some(y)) => Some(x |+| y)
      case (x, None) => x
      case (None, y) => y
