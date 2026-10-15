package advfp.ch01typeclassesandlaws

/** Chapter 01 — Typeclasses and laws.
  *
  * Read this file top to bottom before opening the exercises. It is the whole
  * chapter in executable form: it compiles with the module, so none of it is
  * pseudo-code.
  *
  * Prose version: `docs/theory/ch01-typeclasses-and-laws.md`.
  */
object Walkthrough:

  // --- 1. An instance is operations ------------------------------------------
  //
  // `Semigroup[String]` is a value: a `combine` for strings. `Semigroup` has a
  // single abstract method, so a lambda is enough to write one.

  given Semigroup[String] = _ + _

  // The extension method comes with the instance: wherever the instance is
  // visible, so is `|+|`.
  val greeting: String = "hello, " |+| "world"

  // --- 2. ... and laws -------------------------------------------------------
  //
  // A law is a property the type cannot state. Written as a function, it can
  // at least be checked on samples. The chapter's specs do exactly this.

  def associative[A: Semigroup](x: A, y: A, z: A): Boolean =
    ((x |+| y) |+| z) == (x |+| (y |+| z))

  def leftIdentity[A: Monoid](x: A): Boolean = (Monoid[A].empty |+| x) == x
  def rightIdentity[A: Monoid](x: A): Boolean = (x |+| Monoid[A].empty) == x

  // This instance compiles and is wrong: subtraction is not associative, and
  // nothing but a law check will say so.
  val subtraction: Semigroup[Int] = _ - _
  val subtractionIsAssociative: Boolean =
    associative(1, 2, 3)(using subtraction) // (1 - 2) - 3 = -4, 1 - (2 - 3) = 2

  // --- 3. One instance per type ------------------------------------------------
  //
  // Implicit search finds *the* instance for a type, so a type has one. `Int`
  // has many lawful monoids — addition, multiplication, max, min — and a single
  // `given Monoid[Int]` would choose one for every caller. An opaque type is a
  // new type with the old representation: one opaque type per monoid.

  object numbers:
    opaque type Max = Int

    object Max:
      def apply(n: Int): Max = n
      extension (m: Max) def value: Int = m

      // In the companion of `Max`, the instance is in `Max`'s implicit scope:
      // found wherever a `Max` is, with no import.
      given Monoid[Max] = Monoid.instance(Int.MinValue)(math.max)

  import numbers.Max

  // Out here `Max` is opaque, so `|+|` is found through its companion. Inside
  // `numbers` the alias is transparent: there `Max(3)` is just an `Int`, the
  // companion is not consulted, and the same line does not compile. That is
  // why the type lives in an object of its own.
  val largest: Int = (Max(3) |+| Max(9) |+| Max(4)).value // 9

  // --- 4. Functors map the contents, never the structure -----------------------

  final case class Box[A](content: A)

  given Functor[Box]:
    extension [A](fa: Box[A]) def map[B](f: A => B): Box[B] = Box(f(fa.content))

  val boxed: Box[Int] = Box("four").map(_.length) // Box(4)

  // The two functor laws, as functions. "Equal" here means `==`, which is
  // structural for case classes and enums: exactly the equality the laws need.
  def functorIdentity[F[_]: Functor, A](fa: F[A]): Boolean = fa.map(identity) == fa

  def functorComposition[F[_]: Functor, A, B, C](fa: F[A], f: A => B, g: B => C): Boolean =
    fa.map(f).map(g) == fa.map(f andThen g)

  // --- 5. Where implicit search looks ------------------------------------------
  //
  // For `Monoid[Max]` it looks in `Max`'s companion: found. For an instance on
  // `Map[K, V]` that lives in some `object Instances`, it looks in the
  // companions of `Map`, `K`, `V` and `Monoid` — and `Instances` is none of
  // them. Such an instance has to be imported (`import Instances.given`), or it
  // does not exist as far as the call site is concerned. A plain
  // `import Instances.*` does not bring givens in: `given` imports are
  // separate on purpose, so that instances never arrive by accident.
end Walkthrough
