package advfp.ch03statereaderwriterandvalidated

import advfp.core.unsolved

/** Exercise 02 — Write `Reader` for a configuration and `Writer` for a log.
  *
  * `Reader[R, A]` is a program that needs an environment `R` to produce an `A`.
  *
  *   - `ask`: the environment itself, as the value.
  *   - `local`: run a program in an environment changed by `f`. Only that
  *     program sees the change; what is sequenced after it sees the original.
  *   - `readerMonad`: `pure` and `flatMap`, both steps reading the same
  *     environment.
  *   - `baseUrl`: `"https://host:port"` when the configuration is secure,
  *     `"http://host:port"` otherwise. `endpoint(path)`: the base URL followed
  *     by `path`. `onStaging(program)`: `program` run against the host with
  *     `"staging."` in front of it.
  *
  * `Writer[W, A]` is a value together with a log of how it was produced.
  *
  *   - `tell`: log `w`, and return nothing.
  *   - `writerMonad`: `pure` and `flatMap`, for any log with a `Monoid`. The
  *     log of the first step comes before the log of the second.
  *   - `collatz(n)`: the number of steps from `n` to 1 — halve an even number,
  *     take `3n + 1` of an odd one — logging every value visited, `n` and 1
  *     included. `n` must be positive.
  *
  * Write `endpoint`, `onStaging` and `collatz` with the operations above, not
  * by building the case classes by hand: the point is that the environment
  * and the log are never mentioned.
  *
  * Hint: in `collatz`, each step is "log the current value, then the steps of
  * the next one, plus one".
  */
object Exercise02:

  final case class Reader[R, A](run: R => A)

  object Reader:
    def ask[R]: Reader[R, R] = unsolved

    def local[R, A](f: R => R)(fa: Reader[R, A]): Reader[R, A] = unsolved

  given readerMonad: [R] => Monad[[A] =>> Reader[R, A]] = unsolved

  final case class Config(host: String, port: Int, secure: Boolean)

  def baseUrl: Reader[Config, String] = unsolved

  def endpoint(path: String): Reader[Config, String] = unsolved

  def onStaging[A](program: Reader[Config, A]): Reader[Config, A] = unsolved

  final case class Writer[W, A](log: W, value: A)

  object Writer:
    def tell[W](w: W): Writer[W, Unit] = unsolved

  given writerMonad: [W: Monoid] => Monad[[A] =>> Writer[W, A]] = unsolved

  def collatz(n: Int): Writer[List[Int], Int] = unsolved
end Exercise02
