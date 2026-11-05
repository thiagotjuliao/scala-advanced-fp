package advfp.laws

/** A generated function, as a finite table plus a default, so a failing law
  * can print the function it failed on and the shrinker can make it smaller
  * (docs/kernel/decisions/0005-functions-as-finite-tables.md).
  *
  * Equality is the case class's, on the table and the default, not on the
  * function: `Fun(Map(1 -> 0), 0)` and `Fun(Map.empty, 0)` are the same
  * function and unequal values.
  */
final case class Fun[A, B](table: Map[A, B], default: B):
  def apply(a: A): B = table.getOrElse(a, default)

  val curried: A => B = a => apply(a)

  /** The entries in the table's order, then the default:
    * `{1 -> 2, 2 -> 3, _ -> 5}`.
    */
  override def toString: String =
    val parts = table.toSeq.map: (k, v) =>
      k.toString + " -> " + v
    (parts ++ Seq("_ -> " + default)).mkString("{", ", ", "}")
