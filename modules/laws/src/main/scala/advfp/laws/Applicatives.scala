package advfp.laws

import advfp.typeclasses.{Monoid, Applicative}

/** A traversal with no effect, which is what `map` is. An alias, so the result
  * is the value itself. Its instance is not found through the type: it needs
  * `import Id.given`, and `G = Id` is never inferred.
  */
private[laws] type Id[A] = A

private[laws] object Id:
  given Applicative[Id] = new Applicative[Id]:
    override def pure[A](a: A): Id[A] = a
    override def ap[A, B](ff: Id[A => B])(fa: Id[A]): Id[B] = ff(fa)

/** Gathers an `M` and ignores the values: `A` is phantom, so a traversal in it
  * is a fold. `ap` combines the function's `M` first, to keep effects left to
  * right. The applicative laws do not see that order, flipped or not.
  */
private[laws] final case class Const[M, A](value: M)

private[laws] object Const:
  given [M] => (Monoid[M]) => Applicative[[A] =>> Const[M, A]] =
    new Applicative[[A] =>> Const[M, A]]:
      override def pure[A](a: A): Const[M, A] = Const(Monoid[M].empty)
      override def ap[A, B](ff: Const[M, A => B])(fa: Const[M, A]): Const[M, B] =
        Const(ff.value |+| fa.value)

/** Two applicatives as one: `G`'s effects first, and `H`'s under each of them. */
private[laws] final case class Nested[G[_], H[_], A](value: G[H[A]])

private[laws] object Nested:
  given [G[_], H[_]] => (Applicative[G], Applicative[H]) => Applicative[[A] =>> Nested[G, H, A]] =
    new Applicative[[A] =>> Nested[G, H, A]]:
      override def pure[A](a: A): Nested[G, H, A] =
        Nested(Applicative[G].pure(Applicative[H].pure(a)))

      override def ap[A, B](ff: Nested[G, H, A => B])(fa: Nested[G, H, A]): Nested[G, H, B] =
        val ghf = ff.value
        val gha = fa.value
        val apH: H[A => B] => H[A] => H[B] = hf => ha => Applicative[H].ap(hf)(ha)
        val ghg = Applicative[G].map(ghf)(apH)
        Nested(Applicative[G].ap(ghg)(gha))
