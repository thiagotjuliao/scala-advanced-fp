package advfp.laws

import munit.{Assertions, Location}

/** Where a `Result` becomes a test's outcome: `laws` returns values, and tests
  * assert (docs/kernel/m01-the-law-checker.md#boundaries).
  */
trait PropAssertions:
  self: Assertions =>

  /** Fails the test with the report as its message. An exception the property
    * threw at the shrunk input is kept as the failure's cause, so its stack
    * trace reaches the test's output.
    */
  def assertHolds[A](prop: Prop[A], config: Config = Config())(using Location): Unit =
    Runner.check(prop, config) match
      case Result.Passed(_) => ()
      case Result.Failed(report) =>
        report.failure match
          case Failure.Threw(error) => fail(PropAssertions.describe(report), error)
          case Failure.Unequal(_, _) => fail(PropAssertions.describe(report))

object PropAssertions:
  /** Named lines, where `Report`'s `toString` is eight values by position.
    * Sides are left out when both are empty, as a `Boolean` property's are.
    */
  def describe[A](report: Report[A]): String =
    val limit = if report.exhausted then ", stopped by the limit: may not be a minimum" else ""
    val failure = report.failure match
      case Failure.Unequal("", "") => Nil
      case Failure.Unequal(lhs, rhs) => List(s"lhs:      $lhs", s"rhs:      $rhs")
      case Failure.Threw(error) => List(s"threw:    $error")
    val lines = List(
      s"failed at test ${report.test}",
      s"original: ${report.original}",
      s"shrunk:   ${report.shrunk} (${report.shrinks} steps$limit)"
    ) ++ failure ++ List(s"recheck:  seed ${report.seed}L, size ${report.size.toInt}")
    lines.mkString("\n")
end PropAssertions
