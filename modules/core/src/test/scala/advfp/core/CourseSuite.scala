package advfp.core

import munit.{FunSuite, Location}

import scala.compiletime.testing.typeCheckErrors

/** The base class for every chapter's specs.
  *
  * Most claims in this course are about values, and munit's own assertions
  * cover them: a stub throws [[Unsolved]], and the test goes red.
  *
  * Some claims are about *compilation* — that an instance exists, that a
  * program which reads a value at run time is rejected by an applicative
  * interface. For those it adds snippet assertions. Each takes Scala source,
  * type-checks it where the call sits — so everything in scope at the call
  * site is in scope in the snippet, imports included — and reports the outcome
  * as an ordinary test result. The indirection through a string is what keeps
  * a false claim from escalating into a build failure: `summon[Monad[ZipList]]`
  * written as code either compiles or takes the module down; written as a
  * snippet it is a test that passes or fails.
  *
  * The snippet must be a literal, or an `inline` value that folds to one: it
  * has to exist at compile time, because that is when it is checked.
  */
abstract class CourseSuite extends FunSuite:

  /** Checks a property against every sample, and names the sample that broke it.
    *
    * Laws in the chapters' specs are checked on fixed, hand-picked samples, not
    * on generated ones: the specs must not depend on the law checker the
    * learner builds in milestone 1. Pick samples that include the edges — the
    * empty value, the identity, the case a naive instance gets wrong.
    */
  def forAllSamples[A](samples: Seq[A])(property: A => Unit)(using Location): Unit =
    assert(samples.nonEmpty, "a property checked against no samples checks nothing")
    samples.foreach: sample =>
      try property(sample)
      catch
        case e: munit.FailException =>
          fail(s"the property does not hold for $sample\n${e.getMessage}", e)

  /** Asserts that the snippet compiles. The workhorse: a positive claim about
    * a type is a snippet that only type-checks when the claim holds.
    */
  inline def assertTypeChecks(inline code: String)(using Location): Unit =
    val errors = compileErrors(code)
    if errors.nonEmpty then
      fail(s"expected this to compile:\n  $code\n\nbut the compiler said:\n$errors")

  /** Asserts that the snippet does *not* compile.
    *
    * Half of what a type-level design is worth lies here. A phantom-typed
    * builder that accepts the right call order is only interesting if it also
    * rejects the wrong one, and a test that never checks the rejection cannot
    * tell a precise type from `Any`.
    */
  inline def assertTypeError(inline code: String)(using Location): Unit =
    val errors = compileErrors(code)
    if errors.isEmpty then fail(s"expected this to be rejected, but it compiled:\n  $code")

  /** Asserts that the snippet is rejected *for the stated reason*.
    *
    * Stricter than [[assertTypeError]], and the right assertion whenever the
    * error message is itself part of the design — a `compiletime.error` in an
    * inline method, say, whose whole purpose is the sentence it prints.
    *
    * The fragment is looked for in the messages alone. `compileErrors` also
    * echoes the source line each error points at, and an error reported at the
    * call site — a `compiletime.error`, a macro's error given no position —
    * echoes the line of this very call, which spells out the fragment.
    */
  inline def assertTypeErrorContains(inline code: String, inline fragment: String)(using
      Location
  ): Unit =
    val rejections = typeCheckErrors(code)
    val errors = compileErrors(code)
    if rejections.isEmpty then fail(s"expected this to be rejected, but it compiled:\n  $code")
    else if !rejections.exists(_.message.contains(fragment)) then
      fail(
        s"the snippet was rejected, but not for the expected reason.\n" +
          s"expected the error to mention: $fragment\ngot:\n$errors"
      )

  /** Asserts that the snippet is rejected with a message that *opens* with the
    * given text.
    *
    * For a message that is the whole design, such as an `@implicitNotFound`:
    * [[assertTypeErrorContains]] also passes when the text only appears inside
    * the compiler's own message — an annotation on the wrong type is printed
    * in *"Cannot prove that ..."*, and the fragment is found there.
    */
  inline def assertTypeErrorStartsWith(inline code: String, inline prefix: String)(using
      Location
  ): Unit =
    val rejections = typeCheckErrors(code)
    val errors = compileErrors(code)
    if rejections.isEmpty then fail(s"expected this to be rejected, but it compiled:\n  $code")
    else if !rejections.exists(_.message.startsWith(prefix)) then
      fail(
        s"the snippet was rejected, but its message does not open as expected.\n" +
          s"expected the error to start with: $prefix\ngot:\n$errors"
      )
end CourseSuite
