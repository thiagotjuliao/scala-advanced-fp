package advfp.ch01typeclassesandlaws

import advfp.core.unsolved

/** Exercise 03 — Write two functors, one of them for a type with two parameters.
  *
  * `Tree` is a binary tree with values at the leaves. Its functor transforms
  * every value and keeps the shape: the same branches, the same leaves, in the
  * same order.
  *
  * `Either[E, A]` has two type parameters and `Functor` wants one. Fix the
  * first: for every `E`, `[A] =>> Either[E, A]` is a type constructor of one
  * parameter, and its functor transforms a `Right` and leaves a `Left` alone.
  * The type lambda is the whole trick — Scala 3 writes it natively, where
  * Scala 2 needed a compiler plugin.
  *
  * Hint: a functor may not look at the values it transforms, only at the
  * structure around them.
  */
object Exercise03:

  enum Tree[+A]:
    case Leaf(value: A)
    case Branch(left: Tree[A], right: Tree[A])

  given treeFunctor: Functor[Tree] = unsolved

  given eitherFunctor: [E] => Functor[[A] =>> Either[E, A]] = unsolved
