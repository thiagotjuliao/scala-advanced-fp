package advfp.ch05ioasadescription

/** Exercise 04 — Make `IO` a `Monad`, check its laws on effects, and write a
  * stack-safe `traverse`.
  *
  * The `IO` is the one in `IO.scala`, given.
  *
  *   - `ioMonad`: `pure` and `flatMap`. The spec checks the monad laws, and
  *     checks them the only way two descriptions can be compared: by running
  *     both and comparing the result *and* the effects, in order. A `flatMap`
  *     that runs anything while the program is being built fails it.
  *   - `traverse(as)(f)`: one program that runs `f` on every element, in order,
  *     and collects the results. It stops at the first error — the elements
  *     after it are not run — and it survives a million elements.
  *
  * Hint: the instance is two lines, and both delegate. For `traverse`, think
  * about which end of the list the first effect must come from, and which end
  * of a `List` is cheap to add to.
  */
object Exercise04:

  given ioMonad: Monad[IO] =
    new Monad[IO]:
      override def pure[A](a: A): IO[A] = IO.pure(a)

      extension [A](fa: IO[A])
        override def flatMap[B](f: A => IO[B]): IO[B] =
          fa.flatMap(f)

  def traverse[A, B](as: List[A])(f: A => IO[B]): IO[List[B]] =
    as match
      case Nil => IO.pure(List())
      case h :: t =>
        f(h).flatMap(b => traverse(t)(f).map(b :: _))

end Exercise04
