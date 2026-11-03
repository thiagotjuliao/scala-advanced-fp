package advfp.laws

final case class Config(
    tests: Int = 100,
    maxSize: Int = 30,
    seed: Long = System.nanoTime(),
    maxShrinks: Int = 1000
):
  require(tests > 0, "total number of tests must be positive")
  require(maxSize >= 0, "max size for tests must be non-negative")
  require(maxShrinks >= 0, "maximum number of shrinks must be non-negative")
