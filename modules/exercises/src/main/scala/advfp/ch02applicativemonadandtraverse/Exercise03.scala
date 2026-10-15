package advfp.ch02applicativemonadandtraverse

import advfp.core.unsolved

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

  given roseTraverse: Traverse[Rose] = unsolved

  given optionApplicative: Applicative[Option] = unsolved

  def parseAll(tree: Rose[String]): Option[Rose[Int]] = unsolved
