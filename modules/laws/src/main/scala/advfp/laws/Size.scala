package advfp.laws

/** How big a generated value may be: the upper bound of a generated
  * collection's length. The runner raises it from 0 to `maxSize` over a run,
  * so the empty cases come first (docs/kernel/m01-the-law-checker.md#size).
  *
  * Opaque, so an arbitrary `Int` (a count, an index, a negative number)
  * cannot be passed where a size is meant: `Size.of` is the only way in, and
  * `toInt` the only way out.
  */
opaque type Size = Int

object Size:
  /** `n` as a size. Zero is a size, the one the runner starts from.
    *
    * @throws IllegalArgumentException
    *   if `n` is negative
    */
  def of(n: Int): Size =
    require(n >= 0, s"size must be non-negative: $n")
    n

  extension (s: Size) def toInt: Int = s
