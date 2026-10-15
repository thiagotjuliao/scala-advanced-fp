package advfp.core

/** Thrown by an exercise that has not been solved yet.
  *
  * A stub answers with [[unsolved]], which has type `Nothing` and therefore fits
  * any signature: the stub compiles, the module builds, and the moment a spec
  * calls it, the test fails with this exception instead of a wrong value.
  *
  * A dedicated type rather than `???`: its message says what happened, and
  * `finish-chapter` searches the answer key for it, so a solution that still
  * leans on a stub cannot be tagged.
  */
final class Unsolved extends RuntimeException("not solved yet — this exercise is still a stub")

/** The body of every stub. */
def unsolved: Nothing = throw Unsolved()
