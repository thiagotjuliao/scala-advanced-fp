package advfp.ch01typeclassesandlaws

/** Exercise 04 — Find where a wrong instance hides. *Solved.*
  *
  * Reversing leaves a list unchanged exactly when the list is a palindrome. The
  * empty list and every single-element list are palindromes by accident of
  * size; `List(1, 2, 1)` is one by content. Those are the blind spots: a suite
  * whose samples are all palindromes passes `reversing` without a single
  * failure.
  *
  * The shortest list that is not a palindrome has two different elements, and
  * that is the counterexample. A suite needs at least one sample like it, and
  * the natural samples a person reaches for — `Nil`, `List(1)`, `List(0, 0)` —
  * are all blind spots. Samples are chosen against the bugs one fears, not for
  * how typical they look.
  *
  * `reversing` breaks composition too, on the same samples:
  * `xs.map(f).map(g)` reverses twice and comes back in order, while
  * `xs.map(f andThen g)` reverses once. That is no coincidence. For a `map`
  * that cannot inspect its elements, the identity law, if it held for every
  * list, would imply the composition law — a "free theorem" of the type, which
  * parametricity guarantees (Wadler, *Theorems for free!*). That is why the
  * identity law is usually the one worth testing first, with samples chosen to
  * break it.
  */
object Exercise04:

  /** A lawless functor: every element is mapped, and the order is lost. */
  val reversing: Functor[List] = new Functor[List]:
    extension [A](fa: List[A]) def map[B](f: A => B): List[B] = fa.reverse.map(f)

  def counterexample: List[Int] = List(1, 2)

  def blindSpots: List[List[Int]] = List(Nil, List(7), List(1, 2, 1), List(3, 3))
