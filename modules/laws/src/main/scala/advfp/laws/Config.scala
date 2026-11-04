package advfp.laws

/** How `check` runs (docs/kernel/m01-the-law-checker.md#prop-the-runner-and-the-report).
  *
  * `seed` defaults to `System.nanoTime()`, evaluated at each `Config()`: the
  * one impure choice, made here so `check` stays a pure function of the
  * property and the configuration. `maxShrinks` limits the candidates tried,
  * not the steps taken.
  *
  * @throws IllegalArgumentException
  *   if `tests` is not positive, or `maxSize` or `maxShrinks` is negative
  */
final case class Config(
    tests: Int = 100,
    maxSize: Int = 30,
    seed: Long = System.nanoTime(),
    maxShrinks: Int = 1000
):
  require(tests > 0, "total number of tests must be positive")
  require(maxSize >= 0, "max size for tests must be non-negative")
  require(maxShrinks >= 0, "maximum number of shrinks must be non-negative")
