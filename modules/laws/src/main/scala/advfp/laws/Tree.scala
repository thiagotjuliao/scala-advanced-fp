package advfp.laws

import advfp.typeclasses.Monad

/** A value and its smaller candidates, each a tree of its own: the shrink
  * tree of integrated shrinking (docs/kernel/m01-the-law-checker.md#tree).
  *
  * `shrinks` is lazy per element, and a generator's tree is infinite in
  * practice: nothing here forces more of it than is asked for. The order of
  * `shrinks` is the order of preference, since the shrinker descends into the
  * first candidate that still fails.
  *
  * The structural `==` is a trap: on two `LazyList`s it forces both trees
  * whole, and never returns on an infinite one. Compare trees down to a
  * bounded depth and width instead.
  */
final case class Tree[A](value: A, shrinks: LazyList[Tree[A]])

object Tree:
  /** The tree that `shrink` grows from `a`: `a`'s candidates are `shrink(a)`,
    * theirs are `shrink` of each, and so on. `shrink` is called once for each
    * node built, when its parent's candidates are forced, so a `shrink` that
    * never runs out gives an infinite tree, not a loop.
    */
  def unfold[A](a: A)(shrink: A => LazyList[A]): Tree[A] =
    Tree(a, shrink(a).map(Tree.unfold(_)(shrink)))

  /** In this companion, not `Functor`'s: `Tree` is ours, and its companion is
    * in the implicit scope of every typeclass of `Tree`
    * (docs/kernel/decisions/0007-one-instance-per-type.md, "types of our
    * own").
    *
    * `flatMap` shrinks the outer value first: its candidates are `fa`'s
    * candidates, each bound again through `f`, and only then the candidates of
    * `f(fa.value)`. Rebinding is what lets a shrunk size regenerate what
    * depends on it. `ap` follows from it, so the function shrinks before the
    * argument, and `map`, through `pure`'s empty candidates, keeps the shape.
    */
  given Monad[Tree] = new Monad[Tree]:
    override def pure[A](a: A): Tree[A] = Tree(a, LazyList.empty)

    override def ap[A, B](ff: Tree[A => B])(fa: Tree[A]): Tree[B] =
      flatMap(ff)(f => map(fa)(f))

    override def flatMap[A, B](fa: Tree[A])(f: A => Tree[B]): Tree[B] =
      val Tree(value, shrinks) = f(fa.value)
      val _shrinks = fa.shrinks.map(flatMap(_)(f))
      Tree(value, _shrinks #::: shrinks)
end Tree
