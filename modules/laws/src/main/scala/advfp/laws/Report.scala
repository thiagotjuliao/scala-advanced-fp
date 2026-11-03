package advfp.laws

final case class Report[A](
    seed: Long,
    size: Size,
    test: Int,
    original: A,
    shrunk: A,
    failure: Failure,
    shrinks: Int,
    exhausted: Boolean
)
