package advfp.ch05ioasadescription

/** Exercise 04 — Make `IO` a `Monad`, check its laws on effects, and write a
  * stack-safe `traverse`. *Solved.*
  *
  * The instance delegates: `pure` is `IO.pure`, `flatMap` is `IO`'s own. The
  * laws then hold because the run loop treats `FlatMap(Pure(a), f)` exactly as
  * `f(a)` — push `f`, get `a`, pop and apply — and treats a left-nested
  * `FlatMap` and a right-nested one the same, frame by frame. What the spec
  * adds is the definition of "equal". Comparing only results, an instance whose
  * `flatMap` ran its source while building the program would pass all three
  * laws, since the value is the same either way. Comparing the effects, run
  * after run, it fails: its source's effect happens once, at construction, and
  * never again. That is the property the laws are really checking for an
  * `IO`, and why its samples write to a log.
  *
  * `traverse` folds the list from the right, so that each element's program is
  * a `flatMap` around the rest: `f(a).flatMap(b => rest.map(b :: _))`. The
  * first element's effect runs first, the results are consed onto the front of
  * a list that is already in order, and an error in `f(a)` skips the `flatMap`
  * and with it every element after. `List.foldRight` itself is stack-safe — it
  * reverses and folds from the left — and running the million nested
  * `flatMap`s is `IO`'s loop's job, a million frames on the heap. A fold from
  * the left works too, consing with `::` and reversing at the end.
  */
object Exercise04:

  given ioMonad: Monad[IO]:
    def pure[A](a: A): IO[A] = IO.pure(a)

    extension [A](fa: IO[A]) def flatMap[B](f: A => IO[B]): IO[B] = fa.flatMap(f)

  def traverse[A, B](as: List[A])(f: A => IO[B]): IO[List[B]] =
    as.foldRight(IO.pure(List.empty[B]))((a, rest) => f(a).flatMap(b => rest.map(b :: _)))
end Exercise04
