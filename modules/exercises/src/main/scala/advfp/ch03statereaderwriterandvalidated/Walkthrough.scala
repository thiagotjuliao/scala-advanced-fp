package advfp.ch03statereaderwriterandvalidated

/** Chapter 03 — State, Reader, Writer and Validated.
  *
  * Read this file top to bottom before opening the exercises. It is the whole
  * chapter in executable form: it compiles with the module, so none of it is
  * pseudo-code.
  *
  * Prose version: `docs/theory/ch03-state-reader-writer-and-validated.md`.
  */
object Walkthrough:

  // --- 1. Threading a state by hand ----------------------------------------------
  //
  // A pure random number generator: a seed in, the next seed and a number out.
  // The constants are the ones of `java.util.Random`, cut down to what a die
  // needs.

  def roll(seed: Long): (Long, Int) =
    val next = (seed * 0x5deece66dL + 0xbL) & ((1L << 48) - 1)
    (next, ((next >>> 17) % 6).toInt.abs + 1)

  // Three rolls. Every line takes the state out and passes it on, and the
  // compiler cannot tell `s1` from `s2`: both are `Long`.
  def threeRolls(seed: Long): (Int, Int, Int) =
    val (s1, a) = roll(seed)
    val (s2, b) = roll(s1)
    val (_, c) = roll(s2)
    (a, b, c)

  // The same program with one character wrong. It compiles, it is pure, and
  // the third die always repeats the second.
  def threeRollsOops(seed: Long): (Int, Int, Int) =
    val (s1, a) = roll(seed)
    val (_, b) = roll(s1)
    val (_, c) = roll(s1)
    (a, b, c)

  // `roll` already has the shape `S => (S, A)`. Exercise 01 gives the shape a
  // name, `State`, and writes the passing-on once, in `flatMap`. After that
  // there is no `s1` to get wrong: a program says *what* to do with the
  // state — `get`, `set`, `modify` — and never *which* state.

  // --- 2. Threading an environment by hand ---------------------------------------
  //
  // A configuration that half the program needs, passed to all of it.

  final case class Settings(currency: String, vatRate: BigDecimal)

  def withVat(net: BigDecimal, settings: Settings): BigDecimal = net * (1 + settings.vatRate)

  def label(net: BigDecimal, settings: Settings): String =
    s"${withVat(net, settings)} ${settings.currency}"

  // `label` does not use `vatRate`; it only has `settings` to hand it on. A
  // function of the environment is `Settings => A`, and composing two of them
  // means giving both the same argument. That is all a Reader's `flatMap`
  // does, and exercise 02 writes it.
  val labelOf: BigDecimal => Settings => String = net => settings => label(net, settings)

  // --- 3. Threading a log by hand ------------------------------------------------
  //
  // Each step returns its value and what it wants recorded, and every caller
  // concatenates — in the right order, every time.

  def half(n: Int): (List[String], Int) = (List(s"halved $n"), n / 2)

  def quarter(n: Int): (List[String], Int) =
    val (log1, h) = half(n)
    val (log2, q) = half(h)
    (log1 ++ log2, q)

  // Writer's `flatMap` is that `log1 ++ log2`, written once. The `++` becomes
  // the `|+|` of a `Monoid`, so the log can be any type that combines: a
  // list, a count, a set of warnings.

  // --- 4. Either stops at the first error ----------------------------------------
  //
  // The standard library's `Either` is a monad, and its `for` reads well. It
  // also reports one problem at a time.

  def nonEmpty(field: String, raw: String): Either[String, String] =
    if raw.trim.isEmpty then Left(s"$field is empty") else Right(raw.trim)

  def number(field: String, raw: String): Either[String, Int] =
    raw.toIntOption.toRight(s"$field is not a number")

  def pairE(name: String, age: String): Either[String, (String, Int)] =
    for
      n <- nonEmpty("name", name)
      a <- number("age", age)
    yield (n, a)

  val oneError: Either[String, (String, Int)] = pairE("", "x") // Left(name is empty)

  // To report both, look at both results before deciding — every combination,
  // by hand. With two fields there are four cases; with five, thirty-two.
  def pairAll(name: String, age: String): Either[List[String], (String, Int)] =
    (nonEmpty("name", name), number("age", age)) match
      case (Right(n), Right(a)) => Right((n, a))
      case (Left(e1), Left(e2)) => Left(List(e1, e2))
      case (Left(e), _) => Left(List(e))
      case (_, Left(e)) => Left(List(e))

  val bothErrors: Either[List[String], (String, Int)] = pairAll("", "x")

  // That match is an `ap` for one pair of types. Exercise 03 writes it once,
  // for every pair, as `Validated`'s applicative — and then a form of any
  // number of fields is one `pure` and one `ap` per field.

  // --- 5. Why the accumulating ap has no monad -----------------------------------
  //
  // `pairE` could not report the age because `flatMap` hands the age check a
  // *name*: with no name there is nothing to hand it, and so nothing to run.
  // Any `flatMap` on a failure is stuck the same way. An `ap` derived from it
  // inherits the blindness, and `pairAll` shows the other `ap`, which looks at
  // both sides. A type can have one of the two, and exercise 04 finds the
  // sample where they differ.
end Walkthrough
