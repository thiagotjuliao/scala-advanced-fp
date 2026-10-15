package advfp.playground

/** Scratch space. Nothing here is verified and nothing depends on it.
  *
  * The chapters are deliberately rigid — a spec, a stub, an answer, a gate —
  * and that rigidity is wrong for the ten minutes where the question is just
  * "what does the compiler actually do with this?". Do that here.
  *
  * The whole repository is on the classpath:
  *
  * {{{
  * sbt playground/console
  * scala> import advfp.core.unsolved
  * }}}
  *
  * `*.worksheet.sc` files are git-ignored everywhere except under this module,
  * so a Metals worksheet kept here can be committed and one anywhere else
  * cannot.
  */
object Scratch:

  def question: String = "what does the compiler actually do with this?"
