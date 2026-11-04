package advfp.laws

/** A failing test, with what is needed to understand and reproduce it.
  *
  * @param seed
  *   this test's seed, not the run's: `recheck(prop, seed, size)` runs it again
  * @param test
  *   its number, from 1, so also the tests run so far
  * @param original
  *   the input the test drew
  * @param shrunk
  *   the smallest failing input the shrink reached
  * @param failure
  *   how `shrunk` fails, which may differ from how `original` does
  * @param shrinks
  *   the steps taken to `shrunk`, not the candidates tried
  * @param exhausted
  *   the limit stopped the shrink with candidates left, so `shrunk` may not be
  *   a minimum
  */
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
