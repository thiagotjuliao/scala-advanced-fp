package advfp.laws

/** A pure, splittable source of random `Long`s: SplitMix64, reproducing
  * `java.util.SplittableRandom` bit for bit (see
  * docs/kernel/decisions/0008-seed-follows-splittablerandom.md).
  *
  * A seed is a value: the same seed always gives the same numbers, and two
  * seeds with the same state and gamma are the same seed, so structural
  * equality is the right one. The constructor is private because `gamma` must
  * be odd for the period to be 2^64; only `Seed(n)` and `split` make seeds.
  */
final case class Seed private (state: Long, gamma: Long):
  import Seed.{mix64, mixGamma}

  /** The next number, and the seed to draw the one after it from. Drawing
    * from the same seed twice gives the same number: thread the returned seed.
    */
  def next: (Long, Seed) =
    val newState = state + gamma
    (mix64(newState), Seed(newState, gamma))

  /** Two seeds with independent streams: a child, and this seed advanced past
    * what the child was made from. Use both in place of this one: this seed's
    * next number is the child's state, so drawing from it beside the child
    * correlates the two.
    */
  def split: (Seed, Seed) =
    val (_, Seed(newState, _)) = next
    val child = Seed(mix64(newState), mixGamma(state + 2 * gamma))
    val parent = Seed(state + 2 * gamma, gamma)
    (child, parent)

object Seed:
  private def mix64(z: Long): Long =
    val z1 = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L
    val z2 = (z1 ^ (z1 >>> 27)) * 0x94d049bb133111ebL
    z2 ^ (z2 >>> 31)

  private def mixGamma(z: Long): Long =
    val z1 = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL
    val z2 = (z1 ^ (z1 >>> 33)) * 0xc4ceb9fe1a85ec53L
    val z3 = (z2 ^ (z2 >>> 33)) | 1L
    val n = java.lang.Long.bitCount(z3 ^ (z3 >>> 1))
    if n < 24 then z3 ^ 0xaaaaaaaaaaaaaaaaL else z3

  private val GoldenGamma = 0x9e3779b97f4a7c15L

  /** A root seed, with the same stream as `new SplittableRandom(n)`. */
  def apply(n: Long): Seed = Seed(n, GoldenGamma)
