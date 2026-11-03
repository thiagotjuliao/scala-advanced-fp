package advfp.laws

enum Failure:
  case Unequal(lhs: String, rhs: String)
  case Threw(error: Throwable)
