package advfp.laws

enum Result[A]:
  case Passed(tests: Int)
  case Failed(report: Report[A])
