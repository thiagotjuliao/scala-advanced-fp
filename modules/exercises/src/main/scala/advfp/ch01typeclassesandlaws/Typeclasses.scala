package advfp.ch01typeclassesandlaws

/** The chapter's typeclasses: given, not exercises.
  *
  * The same file sits in `exercises` and in `solutions`, so a stub and its
  * answer program against identical definitions. Later chapters declare their
  * own hierarchy in their own package rather than importing this one: a chapter
  * that depended on another's stubs could not be studied on its own.
  */

/** An associative binary operation.
  *
  * Law — associativity: `(x |+| y) |+| z == x |+| (y |+| z)`. Nothing in the
  * type says so, and an instance that breaks it compiles just as well.
  */
trait Semigroup[A]:
  def combine(x: A, y: A): A

  extension (x: A) def |+|(y: A): A = combine(x, y)

object Semigroup:
  def apply[A](using semigroup: Semigroup[A]): Semigroup[A] = semigroup

/** A semigroup with an identity element.
  *
  * Laws — associativity, inherited, and identity on both sides:
  * `empty |+| x == x` and `x |+| empty == x`.
  */
trait Monoid[A] extends Semigroup[A]:
  def empty: A

object Monoid:
  def apply[A](using monoid: Monoid[A]): Monoid[A] = monoid

  /** A monoid from its two parts. The laws remain the caller's to keep. */
  def instance[A](identity: A)(operation: (A, A) => A): Monoid[A] =
    new Monoid[A]:
      def empty: A = identity
      def combine(x: A, y: A): A = operation(x, y)

/** A type constructor whose contents can be transformed in place.
  *
  * Laws — identity: `fa.map(a => a) == fa`; composition:
  * `fa.map(f).map(g) == fa.map(f andThen g)`.
  */
trait Functor[F[_]]:
  extension [A](fa: F[A]) def map[B](f: A => B): F[B]

object Functor:
  def apply[F[_]](using functor: Functor[F]): Functor[F] = functor
