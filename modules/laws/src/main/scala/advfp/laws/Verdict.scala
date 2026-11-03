package advfp.laws

/** What a property says about one input. */
enum Verdict:
  case Holds

  /** The sides as their `toString`, which is what the report prints. Both
    * are empty for a `Boolean` property: the report already shows the input,
    * and there is no equation to show.
    */
  case Fails(lhs: String, rhs: String)
