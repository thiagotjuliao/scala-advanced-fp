package advfp.laws

/** The value `check` returns: `laws` does not assert, a test does. */
enum Result[A]:
  case Passed(tests: Int)

  /** Stopped at the first failing test. */
  case Failed(report: Report[A])
