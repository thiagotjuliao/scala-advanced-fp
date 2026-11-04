package advfp.laws

import advfp.typeclasses.*

/** The unlawful instances the suites must catch, recreated against
  * `typeclasses` (docs/kernel/decisions/0002-fixtures-for-the-unlawful-instances.md).
  * None of them is a given: a test that wants one says so, in a scope of its
  * own, so the lawful instances stay the default everywhere else.
  */
object Fixtures:
  type OrInt[A] = Either[Int, A]

  /** Not associative: `(a - b) - c` and `a - (b - c)` differ by `2c`. */
  val subtraction: Semigroup[Int] = new Semigroup[Int]:
    override def combine(x: Int, y: Int): Int = x - y

  /** Chapter 01, pitfalls: associative, with an `empty` that absorbs. */
  val timesWithZero: Monoid[Int] = new Monoid[Int]:
    override def empty: Int = 0
    override def combine(x: Int, y: Int): Int = x * y

  /** Associative, and `0` is an identity on the left only: `a |+| 0` is `0`.
    * `timesWithZero` breaks both identities, so it cannot tell them apart.
    */
  val keepsTheRight: Monoid[Int] = new Monoid[Int]:
    override def empty: Int = 0
    override def combine(x: Int, y: Int): Int = y

  /** The mirror of `keepsTheRight`: `0` is an identity on the right only. */
  val keepsTheLeft: Monoid[Int] = new Monoid[Int]:
    override def empty: Int = 0
    override def combine(x: Int, y: Int): Int = x

  /** Chapter 01, exercise 04. */
  val reversing: Functor[List] = new Functor[List]:
    override def map[A, B](fa: List[A])(f: A => B): List[B] = fa.reverse.map(f)

  /** Chapter 02, exercise 01: a lawful applicative, but when both are `Left`
    * `flatMap` would keep the function's.
    */
  val keepsTheValuesLeft: Monad[OrInt] = new Monad[OrInt]:
    override def pure[A](a: A): OrInt[A] = Right(a)
    override def flatMap[A, B](fa: OrInt[A])(f: A => OrInt[B]): OrInt[B] = fa.flatMap(f)
    override def ap[A, B](ff: OrInt[A => B])(fa: OrInt[A]): OrInt[B] =
      fa match
        case Left(e) => Left(e)
        case Right(a) => ff.map(_(a))

  /** Chapter 02, exercise 01: a lawful applicative, but `flatMap` would run the
    * functions as the outer loop.
    */
  val swapsTheLoops: Monad[List] = new Monad[List]:
    override def pure[A](a: A): List[A] = List(a)
    override def flatMap[A, B](fa: List[A])(f: A => List[B]): List[B] = fa.flatMap(f)
    override def ap[A, B](ff: List[A => B])(fa: List[A]): List[B] =
      fa.flatMap(a => ff.map(_(a)))

  /** `LazyList`, since a lawful `pure` for it is infinite (chapter 02, exercise
    * 04). Not a case class's `==`, which would never finish on one: `prefix`
    * is what it is compared by, and its `toString` prints that prefix.
    */
  final class ZipList[A](val values: LazyList[A]):
    def prefix: List[A] = values.take(ZipList.bound).toList
    override def toString: String = prefix.mkString("ZipList(", ", ", ")")

  object ZipList:
    /** Above `Config`'s default `maxSize`, so every finite list generated is
      * compared whole (docs/kernel/decisions/0004-equality-as-a-typeclass.md).
      */
    val bound: Int = 100

    given Eq[ZipList[Int]] = (x, y) => x.prefix == y.prefix

  val zipLists: [X] => Gen[X] => Gen[ZipList[X]] =
    [X] => (g: Gen[X]) => Gen.listOf(g).map(xs => ZipList(LazyList.from(xs)))

  /** Chapter 02, exercise 04: `pure` is a single element, so zipping with it
    * cuts the other side down to one.
    */
  val onePure: Applicative[ZipList] = new Applicative[ZipList]:
    override def pure[A](a: A): ZipList[A] = ZipList(LazyList(a))
    override def ap[A, B](ff: ZipList[A => B])(fa: ZipList[A]): ZipList[B] =
      ZipList(ff.values.zip(fa.values).map((f, a) => f(a)))

  /** Chapter 02, exercise 04: the `flatMap` that agrees with zipping, and is
    * not associative: a list that ends early in the middle loses what follows.
    */
  val diagonal: Monad[ZipList] = new Monad[ZipList]:
    override def pure[A](a: A): ZipList[A] = ZipList(LazyList.continually(a))
    override def ap[A, B](ff: ZipList[A => B])(fa: ZipList[A]): ZipList[B] =
      ZipList(ff.values.zip(fa.values).map((f, a) => f(a)))
    override def flatMap[A, B](fa: ZipList[A])(f: A => ZipList[B]): ZipList[B] =
      ZipList(
        fa.values.zipWithIndex
          .map((a, i) => f(a).values.drop(i).headOption)
          .takeWhile(_.isDefined)
          .flatten
      )
end Fixtures
