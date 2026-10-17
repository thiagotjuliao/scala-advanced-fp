package advfp.ch02applicativemonadandtraverse

/** Exercise 03 — Traverse a rose tree, and let `Option` short-circuit it.
  *
  * A `Rose` is a value with any number of children, each one a `Rose` itself.
  *
  *   - `roseTraverse`: `foldRight` and `traverse` for `Rose`. Both visit the
  *     tree in pre-order — a node before its children, the children left to
  *     right — and `traverse` runs the effects in that same order. `map` and
  *     `sequence` come with `Traverse`, derived from your `traverse`.
  *   - `optionApplicative`: `pure` and `ap` for `Option`. Exercise 01 wrote
  *     this already; it is asked for again so this exercise stands on its own.
  *   - `parseAll`: every label of the tree read as an `Int`, or `None` if any
  *     one of them is not a number. Write it with `traverse`, not by hand.
  *
  * `traverse` gets an `Applicative[G]` and nothing more — no `flatMap` — so the
  * children have to be combined with `ap` alone.
  *
  * Hint: a list of children is walked with `foldRight`, starting from
  * `pure(Nil)`; at each child, `ap` is how `G[Rose[B]]` and `G[List[Rose[B]]]`
  * become one `G[List[Rose[B]]]`.
  */
object Exercise03:

  final case class Rose[A](value: A, children: List[Rose[A]])

  object Rose:
    def leaf[A](value: A): Rose[A] = Rose(value, Nil)

  given roseTraverse: Traverse[Rose] =
    new Traverse[Rose]:
      extension [A](fa: Rose[A])
        override def traverse[G[_]: Applicative, B](f: A => G[B]): G[Rose[B]] =
          fa match
            case Rose(a, children) =>
              val app = Applicative[G]
              val gb = f(a)

              val g: Rose[B] => List[Rose[B]] => List[Rose[B]] = r => ls => r :: ls
              val childrenG = children.foldRight[G[List[Rose[B]]]](app.pure(Nil)): (r, acc) =>
                val child = r.traverse(f)
                app.ap(child.map(g))(acc)

              val r: B => List[Rose[B]] => Rose[B] = b => ls => Rose(b, ls)
              app.ap(gb.map(r))(childrenG)

      extension [A](fa: Rose[A])
        override def map[B](f: A => B): Rose[B] =
          fa.traverse[Id, B](f)

      extension [A](fa: Rose[A])
        override def foldRight[B](z: B)(f: (A, B) => B): B =
          fa match
            case Rose(a, children) => f(a, children.foldRight(z)((r, acc) => r.foldRight(acc)(f)))

      extension [A](fa: Rose[A])
        override def toList: List[A] =
          fa.foldRight(List.empty[A])(_ :: _)

      extension [G[_]: Applicative, A](fga: Rose[G[A]])
        override def sequence: G[Rose[A]] =
          fga.traverse(identity)

  given optionApplicative: Applicative[Option] =
    new Applicative[Option]:
      override def pure[A](a: A): Option[A] = Some(a)

      extension [A](fa: Option[A])
        override def map[B](f: A => B): Option[B] =
          fa.map(f)

      extension [A, B](ff: Option[A => B])
        override def ap(fa: Option[A]): Option[B] =
          (ff, fa) match
            case (Some(f), Some(a)) => Some(f(a))
            case _ => None

  def parseAll(tree: Rose[String]): Option[Rose[Int]] =
    tree.traverse(_.toIntOption)
end Exercise03
