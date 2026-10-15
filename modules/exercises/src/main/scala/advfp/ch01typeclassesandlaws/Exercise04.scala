package advfp.ch01typeclassesandlaws

import advfp.core.unsolved

/** Exercise 04 — Find where a wrong instance hides.
  *
  * `reversing` below is a `Functor[List]` that compiles, transforms every
  * element exactly once, and is wrong: it also reverses the list. It is not
  * yours to fix. The exercise is about the samples a law is checked on, because
  * a law checked on the wrong samples passes for this instance.
  *
  *   - `counterexample`: a list, as short as possible, on which `reversing`
  *     breaks the identity law.
  *   - `blindSpots`: at least three different lists on which `reversing`
  *     *satisfies* the identity law — a test suite using only these would pass
  *     it. At least one of them must have three or more elements, so that
  *     "too short to notice" is not the only answer.
  *
  * Hint: ask what reversing a list leaves unchanged.
  */
object Exercise04:

  /** A lawless functor: every element is mapped, and the order is lost. */
  val reversing: Functor[List] = new Functor[List]:
    extension [A](fa: List[A]) def map[B](f: A => B): List[B] = fa.reverse.map(f)

  def counterexample: List[Int] = unsolved

  def blindSpots: List[List[Int]] = unsolved
