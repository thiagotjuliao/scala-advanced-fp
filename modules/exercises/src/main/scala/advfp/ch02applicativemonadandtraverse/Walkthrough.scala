package advfp.ch02applicativemonadandtraverse

/** Chapter 02 — Applicative, Monad and Traverse.
  *
  * Read this file top to bottom before opening the exercises. It is the whole
  * chapter in executable form: it compiles with the module, so none of it is
  * pseudo-code.
  *
  * Prose version: `docs/theory/ch02-applicative-monad-and-traverse.md`.
  */
object Walkthrough:

  // A context with nothing in it but the value: the smallest type that can
  // carry every instance in this file. The exercises are about types where the
  // context does something; here the mechanics are the point.
  final case class Box[A](content: A)

  given boxMonad: Monad[Box]:
    def pure[A](a: A): Box[A] = Box(a)
    extension [A, B](ff: Box[A => B]) def ap(fa: Box[A]): Box[B] = Box(ff.content(fa.content))
    extension [A](fa: Box[A]) def flatMap[B](f: A => Box[B]): Box[B] = f(fa.content)

  // --- 1. map is not enough ----------------------------------------------------
  //
  // `map` takes a function of one argument. Map a function of two arguments,
  // curried, over one box and the result is a *function in a box*, waiting for
  // its second argument. A functor has no way to feed it one.

  val add: (Int, Int) => Int = _ + _

  val halfDone: Box[Int => Int] = Box(2).map(add.curried)

  // `ap` is exactly the missing step: apply a function in the context to a
  // value in the context. With `pure` to lift the function in the first place,
  // a function of any arity is applied one `ap` per argument.
  val done: Box[Int] = halfDone.ap(Box(3)) // Box(5)

  val three: Box[Int] = boxMonad
    .pure((a: Int) => (b: Int) => (c: Int) => a + b + c)
    .ap(Box(1))
    .ap(Box(1))
    .ap(Box(1)) // Box(3)

  // --- 2. flatMap decides what comes next --------------------------------------
  //
  // With `ap`, the shape of the program — how many effects, in which order —
  // is fixed when the program is written; only the values flow through it.
  // With `flatMap`, the next effect is computed from the previous value: the
  // program can branch on what it has seen.

  def describe(n: Box[Int]): Box[String] =
    n.flatMap(k => if k > 0 then Box("positive") else Box("not positive"))

  // That is why every monad is an applicative — `ap` can be written with
  // `flatMap` and `map`, and exercise 02 writes it — and why the converse
  // fails: an applicative has nothing that could choose the second effect
  // after seeing the first value.

  // --- 3. Laws, as functions -----------------------------------------------------
  //
  // The applicative laws, and the one that ties a monad's `ap` to its `flatMap`.
  // A monad's `ap` is not free to do anything its `flatMap` cannot reproduce.

  def applicativeIdentity[F[_]: Applicative, A](v: F[A]): Boolean =
    Applicative[F].pure((a: A) => a).ap(v) == v

  def homomorphism[F[_]: Applicative, A, B](f: A => B, x: A): Boolean =
    val F = Applicative[F]
    F.pure(f).ap(F.pure(x)) == F.pure(f(x))

  def apAgreesWithFlatMap[F[_]: Monad, A, B](ff: F[A => B], fa: F[A]): Boolean =
    ff.ap(fa) == ff.flatMap(f => fa.map(f))

  // --- 4. One type, two applicatives ---------------------------------------------
  //
  // Lists can be combined two ways. Crossing every function with every value is
  // what `flatMap` produces, so it is the `ap` of the list monad. Zipping them
  // position by position is also a lawful `ap` — exercise 04 — but no `flatMap`
  // produces it, so the zipping applicative belongs to a type with no monad.

  val functions: List[Int => Int] = List(_ + 1, _ * 10)
  val numbers: List[Int] = List(1, 2)

  val crossed: List[Int] = functions.flatMap(f => numbers.map(f)) // List(2, 3, 10, 20)
  val zipped: List[Int] = functions.zip(numbers).map((f, n) => f(n)) // List(2, 20)

  // --- 5. Traverse: map with an effect -------------------------------------------
  //
  // `traverse` walks a structure, runs an effect at every element, and
  // rebuilds the structure *inside* the effect. It needs only `Applicative`:
  // the walk is decided by the structure, never by a value found on the way.
  //
  // The instance sits in an object of its own. `Traverse[Box]` brings a `map`
  // of its own, and next to `boxMonad`, at the same level, `.map` on a box
  // would find two extension methods and refuse both as ambiguous. Inside
  // `traversal` the inner instance is the nearer one, and wins.

  object traversal:
    given boxTraverse: Traverse[Box]:
      extension [A](fa: Box[A])
        def foldRight[B](z: B)(f: (A, B) => B): B = f(fa.content, z)

        def traverse[G[_]: Applicative, B](f: A => G[B]): G[Box[B]] =
          f(fa.content).map(Box(_))

    // With no effect at all — `Id` — traversing is mapping. That is how
    // `Traverse` gets its `map` for free.
    val traversedWithId: Box[Int] = Box(20).traverse[Id, Int](_ + 1) // Box(21)
    val mapped: Box[Int] = Box(20).map(_ + 1) // the same, through the traversal

    // The effects run in the order `foldRight` visits the elements; for a box
    // there is one. Exercise 03 has a tree, where the order is the whole point.
    val visited: List[Int] = Box(5).toList // List(5)
  end traversal
end Walkthrough
