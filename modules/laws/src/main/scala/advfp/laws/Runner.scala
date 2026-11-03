package advfp.laws

import Verdict.*
import Failure.*
import scala.util.control.NonFatal

object Runner:
  private def eval[A](a: A, prop: Prop[A]): Option[Failure] =
    try
      prop.verdict(a) match
        case Holds => None
        case Fails(lhs, rhs) => Some(Unequal(lhs, rhs))
    catch case NonFatal(error) => Some(Threw(error))

  private def shrink[A](
      maxShrinks: Int,
      prop: Prop[A],
      tree: Tree[A],
      failure: Failure
  ): Shrunk[A] =
    @scala.annotation.tailrec
    def loop(
        a: A,
        failure: Failure,
        rest: LazyList[Tree[A]],
        tried: Int,
        steps: Int
    ): Shrunk[A] =
      rest match
        case h #:: t =>
          if tried == maxShrinks then Shrunk(a, failure, steps, true)
          else
            eval(h.value, prop) match
              case None =>
                loop(a, failure, t, tried + 1, steps)
              case Some(smaller) =>
                loop(h.value, smaller, h.shrinks, tried + 1, steps + 1)
        case _ => Shrunk(a, failure, steps, false)

    loop(tree.value, failure, tree.shrinks, 0, 0)
  end shrink

  private final case class Shrunk[A](value: A, failure: Failure, steps: Int, exhausted: Boolean)

  def check[A](prop: Prop[A], config: Config = Config()): Result[A] =
    val Prop(gen, verdict) = prop
    val Config(tests, maxSize, seed, maxShrinks) = config
    ???

  def recheck[A](prop: Prop[A], seed: Long, size: Size, config: Config = Config()): Result[A] = ???
end Runner
