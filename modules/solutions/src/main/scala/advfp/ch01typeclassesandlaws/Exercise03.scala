package advfp.ch01typeclassesandlaws

/** Exercise 03 — Write two functors, one of them for a type with two
  * parameters. *Solved.*
  *
  * The tree instance recurses through the structure and applies `f` only at the
  * leaves. Every `Branch` is rebuilt as a `Branch` with its children in the
  * same positions, which is what the identity law demands: `map(identity)` must
  * give back a tree equal to the original, so it may neither reorder nor drop
  * nor duplicate anything. Swapping `left` and `right` in the rebuilt branch
  * would still compile, and break both laws: `map(identity)` mirrors the tree,
  * and `t.map(f).map(g)` mirrors it twice — back to the original order — while
  * `t.map(f andThen g)` mirrors it once.
  *
  * The either instance needs a functor for every `E`, so it is a given with a
  * type parameter, and the functor's type is the lambda `[A] =>> Either[E, A]`.
  * Its `map` cannot do anything with a `Left`: it has no `E => E` to apply, and
  * no way to invent a `B`. Parametricity leaves exactly one choice, and the
  * types make it for you.
  */
object Exercise03:

  enum Tree[+A]:
    case Leaf(value: A)
    case Branch(left: Tree[A], right: Tree[A])

  given treeFunctor: Functor[Tree]:
    extension [A](fa: Tree[A])
      def map[B](f: A => B): Tree[B] = fa match
        case Tree.Leaf(value) => Tree.Leaf(f(value))
        case Tree.Branch(left, right) => Tree.Branch(left.map(f), right.map(f))

  given eitherFunctor: [E] => Functor[[A] =>> Either[E, A]]:
    extension [A](fa: Either[E, A])
      def map[B](f: A => B): Either[E, B] = fa match
        case Right(a) => Right(f(a))
        case Left(error) => Left(error)
