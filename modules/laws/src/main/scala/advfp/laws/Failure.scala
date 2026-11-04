package advfp.laws

/** How an input fails. A shrink can move from one case to the other: any
  * failure counts as still failing.
  */
enum Failure:
  case Unequal(lhs: String, rhs: String)
  case Threw(error: Throwable)
