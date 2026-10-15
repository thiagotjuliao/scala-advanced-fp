package advfp.ch02applicativemonadandtraverse

/** Exercise 03 — Traverse a rose tree, and let `Option` short-circuit it.
  * *Solved.*
  *
  * `traverse` has two jobs, and they are the same job at two sizes. A node
  * combines the effect of its own value with the effect of its children; the
  * children combine their effects one by one. In both, the tool is `map2`
  * spelled out: map a curried constructor over the first effect, `ap` the
  * second. At the node the constructor is `Rose(_, _)`; along the children it
  * is `::`. The `foldRight` over the children starts from `pure(Nil)` — the
  * effect that does nothing, holding the empty list — so a leaf runs exactly
  * one effect, its own.
  *
  * The order of the effects is the order of the `ap`s: the node's value first,
  * then each child's subtree, left to right. That is pre-order, and it agrees
  * with `foldRight`, as the law asks. A version that folded the children with
  * `foldLeft` and prepended would build the right tree with the children's
  * effects reversed — the result value cannot show it, the spec's log can.
  *
  * `parseAll` is one line because `Option` is the applicative that fails: as
  * soon as one `ap` meets a `None`, the result is `None`, and every `ap` after
  * it keeps it so. The short-circuit is in the *result*. `traverse` is strict
  * and still calls `toIntOption` on every label — an applicative cannot stop a
  * walk whose shape was fixed before it began.
  */
object Exercise03:

  final case class Rose[A](value: A, children: List[Rose[A]])

  object Rose:
    def leaf[A](value: A): Rose[A] = Rose(value, Nil)

  given roseTraverse: Traverse[Rose]:
    extension [A](fa: Rose[A])
      def foldRight[B](z: B)(f: (A, B) => B): B =
        f(fa.value, fa.children.foldRight(z)((child, acc) => child.foldRight(acc)(f)))

      def traverse[G[_]: Applicative, B](f: A => G[B]): G[Rose[B]] =
        val G = Applicative[G]
        val children: G[List[Rose[B]]] =
          fa.children.foldRight(G.pure(List.empty[Rose[B]])): (child, rest) =>
            child.traverse(f).map(tree => (trees: List[Rose[B]]) => tree :: trees).ap(rest)
        f(fa.value).map(b => (trees: List[Rose[B]]) => Rose(b, trees)).ap(children)

  given optionApplicative: Applicative[Option]:
    def pure[A](a: A): Option[A] = Some(a)

    extension [A, B](ff: Option[A => B])
      def ap(fa: Option[A]): Option[B] = (ff, fa) match
        case (Some(f), Some(a)) => Some(f(a))
        case _ => None

  def parseAll(tree: Rose[String]): Option[Rose[Int]] = tree.traverse(_.toIntOption)
end Exercise03
